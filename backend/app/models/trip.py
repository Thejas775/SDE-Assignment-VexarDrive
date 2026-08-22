from sqlalchemy import (
    CheckConstraint, Column, DateTime, Enum, ForeignKey, Index, Integer, Numeric,
    String, Text, Uuid,
)
from sqlalchemy.orm import relationship

from app.db.base import Base, TimestampMixin, UUIDPrimaryKeyMixin
from app.models.enums import TripStatus


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

    trip_number = Column(String(20), unique=True, nullable=False)
    vehicle_id = Column(Uuid, ForeignKey("vehicles.id"), nullable=False)
    driver_id = Column(Uuid, ForeignKey("drivers.id"), nullable=False)

    source = Column(String(200), nullable=False)
    destination = Column(String(200), nullable=False)
    scheduled_start = Column(DateTime(timezone=True), nullable=False)
    scheduled_end = Column(DateTime(timezone=True), nullable=False)
    status = Column(
        Enum(TripStatus, name="trip_status"), nullable=False, index=True,
        default=TripStatus.SCHEDULED, server_default=TripStatus.SCHEDULED.value,
    )

    actual_start = Column(DateTime(timezone=True), nullable=True)
    actual_end = Column(DateTime(timezone=True), nullable=True)
    start_odometer = Column(Integer, nullable=True)
    end_odometer = Column(Integer, nullable=True)
    start_latitude = Column(Numeric(9, 6), nullable=True)
    start_longitude = Column(Numeric(9, 6), nullable=True)
    end_latitude = Column(Numeric(9, 6), nullable=True)
    end_longitude = Column(Numeric(9, 6), nullable=True)
    distance_km = Column(Numeric(10, 2), nullable=True)
    notes = Column(Text, nullable=True)

    vehicle = relationship("Vehicle", back_populates="trips", lazy="joined")
    driver = relationship("Driver", back_populates="trips", lazy="joined")
    locations = relationship(
        "Location", back_populates="trip", lazy="raise_on_sql", cascade="all, delete-orphan"
    )
    incidents = relationship("Incident", back_populates="trip", lazy="raise_on_sql")

    def __repr__(self) -> str:
        return f"<Trip {self.trip_number} {self.source}->{self.destination} status={self.status}>"
