from datetime import datetime
from uuid import UUID

from datetime import date

from pydantic import EmailStr, Field, field_validator, model_validator

from app.core.config import settings
from app.models.enums import UserRole
from app.schemas.common import ORMModel


class UserResponse(ORMModel):
    id: UUID
    email: EmailStr
    full_name: str
    phone_number: str | None
    role: UserRole
    is_active: bool
    created_at: datetime


class UserCreate(ORMModel):
    email: EmailStr
    password: str = Field(
        min_length=settings.PASSWORD_MIN_LENGTH, max_length=settings.PASSWORD_MAX_LENGTH
    )
    full_name: str = Field(min_length=2, max_length=120)
    phone_number: str | None = Field(default=None, max_length=20)
    role: UserRole = UserRole.DRIVER

    @field_validator("email")
    @classmethod
    def _normalise_email(cls, v: str) -> str:
        return v.strip().lower()


class SignupRequest(UserCreate):
    """Self-service registration.

    A driver account is only usable once it has a driver profile, so the
    licence fields are required when the chosen role is DRIVER. Creating the
    user without them leaves an account that cannot see a vehicle or a trip.
    """

    license_number: str | None = Field(default=None, min_length=4, max_length=30)
    license_expiry: date | None = None

    @field_validator("license_number")
    @classmethod
    def _normalise_license(cls, v: str | None) -> str | None:
        return "".join(v.split()).upper() if v else v

    @model_validator(mode="after")
    def _driver_needs_a_licence(self) -> "SignupRequest":
        if self.role is not UserRole.DRIVER:
            return self
        missing = [
            name
            for name, value in (
                ("phone_number", self.phone_number),
                ("license_number", self.license_number),
                ("license_expiry", self.license_expiry),
            )
            if not value
        ]
        if missing:
            raise ValueError(f"A driver signup requires: {', '.join(missing)}")
        return self
