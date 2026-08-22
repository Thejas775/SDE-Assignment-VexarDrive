from datetime import datetime
from decimal import Decimal
from typing import TYPE_CHECKING
from uuid import UUID

from sqlalchemy import CheckConstraint, DateTime, ForeignKey, Index, Numeric, func
from sqlalchemy.orm import Mapped, mapped_column, relationship

from app.db.base import Base, UUIDPrimaryKeyMixin

if TYPE_CHECKING:
    from app.models.trip import Trip


class Location(Base, UUIDPrimaryKeyMixin):
    __tablename__ = "locations"
    __table_args__ = (
        CheckConstraint("latitude BETWEEN -90 AND 90", name="latitude_range"),
        CheckConstraint("longitude BETWEEN -180 AND 180", name="longitude_range"),
        Index("ix_locations_vehicle_recorded_at", "vehicle_id", "recorded_at"),
        Index("ix_locations_trip_recorded_at", "trip_id", "recorded_at"),
    )

    trip_id: Mapped[UUID] = mapped_column(ForeignKey("trips.id", ondelete="CASCADE"), nullable=False)
    vehicle_id: Mapped[UUID] = mapped_column(ForeignKey("vehicles.id", ondelete="CASCADE"), nullable=False)
    latitude: Mapped[Decimal] = mapped_column(Numeric(9, 6), nullable=False)
    longitude: Mapped[Decimal] = mapped_column(Numeric(9, 6), nullable=False)
    speed_kmph: Mapped[Decimal | None] = mapped_column(Numeric(6, 2))
    heading: Mapped[Decimal | None] = mapped_column(Numeric(5, 2))
    accuracy_m: Mapped[Decimal | None] = mapped_column(Numeric(7, 2))
    recorded_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), nullable=False)
    received_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True), nullable=False, server_default=func.now()
    )

    trip: Mapped["Trip"] = relationship(back_populates="locations", lazy="raise_on_sql")

    def __repr__(self) -> str:
        return f"<Location {self.latitude},{self.longitude} at={self.recorded_at}>"
