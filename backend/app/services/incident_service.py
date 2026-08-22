from datetime import date, datetime, timezone
from uuid import UUID

from sqlalchemy import func, or_, select
from sqlalchemy.ext.asyncio import AsyncSession
from sqlalchemy.orm import selectinload

from app.core.exceptions import ConflictError, ForbiddenError, NotFoundError, ValidationError
from app.core.logging import get_logger
from app.models.enums import (
    AssignmentStatus,
    IncidentSeverity,
    IncidentStatus,
    NotificationType,
    UserRole,
    VehicleStatus,
)
from app.models.incident import Incident
from app.models.trip import Trip
from app.models.user import User
from app.models.vehicle import Vehicle
from app.models.vehicle_assignment import VehicleAssignment
from app.schemas.common import Page, PageParams
from app.schemas.driver import AssignedVehicle
from app.schemas.incident import IncidentCreate, IncidentResponse, IncidentUpdate, ReporterSummary
from app.services.notification_service import NotificationService

logger = get_logger(__name__)

INCIDENT_TRANSITIONS: dict[IncidentStatus, set[IncidentStatus]] = {
    IncidentStatus.OPEN: {IncidentStatus.IN_PROGRESS, IncidentStatus.RESOLVED},
    IncidentStatus.IN_PROGRESS: {IncidentStatus.RESOLVED, IncidentStatus.OPEN},
    IncidentStatus.RESOLVED: {IncidentStatus.OPEN},
}


