from datetime import date, timedelta

from fastapi import APIRouter, Query

from app.api.deps import DbSession, FleetManager
from app.schemas.analytics import FleetAnalytics, MonthlyPoint, VehicleAnalytics
from app.services.analytics_service import AnalyticsService

router = APIRouter(prefix="/analytics", tags=["Analytics"])


def _default_range(date_from: date | None, date_to: date | None) -> tuple[date, date]:
    end = date_to or date.today()
    start = date_from or (end - timedelta(days=29))
    return start, end


@router.get("/fleet", response_model=FleetAnalytics)
async def fleet_analytics(
    db: DbSession,
    _: FleetManager,
    date_from: date | None = None,
    date_to: date | None = None,
    top: int = Query(default=5, ge=1, le=20),
) -> FleetAnalytics:
    start, end = _default_range(date_from, date_to)
    return await AnalyticsService(db).fleet(start, end, top)


@router.get("/vehicles", response_model=list[VehicleAnalytics])
async def vehicle_analytics(
    db: DbSession,
    _: FleetManager,
    date_from: date | None = None,
    date_to: date | None = None,
) -> list[VehicleAnalytics]:
    start, end = _default_range(date_from, date_to)
    return await AnalyticsService(db).per_vehicle(start, end)


@router.get("/monthly", response_model=list[MonthlyPoint])
async def monthly_analytics(
    db: DbSession, _: FleetManager, months: int = Query(default=6, ge=1, le=24)
) -> list[MonthlyPoint]:
    return await AnalyticsService(db).monthly(months)
