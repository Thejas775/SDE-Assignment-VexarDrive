from datetime import date
from uuid import UUID

from sqlalchemy import func, or_, select
from sqlalchemy.exc import IntegrityError
from sqlalchemy.ext.asyncio import AsyncSession
from sqlalchemy.orm import selectinload

from app.core.exceptions import ConflictError, ForbiddenError, NotFoundError, ValidationError
from app.core.logging import get_logger
from app.models.driver import Driver
from app.models.enums import AssignmentStatus, DriverStatus, UserRole, VehicleStatus
from app.models.user import User
from app.models.vehicle import Vehicle
from app.models.vehicle_assignment import VehicleAssignment
from app.schemas.assignment import AssignmentCreate, AssignmentDriver, AssignmentResponse
from app.schemas.common import Page, PageParams
from app.schemas.driver import AssignedVehicle

logger = get_logger(__name__)

OPEN_ENDED = date.max


class AssignmentService:
    def __init__(self, db: AsyncSession):
        self.db = db

    async def get_or_404(self, assignment_id: UUID) -> VehicleAssignment:
        row = await self.db.scalar(
            select(VehicleAssignment)
            .options(
                selectinload(VehicleAssignment.vehicle),
                selectinload(VehicleAssignment.driver).selectinload(Driver.user),
            )
            .where(VehicleAssignment.id == assignment_id)
        )
        if row is None:
            raise NotFoundError("Assignment not found")
        return row

    async def create(self, payload: AssignmentCreate) -> AssignmentResponse:
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
            raise ConflictError(f"Driver is {driver.status} and cannot be assigned")
        if driver.license_expiry < payload.start_date:
            raise ValidationError(
                f"Driver's licence expires {driver.license_expiry}, before the assignment starts"
            )

        await self._assert_free(payload, vehicle, driver)

        assignment = VehicleAssignment(**payload.model_dump())
        self.db.add(assignment)
        try:
            await self.db.commit()
        except IntegrityError as exc:
            # The EXCLUDE constraint is the real guarantee: two concurrent
            # requests can both pass _assert_free, only one can commit.
            await self.db.rollback()
            if "no_vehicle_overlap" in str(exc.orig) or "no_driver_overlap" in str(exc.orig):
                raise ConflictError("Assignment overlaps an existing one")
            raise
        logger.info("assignment.created", assignment_id=str(assignment.id),
                    vehicle_id=str(vehicle.id), driver_id=str(driver.id))
        return self._build(assignment, vehicle, driver)

    async def _assert_free(
        self, payload: AssignmentCreate, vehicle: Vehicle, driver: Driver
    ) -> None:
        """Pre-flight check purely so the client gets a useful message."""
        new_end = payload.end_date or OPEN_ENDED
        overlaps = (
            VehicleAssignment.status == AssignmentStatus.ACTIVE,
            VehicleAssignment.start_date <= new_end,
            func.coalesce(VehicleAssignment.end_date, OPEN_ENDED) >= payload.start_date,
        )

        clash = await self.db.scalar(
            select(VehicleAssignment).where(
                VehicleAssignment.vehicle_id == payload.vehicle_id, *overlaps
            )
        )
        if clash is not None:
            raise ConflictError(
                f"{vehicle.registration_number} is already assigned from {clash.start_date} "
                f"to {clash.end_date or 'open ended'}"
            )

        clash = await self.db.scalar(
            select(VehicleAssignment).where(
                VehicleAssignment.driver_id == payload.driver_id, *overlaps
            )
        )
        if clash is not None:
            raise ConflictError(
                f"{driver.user.full_name} already has a vehicle from {clash.start_date} "
                f"to {clash.end_date or 'open ended'}"
            )

    async def end(self, assignment_id: UUID, end_date: date | None) -> AssignmentResponse:
        assignment = await self.get_or_404(assignment_id)
        if assignment.status is not AssignmentStatus.ACTIVE:
            raise ConflictError(f"Assignment is already {assignment.status}")
        end_date = end_date or date.today()
        if end_date < assignment.start_date:
            raise ValidationError("end_date cannot be before the assignment start")
        assignment.end_date = end_date
        assignment.status = AssignmentStatus.COMPLETED
        await self.db.commit()
        logger.info("assignment.ended", assignment_id=str(assignment.id), end_date=str(end_date))
        return self._build(assignment, assignment.vehicle, assignment.driver)

    async def cancel(self, assignment_id: UUID) -> AssignmentResponse:
        assignment = await self.get_or_404(assignment_id)
        if assignment.status is not AssignmentStatus.ACTIVE:
            raise ConflictError(f"Assignment is already {assignment.status}")
        assignment.status = AssignmentStatus.CANCELLED
        await self.db.commit()
        logger.info("assignment.cancelled", assignment_id=str(assignment.id))
        return self._build(assignment, assignment.vehicle, assignment.driver)

    async def list_assignments(
        self,
        params: PageParams,
        *,
        vehicle_id: UUID | None = None,
        driver_id: UUID | None = None,
        status: AssignmentStatus | None = None,
        active_on: date | None = None,
    ) -> Page[AssignmentResponse]:
        stmt = select(VehicleAssignment)
        if vehicle_id:
            stmt = stmt.where(VehicleAssignment.vehicle_id == vehicle_id)
        if driver_id:
            stmt = stmt.where(VehicleAssignment.driver_id == driver_id)
        if status:
            stmt = stmt.where(VehicleAssignment.status == status)
        if active_on:
            stmt = stmt.where(
                VehicleAssignment.start_date <= active_on,
                or_(
                    VehicleAssignment.end_date.is_(None),
                    VehicleAssignment.end_date >= active_on,
                ),
            )

        total = await self.db.scalar(
            select(func.count()).select_from(stmt.order_by(None).subquery())
        )
        rows = await self.db.scalars(
            stmt.options(
                selectinload(VehicleAssignment.vehicle),
                selectinload(VehicleAssignment.driver).selectinload(Driver.user),
            )
            .order_by(VehicleAssignment.start_date.desc())
            .offset(params.offset)
            .limit(params.page_size)
        )
        items = [self._build(a, a.vehicle, a.driver) for a in rows.unique()]
        return Page.build(items, total or 0, params)

    async def for_user(self, params: PageParams, user: User) -> Page[AssignmentResponse]:
        if user.role is UserRole.FLEET_MANAGER:
            raise ForbiddenError("Use /assignments with filters instead")
        if user.driver is None:
            raise NotFoundError("No driver profile for this account")
        return await self.list_assignments(params, driver_id=user.driver.id)

    @staticmethod
    def _build(
        assignment: VehicleAssignment, vehicle: Vehicle, driver: Driver
    ) -> AssignmentResponse:
        return AssignmentResponse(
            id=assignment.id,
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
            start_date=assignment.start_date,
            end_date=assignment.end_date,
            status=assignment.status,
            notes=assignment.notes,
            created_at=assignment.created_at,
        )
