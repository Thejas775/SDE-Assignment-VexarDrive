from uuid import UUID

from fastapi import APIRouter, Depends, Query, Response, status

from app.api.deps import CurrentUser, DbSession, FleetManager
from app.core.config import settings
from app.core.qr import render
from app.models.enums import FuelType, VehicleStatus, VehicleType
from app.schemas.common import Page, PageParams
from app.schemas.vehicle import VehicleCreate, VehicleResponse, VehicleUpdate
from app.services.vehicle_service import VehicleService

router = APIRouter(prefix="/vehicles", tags=["Vehicles"])


@router.post("", response_model=VehicleResponse, status_code=status.HTTP_201_CREATED)
async def create_vehicle(
    payload: VehicleCreate, db: DbSession, _: FleetManager
) -> VehicleResponse:
    return await VehicleService(db).create(payload)


@router.get("", response_model=Page[VehicleResponse])
async def list_vehicles(
    db: DbSession,
    _: FleetManager,
    params: PageParams = Depends(),
    search: str | None = Query(default=None, description="registration number, make or model"),
    status_filter: VehicleStatus | None = Query(default=None, alias="status"),
    vehicle_type: VehicleType | None = None,
    fuel_type: FuelType | None = None,
    expiring_documents: bool = Query(
        default=False, description="only vehicles with insurance or registration expiring soon"
    ),
) -> Page[VehicleResponse]:
    return await VehicleService(db).list_vehicles(
        params,
        search=search,
        status=status_filter,
        vehicle_type=vehicle_type,
        fuel_type=fuel_type,
        expiring_documents=expiring_documents,
        warning_days=settings.DOCUMENT_EXPIRY_WARNING_DAYS,
    )


@router.get("/my-vehicle", response_model=VehicleResponse)
async def my_vehicle(db: DbSession, user: CurrentUser) -> VehicleResponse:
    return await VehicleService(db).current_for_driver(user)


@router.get("/lookup", response_model=VehicleResponse)
async def lookup_vehicle(
    db: DbSession,
    user: CurrentUser,
    code: str = Query(description="scanned QR payload, or a bare vehicle id"),
) -> VehicleResponse:
    return await VehicleService(db).lookup_by_code(code, user)


@router.get(
    "/{vehicle_id}/qr",
    responses={200: {"content": {"image/png": {}, "image/svg+xml": {}}}},
    response_class=Response,
)
async def vehicle_qr(
    vehicle_id: UUID,
    db: DbSession,
    _: FleetManager,
    image_format: str = Query(default="png", pattern="^(png|svg)$", alias="format"),
    box_size: int = Query(default=10, ge=2, le=40),
) -> Response:
    """QR sticker for the vehicle. Scanning it deep-links into the mobile app."""
    payload = await VehicleService(db).qr_payload(vehicle_id)
    body, media_type = render(payload, image_format, box_size)
    return Response(content=body, media_type=media_type)


@router.get("/{vehicle_id}", response_model=VehicleResponse)
async def get_vehicle(vehicle_id: UUID, db: DbSession, user: CurrentUser) -> VehicleResponse:
    return await VehicleService(db).get_for_user(vehicle_id, user)


@router.put("/{vehicle_id}", response_model=VehicleResponse)
async def update_vehicle(
    vehicle_id: UUID, payload: VehicleUpdate, db: DbSession, _: FleetManager
) -> VehicleResponse:
    return await VehicleService(db).update(vehicle_id, payload)


@router.post("/{vehicle_id}/deactivate", response_model=VehicleResponse)
async def deactivate_vehicle(
    vehicle_id: UUID, db: DbSession, _: FleetManager
) -> VehicleResponse:
    return await VehicleService(db).deactivate(vehicle_id)


@router.post("/{vehicle_id}/activate", response_model=VehicleResponse)
async def activate_vehicle(vehicle_id: UUID, db: DbSession, _: FleetManager) -> VehicleResponse:
    return await VehicleService(db).activate(vehicle_id)
