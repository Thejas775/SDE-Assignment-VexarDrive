from datetime import date
from uuid import UUID

from sqlalchemy import func, or_, select
from sqlalchemy.orm import selectinload
from sqlalchemy.ext.asyncio import AsyncSession

from app.core.exceptions import ConflictError, ForbiddenError, NotFoundError, ValidationError
from app.core.logging import get_logger
from app.core.security import hash_password
from app.models.driver import Driver
from app.models.enums import AssignmentStatus, DriverStatus, TripStatus, UserRole
from app.models.incident import Incident
from app.models.trip import Trip
from app.models.user import User
from app.models.vehicle import Vehicle
from app.models.vehicle_assignment import VehicleAssignment
from app.schemas.common import Page, PageParams
from app.schemas.driver import (
    AssignedVehicle,
    AssignmentHistoryEntry,
    DriverCreate,
    DriverHistory,
    DriverPerformance,
    DriverResponse,
    DriverUpdate,
)

logger = get_logger(__name__)

OPEN_TRIP_STATUSES = (TripStatus.SCHEDULED, TripStatus.STARTED, TripStatus.IN_PROGRESS)


class DriverService:
    def __init__(self, db: AsyncSession):
        self.db = db

    async def get_or_404(self, driver_id: UUID) -> Driver:
        driver = await self.db.scalar(
            select(Driver).options(selectinload(Driver.user)).where(Driver.id == driver_id)
        )
        if driver is None:
            raise NotFoundError("Driver not found")
        return driver

    async def create(self, payload: DriverCreate) -> DriverResponse:
        if payload.license_expiry < date.today():
            raise ValidationError("Licence has already expired")
        if await self.db.scalar(select(User.id).where(User.email == payload.email)):
            raise ConflictError("An account with this email already exists")
        if await self.db.scalar(
            select(Driver.id).where(Driver.license_number == payload.license_number)
        ):
            raise ConflictError(f"Licence {payload.license_number} is already registered")

        user = User(
            email=payload.email,
            hashed_password=hash_password(payload.password),
            full_name=payload.full_name,
            phone_number=payload.phone_number,
            role=UserRole.DRIVER,
        )
        user.driver = Driver(
            license_number=payload.license_number, license_expiry=payload.license_expiry
        )
        self.db.add(user)
        await self.db.commit()
        logger.info("driver.created", driver_id=str(user.driver.id), user_id=str(user.id))
        return await self._to_response(user.driver)

    async def update(self, driver_id: UUID, payload: DriverUpdate) -> DriverResponse:
        driver = await self.get_or_404(driver_id)
        changes = payload.model_dump(exclude_unset=True)

        license_number = changes.pop("license_number", None)
        if license_number and license_number != driver.license_number:
            clash = await self.db.scalar(
                select(Driver.id).where(Driver.license_number == license_number)
            )
            if clash is not None:
                raise ConflictError(f"Licence {license_number} is already registered")
            driver.license_number = license_number

        if "license_expiry" in changes:
            driver.license_expiry = changes.pop("license_expiry")
        for field, value in changes.items():
            setattr(driver.user, field, value)

        await self.db.commit()
        logger.info("driver.updated", driver_id=str(driver.id))
        return await self._to_response(driver)

    async def set_status(self, driver_id: UUID, status: DriverStatus) -> DriverResponse:
        driver = await self.get_or_404(driver_id)
        if status is not DriverStatus.ACTIVE:
            await self._assert_no_open_trips(driver)
        driver.status = status
        # INACTIVE removes the account; SUSPENDED keeps read-only access so the
        # driver can still see their own history.
        driver.user.is_active = status is not DriverStatus.INACTIVE
        await self.db.commit()
        logger.info("driver.status_changed", driver_id=str(driver.id), status=status)
        return await self._to_response(driver)

    async def _assert_no_open_trips(self, driver: Driver) -> None:
        open_trips = await self.db.scalar(
            select(func.count())
            .select_from(Trip)
            .where(Trip.driver_id == driver.id, Trip.status.in_(OPEN_TRIP_STATUSES))
        )
        if open_trips:
            raise ConflictError(
                f"Driver has {open_trips} scheduled or running trip(s); reassign them first"
            )

    async def list_drivers(
        self,
        params: PageParams,
        *,
        search: str | None = None,
        status: DriverStatus | None = None,
        license_expiring: bool = False,
        warning_days: int = 30,
    ) -> Page[DriverResponse]:
        stmt = select(Driver).join(User, Driver.user_id == User.id)
        if search:
            term = f"%{search.strip().lower()}%"
            stmt = stmt.where(
                or_(
                    func.lower(User.full_name).like(term),
                    func.lower(User.email).like(term),
                    func.lower(Driver.license_number).like(term),
                    func.lower(func.coalesce(User.phone_number, "")).like(term),
                )
            )
        if status:
            stmt = stmt.where(Driver.status == status)
        if license_expiring:
            from datetime import timedelta

            stmt = stmt.where(
                Driver.license_expiry <= date.today() + timedelta(days=warning_days)
            )

        total = await self.db.scalar(
            select(func.count()).select_from(stmt.order_by(None).subquery())
        )
        rows = list(
            await self.db.scalars(
                stmt.options(selectinload(Driver.user))
                .order_by(User.full_name)
                .offset(params.offset)
                .limit(params.page_size)
            )
        )
        assigned = await self._current_vehicles([d.id for d in rows])
        items = [self._build(d, assigned.get(d.id)) for d in rows]
        return Page.build(items, total or 0, params)

    async def history(self, driver_id: UUID) -> DriverHistory:
        driver = await self.get_or_404(driver_id)
        rows = await self.db.execute(
            select(VehicleAssignment, Vehicle)
            .join(Vehicle, VehicleAssignment.vehicle_id == Vehicle.id)
            .where(VehicleAssignment.driver_id == driver_id)
            .order_by(VehicleAssignment.start_date.desc())
        )
        assignments = [
            AssignmentHistoryEntry(
                assignment_id=a.id,
                vehicle=AssignedVehicle(
                    id=v.id, registration_number=v.registration_number, make=v.make, model=v.model
                ),
                start_date=a.start_date,
                end_date=a.end_date,
                status=a.status,
            )
            for a, v in rows.all()
        ]
        return DriverHistory(
            assignments=assignments,
            **(await self._performance(driver)).model_dump(),
        )

    async def performance(self, driver_id: UUID) -> DriverPerformance:
        return await self._performance(await self.get_or_404(driver_id))

    async def _performance(self, driver: Driver) -> DriverPerformance:
        duration = func.extract("epoch", Trip.actual_end - Trip.actual_start) / 60
        stats = (
            await self.db.execute(
                select(
                    func.count(Trip.id),
                    func.count(Trip.id).filter(Trip.status == TripStatus.COMPLETED),
                    func.count(Trip.id).filter(Trip.status == TripStatus.CANCELLED),
                    func.coalesce(func.sum(Trip.distance_km), 0),
                    func.avg(duration).filter(Trip.actual_end.isnot(None)),
                    func.avg(Trip.distance_km).filter(Trip.distance_km.isnot(None)),
                ).where(Trip.driver_id == driver.id)
            )
        ).one()
        incidents = await self.db.scalar(
            select(func.count())
            .select_from(Incident)
            .where(Incident.reported_by_id == driver.user_id)
        )
        return DriverPerformance(
            driver_id=driver.id,
            full_name=driver.user.full_name,
            total_trips=stats[0],
            completed_trips=stats[1],
            cancelled_trips=stats[2],
            total_distance_km=float(stats[3]),
            average_trip_duration_minutes=int(stats[4]) if stats[4] is not None else None,
            average_distance_km=round(float(stats[5]), 2) if stats[5] is not None else None,
            incidents_reported=incidents or 0,
        )

    async def performance_leaderboard(self) -> list[DriverPerformance]:
        drivers = list(
            await self.db.scalars(select(Driver).options(selectinload(Driver.user)))
        )
        rows = [await self._performance(d) for d in drivers]
        return sorted(rows, key=lambda r: r.total_distance_km, reverse=True)

    async def get_for_user(self, driver_id: UUID, user: User) -> DriverResponse:
        driver = await self.get_or_404(driver_id)
        if user.role is not UserRole.FLEET_MANAGER and driver.user_id != user.id:
            raise ForbiddenError("You may only view your own driver profile")
        return await self._to_response(driver)

    async def me(self, user: User) -> DriverResponse:
        if user.driver is None:
            raise NotFoundError("No driver profile for this account")
        return await self._to_response(await self.get_or_404(user.driver.id))

    async def _current_vehicles(self, driver_ids: list[UUID]) -> dict[UUID, AssignedVehicle]:
        """One query for the whole page instead of one per driver."""
        if not driver_ids:
            return {}
        today = date.today()
        rows = await self.db.execute(
            select(VehicleAssignment.driver_id, Vehicle)
            .join(Vehicle, VehicleAssignment.vehicle_id == Vehicle.id)
            .where(
                VehicleAssignment.driver_id.in_(driver_ids),
                VehicleAssignment.status == AssignmentStatus.ACTIVE,
                VehicleAssignment.start_date <= today,
                or_(VehicleAssignment.end_date.is_(None), VehicleAssignment.end_date >= today),
            )
        )
        return {
            driver_id: AssignedVehicle(
                id=v.id, registration_number=v.registration_number, make=v.make, model=v.model
            )
            for driver_id, v in rows.all()
        }

    async def _to_response(self, driver: Driver) -> DriverResponse:
        assigned = await self._current_vehicles([driver.id])
        return self._build(driver, assigned.get(driver.id))

    @staticmethod
    def _build(driver: Driver, vehicle: AssignedVehicle | None) -> DriverResponse:
        return DriverResponse(
            id=driver.id,
            user_id=driver.user_id,
            email=driver.user.email,
            full_name=driver.user.full_name,
            phone_number=driver.user.phone_number,
            license_number=driver.license_number,
            license_expiry=driver.license_expiry,
            status=driver.status,
            can_login=driver.user.is_active,
            assigned_vehicle=vehicle,
            created_at=driver.created_at,
        )
