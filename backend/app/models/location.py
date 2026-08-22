from sqlalchemy import CheckConstraint, Column, DateTime, ForeignKey, Index, Numeric, Uuid, func
from sqlalchemy.orm import relationship

from app.db.base import Base, UUIDPrimaryKeyMixin


class Location(Base, UUIDPrimaryKeyMixin):
    __tablename__ = "locations"
    __table_args__ = (
        CheckConstraint("latitude BETWEEN -90 AND 90", name="latitude_range"),
        CheckConstraint("longitude BETWEEN -180 AND 180", name="longitude_range"),
        Index("ix_locations_vehicle_recorded_at", "vehicle_id", "recorded_at"),
        Index("ix_locations_trip_recorded_at", "trip_id", "recorded_at"),
    )

    trip_id = Column(Uuid, ForeignKey("trips.id", ondelete="CASCADE"), nullable=False)
    vehicle_id = Column(Uuid, ForeignKey("vehicles.id", ondelete="CASCADE"), nullable=False)
    latitude = Column(Numeric(9, 6), nullable=False)
    longitude = Column(Numeric(9, 6), nullable=False)
    speed_kmph = Column(Numeric(6, 2), nullable=True)
    heading = Column(Numeric(5, 2), nullable=True)
    accuracy_m = Column(Numeric(7, 2), nullable=True)
    recorded_at = Column(DateTime(timezone=True), nullable=False)
    received_at = Column(DateTime(timezone=True), nullable=False, server_default=func.now())

    trip = relationship("Trip", back_populates="locations", lazy="raise_on_sql")

    def __repr__(self) -> str:
        return f"<Location {self.latitude},{self.longitude} at={self.recorded_at}>"
