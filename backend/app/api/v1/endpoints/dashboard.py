from fastapi import APIRouter

from app.api.deps import DbSession, FleetManager
from app.schemas.dashboard import DashboardResponse
from app.services.dashboard_service import DashboardService

router = APIRouter(prefix="/dashboard", tags=["Dashboard"])


@router.get("", response_model=DashboardResponse)
async def dashboard(db: DbSession, _: FleetManager) -> DashboardResponse:
    return await DashboardService(db).summary()
