from sqlalchemy import Column, DateTime, Enum, ForeignKey, Index, String, Text, Uuid, func
from sqlalchemy.orm import relationship

from app.db.base import Base, TimestampMixin, UUIDPrimaryKeyMixin
from app.models.enums import IncidentSeverity, IncidentStatus


class Incident(Base, UUIDPrimaryKeyMixin, TimestampMixin):
    __tablename__ = "incidents"
    __table_args__ = (
        Index("ix_incidents_vehicle_status", "vehicle_id", "status"),
        Index("ix_incidents_status_severity", "status", "severity"),
    )

    vehicle_id = Column(Uuid, ForeignKey("vehicles.id", ondelete="CASCADE"), nullable=False)
    trip_id = Column(Uuid, ForeignKey("trips.id", ondelete="SET NULL"), nullable=True)
    reported_by_id = Column(Uuid, ForeignKey("users.id"), nullable=False)
    assigned_to_id = Column(Uuid, ForeignKey("users.id", ondelete="SET NULL"), nullable=True)

    title = Column(String(200), nullable=False)
    description = Column(Text, nullable=False)
    severity = Column(Enum(IncidentSeverity, name="incident_severity"), nullable=False)
    status = Column(
        Enum(IncidentStatus, name="incident_status"), nullable=False,
        default=IncidentStatus.OPEN, server_default=IncidentStatus.OPEN.value,
    )
    reported_at = Column(DateTime(timezone=True), nullable=False, server_default=func.now())
    resolved_at = Column(DateTime(timezone=True), nullable=True)
    resolution_notes = Column(Text, nullable=True)

    vehicle = relationship("Vehicle", back_populates="incidents", lazy="joined")
    trip = relationship("Trip", back_populates="incidents", lazy="raise_on_sql")
    reported_by = relationship(
        "User", foreign_keys=[reported_by_id], back_populates="reported_incidents", lazy="joined"
    )
    assigned_to = relationship(
        "User", foreign_keys=[assigned_to_id], back_populates="assigned_incidents", lazy="joined"
    )

    def __repr__(self) -> str:
        return f"<Incident {self.title!r} severity={self.severity} status={self.status}>"
