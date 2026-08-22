from uuid import UUID

from fastapi import APIRouter, Depends

from app.api.deps import CurrentUser, DbSession, FleetManager
from app.schemas.common import MessageResponse, Page, PageParams
from app.schemas.notification import NotificationResponse, SweepResult, UnreadCount
from app.services.notification_service import NotificationService

router = APIRouter(prefix="/notifications", tags=["Notifications"])


@router.get("", response_model=Page[NotificationResponse])
async def list_notifications(
    db: DbSession,
    user: CurrentUser,
    params: PageParams = Depends(),
    unread_only: bool = False,
) -> Page[NotificationResponse]:
    return await NotificationService(db).list_for_user(params, user, unread_only=unread_only)


@router.get("/unread-count", response_model=UnreadCount)
async def unread_count(db: DbSession, user: CurrentUser) -> UnreadCount:
    return UnreadCount(unread=await NotificationService(db).unread_count(user))


@router.post("/read-all", response_model=MessageResponse)
async def mark_all_read(db: DbSession, user: CurrentUser) -> MessageResponse:
    count = await NotificationService(db).mark_all_read(user)
    return MessageResponse(message=f"{count} notification(s) marked read")


@router.post("/{notification_id}/read", response_model=NotificationResponse)
async def mark_read(
    notification_id: UUID, db: DbSession, user: CurrentUser
) -> NotificationResponse:
    return await NotificationService(db).mark_read(notification_id, user)


@router.post("/sweep", response_model=SweepResult)
async def run_sweep(db: DbSession, _: FleetManager) -> SweepResult:
    """Generate expiry and maintenance-due alerts. A nightly scheduler would call this."""
    return await NotificationService(db).sweep()
