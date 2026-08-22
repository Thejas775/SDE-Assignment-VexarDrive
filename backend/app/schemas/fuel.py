from datetime import date, datetime
from decimal import Decimal
from uuid import UUID

from pydantic import BaseModel, Field, computed_field

from app.schemas.driver import AssignedVehicle


class FuelCreate(BaseModel):
    vehicle_id: UUID
    driver_id: UUID | None = None
    trip_id: UUID | None = None
    fuel_date: date
    quantity_litres: Decimal = Field(gt=0, max_digits=8, decimal_places=2)
    cost: Decimal = Field(ge=0, max_digits=12, decimal_places=2)
    odometer: int = Field(ge=0)
    full_tank: bool = True
    station: str | None = Field(default=None, max_length=150)
    notes: str | None = Field(default=None, max_length=500)


class FuelUpdate(BaseModel):
    fuel_date: date | None = None
    quantity_litres: Decimal | None = Field(default=None, gt=0, max_digits=8, decimal_places=2)
    cost: Decimal | None = Field(default=None, ge=0, max_digits=12, decimal_places=2)
    odometer: int | None = Field(default=None, ge=0)
    full_tank: bool | None = None
    station: str | None = Field(default=None, max_length=150)
    notes: str | None = Field(default=None, max_length=500)


class FuelResponse(BaseModel):
    id: UUID
    vehicle: AssignedVehicle
    driver_id: UUID | None
    trip_id: UUID | None
    fuel_date: date
    quantity_litres: Decimal
    cost: Decimal
    odometer: int
    full_tank: bool
    station: str | None
    notes: str | None
    created_at: datetime

    @computed_field
    @property
    def cost_per_litre(self) -> Decimal:
        return (self.cost / self.quantity_litres).quantize(Decimal("0.01"))


class FuelEfficiencyEntry(BaseModel):
    fuel_log_id: UUID
    fuel_date: date
    odometer: int
    distance_km: int
    quantity_litres: Decimal
    cost: Decimal
    km_per_litre: Decimal
    cost_per_km: Decimal


class FuelEfficiency(BaseModel):
    vehicle: AssignedVehicle
    fills: int
    total_litres: Decimal
    total_cost: Decimal
    distance_km: int
    average_km_per_litre: Decimal | None
    average_cost_per_km: Decimal | None
    entries: list[FuelEfficiencyEntry]
