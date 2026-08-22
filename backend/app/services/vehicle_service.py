from datetime import date, timedelta
from uuid import UUID

from sqlalchemy import func, or_, select
from sqlalchemy.ext.asyncio import AsyncSession

from app.core.exceptions import ConflictError, ForbiddenError, NotFoundError, ValidationError
from app.core.qr import parse_vehicle_code, vehicle_payload
from app.core.logging import get_logger
from app.models.enums import FuelType, TripStatus, UserRole, VehicleStatus, VehicleType
from app.models.trip import Trip
from app.models.user import User
from app.models.vehicle import Vehicle
from app.models.vehicle_assignment import VehicleAssignment
from app.schemas.common import Page, PageParams
from app.schemas.vehicle import VehicleCreate, VehicleResponse, VehicleUpdate

logger = get_logger(__name__)

# ON_TRIP is owned by the trip workflow, not by manual edits.
MANAGER_SETTABLE_STATUSES = {
    VehicleStatus.AVAILABLE,
    VehicleStatus.IN_MAINTENANCE,
    VehicleStatus.INACTIVE,
}


class VehicleService:
    def __init__(self, db: AsyncSession):
        self.db = db

    async def _by_registration(self, registration: str) -> Vehicle | None:
        return await self.db.scalar(
            select(Vehicle).where(Vehicle.registration_number == registration)
        )

    async def get_or_404(self, vehicle_id: UUID) -> Vehicle:
        vehicle = await self.db.get(Vehicle, vehicle_id)
        if vehicle is None:
            raise NotFoundError("Vehicle not found")
        return vehicle

    async def create(self, payload: VehicleCreate) -> VehicleResponse:
        if await self._by_registration(payload.registration_number):
            raise ConflictError(
                f"Vehicle {payload.registration_number} is already registered"
            )
        vehicle = Vehicle(**payload.model_dump())
        self.db.add(vehicle)
        await self.db.commit()
        logger.info("vehicle.created", vehicle_id=str(vehicle.id),
                    registration=vehicle.registration_number)
        return VehicleResponse.model_validate(vehicle)

    async def update(self, vehicle_id: UUID, payload: VehicleUpdate) -> VehicleResponse:
        vehicle = await self.get_or_404(vehicle_id)
        changes = payload.model_dump(exclude_unset=True)

        new_registration = changes.get("registration_number")
        if new_registration and new_registration != vehicle.registration_number:
            clash = await self._by_registration(new_registration)
            if clash is not None:
                raise ConflictError(f"Vehicle {new_registration} is already registered")

        new_status = changes.get("status")
        if new_status is not None and new_status not in MANAGER_SETTABLE_STATUSES:
            raise ValidationError(
                f"{new_status} is set by the trip workflow and cannot be assigned manually"
            )
        if new_status == VehicleStatus.INACTIVE:
            await self._assert_deactivatable(vehicle)

        new_mileage = changes.get("current_mileage")
        if new_mileage is not None and new_mileage < vehicle.current_mileage:
            raise ValidationError(
                f"Mileage cannot decrease (current {vehicle.current_mileage} km)"
            )

        for field, value in changes.items():
            setattr(vehicle, field, value)
        await self.db.commit()
        logger.info("vehicle.updated", vehicle_id=str(vehicle.id), fields=list(changes))
        return VehicleResponse.model_validate(vehicle)

    async def deactivate(self, vehicle_id: UUID) -> VehicleResponse:
        vehicle = await self.get_or_404(vehicle_id)
        await self._assert_deactivatable(vehicle)
        vehicle.status = VehicleStatus.INACTIVE
        await self.db.commit()
        logger.info("vehicle.deactivated", vehicle_id=str(vehicle.id))
        return VehicleResponse.model_validate(vehicle)

    async def activate(self, vehicle_id: UUID) -> VehicleResponse:
        vehicle = await self.get_or_404(vehicle_id)
        if vehicle.status != VehicleStatus.INACTIVE:
            raise ConflictError(f"Vehicle is already {vehicle.status}")
        vehicle.status = VehicleStatus.AVAILABLE
        await self.db.commit()
        logger.info("vehicle.activated", vehicle_id=str(vehicle.id))
        return VehicleResponse.model_validate(vehicle)

    async def _assert_deactivatable(self, vehicle: Vehicle) -> None:
        if vehicle.status == VehicleStatus.ON_TRIP:
            raise ConflictError("Vehicle is on a trip and cannot be deactivated")
        open_trips = await self.db.scalar(
            select(func.count())
            .select_from(Trip)
            .where(
                Trip.vehicle_id == vehicle.id,
                Trip.status.in_([TripStatus.SCHEDULED, TripStatus.STARTED, TripStatus.IN_PROGRESS]),
            )
        )
        if open_trips:
            raise ConflictError(
                f"Vehicle has {open_trips} scheduled or running trip(s); cancel them first"
            )

    async def list_vehicles(
        self,
        params: PageParams,
        *,
        search: str | None = None,
        status: VehicleStatus | None = None,
        vehicle_type: VehicleType | None = None,
        fuel_type: FuelType | None = None,
        expiring_documents: bool = False,
        warning_days: int = 30,
    ) -> Page[VehicleResponse]:
        stmt = select(Vehicle)
        if search:
            term = f"%{search.strip().lower()}%"
            stmt = stmt.where(
                or_(
                    func.lower(Vehicle.registration_number).like(term),
                    func.lower(Vehicle.make).like(term),
                    func.lower(Vehicle.model).like(term),
                )
            )
        if status:
            stmt = stmt.where(Vehicle.status == status)
        if vehicle_type:
            stmt = stmt.where(Vehicle.vehicle_type == vehicle_type)
        if fuel_type:
            stmt = stmt.where(Vehicle.fuel_type == fuel_type)
        if expiring_documents:
            cutoff = date.today() + timedelta(days=warning_days)
            stmt = stmt.where(
                or_(Vehicle.insurance_expiry <= cutoff, Vehicle.registration_expiry <= cutoff)
            )

        total = await self.db.scalar(
            select(func.count()).select_from(stmt.order_by(None).subquery())
        )
        rows = await self.db.scalars(
            stmt.order_by(Vehicle.registration_number)
            .offset(params.offset)
            .limit(params.page_size)
        )
        return Page.build([VehicleResponse.model_validate(v) for v in rows], total or 0, params)

    async def qr_payload(self, vehicle_id: UUID) -> str:
        vehicle = await self.get_or_404(vehicle_id)
        return vehicle_payload(vehicle.id)

    async def lookup_by_code(self, code: str, user: User) -> VehicleResponse:
        try:
            vehicle_id = parse_vehicle_code(code)
        except ValueError:
            raise ValidationError("Not a recognised vehicle code")
        return await self.get_for_user(vehicle_id, user)

    async def get_for_user(self, vehicle_id: UUID, user: User) -> VehicleResponse:
        vehicle = await self.get_or_404(vehicle_id)
        if user.role == UserRole.FLEET_MANAGER:
            return VehicleResponse.model_validate(vehicle)
        if await self._is_assigned_today(vehicle.id, user):
            return VehicleResponse.model_validate(vehicle)
        raise ForbiddenError("This vehicle is not assigned to you")

    async def current_for_driver(self, user: User) -> VehicleResponse:
        if user.driver is None:
            raise NotFoundError("No driver profile for this account")
        today = date.today()
        vehicle = await self.db.scalar(
            select(Vehicle)
            .join(VehicleAssignment, VehicleAssignment.vehicle_id == Vehicle.id)
            .where(
                VehicleAssignment.driver_id == user.driver.id,
                VehicleAssignment.start_date <= today,
                or_(VehicleAssignment.end_date.is_(None), VehicleAssignment.end_date >= today),
            )
        )
        if vehicle is None:
            raise NotFoundError("No vehicle is currently assigned to you")
        return VehicleResponse.model_validate(vehicle)

    async def _is_assigned_today(self, vehicle_id: UUID, user: User) -> bool:
        if user.driver is None:
            return False
        today = date.today()
        found = await self.db.scalar(
            select(VehicleAssignment.id).where(
                VehicleAssignment.vehicle_id == vehicle_id,
                VehicleAssignment.driver_id == user.driver.id,
                VehicleAssignment.start_date <= today,
                or_(VehicleAssignment.end_date.is_(None), VehicleAssignment.end_date >= today),
            )
        )
        return found is not None
