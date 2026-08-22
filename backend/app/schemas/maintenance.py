from datetime import date, datetime
from decimal import Decimal
from uuid import UUID

from pydantic import BaseModel, Field, model_validator

from app.models.enums import MaintenanceType
from app.schemas.driver import AssignedVehicle


class MaintenanceCreate(BaseModel):
    vehicle_id: UUID
    maintenance_type: MaintenanceType
    description: str = Field(min_length=3)
    service_date: date
    cost: Decimal = Field(default=Decimal("0"), ge=0, max_digits=12, decimal_places=2)
    odometer: int = Field(ge=0)
    next_service_date: date | None = None
    next_service_mileage: int | None = Field(default=None, ge=0)
    performed_by: str | None = Field(default=None, max_length=150)

    @model_validator(mode="after")
    def _forward_looking(self) -> "MaintenanceCreate":
        if self.next_service_date and self.next_service_date < self.service_date:
            raise ValueError("next_service_date cannot be before service_date")
        if self.next_service_mileage and self.next_service_mileage < self.odometer:
            raise ValueError("next_service_mileage cannot be below the current odometer")
        return self


class MaintenanceUpdate(BaseModel):
    maintenance_type: MaintenanceType | None = None
    description: str | None = Field(default=None, min_length=3)
    service_date: date | None = None
    cost: Decimal | None = Field(default=None, ge=0, max_digits=12, decimal_places=2)
    odometer: int | None = Field(default=None, ge=0)
    next_service_date: date | None = None
    next_service_mileage: int | None = Field(default=None, ge=0)
    performed_by: str | None = Field(default=None, max_length=150)


class MaintenanceResponse(BaseModel):
    id: UUID
    vehicle: AssignedVehicle
    maintenance_type: MaintenanceType
    description: str
    service_date: date
    cost: Decimal
    odometer: int
    next_service_date: date | None
    next_service_mileage: int | None
    performed_by: str | None
    created_at: datetime


class MaintenanceDueItem(BaseModel):
    vehicle: AssignedVehicle
    current_mileage: int
    last_service_date: date | None
    next_service_date: date | None
    next_service_mileage: int | None
    days_until_due: int | None
    km_until_due: int | None
    reasons: list[str]
