from sqlalchemy import CheckConstraint, Column, Date, Enum, Integer, SmallInteger, String
from sqlalchemy.orm import relationship

from app.db.base import Base, TimestampMixin, UUIDPrimaryKeyMixin
from app.models.enums import FuelType, VehicleStatus, VehicleType


class Vehicle(Base, UUIDPrimaryKeyMixin, TimestampMixin):
    __tablename__ = "vehicles"
    __table_args__ = (
        CheckConstraint("year >= 1900 AND year <= 2100", name="year_range"),
        CheckConstraint("current_mileage >= 0", name="mileage_non_negative"),
    )

    registration_number = Column(String(20), unique=True, nullable=False)
    vehicle_type = Column(Enum(VehicleType, name="vehicle_type"), nullable=False)
    make = Column(String(50), nullable=False)
    model = Column(String(50), nullable=False)
    year = Column(SmallInteger, nullable=False)
    fuel_type = Column(Enum(FuelType, name="fuel_type"), nullable=False)
    current_mileage = Column(Integer, nullable=False, default=0, server_default="0")
    status = Column(
        Enum(VehicleStatus, name="vehicle_status"), nullable=False, index=True,
        default=VehicleStatus.AVAILABLE, server_default=VehicleStatus.AVAILABLE.value,
    )
    insurance_expiry = Column(Date, nullable=False)
    registration_expiry = Column(Date, nullable=False)

    assignments = relationship("VehicleAssignment", back_populates="vehicle", lazy="raise_on_sql")
    trips = relationship("Trip", back_populates="vehicle", lazy="raise_on_sql")
    maintenance_records = relationship("MaintenanceRecord", back_populates="vehicle", lazy="raise_on_sql")
    incidents = relationship("Incident", back_populates="vehicle", lazy="raise_on_sql")
    fuel_logs = relationship("FuelLog", back_populates="vehicle", lazy="raise_on_sql")

    def __repr__(self) -> str:
        return f"<Vehicle {self.registration_number} status={self.status}>"
