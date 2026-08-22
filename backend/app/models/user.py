from sqlalchemy import Boolean, Column, Enum, String, text
from sqlalchemy.orm import relationship

from app.db.base import Base, TimestampMixin, UUIDPrimaryKeyMixin
from app.models.enums import UserRole


class User(Base, UUIDPrimaryKeyMixin, TimestampMixin):
    __tablename__ = "users"

    email = Column(String(255), unique=True, nullable=False)
    hashed_password = Column(String(255), nullable=False)
    full_name = Column(String(120), nullable=False)
    phone_number = Column(String(20), nullable=True)
    role = Column(Enum(UserRole, name="user_role"), nullable=False)
    is_active = Column(Boolean, nullable=False, default=True, server_default=text("true"))

    driver = relationship(
        "Driver", back_populates="user", uselist=False,
        cascade="all, delete-orphan", lazy="selectin",
    )
    notifications = relationship(
        "Notification", back_populates="user",
        cascade="all, delete-orphan", lazy="raise_on_sql",
    )
    refresh_tokens = relationship(
        "RefreshToken", back_populates="user",
        cascade="all, delete-orphan", lazy="raise_on_sql",
    )
    reported_incidents = relationship(
        "Incident", foreign_keys="Incident.reported_by_id",
        back_populates="reported_by", lazy="raise_on_sql",
    )
    assigned_incidents = relationship(
        "Incident", foreign_keys="Incident.assigned_to_id",
        back_populates="assigned_to", lazy="raise_on_sql",
    )

    def __repr__(self) -> str:
        return f"<User {self.email} role={self.role}>"
