from typing import TYPE_CHECKING

from sqlalchemy import Boolean, Enum, String, text
from sqlalchemy.orm import Mapped, mapped_column, relationship

from app.db.base import Base, TimestampMixin, UUIDPrimaryKeyMixin
from app.models.enums import UserRole

if TYPE_CHECKING:
    from app.models.driver import Driver
    from app.models.incident import Incident
    from app.models.notification import Notification


class User(Base, UUIDPrimaryKeyMixin, TimestampMixin):
    __tablename__ = "users"

    email: Mapped[str] = mapped_column(String(255), unique=True, nullable=False)
    hashed_password: Mapped[str] = mapped_column(String(255), nullable=False)
    full_name: Mapped[str] = mapped_column(String(120), nullable=False)
    phone_number: Mapped[str | None] = mapped_column(String(20))
    role: Mapped[UserRole] = mapped_column(
        Enum(UserRole, name="user_role"), nullable=False
    )
    is_active: Mapped[bool] = mapped_column(
        Boolean, nullable=False, default=True, server_default=text("true")
    )

    driver: Mapped["Driver | None"] = relationship(
        back_populates="user", cascade="all, delete-orphan", lazy="selectin"
    )

    notifications: Mapped[list["Notification"]] = relationship(
        back_populates="user", cascade="all, delete-orphan", lazy="raise_on_sql"
    )
    reported_incidents: Mapped[list["Incident"]] = relationship(
        foreign_keys="Incident.reported_by_id", back_populates="reported_by", lazy="raise_on_sql"
    )
    assigned_incidents: Mapped[list["Incident"]] = relationship(
        foreign_keys="Incident.assigned_to_id", back_populates="assigned_to", lazy="raise_on_sql"
    )

    def __repr__(self) -> str:
        return f"<User {self.email} role={self.role}>"
