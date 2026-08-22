from sqlalchemy import (
    Boolean, CheckConstraint, Column, Date, ForeignKey, Index, Integer, Numeric, String, Uuid,
)
from sqlalchemy.orm import relationship

from app.db.base import Base, TimestampMixin, UUIDPrimaryKeyMixin


class FuelLog(Base, UUIDPrimaryKeyMixin, TimestampMixin):
    __tablename__ = "fuel_logs"
    __table_args__ = (
        CheckConstraint("quantity_litres > 0", name="quantity_positive"),
        CheckConstraint("cost >= 0", name="cost_non_negative"),
        CheckConstraint("odometer >= 0", name="odometer_non_negative"),
        Index("ix_fuel_logs_vehicle_fuel_date", "vehicle_id", "fuel_date"),
    )

    vehicle_id = Column(Uuid, ForeignKey("vehicles.id", ondelete="CASCADE"), nullable=False)
    driver_id = Column(Uuid, ForeignKey("drivers.id", ondelete="SET NULL"), nullable=True)
    trip_id = Column(Uuid, ForeignKey("trips.id", ondelete="SET NULL"), nullable=True)
    fuel_date = Column(Date, nullable=False)
    quantity_litres = Column(Numeric(8, 2), nullable=False)
    cost = Column(Numeric(12, 2), nullable=False)
    odometer = Column(Integer, nullable=False)
    full_tank = Column(Boolean, nullable=False, default=True, server_default="true")
    station = Column(String(150), nullable=True)
    notes = Column(String(500), nullable=True)
    created_by_id = Column(Uuid, ForeignKey("users.id", ondelete="SET NULL"), nullable=True)

    vehicle = relationship("Vehicle", back_populates="fuel_logs", lazy="joined")

    def __repr__(self) -> str:
        return f"<FuelLog {self.quantity_litres}L vehicle={self.vehicle_id} on={self.fuel_date}>"
