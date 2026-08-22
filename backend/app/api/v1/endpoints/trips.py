from datetime import date
from uuid import UUID

from fastapi import APIRouter, Body, Depends, Query, status

from app.api.deps import CurrentUser, DbSession, FleetManager
from app.models.enums import TripStatus
from app.schemas.common import Page, PageParams
from app.schemas.trip import (
    TripComplete,
    TripCreate,
    TripResponse,
    TripStart,
    TripStatusUpdate,
)
from app.services.trip_service import TripService

router = APIRouter(prefix="/trips", tags=["Trips"])


@router.post("", response_model=TripResponse, status_code=status.HTTP_201_CREATED)
async def create_trip(payload: TripCreate, db: DbSession, _: FleetManager) -> TripResponse:
    return await TripService(db).create(payload)


@router.get("", response_model=Page[TripResponse])
async def list_trips(
    db: DbSession,
    _: FleetManager,
    params: PageParams = Depends(),
    vehicle_id: UUID | None = None,
    driver_id: UUID | None = None,
    status_filter: TripStatus | None = Query(default=None, alias="status"),
    active_only: bool = False,
    scheduled_on: date | None = None,
) -> Page[TripResponse]:
    return await TripService(db).list_trips(
        params,
        vehicle_id=vehicle_id,
        driver_id=driver_id,
        status=status_filter,
        active_only=active_only,
        scheduled_on=scheduled_on,
    )


@router.get("/my", response_model=Page[TripResponse])
async def my_trips(
    db: DbSession,
    user: CurrentUser,
    params: PageParams = Depends(),
    status_filter: TripStatus | None = Query(default=None, alias="status"),
    active_only: bool = False,
) -> Page[TripResponse]:
    return await TripService(db).for_driver(
        params, user, status=status_filter, active_only=active_only
    )


@router.get("/{trip_id}", response_model=TripResponse)
async def get_trip(trip_id: UUID, db: DbSession, user: CurrentUser) -> TripResponse:
    return await TripService(db).get_for_user(trip_id, user)


@router.post("/{trip_id}/start", response_model=TripResponse)
async def start_trip(
    trip_id: UUID, payload: TripStart, db: DbSession, user: CurrentUser
) -> TripResponse:
    return await TripService(db).start(trip_id, payload, user)


@router.post("/{trip_id}/status", response_model=TripResponse)
async def update_trip_status(
    trip_id: UUID, payload: TripStatusUpdate, db: DbSession, user: CurrentUser
) -> TripResponse:
    return await TripService(db).update_status(trip_id, payload.status, user)


@router.post("/{trip_id}/complete", response_model=TripResponse)
async def complete_trip(
    trip_id: UUID, payload: TripComplete, db: DbSession, user: CurrentUser
) -> TripResponse:
    return await TripService(db).complete(trip_id, payload, user)


@router.post("/{trip_id}/cancel", response_model=TripResponse)
async def cancel_trip(
    trip_id: UUID,
    db: DbSession,
    _: FleetManager,
    reason: str | None = Body(default=None, embed=True),
) -> TripResponse:
    return await TripService(db).cancel(trip_id, reason)
