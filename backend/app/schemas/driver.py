from datetime import date, datetime
from uuid import UUID

from pydantic import BaseModel, EmailStr, Field, computed_field, field_validator

from app.core.config import settings
from app.models.enums import DriverStatus
from app.schemas.common import ORMModel


class AssignedVehicle(BaseModel):
    id: UUID
    registration_number: str
    make: str
    model: str


class DriverCreate(BaseModel):
    email: EmailStr
    password: str = Field(
        min_length=settings.PASSWORD_MIN_LENGTH, max_length=settings.PASSWORD_MAX_LENGTH
    )
    full_name: str = Field(min_length=2, max_length=120)
    phone_number: str = Field(min_length=6, max_length=20)
    license_number: str = Field(min_length=4, max_length=30)
    license_expiry: date

    @field_validator("email")
    @classmethod
    def _email(cls, v: str) -> str:
        return v.strip().lower()

    @field_validator("license_number")
    @classmethod
    def _license(cls, v: str) -> str:
        return "".join(v.split()).upper()


class DriverUpdate(BaseModel):
    full_name: str | None = Field(default=None, min_length=2, max_length=120)
    phone_number: str | None = Field(default=None, min_length=6, max_length=20)
    license_number: str | None = Field(default=None, min_length=4, max_length=30)
    license_expiry: date | None = None

    @field_validator("license_number")
    @classmethod
    def _license(cls, v: str | None) -> str | None:
        return "".join(v.split()).upper() if v else v


class DriverResponse(ORMModel):
    id: UUID
    user_id: UUID
    email: EmailStr
    full_name: str
    phone_number: str | None
    license_number: str
    license_expiry: date
    status: DriverStatus
    can_login: bool
    assigned_vehicle: AssignedVehicle | None = None
    created_at: datetime

    @computed_field
    @property
    def license_expiring_soon(self) -> bool:
        return (self.license_expiry - date.today()).days <= settings.DOCUMENT_EXPIRY_WARNING_DAYS

    @computed_field
    @property
    def license_expired(self) -> bool:
        return self.license_expiry < date.today()


class AssignmentHistoryEntry(BaseModel):
    assignment_id: UUID
    vehicle: AssignedVehicle
    start_date: date
    end_date: date | None
    status: str


class DriverHistory(BaseModel):
    driver_id: UUID
    full_name: str
    assignments: list[AssignmentHistoryEntry]
    total_trips: int
    completed_trips: int
    total_distance_km: float