class IncidentService:
    def __init__(self, db: AsyncSession):
        self.db = db

    async def get_or_404(self, incident_id: UUID) -> Incident:
        incident = await self.db.scalar(
            select(Incident)
            .options(
                selectinload(Incident.vehicle),
                selectinload(Incident.reported_by),
                selectinload(Incident.assigned_to),
            )
            .where(Incident.id == incident_id)
        )
        if incident is None:
            raise NotFoundError("Incident not found")
        return incident

    async def report(self, payload: IncidentCreate, user: User) -> IncidentResponse:
        vehicle = await self.db.get(Vehicle, payload.vehicle_id)
        if vehicle is None:
            raise NotFoundError("Vehicle not found")

        trip = None
        if payload.trip_id:
            trip = await self.db.get(Trip, payload.trip_id)
            if trip is None:
                raise NotFoundError("Trip not found")
            if trip.vehicle_id != vehicle.id:
                raise ValidationError("Trip does not belong to that vehicle")

        if user.role is not UserRole.FLEET_MANAGER:
            await self._assert_driver_may_report(vehicle.id, trip, user)

        incident = Incident(
            vehicle_id=vehicle.id,
            trip_id=payload.trip_id,
            reported_by_id=user.id,
            title=payload.title,
            description=payload.description,
            severity=payload.severity,
        )
        self.db.add(incident)

        # A critical fault takes an idle vehicle off the road immediately; one
        # already on a trip is left alone so the driver can finish safely.
        if payload.severity is IncidentSeverity.CRITICAL and vehicle.status is VehicleStatus.AVAILABLE:
            vehicle.status = VehicleStatus.IN_MAINTENANCE

        await self.db.flush()
        await NotificationService(self.db).notify_managers(
            NotificationType.INCIDENT_REPORTED,
            f"{payload.severity} issue: {vehicle.registration_number}",
            f"{payload.title} - reported by {user.full_name}.",
            entity_type="incident",
            entity_id=incident.id,
        )
        await self.db.commit()
        logger.info("incident.reported", incident_id=str(incident.id),
                    vehicle_id=str(vehicle.id), severity=payload.severity)
        return await self._response(incident.id)

    async def _assert_driver_may_report(
        self, vehicle_id: UUID, trip: Trip | None, user: User
    ) -> None:
        if user.driver is None:
            raise ForbiddenError("No driver profile for this account")
        if trip is not None and trip.driver_id != user.driver.id:
            raise ForbiddenError("This trip is not assigned to you")
        today = date.today()
        assigned = await self.db.scalar(
            select(VehicleAssignment.id).where(
                VehicleAssignment.vehicle_id == vehicle_id,
                VehicleAssignment.driver_id == user.driver.id,
                VehicleAssignment.status == AssignmentStatus.ACTIVE,
                VehicleAssignment.start_date <= today,
                or_(VehicleAssignment.end_date.is_(None), VehicleAssignment.end_date >= today),
            )
        )
        if assigned is None:
            raise ForbiddenError("You may only report issues for your assigned vehicle")

    async def update(self, incident_id: UUID, payload: IncidentUpdate) -> IncidentResponse:
        incident = await self.get_or_404(incident_id)
        changes = payload.model_dump(exclude_unset=True)
        if "assigned_to_id" in changes:
            assignee_id = changes.pop("assigned_to_id")
            # Set the relationship, not just the column: the loaded object
            # would otherwise keep serialising the stale assignee.
            incident.assigned_to = await self._assert_manager(assignee_id) if assignee_id else None
        for field, value in changes.items():
            setattr(incident, field, value)
        await self.db.commit()
        logger.info("incident.updated", incident_id=str(incident.id), fields=list(changes))
        return await self._response(incident.id)

    async def assign(self, incident_id: UUID, assignee_id: UUID) -> IncidentResponse:
        incident = await self.get_or_404(incident_id)
        incident.assigned_to = await self._assert_manager(assignee_id)
        if incident.status is IncidentStatus.OPEN:
            incident.status = IncidentStatus.IN_PROGRESS
        await self.db.commit()
        logger.info("incident.assigned", incident_id=str(incident.id), assignee=str(assignee_id))
        return await self._response(incident.id)

    async def set_status(
        self, incident_id: UUID, target: IncidentStatus, resolution_notes: str | None
    ) -> IncidentResponse:
        incident = await self.get_or_404(incident_id)
        if target not in INCIDENT_TRANSITIONS[incident.status]:
            raise ConflictError(f"Cannot move a {incident.status} incident to {target}")
        if target is IncidentStatus.RESOLVED:
            if not resolution_notes:
                raise ValidationError("resolution_notes is required when resolving an incident")
            incident.resolution_notes = resolution_notes
            incident.resolved_at = datetime.now(timezone.utc)
            await NotificationService(self.db).notify(
                incident.reported_by_id,
                NotificationType.INCIDENT_RESOLVED,
                f"Resolved: {incident.title}",
                resolution_notes,
                entity_type="incident",
                entity_id=incident.id,
            )
        if target is IncidentStatus.OPEN:
            incident.resolved_at = None
        incident.status = target
        await self.db.commit()
        logger.info("incident.status", incident_id=str(incident.id), status=target)
        return await self._response(incident.id)

    async def _assert_manager(self, user_id: UUID) -> User:
        user = await self.db.get(User, user_id)
        if user is None:
            raise NotFoundError("Assignee not found")
        if user.role is not UserRole.FLEET_MANAGER:
            raise ValidationError("Incidents can only be assigned to a fleet manager")
        return user

    async def list_incidents(
        self,
        params: PageParams,
        *,
        vehicle_id: UUID | None = None,
        status: IncidentStatus | None = None,
        severity: IncidentSeverity | None = None,
        reported_by_id: UUID | None = None,
        open_only: bool = False,
    ) -> Page[IncidentResponse]:
        stmt = select(Incident)
        if vehicle_id:
            stmt = stmt.where(Incident.vehicle_id == vehicle_id)
        if status:
            stmt = stmt.where(Incident.status == status)
        if severity:
            stmt = stmt.where(Incident.severity == severity)
        if reported_by_id:
            stmt = stmt.where(Incident.reported_by_id == reported_by_id)
        if open_only:
            stmt = stmt.where(Incident.status != IncidentStatus.RESOLVED)

        total = await self.db.scalar(
            select(func.count()).select_from(stmt.order_by(None).subquery())
        )
        rows = await self.db.scalars(
            stmt.options(
                selectinload(Incident.vehicle),
                selectinload(Incident.reported_by),
                selectinload(Incident.assigned_to),
            )
            .order_by(Incident.reported_at.desc())
            .offset(params.offset)
            .limit(params.page_size)
        )
        items = [await self._build(i) for i in rows.unique()]
        return Page.build(items, total or 0, params)

    async def for_driver(self, params: PageParams, user: User) -> Page[IncidentResponse]:
        return await self.list_incidents(params, reported_by_id=user.id)

    async def get_for_user(self, incident_id: UUID, user: User) -> IncidentResponse:
        incident = await self.get_or_404(incident_id)
        if user.role is not UserRole.FLEET_MANAGER and incident.reported_by_id != user.id:
            raise ForbiddenError("You may only view incidents you reported")
        return await self._build(incident)

    async def _response(self, incident_id: UUID) -> IncidentResponse:
        return await self._build(await self.get_or_404(incident_id))

    async def _build(self, incident: Incident) -> IncidentResponse:
        trip_number = None
        if incident.trip_id:
            trip_number = await self.db.scalar(
                select(Trip.trip_number).where(Trip.id == incident.trip_id)
            )
        return IncidentResponse(
            id=incident.id,
            vehicle=AssignedVehicle(
                id=incident.vehicle.id,
                registration_number=incident.vehicle.registration_number,
                make=incident.vehicle.make,
                model=incident.vehicle.model,
            ),
            trip_id=incident.trip_id,
            trip_number=trip_number,
            reported_by=ReporterSummary(
                id=incident.reported_by.id, full_name=incident.reported_by.full_name
            ),
            assigned_to=(
                ReporterSummary(
                    id=incident.assigned_to.id, full_name=incident.assigned_to.full_name
                )
                if incident.assigned_to
                else None
            ),
            title=incident.title,
            description=incident.description,
            severity=incident.severity,
            status=incident.status,
            reported_at=incident.reported_at,
            resolved_at=incident.resolved_at,
            resolution_notes=incident.resolution_notes,
        )
