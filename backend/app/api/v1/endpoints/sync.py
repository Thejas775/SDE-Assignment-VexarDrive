from fastapi import APIRouter

from app.api.deps import CurrentUser, DbSession
from app.schemas.sync import SyncRequest, SyncResponse
from app.services.sync_service import SyncService

router = APIRouter(prefix="/sync", tags=["Sync"])


@router.post("", response_model=SyncResponse)
async def sync(payload: SyncRequest, db: DbSession, user: CurrentUser) -> SyncResponse:
    """Replay a mobile client's offline queue in one request.

    Each operation reports its own outcome; a failure does not abort the batch.
    """
    return await SyncService(db).apply(payload, user)
