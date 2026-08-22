from datetime import date, datetime
from uuid import UUID

from pydantic import BaseModel, Field, computed_field, model_validator

from app.models.enums import AssignmentStatus
from app.schemas.driver import AssignedVehicle


class AssignmentDriver(BaseModel):
    id: UUID
    full_name: str
    license_number: str


class AssignmentCreate(BaseModel):
    vehicle_id: UUID
    driver_id: UUID
    start_date: date
    end_date: date | None = None
    notes: str | None = Field(default=None, max_length=500)

    @model_validator(mode="after")
    def _ordered(self) -> "AssignmentCreate":
        if self.end_date and self.end_date < self.start_date:
            raise ValueError("end_date cannot be before start_date")
        return self


class AssignmentEnd(BaseModel):
    end_date: date | None = None


class AssignmentResponse(BaseModel):
    id: UUID
    vehicle: AssignedVehicle
    driver: AssignmentDriver
    start_date: date
    end_date: date | None
    status: AssignmentStatus
    notes: str | None
    created_at: datetime

    @computed_field
    @property
    def is_current(self) -> bool:
        if self.status is not AssignmentStatus.ACTIVE:
            return False
        today = date.today()
        return self.start_date <= today and (self.end_date is None or self.end_date >= today)
