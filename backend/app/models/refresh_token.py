from sqlalchemy import Column, DateTime, ForeignKey, Index, String, Uuid
from sqlalchemy.orm import relationship

from app.db.base import Base, UUIDPrimaryKeyMixin


class RefreshToken(Base, UUIDPrimaryKeyMixin):
    __tablename__ = "refresh_tokens"
    __table_args__ = (Index("ix_refresh_tokens_user_id", "user_id"),)

    user_id = Column(Uuid, ForeignKey("users.id", ondelete="CASCADE"), nullable=False)
    token_hash = Column(String(64), unique=True, nullable=False)
    expires_at = Column(DateTime(timezone=True), nullable=False)
    revoked_at = Column(DateTime(timezone=True), nullable=True)
    created_at = Column(DateTime(timezone=True), nullable=False)

    user = relationship("User", back_populates="refresh_tokens", lazy="raise_on_sql")

    def __repr__(self) -> str:
        return f"<RefreshToken user={self.user_id} revoked={self.revoked_at is not None}>"
