from sqlalchemy import Boolean, Column, DateTime, Enum, ForeignKey, Index, String, Text, Uuid, text
from sqlalchemy.orm import relationship

from app.db.base import Base, TimestampMixin, UUIDPrimaryKeyMixin
from app.models.enums import NotificationType


class Notification(Base, UUIDPrimaryKeyMixin, TimestampMixin):
    __tablename__ = "notifications"
    __table_args__ = (Index("ix_notifications_user_is_read", "user_id", "is_read"),)

    user_id = Column(Uuid, ForeignKey("users.id", ondelete="CASCADE"), nullable=False)
    notification_type = Column(Enum(NotificationType, name="notification_type"), nullable=False)
    title = Column(String(200), nullable=False)
    body = Column(Text, nullable=False)
    is_read = Column(Boolean, nullable=False, default=False, server_default=text("false"))
    read_at = Column(DateTime(timezone=True), nullable=True)
    related_entity_type = Column(String(50), nullable=True)
    related_entity_id = Column(Uuid, nullable=True)

    user = relationship("User", back_populates="notifications", lazy="raise_on_sql")

    def __repr__(self) -> str:
        return f"<Notification {self.notification_type} user={self.user_id} read={self.is_read}>"
