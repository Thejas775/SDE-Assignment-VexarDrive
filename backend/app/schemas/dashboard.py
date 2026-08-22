from datetime import date
from decimal import Decimal
from uuid import UUID

from pydantic import BaseModel

from app.models.enums import IncidentSeverity, IncidentStatus


class VehicleCounts(BaseModel):
    total: int
    available: int
    on_trip: int
    in_maintenance: int
    inactive: int


class TripCounts(BaseModel):
    active: int
    scheduled_today: int
    completed_today: int


class ExpiringDocuments(BaseModel):
    insurance: int
    registration: int
    driver_license: int


class RecentIncident(BaseModel):
    id: UUID
    title: str
    severity: IncidentSeverity
    status: IncidentStatus
    registration_number: str
    reported_at: date


class DashboardResponse(BaseModel):
    vehicles: VehicleCounts
    trips: TripCounts
    drivers_active: int
    distance_today_km: Decimal
    maintenance_due: int
    open_incidents: int
    expiring_documents: ExpiringDocuments
    recent_incidents: list[RecentIncident]
