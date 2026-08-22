from datetime import datetime
from decimal import Decimal
from typing import TYPE_CHECKING
from uuid import UUID

from sqlalchemy import (
    CheckConstraint,
    DateTime,
    Enum,
    ForeignKey,
    Index,
    Integer,
    Numeric,
    String,
    Text,
)
from sqlalchemy.orm import Mapped, mapped_column, relationship

from app.db.base import Base, TimestampMixin, UUIDPrimaryKeyMixin
from app.models.enums import TripStatus

if TYPE_CHECKING:
    from app.models.driver import Driver
    from app.models.incident import Incident
    from app.models.location import Location
    from app.models.vehicle import Vehicle


class Trip(Base, UUIDPrimaryKeyMixin, TimestampMixin):
    __tablename__ = "trips"
    __table_args__ = (
        CheckConstraint(
            "end_odometer IS NULL OR start_odometer IS NULL OR end_odometer >= start_odometer",
            name="odometer_not_decreasing",
        ),
        CheckConstraint("scheduled_end > scheduled_start", name="schedule_ordered"),
        Index("ix_trips_vehicle_status", "vehicle_id", "status"),
        Index("ix_trips_driver_status", "driver_id", "status"),
    )

    trip_number: Mapped[str] = mapped_column(String(20), unique=True, nullable=False)
    vehicle_id: Mapped[UUID] = mapped_column(ForeignKey("vehicles.id"), nullable=False)
    driver_id: Mapped[UUID] = mapped_column(ForeignKey("drivers.id"), nullable=False)

    source: Mapped[str] = mapped_column(String(200), nullable=False)
    destination: Mapped[str] = mapped_column(String(200), nullable=False)
    scheduled_start: Mapped[datetime] = mapped_column(DateTime(timezone=True), nullable=False)
    scheduled_end: Mapped[datetime] = mapped_column(DateTime(timezone=True), nullable=False)
    status: Mapped[TripStatus] = mapped_column(
        Enum(TripStatus, name="trip_status"),
        nullable=False,
        default=TripStatus.SCHEDULED,
        server_default=TripStatus.SCHEDULED.value,
        index=True,
    )

    actual_start: Mapped[datetime | None] = mapped_column(DateTime(timezone=True))
    actual_end: Mapped[datetime | None] = mapped_column(DateTime(timezone=True))
    start_odometer: Mapped[int | None] = mapped_column(Integer)
    end_odometer: Mapped[int | None] = mapped_column(Integer)
    start_latitude: Mapped[Decimal | None] = mapped_column(Numeric(9, 6))
    start_longitude: Mapped[Decimal | None] = mapped_column(Numeric(9, 6))
    end_latitude: Mapped[Decimal | None] = mapped_column(Numeric(9, 6))
    end_longitude: Mapped[Decimal | None] = mapped_column(Numeric(9, 6))
    distance_km: Mapped[Decimal | None] = mapped_column(Numeric(10, 2))
    notes: Mapped[str | None] = mapped_column(Text)

    vehicle: Mapped["Vehicle"] = relationship(back_populates="trips", lazy="joined")
    driver: Mapped["Driver"] = relationship(back_populates="trips", lazy="joined")
    locations: Mapped[list["Location"]] = relationship(
        back_populates="trip", lazy="raise_on_sql", cascade="all, delete-orphan"
    )
    incidents: Mapped[list["Incident"]] = relationship(back_populates="trip", lazy="raise_on_sql")

    def __repr__(self) -> str:
        return f"<Trip {self.trip_number} {self.source}->{self.destination} status={self.status}>"
