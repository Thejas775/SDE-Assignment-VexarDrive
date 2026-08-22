from datetime import datetime
from uuid import UUID

from pydantic import BaseModel

from app.models.enums import NotificationType
from app.schemas.common import ORMModel


class NotificationResponse(ORMModel):
    id: UUID
    notification_type: NotificationType
    title: str
    body: str
    is_read: bool
    read_at: datetime | None
    related_entity_type: str | None
    related_entity_id: UUID | None
    created_at: datetime


class UnreadCount(BaseModel):
    unread: int


class SweepResult(BaseModel):
    insurance_expiring: int
    registration_expiring: int
    license_expiring: int
    maintenance_due: int
    total_created: int
