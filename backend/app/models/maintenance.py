from sqlalchemy import (
    CheckConstraint, Column, Date, Enum, ForeignKey, Index, Integer, Numeric,
    String, Text, Uuid,
)
from sqlalchemy.orm import relationship

from app.db.base import Base, TimestampMixin, UUIDPrimaryKeyMixin
from app.models.enums import MaintenanceType


class MaintenanceRecord(Base, UUIDPrimaryKeyMixin, TimestampMixin):
    __tablename__ = "maintenance_records"
    __table_args__ = (
        CheckConstraint("cost >= 0", name="cost_non_negative"),
        CheckConstraint("odometer >= 0", name="odometer_non_negative"),
        Index("ix_maintenance_records_vehicle_service_date", "vehicle_id", "service_date"),
    )

    vehicle_id = Column(Uuid, ForeignKey("vehicles.id", ondelete="CASCADE"), nullable=False)
    maintenance_type = Column(Enum(MaintenanceType, name="maintenance_type"), nullable=False)
    description = Column(Text, nullable=False)
    service_date = Column(Date, nullable=False)
    cost = Column(Numeric(12, 2), nullable=False, server_default="0")
    odometer = Column(Integer, nullable=False)
    next_service_date = Column(Date, nullable=True)
    next_service_mileage = Column(Integer, nullable=True)
    performed_by = Column(String(150), nullable=True)
    created_by_id = Column(Uuid, ForeignKey("users.id", ondelete="SET NULL"), nullable=True)

    vehicle = relationship("Vehicle", back_populates="maintenance_records", lazy="joined")

    def __repr__(self) -> str:
        return f"<MaintenanceRecord {self.maintenance_type} vehicle={self.vehicle_id} on={self.service_date}>"
