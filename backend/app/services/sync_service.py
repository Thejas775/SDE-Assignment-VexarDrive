from uuid import UUID

from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession
from sqlalchemy.orm import selectinload

from app.core.exceptions import CustomException, ValidationError
from app.core.logging import get_logger
from app.models.enums import TripStatus
from app.models.user import User
from app.schemas.incident import IncidentCreate
from app.schemas.location import LocationBatch
from app.schemas.sync import (
    SyncOperation,
    SyncOperationResult,
    SyncOperationType,
    SyncRequest,
    SyncResponse,
)
from app.schemas.trip import TripComplete, TripStart, TripStatusUpdate
from app.services.incident_service import IncidentService
from app.services.location_service import LocationService
from app.services.trip_service import TripService

logger = get_logger(__name__)


class SyncService:
    """Replay a mobile client's queued writes in one request.

    Operations are applied in the order the client recorded them, and a failure
    does not abort the batch: a rejected incident must not block the GPS trail
    behind it. Every operation reports its own outcome so the app knows exactly
    which local rows to clear and which to keep for another attempt.
    """

    def __init__(self, db: AsyncSession):
        self.db = db

    async def apply(self, payload: SyncRequest, user: User) -> SyncResponse:
        user_id = user.id
        results: list[SyncOperationResult] = []
        for operation in payload.operations:
            result, user = await self._apply_one(operation, user, user_id)
            results.append(result)
        applied = sum(1 for r in results if r.status == "applied")
        logger.info(
            "sync.batch", user_id=str(user_id), applied=applied, failed=len(results) - applied
        )
        return SyncResponse(
            applied=applied, failed=len(results) - applied, results=results
        )

    async def _apply_one(
        self, operation: SyncOperation, user: User, user_id: UUID
    ) -> tuple[SyncOperationResult, User]:
        try:
            result = await self._dispatch(operation, user)
            return (
                SyncOperationResult(
                    client_id=operation.client_id,
                    type=operation.type,
                    status="applied",
                    code=200,
                    result=result,
                ),
                user,
            )
        except CustomException as exc:
            return (
                SyncOperationResult(
                    client_id=operation.client_id,
                    type=operation.type,
                    status="failed",
                    code=exc.code,
                    error_message=exc.message,
                ),
                await self._recover(user_id),
            )
        except Exception as exc:
            logger.warning(
                "sync.operation_failed", client_id=operation.client_id, error=str(exc)
            )
            return (
                SyncOperationResult(
                    client_id=operation.client_id,
                    type=operation.type,
                    status="failed",
                    code=422,
                    error_message=str(exc),
                ),
                await self._recover(user_id),
            )

    async def _recover(self, user_id: UUID) -> User:
        """Roll back the failed operation and reload the caller.

        rollback() expires every object in the session, so the User handed to
        the next operation has to be fetched again or the first attribute read
        raises MissingGreenlet.
        """
        await self.db.rollback()
        return await self.db.scalar(
            select(User).options(selectinload(User.driver)).where(User.id == user_id)
        )

    async def _dispatch(self, operation: SyncOperation, user: User):
        if operation.type is SyncOperationType.LOCATION_BATCH:
            batch = LocationBatch(trip_id=self._trip_id(operation), **operation.payload)
            return (await LocationService(self.db).ingest(batch, user)).model_dump()

        if operation.type is SyncOperationType.INCIDENT_REPORT:
            body = IncidentCreate(**operation.payload)
            incident = await IncidentService(self.db).report(
                body, user, client_reference_id=operation.client_id
            )
            return incident.model_dump(mode="json")

        trips = TripService(self.db)
        trip_id = self._trip_id(operation)
        if operation.type is SyncOperationType.TRIP_START:
            trip = await trips.start(trip_id, TripStart(**operation.payload), user)
        elif operation.type is SyncOperationType.TRIP_COMPLETE:
            trip = await trips.complete(trip_id, TripComplete(**operation.payload), user)
        else:
            update = TripStatusUpdate(**operation.payload)
            trip = await trips.update_status(trip_id, update.status, user)
        return trip.model_dump(mode="json")

    @staticmethod
    def _trip_id(operation: SyncOperation):
        if operation.trip_id is None:
            raise ValidationError(f"{operation.type} requires trip_id")
        return operation.trip_id
