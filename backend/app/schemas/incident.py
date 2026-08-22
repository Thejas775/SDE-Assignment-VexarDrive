from datetime import datetime
from uuid import UUID

from pydantic import BaseModel, Field

from app.models.enums import IncidentSeverity, IncidentStatus
from app.schemas.driver import AssignedVehicle


class ReporterSummary(BaseModel):
    id: UUID
    full_name: str


class IncidentCreate(BaseModel):
    vehicle_id: UUID
    trip_id: UUID | None = None
    title: str = Field(min_length=3, max_length=200)
    description: str = Field(min_length=3)
    severity: IncidentSeverity


class IncidentUpdate(BaseModel):
    title: str | None = Field(default=None, min_length=3, max_length=200)
    description: str | None = Field(default=None, min_length=3)
    severity: IncidentSeverity | None = None
    assigned_to_id: UUID | None = None


class IncidentStatusUpdate(BaseModel):
    status: IncidentStatus
    resolution_notes: str | None = None


class IncidentAssign(BaseModel):
    assigned_to_id: UUID


class IncidentResponse(BaseModel):
    id: UUID
    vehicle: AssignedVehicle
    trip_id: UUID | None
    trip_number: str | None
    reported_by: ReporterSummary
    assigned_to: ReporterSummary | None
    title: str
    description: str
    severity: IncidentSeverity
    status: IncidentStatus
    reported_at: datetime
    resolved_at: datetime | None
    resolution_notes: str | None
