from datetime import datetime, timedelta, timezone
from decimal import Decimal
from uuid import UUID

from pydantic import BaseModel, Field, field_validator, model_validator

MAX_BATCH = 500
MAX_CLOCK_SKEW = timedelta(minutes=5)


class LocationPing(BaseModel):
    latitude: Decimal = Field(ge=-90, le=90)
    longitude: Decimal = Field(ge=-180, le=180)
    recorded_at: datetime
    speed_kmph: Decimal | None = Field(default=None, ge=0, le=400)
    heading: Decimal | None = Field(default=None, ge=0, lt=360)
    accuracy_m: Decimal | None = Field(default=None, ge=0)

    @field_validator("recorded_at")
    @classmethod
    def _not_from_the_future(cls, v: datetime) -> datetime:
        if v.tzinfo is None:
            v = v.replace(tzinfo=timezone.utc)
        if v > datetime.now(timezone.utc) + MAX_CLOCK_SKEW:
            raise ValueError("recorded_at is too far in the future")
        return v


class LocationBatch(BaseModel):
    """One or many pings for a trip. Batching is what makes offline sync work."""

    trip_id: UUID
    pings: list[LocationPing] = Field(min_length=1, max_length=MAX_BATCH)

    @model_validator(mode="after")
    def _chronological(self) -> "LocationBatch":
        self.pings.sort(key=lambda p: p.recorded_at)
        return self


class LocationResponse(BaseModel):
    id: UUID
    trip_id: UUID
    vehicle_id: UUID
    latitude: Decimal
    longitude: Decimal
    speed_kmph: Decimal | None
    heading: Decimal | None
    accuracy_m: Decimal | None
    recorded_at: datetime
    received_at: datetime


class LocationIngestResult(BaseModel):
    accepted: int
    duplicates: int
    trip_status: str


class VehiclePosition(BaseModel):
    vehicle_id: UUID
    registration_number: str
    trip_id: UUID | None
    trip_number: str | None
    driver_name: str | None
    latitude: Decimal
    longitude: Decimal
    speed_kmph: Decimal | None
    heading: Decimal | None
    recorded_at: datetime
