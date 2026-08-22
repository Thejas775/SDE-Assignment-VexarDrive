from sqlalchemy import Column, Date, Enum, ForeignKey, String, Uuid
from sqlalchemy.orm import relationship

from app.db.base import Base, TimestampMixin, UUIDPrimaryKeyMixin
from app.models.enums import DriverStatus


class Driver(Base, UUIDPrimaryKeyMixin, TimestampMixin):
    __tablename__ = "drivers"

    user_id = Column(
        Uuid, ForeignKey("users.id", ondelete="CASCADE"), unique=True, nullable=False
    )
    license_number = Column(String(30), unique=True, nullable=False)
    license_expiry = Column(Date, nullable=False)
    status = Column(
        Enum(DriverStatus, name="driver_status"), nullable=False,
        default=DriverStatus.ACTIVE, server_default=DriverStatus.ACTIVE.value,
    )

    user = relationship("User", back_populates="driver", lazy="joined")
    assignments = relationship("VehicleAssignment", back_populates="driver", lazy="raise_on_sql")
    trips = relationship("Trip", back_populates="driver", lazy="raise_on_sql")

    def __repr__(self) -> str:
        return f"<Driver {self.license_number} status={self.status}>"
