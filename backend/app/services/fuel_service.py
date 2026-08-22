from datetime import date
from decimal import Decimal, ROUND_HALF_UP
from uuid import UUID

from sqlalchemy import func, select
from sqlalchemy.ext.asyncio import AsyncSession
from sqlalchemy.orm import selectinload

from app.core.exceptions import NotFoundError, ValidationError
from app.core.logging import get_logger
from app.models.fuel import FuelLog
from app.models.vehicle import Vehicle
from app.schemas.common import Page, PageParams
from app.schemas.driver import AssignedVehicle
from app.schemas.fuel import (
    FuelCreate,
    FuelEfficiency,
    FuelEfficiencyEntry,
    FuelResponse,
    FuelUpdate,
)

logger = get_logger(__name__)
TWO_DP = Decimal("0.01")


def _round(value: Decimal) -> Decimal:
    return value.quantize(TWO_DP, rounding=ROUND_HALF_UP)


class FuelService:
    def __init__(self, db: AsyncSession):
        self.db = db

    async def get_or_404(self, fuel_id: UUID) -> FuelLog:
        row = await self.db.scalar(
            select(FuelLog).options(selectinload(FuelLog.vehicle)).where(FuelLog.id == fuel_id)
        )
        if row is None:
            raise NotFoundError("Fuel log not found")
        return row

    async def create(self, payload: FuelCreate, created_by: UUID) -> FuelResponse:
        vehicle = await self.db.get(Vehicle, payload.vehicle_id)
        if vehicle is None:
            raise NotFoundError("Vehicle not found")

        previous = await self.db.scalar(
            select(func.max(FuelLog.odometer)).where(FuelLog.vehicle_id == vehicle.id)
        )
        if previous is not None and payload.odometer < previous:
            raise ValidationError(
                f"Odometer {payload.odometer} is below the last recorded fill ({previous} km)"
            )

        entry = FuelLog(**payload.model_dump(), created_by_id=created_by)
        self.db.add(entry)
        if payload.odometer > vehicle.current_mileage:
            vehicle.current_mileage = payload.odometer
        await self.db.commit()
        logger.info("fuel.logged", fuel_id=str(entry.id), vehicle_id=str(vehicle.id),
                    litres=str(payload.quantity_litres))
        return self._build(entry, vehicle)

    async def update(self, fuel_id: UUID, payload: FuelUpdate) -> FuelResponse:
        entry = await self.get_or_404(fuel_id)
        for field, value in payload.model_dump(exclude_unset=True).items():
            setattr(entry, field, value)
        await self.db.commit()
        return self._build(entry, entry.vehicle)

    async def delete(self, fuel_id: UUID) -> None:
        entry = await self.get_or_404(fuel_id)
        await self.db.delete(entry)
        await self.db.commit()
        logger.info("fuel.deleted", fuel_id=str(fuel_id))

    async def list_logs(
        self,
        params: PageParams,
        *,
        vehicle_id: UUID | None = None,
        driver_id: UUID | None = None,
        date_from: date | None = None,
        date_to: date | None = None,
    ) -> Page[FuelResponse]:
        stmt = select(FuelLog)
        if vehicle_id:
            stmt = stmt.where(FuelLog.vehicle_id == vehicle_id)
        if driver_id:
            stmt = stmt.where(FuelLog.driver_id == driver_id)
        if date_from:
            stmt = stmt.where(FuelLog.fuel_date >= date_from)
        if date_to:
            stmt = stmt.where(FuelLog.fuel_date <= date_to)

        total = await self.db.scalar(
            select(func.count()).select_from(stmt.order_by(None).subquery())
        )
        rows = await self.db.scalars(
            stmt.options(selectinload(FuelLog.vehicle))
            .order_by(FuelLog.fuel_date.desc(), FuelLog.odometer.desc())
            .offset(params.offset)
            .limit(params.page_size)
        )
        return Page.build([self._build(f, f.vehicle) for f in rows.unique()], total or 0, params)

    async def efficiency(self, vehicle_id: UUID) -> FuelEfficiency:
        """Fuel economy between consecutive full-tank fills.

        Only full-tank fills give a valid reading: the tank was full at both
        ends, so the litres bought exactly replace the fuel burned over the
        distance covered. A partial fill leaves an unknown amount in the tank.
        """
        vehicle = await self.db.get(Vehicle, vehicle_id)
        if vehicle is None:
            raise NotFoundError("Vehicle not found")

        logs = list(
            await self.db.scalars(
                select(FuelLog)
                .where(FuelLog.vehicle_id == vehicle_id)
                .order_by(FuelLog.odometer)
            )
        )
        entries: list[FuelEfficiencyEntry] = []
        full_tanks = [log for log in logs if log.full_tank]
        for previous, current in zip(full_tanks, full_tanks[1:]):
            distance = current.odometer - previous.odometer
            if distance <= 0 or current.quantity_litres <= 0:
                continue
            entries.append(
                FuelEfficiencyEntry(
                    fuel_log_id=current.id,
                    fuel_date=current.fuel_date,
                    odometer=current.odometer,
                    distance_km=distance,
                    quantity_litres=current.quantity_litres,
                    cost=current.cost,
                    km_per_litre=_round(Decimal(distance) / current.quantity_litres),
                    cost_per_km=_round(current.cost / Decimal(distance)),
                )
            )

        total_litres = sum((log.quantity_litres for log in logs), Decimal("0"))
        total_cost = sum((log.cost for log in logs), Decimal("0"))
        measured_distance = sum(e.distance_km for e in entries)
        measured_litres = sum((e.quantity_litres for e in entries), Decimal("0"))
        measured_cost = sum((e.cost for e in entries), Decimal("0"))

        return FuelEfficiency(
            vehicle=AssignedVehicle(
                id=vehicle.id,
                registration_number=vehicle.registration_number,
                make=vehicle.make,
                model=vehicle.model,
            ),
            fills=len(logs),
            total_litres=_round(total_litres),
            total_cost=_round(total_cost),
            distance_km=measured_distance,
            average_km_per_litre=(
                _round(Decimal(measured_distance) / measured_litres) if measured_litres else None
            ),
            average_cost_per_km=(
                _round(measured_cost / Decimal(measured_distance)) if measured_distance else None
            ),
            entries=entries,
        )

    @staticmethod
    def _build(entry: FuelLog, vehicle: Vehicle) -> FuelResponse:
        return FuelResponse(
            id=entry.id,
            vehicle=AssignedVehicle(
                id=vehicle.id,
                registration_number=vehicle.registration_number,
                make=vehicle.make,
                model=vehicle.model,
            ),
            driver_id=entry.driver_id,
            trip_id=entry.trip_id,
            fuel_date=entry.fuel_date,
            quantity_litres=entry.quantity_litres,
            cost=entry.cost,
            odometer=entry.odometer,
            full_tank=entry.full_tank,
            station=entry.station,
            notes=entry.notes,
            created_at=entry.created_at,
        )
