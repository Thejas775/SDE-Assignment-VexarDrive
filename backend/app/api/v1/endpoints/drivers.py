from uuid import UUID

from fastapi import APIRouter, Depends, Query, status

from app.api.deps import CurrentUser, DbSession, FleetManager
from app.core.config import settings
from app.models.enums import DriverStatus
from app.schemas.common import Page
from app.schemas.common import PageParams
from app.schemas.driver import DriverCreate, DriverHistory, DriverResponse, DriverUpdate
from app.services.driver_service import DriverService

router = APIRouter(prefix="/drivers", tags=["Drivers"])


@router.post("", response_model=DriverResponse, status_code=status.HTTP_201_CREATED)
async def create_driver(payload: DriverCreate, db: DbSession, _: FleetManager) -> DriverResponse:
    return await DriverService(db).create(payload)


@router.get("", response_model=Page[DriverResponse])
async def list_drivers(
    db: DbSession,
    _: FleetManager,
    params: PageParams = Depends(),
    search: str | None = Query(default=None, description="name, email, phone or licence number"),
    status_filter: DriverStatus | None = Query(default=None, alias="status"),
    license_expiring: bool = Query(default=False),
) -> Page[DriverResponse]:
    return await DriverService(db).list_drivers(
        params,
        search=search,
        status=status_filter,
        license_expiring=license_expiring,
        warning_days=settings.DOCUMENT_EXPIRY_WARNING_DAYS,
    )


@router.get("/me", response_model=DriverResponse)
async def my_profile(db: DbSession, user: CurrentUser) -> DriverResponse:
    return await DriverService(db).me(user)


@router.get("/{driver_id}", response_model=DriverResponse)
async def get_driver(driver_id: UUID, db: DbSession, user: CurrentUser) -> DriverResponse:
    return await DriverService(db).get_for_user(driver_id, user)


@router.put("/{driver_id}", response_model=DriverResponse)
async def update_driver(
    driver_id: UUID, payload: DriverUpdate, db: DbSession, _: FleetManager
) -> DriverResponse:
    return await DriverService(db).update(driver_id, payload)


@router.post("/{driver_id}/activate", response_model=DriverResponse)
async def activate_driver(driver_id: UUID, db: DbSession, _: FleetManager) -> DriverResponse:
    return await DriverService(db).set_status(driver_id, DriverStatus.ACTIVE)


@router.post("/{driver_id}/deactivate", response_model=DriverResponse)
async def deactivate_driver(driver_id: UUID, db: DbSession, _: FleetManager) -> DriverResponse:
    return await DriverService(db).set_status(driver_id, DriverStatus.INACTIVE)


@router.post("/{driver_id}/suspend", response_model=DriverResponse)
async def suspend_driver(driver_id: UUID, db: DbSession, _: FleetManager) -> DriverResponse:
    return await DriverService(db).set_status(driver_id, DriverStatus.SUSPENDED)


@router.get("/{driver_id}/history", response_model=DriverHistory)
async def driver_history(driver_id: UUID, db: DbSession, _: FleetManager) -> DriverHistory:
    return await DriverService(db).history(driver_id)
