from datetime import date
from typing import TYPE_CHECKING
from uuid import UUID

from sqlalchemy import CheckConstraint, Date, Enum, ForeignKey, Index, String
from sqlalchemy.orm import Mapped, mapped_column, relationship

from app.db.base import Base, TimestampMixin, UUIDPrimaryKeyMixin
from app.models.enums import AssignmentStatus

if TYPE_CHECKING:
    from app.models.driver import Driver
    from app.models.vehicle import Vehicle


class VehicleAssignment(Base, UUIDPrimaryKeyMixin, TimestampMixin):
    __tablename__ = "vehicle_assignments"
    __table_args__ = (
        CheckConstraint("end_date IS NULL OR end_date >= start_date", name="valid_period"),
        Index("ix_vehicle_assignments_vehicle_period", "vehicle_id", "start_date", "end_date"),
        Index("ix_vehicle_assignments_driver_period", "driver_id", "start_date", "end_date"),
    )

    vehicle_id: Mapped[UUID] = mapped_column(
        ForeignKey("vehicles.id", ondelete="CASCADE"), nullable=False
    )
    driver_id: Mapped[UUID] = mapped_column(
        ForeignKey("drivers.id", ondelete="CASCADE"), nullable=False
    )
    start_date: Mapped[date] = mapped_column(Date, nullable=False)
    end_date: Mapped[date | None] = mapped_column(Date)
    status: Mapped[AssignmentStatus] = mapped_column(
        Enum(AssignmentStatus, name="assignment_status"),
        nullable=False,
        default=AssignmentStatus.ACTIVE,
        server_default=AssignmentStatus.ACTIVE.value,
    )
    notes: Mapped[str | None] = mapped_column(String(500))

    vehicle: Mapped["Vehicle"] = relationship(back_populates="assignments", lazy="joined")
    driver: Mapped["Driver"] = relationship(back_populates="assignments", lazy="joined")

    def __repr__(self) -> str:
        return f"<VehicleAssignment vehicle={self.vehicle_id} driver={self.driver_id} from={self.start_date}>"
