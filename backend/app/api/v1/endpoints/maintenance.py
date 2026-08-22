from datetime import date
from uuid import UUID

from fastapi import APIRouter, Depends, Query, status

from app.api.deps import DbSession, FleetManager
from app.models.enums import MaintenanceType
from app.schemas.common import Page, PageParams
from app.schemas.maintenance import (
    MaintenanceCreate,
    MaintenanceDueItem,
    MaintenanceResponse,
    MaintenanceUpdate,
)
from app.services.maintenance_service import MaintenanceService

router = APIRouter(prefix="/maintenance", tags=["Maintenance"])


@router.post("", response_model=MaintenanceResponse, status_code=status.HTTP_201_CREATED)
async def create_record(
    payload: MaintenanceCreate, db: DbSession, user: FleetManager
) -> MaintenanceResponse:
    return await MaintenanceService(db).create(payload, user.id)


@router.get("", response_model=Page[MaintenanceResponse])
async def list_records(
    db: DbSession,
    _: FleetManager,
    params: PageParams = Depends(),
    vehicle_id: UUID | None = None,
    maintenance_type: MaintenanceType | None = None,
    date_from: date | None = None,
    date_to: date | None = None,
) -> Page[MaintenanceResponse]:
    return await MaintenanceService(db).list_records(
        params,
        vehicle_id=vehicle_id,
        maintenance_type=maintenance_type,
        date_from=date_from,
        date_to=date_to,
    )


@router.get("/due", response_model=list[MaintenanceDueItem])
async def maintenance_due(db: DbSession, _: FleetManager) -> list[MaintenanceDueItem]:
    return await MaintenanceService(db).due()


@router.get("/{record_id}", response_model=MaintenanceResponse)
async def get_record(record_id: UUID, db: DbSession, _: FleetManager) -> MaintenanceResponse:
    service = MaintenanceService(db)
    record = await service.get_or_404(record_id)
    return service._build(record, record.vehicle)


@router.put("/{record_id}", response_model=MaintenanceResponse)
async def update_record(
    record_id: UUID, payload: MaintenanceUpdate, db: DbSession, _: FleetManager
) -> MaintenanceResponse:
    return await MaintenanceService(db).update(record_id, payload)
