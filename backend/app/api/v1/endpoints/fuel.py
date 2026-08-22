from datetime import date
from uuid import UUID

from fastapi import APIRouter, Depends, status

from app.api.deps import DbSession, FleetManager
from app.schemas.common import MessageResponse, Page, PageParams
from app.schemas.fuel import FuelCreate, FuelEfficiency, FuelResponse, FuelUpdate
from app.services.fuel_service import FuelService

router = APIRouter(prefix="/fuel", tags=["Fuel"])


@router.post("", response_model=FuelResponse, status_code=status.HTTP_201_CREATED)
async def log_fuel(payload: FuelCreate, db: DbSession, user: FleetManager) -> FuelResponse:
    return await FuelService(db).create(payload, user.id)


@router.get("", response_model=Page[FuelResponse])
async def list_fuel(
    db: DbSession,
    _: FleetManager,
    params: PageParams = Depends(),
    vehicle_id: UUID | None = None,
    driver_id: UUID | None = None,
    date_from: date | None = None,
    date_to: date | None = None,
) -> Page[FuelResponse]:
    return await FuelService(db).list_logs(
        params, vehicle_id=vehicle_id, driver_id=driver_id, date_from=date_from, date_to=date_to
    )


@router.get("/efficiency/{vehicle_id}", response_model=FuelEfficiency)
async def fuel_efficiency(vehicle_id: UUID, db: DbSession, _: FleetManager) -> FuelEfficiency:
    """Fuel economy computed between consecutive full-tank fills."""
    return await FuelService(db).efficiency(vehicle_id)


@router.get("/{fuel_id}", response_model=FuelResponse)
async def get_fuel(fuel_id: UUID, db: DbSession, _: FleetManager) -> FuelResponse:
    service = FuelService(db)
    entry = await service.get_or_404(fuel_id)
    return service._build(entry, entry.vehicle)


@router.put("/{fuel_id}", response_model=FuelResponse)
async def update_fuel(
    fuel_id: UUID, payload: FuelUpdate, db: DbSession, _: FleetManager
) -> FuelResponse:
    return await FuelService(db).update(fuel_id, payload)


@router.delete("/{fuel_id}", response_model=MessageResponse)
async def delete_fuel(fuel_id: UUID, db: DbSession, _: FleetManager) -> MessageResponse:
    await FuelService(db).delete(fuel_id)
    return MessageResponse(message="Fuel log deleted")
