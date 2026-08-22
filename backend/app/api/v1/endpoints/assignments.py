from datetime import date
from uuid import UUID

from fastapi import APIRouter, Depends, Query, status

from app.api.deps import CurrentUser, DbSession, FleetManager
from app.models.enums import AssignmentStatus
from app.schemas.assignment import AssignmentCreate, AssignmentEnd, AssignmentResponse
from app.schemas.common import Page, PageParams
from app.services.assignment_service import AssignmentService

router = APIRouter(prefix="/assignments", tags=["Assignments"])


@router.post("", response_model=AssignmentResponse, status_code=status.HTTP_201_CREATED)
async def create_assignment(
    payload: AssignmentCreate, db: DbSession, _: FleetManager
) -> AssignmentResponse:
    return await AssignmentService(db).create(payload)


@router.get("", response_model=Page[AssignmentResponse])
async def list_assignments(
    db: DbSession,
    _: FleetManager,
    params: PageParams = Depends(),
    vehicle_id: UUID | None = None,
    driver_id: UUID | None = None,
    status_filter: AssignmentStatus | None = Query(default=None, alias="status"),
    active_on: date | None = Query(default=None, description="assignments covering this date"),
) -> Page[AssignmentResponse]:
    return await AssignmentService(db).list_assignments(
        params,
        vehicle_id=vehicle_id,
        driver_id=driver_id,
        status=status_filter,
        active_on=active_on,
    )


@router.get("/my", response_model=Page[AssignmentResponse])
async def my_assignments(
    db: DbSession, user: CurrentUser, params: PageParams = Depends()
) -> Page[AssignmentResponse]:
    return await AssignmentService(db).for_user(params, user)


@router.get("/{assignment_id}", response_model=AssignmentResponse)
async def get_assignment(
    assignment_id: UUID, db: DbSession, _: FleetManager
) -> AssignmentResponse:
    service = AssignmentService(db)
    row = await service.get_or_404(assignment_id)
    return service._build(row, row.vehicle, row.driver)


@router.post("/{assignment_id}/end", response_model=AssignmentResponse)
async def end_assignment(
    assignment_id: UUID, payload: AssignmentEnd, db: DbSession, _: FleetManager
) -> AssignmentResponse:
    return await AssignmentService(db).end(assignment_id, payload.end_date)


@router.post("/{assignment_id}/cancel", response_model=AssignmentResponse)
async def cancel_assignment(
    assignment_id: UUID, db: DbSession, _: FleetManager
) -> AssignmentResponse:
    return await AssignmentService(db).cancel(assignment_id)
