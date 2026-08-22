from uuid import UUID

from fastapi import APIRouter, Query, WebSocket, WebSocketDisconnect, status

from app.api.deps import CurrentUser, DbSession, FleetManager
from app.core.exceptions import CustomException
from app.core.logging import get_logger
from app.core.ws_manager import tracking_manager
from app.db.session import session_factory
from app.models.enums import UserRole
from app.schemas.location import (
    LocationBatch,
    LocationIngestResult,
    LocationResponse,
    VehiclePosition,
)
from app.services.auth_service import AuthService
from app.services.location_service import LocationService

router = APIRouter(tags=["Tracking"])
logger = get_logger(__name__)


@router.post(
    "/locations", response_model=LocationIngestResult, status_code=status.HTTP_202_ACCEPTED
)
async def ingest_locations(
    payload: LocationBatch, db: DbSession, user: CurrentUser
) -> LocationIngestResult:
    return await LocationService(db).ingest(payload, user)


@router.get("/trips/{trip_id}/route", response_model=list[LocationResponse])
async def trip_route(
    trip_id: UUID,
    db: DbSession,
    user: CurrentUser,
    limit: int = Query(default=1000, ge=1, le=5000),
) -> list[LocationResponse]:
    return await LocationService(db).route(trip_id, user, limit)


@router.get("/vehicles/{vehicle_id}/location", response_model=VehiclePosition)
async def vehicle_location(
    vehicle_id: UUID, db: DbSession, _: FleetManager
) -> VehiclePosition:
    return await LocationService(db).latest_for_vehicle(vehicle_id)


@router.get("/tracking/live", response_model=list[VehiclePosition])
async def live_positions(db: DbSession, _: FleetManager) -> list[VehiclePosition]:
    return await LocationService(db).live_positions()


@router.websocket("/tracking/ws")
async def tracking_socket(websocket: WebSocket, token: str = Query(...)) -> None:
    """Live fleet positions.

    The token arrives as a query parameter because the browser WebSocket API
    cannot set an Authorization header.
    """
    async with session_factory()() as db:
        try:
            user = await AuthService(db).user_from_access_token(token)
        except CustomException:
            await websocket.close(code=status.WS_1008_POLICY_VIOLATION, reason="Invalid token")
            return
        if user.role is not UserRole.FLEET_MANAGER:
            await websocket.close(code=status.WS_1008_POLICY_VIOLATION, reason="Forbidden")
            return
        snapshot = await LocationService(db).live_positions()

    await tracking_manager.connect(websocket)
    try:
        await websocket.send_json(
            {"event": "snapshot", "data": [p.model_dump(mode="json") for p in snapshot]}
        )
        while True:
            await websocket.receive_text()
    except WebSocketDisconnect:
        pass
    finally:
        await tracking_manager.disconnect(websocket)
