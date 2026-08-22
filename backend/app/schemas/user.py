from datetime import datetime
from uuid import UUID

from pydantic import EmailStr, Field, field_validator

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
