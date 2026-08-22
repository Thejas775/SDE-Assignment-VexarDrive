from uuid import UUID

from fastapi import APIRouter, Depends, Query, status

from app.api.deps import CurrentUser, DbSession, FleetManager
from app.models.enums import IncidentSeverity, IncidentStatus
from app.schemas.common import Page, PageParams
from app.schemas.incident import (
    IncidentAssign,
    IncidentCreate,
    IncidentResponse,
    IncidentStatusUpdate,
    IncidentUpdate,
)
from app.services.incident_service import IncidentService

router = APIRouter(prefix="/incidents", tags=["Incidents"])


@router.post("", response_model=IncidentResponse, status_code=status.HTTP_201_CREATED)
async def report_incident(
    payload: IncidentCreate, db: DbSession, user: CurrentUser
) -> IncidentResponse:
    return await IncidentService(db).report(payload, user)


@router.get("", response_model=Page[IncidentResponse])
async def list_incidents(
    db: DbSession,
    _: FleetManager,
    params: PageParams = Depends(),
    vehicle_id: UUID | None = None,
    status_filter: IncidentStatus | None = Query(default=None, alias="status"),
    severity: IncidentSeverity | None = None,
    open_only: bool = False,
) -> Page[IncidentResponse]:
    return await IncidentService(db).list_incidents(
        params,
        vehicle_id=vehicle_id,
        status=status_filter,
        severity=severity,
        open_only=open_only,
    )


@router.get("/my", response_model=Page[IncidentResponse])
async def my_incidents(
    db: DbSession, user: CurrentUser, params: PageParams = Depends()
) -> Page[IncidentResponse]:
    return await IncidentService(db).for_driver(params, user)


@router.get("/{incident_id}", response_model=IncidentResponse)
async def get_incident(
    incident_id: UUID, db: DbSession, user: CurrentUser
) -> IncidentResponse:
    return await IncidentService(db).get_for_user(incident_id, user)


@router.put("/{incident_id}", response_model=IncidentResponse)
async def update_incident(
    incident_id: UUID, payload: IncidentUpdate, db: DbSession, _: FleetManager
) -> IncidentResponse:
    return await IncidentService(db).update(incident_id, payload)


@router.post("/{incident_id}/assign", response_model=IncidentResponse)
async def assign_incident(
    incident_id: UUID, payload: IncidentAssign, db: DbSession, _: FleetManager
) -> IncidentResponse:
    return await IncidentService(db).assign(incident_id, payload.assigned_to_id)


@router.post("/{incident_id}/status", response_model=IncidentResponse)
async def set_incident_status(
    incident_id: UUID, payload: IncidentStatusUpdate, db: DbSession, _: FleetManager
) -> IncidentResponse:
    return await IncidentService(db).set_status(
        incident_id, payload.status, payload.resolution_notes
    )
