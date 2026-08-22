from datetime import date
from decimal import Decimal
from uuid import UUID

from pydantic import BaseModel


class Period(BaseModel):
    date_from: date
    date_to: date
    days: int


class VehicleAnalytics(BaseModel):
    vehicle_id: UUID
    registration_number: str
    make: str
    model: str
    trips: int
    distance_km: Decimal
    active_days: int
    utilization_percent: Decimal
    maintenance_cost: Decimal
    fuel_cost: Decimal
    fuel_litres: Decimal
    km_per_litre: Decimal | None
    cost_per_km: Decimal | None


class MonthlyPoint(BaseModel):
    month: str
    trips: int
    distance_km: Decimal
    maintenance_cost: Decimal
    fuel_cost: Decimal


class FleetAnalytics(BaseModel):
    period: Period
    vehicles: int
    trips: int
    distance_km: Decimal
    maintenance_cost: Decimal
    fuel_cost: Decimal
    total_cost: Decimal
    cost_per_km: Decimal | None
    average_utilization_percent: Decimal
    top_vehicles_by_distance: list[VehicleAnalytics]
    least_efficient_by_cost_per_km: list[VehicleAnalytics]
