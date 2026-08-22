from datetime import date
from typing import TYPE_CHECKING
from uuid import UUID

from sqlalchemy import Date, Enum, ForeignKey, String
from sqlalchemy.orm import Mapped, mapped_column, relationship

from app.db.base import Base, TimestampMixin, UUIDPrimaryKeyMixin
from app.models.enums import DriverStatus
from app.models.user import User

if TYPE_CHECKING:
    from app.models.trip import Trip
    from app.models.vehicle_assignment import VehicleAssignment


class Driver(Base, UUIDPrimaryKeyMixin, TimestampMixin):
    __tablename__ = "drivers"

    user_id: Mapped[UUID] = mapped_column(
        ForeignKey("users.id", ondelete="CASCADE"), unique=True, nullable=False
    )
    license_number: Mapped[str] = mapped_column(String(30), unique=True, nullable=False)
    license_expiry: Mapped[date] = mapped_column(Date, nullable=False)
    status: Mapped[DriverStatus] = mapped_column(
        Enum(DriverStatus, name="driver_status"),
        nullable=False,
        default=DriverStatus.ACTIVE,
        server_default=DriverStatus.ACTIVE.value,
    )

    user: Mapped[User] = relationship(back_populates="driver", lazy="joined")

    assignments: Mapped[list["VehicleAssignment"]] = relationship(
        back_populates="driver", lazy="raise_on_sql"
    )
    trips: Mapped[list["Trip"]] = relationship(back_populates="driver", lazy="raise_on_sql")

    def __repr__(self) -> str:
        return f"<Driver {self.license_number} status={self.status}>"
