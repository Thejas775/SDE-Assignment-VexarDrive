from datetime import date
from typing import TYPE_CHECKING

from sqlalchemy import CheckConstraint, Date, Enum, Integer, SmallInteger, String
from sqlalchemy.orm import Mapped, mapped_column, relationship

from app.db.base import Base, TimestampMixin, UUIDPrimaryKeyMixin
from app.models.enums import FuelType, VehicleStatus, VehicleType

if TYPE_CHECKING:
    from app.models.incident import Incident
    from app.models.maintenance import MaintenanceRecord
    from app.models.trip import Trip
    from app.models.vehicle_assignment import VehicleAssignment


class Vehicle(Base, UUIDPrimaryKeyMixin, TimestampMixin):
    __tablename__ = "vehicles"
    __table_args__ = (
        CheckConstraint("year >= 1900 AND year <= 2100", name="year_range"),
        CheckConstraint("current_mileage >= 0", name="mileage_non_negative"),
    )

    registration_number: Mapped[str] = mapped_column(String(20), unique=True, nullable=False)
    vehicle_type: Mapped[VehicleType] = mapped_column(
        Enum(VehicleType, name="vehicle_type"), nullable=False
    )
    make: Mapped[str] = mapped_column(String(50), nullable=False)
    model: Mapped[str] = mapped_column(String(50), nullable=False)
    year: Mapped[int] = mapped_column(SmallInteger, nullable=False)
    fuel_type: Mapped[FuelType] = mapped_column(
        Enum(FuelType, name="fuel_type"), nullable=False
    )
    current_mileage: Mapped[int] = mapped_column(Integer, nullable=False, default=0, server_default="0")
    status: Mapped[VehicleStatus] = mapped_column(
        Enum(VehicleStatus, name="vehicle_status"),
        nullable=False,
        default=VehicleStatus.AVAILABLE,
        server_default=VehicleStatus.AVAILABLE.value,
        index=True,
    )
    insurance_expiry: Mapped[date] = mapped_column(Date, nullable=False)
    registration_expiry: Mapped[date] = mapped_column(Date, nullable=False)

    assignments: Mapped[list["VehicleAssignment"]] = relationship(
        back_populates="vehicle", lazy="raise_on_sql"
    )
    trips: Mapped[list["Trip"]] = relationship(back_populates="vehicle", lazy="raise_on_sql")
    maintenance_records: Mapped[list["MaintenanceRecord"]] = relationship(
        back_populates="vehicle", lazy="raise_on_sql"
    )
    incidents: Mapped[list["Incident"]] = relationship(
        back_populates="vehicle", lazy="raise_on_sql"
    )

    def __repr__(self) -> str:
        return f"<Vehicle {self.registration_number} status={self.status}>"
