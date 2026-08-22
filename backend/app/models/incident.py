from datetime import datetime
from typing import TYPE_CHECKING
from uuid import UUID

from sqlalchemy import DateTime, Enum, ForeignKey, Index, String, Text, func
from sqlalchemy.orm import Mapped, mapped_column, relationship

from app.db.base import Base, TimestampMixin, UUIDPrimaryKeyMixin
from app.models.enums import IncidentSeverity, IncidentStatus

if TYPE_CHECKING:
    from app.models.trip import Trip
    from app.models.user import User
    from app.models.vehicle import Vehicle


class Incident(Base, UUIDPrimaryKeyMixin, TimestampMixin):
    __tablename__ = "incidents"
    __table_args__ = (
        Index("ix_incidents_vehicle_status", "vehicle_id", "status"),
        Index("ix_incidents_status_severity", "status", "severity"),
    )

    vehicle_id: Mapped[UUID] = mapped_column(
        ForeignKey("vehicles.id", ondelete="CASCADE"), nullable=False
    )
    trip_id: Mapped[UUID | None] = mapped_column(ForeignKey("trips.id", ondelete="SET NULL"))
    reported_by_id: Mapped[UUID] = mapped_column(ForeignKey("users.id"), nullable=False)
    assigned_to_id: Mapped[UUID | None] = mapped_column(ForeignKey("users.id", ondelete="SET NULL"))

    title: Mapped[str] = mapped_column(String(200), nullable=False)
    description: Mapped[str] = mapped_column(Text, nullable=False)
    severity: Mapped[IncidentSeverity] = mapped_column(
        Enum(IncidentSeverity, name="incident_severity"), nullable=False
    )
    status: Mapped[IncidentStatus] = mapped_column(
        Enum(IncidentStatus, name="incident_status"),
        nullable=False,
        default=IncidentStatus.OPEN,
        server_default=IncidentStatus.OPEN.value,
    )
    reported_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True), nullable=False, server_default=func.now()
    )
    resolved_at: Mapped[datetime | None] = mapped_column(DateTime(timezone=True))
    resolution_notes: Mapped[str | None] = mapped_column(Text)

    vehicle: Mapped["Vehicle"] = relationship(back_populates="incidents", lazy="joined")
    trip: Mapped["Trip | None"] = relationship(back_populates="incidents", lazy="raise_on_sql")
    reported_by: Mapped["User"] = relationship(
        foreign_keys=[reported_by_id], back_populates="reported_incidents", lazy="joined"
    )
    assigned_to: Mapped["User | None"] = relationship(
        foreign_keys=[assigned_to_id], back_populates="assigned_incidents", lazy="joined"
    )

    def __repr__(self) -> str:
        return f"<Incident {self.title!r} severity={self.severity} status={self.status}>"
