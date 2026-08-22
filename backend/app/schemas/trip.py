from datetime import datetime
from decimal import Decimal
from uuid import UUID

from pydantic import BaseModel, Field, computed_field, model_validator

from app.models.enums import TripStatus
from app.schemas.assignment import AssignmentDriver
from app.schemas.driver import AssignedVehicle

Latitude = Field(default=None, ge=-90, le=90)
Longitude = Field(default=None, ge=-180, le=180)


class TripCreate(BaseModel):
    vehicle_id: UUID
    driver_id: UUID
    source: str = Field(min_length=2, max_length=200)
    destination: str = Field(min_length=2, max_length=200)
    scheduled_start: datetime
    scheduled_end: datetime
    notes: str | None = None

    @model_validator(mode="after")
    def _ordered(self) -> "TripCreate":
        if self.scheduled_end <= self.scheduled_start:
            raise ValueError("scheduled_end must be after scheduled_start")
        return self


class TripStart(BaseModel):
    start_odometer: int = Field(ge=0)
    latitude: Decimal | None = Latitude
    longitude: Decimal | None = Longitude


class TripComplete(BaseModel):
    end_odometer: int = Field(ge=0)
    latitude: Decimal | None = Latitude
    longitude: Decimal | None = Longitude
    notes: str | None = None


class TripStatusUpdate(BaseModel):
    status: TripStatus


class TripResponse(BaseModel):
    id: UUID
    trip_number: str
    vehicle: AssignedVehicle
    driver: AssignmentDriver
    source: str
    destination: str
    scheduled_start: datetime
    scheduled_end: datetime
    status: TripStatus
    actual_start: datetime | None
    actual_end: datetime | None
    start_odometer: int | None
    end_odometer: int | None
    start_latitude: Decimal | None
    start_longitude: Decimal | None
    end_latitude: Decimal | None
    end_longitude: Decimal | None
    distance_km: Decimal | None
    notes: str | None
    created_at: datetime

    @computed_field
    @property
    def duration_minutes(self) -> int | None:
        if self.actual_start and self.actual_end:
            return int((self.actual_end - self.actual_start).total_seconds() // 60)
        return None
