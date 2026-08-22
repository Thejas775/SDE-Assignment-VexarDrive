from datetime import date
from decimal import Decimal
from typing import TYPE_CHECKING
from uuid import UUID

from sqlalchemy import CheckConstraint, Date, Enum, ForeignKey, Index, Integer, Numeric, String, Text
from sqlalchemy.orm import Mapped, mapped_column, relationship

from app.db.base import Base, TimestampMixin, UUIDPrimaryKeyMixin
from app.models.enums import MaintenanceType

if TYPE_CHECKING:
    from app.models.vehicle import Vehicle


class MaintenanceRecord(Base, UUIDPrimaryKeyMixin, TimestampMixin):
    __tablename__ = "maintenance_records"
    __table_args__ = (
        CheckConstraint("cost >= 0", name="cost_non_negative"),
        CheckConstraint("odometer >= 0", name="odometer_non_negative"),
        Index("ix_maintenance_records_vehicle_service_date", "vehicle_id", "service_date"),
    )

    vehicle_id: Mapped[UUID] = mapped_column(
        ForeignKey("vehicles.id", ondelete="CASCADE"), nullable=False
    )
    maintenance_type: Mapped[MaintenanceType] = mapped_column(
        Enum(MaintenanceType, name="maintenance_type"), nullable=False
    )
    description: Mapped[str] = mapped_column(Text, nullable=False)
    service_date: Mapped[date] = mapped_column(Date, nullable=False)
    cost: Mapped[Decimal] = mapped_column(Numeric(12, 2), nullable=False, server_default="0")
    odometer: Mapped[int] = mapped_column(Integer, nullable=False)
    next_service_date: Mapped[date | None] = mapped_column(Date)
    next_service_mileage: Mapped[int | None] = mapped_column(Integer)
    performed_by: Mapped[str | None] = mapped_column(String(150))
    created_by_id: Mapped[UUID | None] = mapped_column(ForeignKey("users.id", ondelete="SET NULL"))

    vehicle: Mapped["Vehicle"] = relationship(back_populates="maintenance_records", lazy="joined")

    def __repr__(self) -> str:
        return f"<MaintenanceRecord {self.maintenance_type} vehicle={self.vehicle_id} on={self.service_date}>"
