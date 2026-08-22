from datetime import date, timedelta
from uuid import UUID

from sqlalchemy import func, or_, select
from sqlalchemy.ext.asyncio import AsyncSession
from sqlalchemy.orm import selectinload

from app.core.config import settings
from app.core.exceptions import NotFoundError, ValidationError
from app.core.logging import get_logger
from app.models.enums import MaintenanceType, VehicleStatus
from app.models.maintenance import MaintenanceRecord
from app.models.vehicle import Vehicle
from app.schemas.common import Page, PageParams
from app.schemas.driver import AssignedVehicle
from app.schemas.maintenance import (
    MaintenanceCreate,
    MaintenanceDueItem,
    MaintenanceResponse,
    MaintenanceUpdate,
)

logger = get_logger(__name__)


class MaintenanceService:
    def __init__(self, db: AsyncSession):
        self.db = db

    async def get_or_404(self, record_id: UUID) -> MaintenanceRecord:
        record = await self.db.scalar(
            select(MaintenanceRecord)
            .options(selectinload(MaintenanceRecord.vehicle))
            .where(MaintenanceRecord.id == record_id)
        )
        if record is None:
            raise NotFoundError("Maintenance record not found")
        return record

    async def create(self, payload: MaintenanceCreate, created_by: UUID) -> MaintenanceResponse:
        vehicle = await self.db.get(Vehicle, payload.vehicle_id)
        if vehicle is None:
            raise NotFoundError("Vehicle not found")

        record = MaintenanceRecord(**payload.model_dump(), created_by_id=created_by)
        self.db.add(record)
        # A workshop reading newer than ours is the better source of truth.
        if payload.odometer > vehicle.current_mileage:
            vehicle.current_mileage = payload.odometer
        await self.db.commit()
        logger.info("maintenance.created", record_id=str(record.id),
                    vehicle_id=str(vehicle.id), type=payload.maintenance_type)
        return self._build(record, vehicle)

    async def update(self, record_id: UUID, payload: MaintenanceUpdate) -> MaintenanceResponse:
        record = await self.get_or_404(record_id)
        changes = payload.model_dump(exclude_unset=True)
        merged_date = changes.get("service_date", record.service_date)
        merged_odo = changes.get("odometer", record.odometer)
        next_date = changes.get("next_service_date", record.next_service_date)
        next_km = changes.get("next_service_mileage", record.next_service_mileage)
        if next_date and next_date < merged_date:
            raise ValidationError("next_service_date cannot be before service_date")
        if next_km and next_km < merged_odo:
            raise ValidationError("next_service_mileage cannot be below the odometer")
        for field, value in changes.items():
            setattr(record, field, value)
        await self.db.commit()
        logger.info("maintenance.updated", record_id=str(record.id), fields=list(changes))
        return self._build(record, record.vehicle)

    async def list_records(
        self,
        params: PageParams,
        *,
        vehicle_id: UUID | None = None,
        maintenance_type: MaintenanceType | None = None,
        date_from: date | None = None,
        date_to: date | None = None,
    ) -> Page[MaintenanceResponse]:
        stmt = select(MaintenanceRecord)
        if vehicle_id:
            stmt = stmt.where(MaintenanceRecord.vehicle_id == vehicle_id)
        if maintenance_type:
            stmt = stmt.where(MaintenanceRecord.maintenance_type == maintenance_type)
        if date_from:
            stmt = stmt.where(MaintenanceRecord.service_date >= date_from)
        if date_to:
            stmt = stmt.where(MaintenanceRecord.service_date <= date_to)

        total = await self.db.scalar(
            select(func.count()).select_from(stmt.order_by(None).subquery())
        )
        rows = await self.db.scalars(
            stmt.options(selectinload(MaintenanceRecord.vehicle))
            .order_by(MaintenanceRecord.service_date.desc())
            .offset(params.offset)
            .limit(params.page_size)
        )
        items = [self._build(r, r.vehicle) for r in rows.unique()]
        return Page.build(items, total or 0, params)

    async def due(self) -> list[MaintenanceDueItem]:
        """Vehicles due for service, by date or by odometer, plus never-serviced ones."""
        latest = (
            select(
                MaintenanceRecord.vehicle_id,
                func.max(MaintenanceRecord.service_date).label("service_date"),
            )
            .group_by(MaintenanceRecord.vehicle_id)
            .subquery()
        )
        rows = await self.db.execute(
            select(Vehicle, MaintenanceRecord)
            .outerjoin(latest, latest.c.vehicle_id == Vehicle.id)
            .outerjoin(
                MaintenanceRecord,
                (MaintenanceRecord.vehicle_id == latest.c.vehicle_id)
                & (MaintenanceRecord.service_date == latest.c.service_date),
            )
            .where(Vehicle.status != VehicleStatus.INACTIVE)
            .order_by(Vehicle.registration_number)
        )

        today = date.today()
        date_cutoff = today + timedelta(days=settings.MAINTENANCE_DUE_WARNING_DAYS)
        out: list[MaintenanceDueItem] = []
        seen: set[UUID] = set()

        for vehicle, record in rows.all():
            if vehicle.id in seen:
                continue
            seen.add(vehicle.id)
            reasons: list[str] = []
            days_until = km_until = None

            if record is None:
                reasons.append("no maintenance history")
            else:
                if record.next_service_date:
                    days_until = (record.next_service_date - today).days
                    if record.next_service_date <= date_cutoff:
                        reasons.append(
                            "overdue by date" if days_until < 0 else f"due in {days_until} days"
                        )
                if record.next_service_mileage:
                    km_until = record.next_service_mileage - vehicle.current_mileage
                    if km_until <= settings.MAINTENANCE_DUE_MILEAGE_BUFFER:
                        reasons.append(
                            "overdue by mileage" if km_until < 0 else f"due in {km_until} km"
                        )
            if not reasons:
                continue
            out.append(
                MaintenanceDueItem(
                    vehicle=AssignedVehicle(
                        id=vehicle.id,
                        registration_number=vehicle.registration_number,
                        make=vehicle.make,
                        model=vehicle.model,
                    ),
                    current_mileage=vehicle.current_mileage,
                    last_service_date=record.service_date if record else None,
                    next_service_date=record.next_service_date if record else None,
                    next_service_mileage=record.next_service_mileage if record else None,
                    days_until_due=days_until,
                    km_until_due=km_until,
                    reasons=reasons,
                )
            )
        return out

    @staticmethod
    def _build(record: MaintenanceRecord, vehicle: Vehicle) -> MaintenanceResponse:
        return MaintenanceResponse(
            id=record.id,
            vehicle=AssignedVehicle(
                id=vehicle.id,
                registration_number=vehicle.registration_number,
                make=vehicle.make,
                model=vehicle.model,
            ),
            maintenance_type=record.maintenance_type,
            description=record.description,
            service_date=record.service_date,
            cost=record.cost,
            odometer=record.odometer,
            next_service_date=record.next_service_date,
            next_service_mileage=record.next_service_mileage,
            performed_by=record.performed_by,
            created_at=record.created_at,
        )
