from enum import StrEnum
from typing import Any
from uuid import UUID

from pydantic import BaseModel, Field

MAX_OPERATIONS = 200


class SyncOperationType(StrEnum):
    TRIP_START = "TRIP_START"
    TRIP_STATUS = "TRIP_STATUS"
    TRIP_COMPLETE = "TRIP_COMPLETE"
    LOCATION_BATCH = "LOCATION_BATCH"
    INCIDENT_REPORT = "INCIDENT_REPORT"


class SyncOperation(BaseModel):
    client_id: str = Field(max_length=64, description="the app's local id for this operation")
    type: SyncOperationType
    trip_id: UUID | None = None
    payload: dict[str, Any] = Field(default_factory=dict)


class SyncRequest(BaseModel):
    operations: list[SyncOperation] = Field(min_length=1, max_length=MAX_OPERATIONS)


class SyncOperationResult(BaseModel):
    client_id: str
    type: SyncOperationType
    status: str
    code: int
    result: Any | None = None
    error_message: str | None = None


class SyncResponse(BaseModel):
    applied: int
    failed: int
    results: list[SyncOperationResult]
