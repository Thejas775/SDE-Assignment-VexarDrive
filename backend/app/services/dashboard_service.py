from datetime import date, datetime, time, timedelta, timezone
from decimal import Decimal

from sqlalchemy import func, select
from sqlalchemy.ext.asyncio import AsyncSession
from sqlalchemy.orm import selectinload

from app.core.config import settings
from app.models.driver import Driver
from app.models.enums import DriverStatus, IncidentStatus, TripStatus, VehicleStatus
from app.models.enums_transitions import ACTIVE_TRIP_STATUSES
from app.models.incident import Incident
from app.models.trip import Trip
from app.models.vehicle import Vehicle
from app.schemas.dashboard import (
    DashboardResponse,
    ExpiringDocuments,
    RecentIncident,
    TripCounts,
    VehicleCounts,
)
from app.services.maintenance_service import MaintenanceService


class DashboardService:
    def __init__(self, db: AsyncSession):
        self.db = db

    async def summary(self) -> DashboardResponse:
        today = date.today()
        day_start = datetime.combine(today, time.min, tzinfo=timezone.utc)
        day_end = day_start + timedelta(days=1)
        doc_cutoff = today + timedelta(days=settings.DOCUMENT_EXPIRY_WARNING_DAYS)

        # One grouped query instead of five COUNT(*) round trips.
        by_status = dict(
            (
                await self.db.execute(
                    select(Vehicle.status, func.count()).group_by(Vehicle.status)
                )
            ).all()
        )
        vehicles = VehicleCounts(
            total=sum(by_status.values()),
            available=by_status.get(VehicleStatus.AVAILABLE, 0),
            on_trip=by_status.get(VehicleStatus.ON_TRIP, 0),
            in_maintenance=by_status.get(VehicleStatus.IN_MAINTENANCE, 0),
            inactive=by_status.get(VehicleStatus.INACTIVE, 0),
        )

        trips = TripCounts(
            active=await self._count(Trip, Trip.status.in_(ACTIVE_TRIP_STATUSES)),
            scheduled_today=await self._count(
                Trip,
                Trip.status == TripStatus.SCHEDULED,
                Trip.scheduled_start >= day_start,
                Trip.scheduled_start < day_end,
            ),
            completed_today=await self._count(
                Trip,
                Trip.status == TripStatus.COMPLETED,
                Trip.actual_end >= day_start,
                Trip.actual_end < day_end,
            ),
        )

        distance_today = await self.db.scalar(
            select(func.coalesce(func.sum(Trip.distance_km), 0)).where(
                Trip.status == TripStatus.COMPLETED,
                Trip.actual_end >= day_start,
                Trip.actual_end < day_end,
            )
        )

        expiring = ExpiringDocuments(
            insurance=await self._count(
                Vehicle,
                Vehicle.insurance_expiry <= doc_cutoff,
                Vehicle.status != VehicleStatus.INACTIVE,
            ),
            registration=await self._count(
                Vehicle,
                Vehicle.registration_expiry <= doc_cutoff,
                Vehicle.status != VehicleStatus.INACTIVE,
            ),
            driver_license=await self._count(Driver, Driver.license_expiry <= doc_cutoff),
        )

        recent = await self.db.scalars(
            select(Incident)
            .options(selectinload(Incident.vehicle))
            .order_by(Incident.reported_at.desc())
            .limit(5)
        )

        return DashboardResponse(
            vehicles=vehicles,
            trips=trips,
            drivers_active=await self._count(Driver, Driver.status == DriverStatus.ACTIVE),
            distance_today_km=Decimal(distance_today or 0),
            maintenance_due=len(await MaintenanceService(self.db).due()),
            open_incidents=await self._count(Incident, Incident.status != IncidentStatus.RESOLVED),
            expiring_documents=expiring,
            recent_incidents=[
                RecentIncident(
                    id=i.id,
                    title=i.title,
                    severity=i.severity,
                    status=i.status,
                    registration_number=i.vehicle.registration_number,
                    reported_at=i.reported_at.date(),
                )
                for i in recent
            ],
        )

    async def _count(self, model, *conditions) -> int:
        return await self.db.scalar(
            select(func.count()).select_from(model).where(*conditions)
        ) or 0
