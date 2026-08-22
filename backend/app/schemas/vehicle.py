from datetime import date, datetime
from uuid import UUID

from pydantic import BaseModel, Field, computed_field, field_validator

from app.core.config import settings
from app.models.enums import FuelType, VehicleStatus, VehicleType
from app.schemas.common import ORMModel

CURRENT_YEAR_CEILING = 2100


def _normalise_registration(v: str) -> str:
    return " ".join(v.strip().upper().split())


class VehicleCreate(BaseModel):
    registration_number: str = Field(min_length=4, max_length=20)
    vehicle_type: VehicleType
    make: str = Field(min_length=1, max_length=50)
    model: str = Field(min_length=1, max_length=50)
    year: int = Field(ge=1900, le=CURRENT_YEAR_CEILING)
    fuel_type: FuelType
    current_mileage: int = Field(default=0, ge=0)
    insurance_expiry: date
    registration_expiry: date

    @field_validator("registration_number")
    @classmethod
    def _reg(cls, v: str) -> str:
        return _normalise_registration(v)


class VehicleUpdate(BaseModel):
    """Every field optional - PUT here is a partial update."""

    registration_number: str | None = Field(default=None, min_length=4, max_length=20)
    vehicle_type: VehicleType | None = None
    make: str | None = Field(default=None, min_length=1, max_length=50)
    model: str | None = Field(default=None, min_length=1, max_length=50)
    year: int | None = Field(default=None, ge=1900, le=CURRENT_YEAR_CEILING)
    fuel_type: FuelType | None = None
    current_mileage: int | None = Field(default=None, ge=0)
    status: VehicleStatus | None = None
    insurance_expiry: date | None = None
    registration_expiry: date | None = None

    @field_validator("registration_number")
    @classmethod
    def _reg(cls, v: str | None) -> str | None:
        return _normalise_registration(v) if v else v


class VehicleResponse(ORMModel):
    id: UUID
    registration_number: str
    vehicle_type: VehicleType
    make: str
    model: str
    year: int
    fuel_type: FuelType
    current_mileage: int
    status: VehicleStatus
    insurance_expiry: date
    registration_expiry: date
    created_at: datetime
    updated_at: datetime

    @computed_field
    @property
    def insurance_expiring_soon(self) -> bool:
        return self._within_warning(self.insurance_expiry)

    @computed_field
    @property
    def registration_expiring_soon(self) -> bool:
        return self._within_warning(self.registration_expiry)

    @staticmethod
    def _within_warning(day: date) -> bool:
        delta = (day - date.today()).days
        return delta <= settings.DOCUMENT_EXPIRY_WARNING_DAYS
