from sqlalchemy import Column, DateTime, ForeignKey, Integer, String, Uuid, UniqueConstraint
from sqlalchemy.dialects.postgresql import JSONB

from app.db.base import Base, UUIDPrimaryKeyMixin


class IdempotencyKey(Base, UUIDPrimaryKeyMixin):
    """Replay guard for offline clients.

    A queued write is retried until the phone sees a response, so the same
    request can arrive several times. The first attempt records its outcome
    here; later attempts with the same key get that outcome back instead of
    executing again.
    """

    __tablename__ = "idempotency_keys"
    __table_args__ = (UniqueConstraint("key", "user_id", name="uq_idempotency_keys_key_user_id"),)

    key = Column(String(128), nullable=False)
    user_id = Column(Uuid, ForeignKey("users.id", ondelete="CASCADE"), nullable=False)
    endpoint = Column(String(200), nullable=False)
    request_hash = Column(String(64), nullable=False)
    status_code = Column(Integer, nullable=True)
    response_body = Column(JSONB, nullable=True)
    created_at = Column(DateTime(timezone=True), nullable=False)
    completed_at = Column(DateTime(timezone=True), nullable=True)

    def __repr__(self) -> str:
        return f"<IdempotencyKey {self.key} status={self.status_code}>"
