from datetime import date, datetime, timezone
from decimal import Decimal
from uuid import UUID

from sqlalchemy import func, or_, select, text
from sqlalchemy.ext.asyncio import AsyncSession
from sqlalchemy.orm import selectinload

from app.core.exceptions import ConflictError, ForbiddenError, NotFoundError, ValidationError
from app.core.logging import get_logger
from app.models.driver import Driver
from app.models.enums import (
    AssignmentStatus,
    NotificationType,
    DriverStatus,
    TripStatus,
    UserRole,
    VehicleStatus,
)
from app.models.enums_transitions import ACTIVE_TRIP_STATUSES, can_transition
from app.models.trip import Trip
from app.models.user import User
from app.models.vehicle import Vehicle
from app.models.vehicle_assignment import VehicleAssignment
from app.schemas.assignment import AssignmentDriver
from app.schemas.common import Page, PageParams
from app.schemas.driver import AssignedVehicle
from app.schemas.trip import TripComplete, TripCreate, TripResponse, TripStart
from app.services.notification_service import NotificationService

logger = get_logger(__name__)


class TripService:
    def __init__(self, db: AsyncSession):
        self.db = db

    async def get_or_404(self, trip_id: UUID) -> Trip:
        trip = await self.db.scalar(
            select(Trip)
            .options(
                selectinload(Trip.vehicle),
                selectinload(Trip.driver).selectinload(Driver.user),
            )
            .where(Trip.id == trip_id)
        )
        if trip is None:
            raise NotFoundError("Trip not found")
        return trip

    async def _next_trip_number(self) -> str:
        value = await self.db.scalar(text("SELECT nextval('trip_number_seq')"))
        return f"TRP{value}"

    async def create(self, payload: TripCreate) -> TripResponse:
        vehicle = await self.db.get(Vehicle, payload.vehicle_id)
        if vehicle is None:
            raise NotFoundError("Vehicle not found")
        if vehicle.status is VehicleStatus.INACTIVE:
            raise ConflictError(f"Vehicle {vehicle.registration_number} is inactive")

        driver = await self.db.scalar(
            select(Driver).options(selectinload(Driver.user)).where(Driver.id == payload.driver_id)
        )
        if driver is None:
            raise NotFoundError("Driver not found")
        if driver.status is not DriverStatus.ACTIVE:
            raise ConflictError(f"Driver is {driver.status} and cannot be given a trip")

        await self._assert_assigned(payload, vehicle, driver)
        await self._assert_driver_free(payload)

        trip = Trip(trip_number=await self._next_trip_number(), **payload.model_dump())
        self.db.add(trip)
        await self.db.flush()
        await NotificationService(self.db).notify(
            driver.user_id,
            NotificationType.TRIP_ASSIGNED,
            f"New trip {trip.trip_number}",
            f"{payload.source} to {payload.destination}, "
            f"departing {payload.scheduled_start:%d %b %Y %H:%M}.",
            entity_type="trip",
            entity_id=trip.id,
        )
        await self.db.commit()
        logger.info("trip.created", trip_id=str(trip.id), trip_number=trip.trip_number)
        return self._build(trip, vehicle, driver)

    async def _assert_assigned(
        self, payload: TripCreate, vehicle: Vehicle, driver: Driver
    ) -> None:
        """A trip may only use a vehicle the driver actually holds (spec section 8)."""
        day = payload.scheduled_start.date()
        found = await self.db.scalar(
            select(VehicleAssignment.id).where(
                VehicleAssignment.vehicle_id == payload.vehicle_id,
                VehicleAssignment.driver_id == payload.driver_id,
                VehicleAssignment.status == AssignmentStatus.ACTIVE,
                VehicleAssignment.start_date <= day,
                or_(VehicleAssignment.end_date.is_(None), VehicleAssignment.end_date >= day),
            )
        )
        if found is None:
            raise ConflictError(
                f"{driver.user.full_name} is not assigned to "
                f"{vehicle.registration_number} on {day}"
            )

    async def _assert_driver_free(self, payload: TripCreate) -> None:
        clash = await self.db.scalar(
            select(Trip).where(
                Trip.driver_id == payload.driver_id,
                Trip.status.in_((TripStatus.SCHEDULED, *ACTIVE_TRIP_STATUSES)),
                Trip.scheduled_start < payload.scheduled_end,
                Trip.scheduled_end > payload.scheduled_start,
            )
        )
        if clash is not None:
            raise ConflictError(
                f"Driver already has trip {clash.trip_number} in that window"
            )

    async def start(self, trip_id: UUID, payload: TripStart, user: User) -> TripResponse:
        trip = await self._driver_trip(trip_id, user)
        self._assert_transition(trip, TripStatus.STARTED)
        if payload.start_odometer < trip.vehicle.current_mileage:
            raise ValidationError(
                f"Odometer {payload.start_odometer} is below the recorded "
                f"{trip.vehicle.current_mileage} km"
            )
        trip.status = TripStatus.STARTED
        trip.actual_start = datetime.now(timezone.utc)
        trip.start_odometer = payload.start_odometer
        trip.start_latitude = payload.latitude
        trip.start_longitude = payload.longitude
        trip.vehicle.status = VehicleStatus.ON_TRIP
        await self.db.commit()
        logger.info("trip.started", trip_number=trip.trip_number, odometer=payload.start_odometer)
        return self._build(trip, trip.vehicle, trip.driver)

    async def update_status(self, trip_id: UUID, target: TripStatus, user: User) -> TripResponse:
        trip = await self._driver_trip(trip_id, user)
        if target in (TripStatus.COMPLETED, TripStatus.CANCELLED):
            raise ValidationError(f"Use the dedicated endpoint to move a trip to {target}")
        self._assert_transition(trip, target)
        trip.status = target
        await self.db.commit()
        logger.info("trip.status", trip_number=trip.trip_number, status=target)
        return self._build(trip, trip.vehicle, trip.driver)

    async def complete(self, trip_id: UUID, payload: TripComplete, user: User) -> TripResponse:
        trip = await self._driver_trip(trip_id, user)
        self._assert_transition(trip, TripStatus.COMPLETED)
        if trip.start_odometer is None:
            raise ConflictError("Trip was never started")
        if payload.end_odometer < trip.start_odometer:
            raise ValidationError(
                f"Ending odometer {payload.end_odometer} is below the starting "
                f"{trip.start_odometer}"
            )
        trip.status = TripStatus.COMPLETED
        trip.actual_end = datetime.now(timezone.utc)
        trip.end_odometer = payload.end_odometer
        trip.end_latitude = payload.latitude
        trip.end_longitude = payload.longitude
        trip.distance_km = Decimal(payload.end_odometer - trip.start_odometer)
        if payload.notes:
            trip.notes = payload.notes
        trip.vehicle.current_mileage = payload.end_odometer
        trip.vehicle.status = VehicleStatus.AVAILABLE
        await NotificationService(self.db).notify_managers(
            NotificationType.TRIP_COMPLETED,
            f"Trip {trip.trip_number} completed",
            f"{trip.source} to {trip.destination}, {trip.distance_km} km.",
            entity_type="trip",
            entity_id=trip.id,
        )
        await self.db.commit()
        logger.info("trip.completed", trip_number=trip.trip_number,
                    distance_km=str(trip.distance_km))
        return self._build(trip, trip.vehicle, trip.driver)

    async def cancel(self, trip_id: UUID, reason: str | None = None) -> TripResponse:
        trip = await self.get_or_404(trip_id)
        self._assert_transition(trip, TripStatus.CANCELLED)
        trip.status = TripStatus.CANCELLED
        if reason:
            trip.notes = reason
        if trip.vehicle.status is VehicleStatus.ON_TRIP:
            trip.vehicle.status = VehicleStatus.AVAILABLE
        await self.db.commit()
        logger.info("trip.cancelled", trip_number=trip.trip_number)
        return self._build(trip, trip.vehicle, trip.driver)

    @staticmethod
    def _assert_transition(trip: Trip, target: TripStatus) -> None:
        if not can_transition(trip.status, target):
            raise ConflictError(f"Cannot move a {trip.status} trip to {target}")

    async def _driver_trip(self, trip_id: UUID, user: User) -> Trip:
        trip = await self.get_or_404(trip_id)
        if user.role is UserRole.FLEET_MANAGER:
            return trip
        if user.driver is None or trip.driver_id != user.driver.id:
            raise ForbiddenError("This trip is not assigned to you")
        return trip

    async def get_for_user(self, trip_id: UUID, user: User) -> TripResponse:
        trip = await self._driver_trip(trip_id, user)
        return self._build(trip, trip.vehicle, trip.driver)

    async def list_trips(
        self,
        params: PageParams,
        *,
        vehicle_id: UUID | None = None,
        driver_id: UUID | None = None,
        status: TripStatus | None = None,
        active_only: bool = False,
        scheduled_on: date | None = None,
    ) -> Page[TripResponse]:
        stmt = select(Trip)
        if vehicle_id:
            stmt = stmt.where(Trip.vehicle_id == vehicle_id)
        if driver_id:
            stmt = stmt.where(Trip.driver_id == driver_id)
        if status:
            stmt = stmt.where(Trip.status == status)
        if active_only:
            stmt = stmt.where(Trip.status.in_(ACTIVE_TRIP_STATUSES))
        if scheduled_on:
            stmt = stmt.where(func.date(Trip.scheduled_start) == scheduled_on)

        total = await self.db.scalar(
            select(func.count()).select_from(stmt.order_by(None).subquery())
        )
        rows = await self.db.scalars(
            stmt.options(
                selectinload(Trip.vehicle), selectinload(Trip.driver).selectinload(Driver.user)
            )
            .order_by(Trip.scheduled_start.desc())
            .offset(params.offset)
            .limit(params.page_size)
        )
        items = [self._build(t, t.vehicle, t.driver) for t in rows.unique()]
        return Page.build(items, total or 0, params)

    async def for_driver(self, params: PageParams, user: User, **kwargs) -> Page[TripResponse]:
        if user.driver is None:
            raise NotFoundError("No driver profile for this account")
        return await self.list_trips(params, driver_id=user.driver.id, **kwargs)

    @staticmethod
    def _build(trip: Trip, vehicle: Vehicle, driver: Driver) -> TripResponse:
        return TripResponse(
            id=trip.id,
            trip_number=trip.trip_number,
            vehicle=AssignedVehicle(
                id=vehicle.id,
                registration_number=vehicle.registration_number,
                make=vehicle.make,
                model=vehicle.model,
            ),
            driver=AssignmentDriver(
                id=driver.id,
                full_name=driver.user.full_name,
                license_number=driver.license_number,
            ),
            source=trip.source,
            destination=trip.destination,
            scheduled_start=trip.scheduled_start,
            scheduled_end=trip.scheduled_end,
            status=trip.status,
            actual_start=trip.actual_start,
            actual_end=trip.actual_end,
            start_odometer=trip.start_odometer,
            end_odometer=trip.end_odometer,
            start_latitude=trip.start_latitude,
            start_longitude=trip.start_longitude,
            end_latitude=trip.end_latitude,
            end_longitude=trip.end_longitude,
            distance_km=trip.distance_km,
            notes=trip.notes,
            created_at=trip.created_at,
        )
