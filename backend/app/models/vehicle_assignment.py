from sqlalchemy import CheckConstraint, Column, Date, Enum, ForeignKey, Index, String, Uuid
from sqlalchemy.orm import relationship

from app.db.base import Base, TimestampMixin, UUIDPrimaryKeyMixin
from app.models.enums import AssignmentStatus


class VehicleAssignment(Base, UUIDPrimaryKeyMixin, TimestampMixin):
    __tablename__ = "vehicle_assignments"
    __table_args__ = (
        CheckConstraint("end_date IS NULL OR end_date >= start_date", name="valid_period"),
        Index("ix_vehicle_assignments_vehicle_period", "vehicle_id", "start_date", "end_date"),
        Index("ix_vehicle_assignments_driver_period", "driver_id", "start_date", "end_date"),
    )

    vehicle_id = Column(Uuid, ForeignKey("vehicles.id", ondelete="CASCADE"), nullable=False)
    driver_id = Column(Uuid, ForeignKey("drivers.id", ondelete="CASCADE"), nullable=False)
    start_date = Column(Date, nullable=False)
    end_date = Column(Date, nullable=True)
    status = Column(
        Enum(AssignmentStatus, name="assignment_status"), nullable=False,
        default=AssignmentStatus.ACTIVE, server_default=AssignmentStatus.ACTIVE.value,
    )
    notes = Column(String(500), nullable=True)

    vehicle = relationship("Vehicle", back_populates="assignments", lazy="joined")
    driver = relationship("Driver", back_populates="assignments", lazy="joined")

    def __repr__(self) -> str:
        return f"<VehicleAssignment vehicle={self.vehicle_id} driver={self.driver_id} from={self.start_date}>"
