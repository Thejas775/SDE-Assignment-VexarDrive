import hashlib
import json
from datetime import datetime, timezone

from sqlalchemy import select
from sqlalchemy.exc import IntegrityError
from starlette.middleware.base import BaseHTTPMiddleware
from starlette.requests import Request
from starlette.responses import JSONResponse, Response

from app.core.exceptions import CustomException
from app.core.logging import get_logger
from app.db.session import session_factory
from app.models.idempotency import IdempotencyKey
from app.services.auth_service import AuthService

logger = get_logger(__name__)

HEADER = "Idempotency-Key"
REPLAYABLE_METHODS = {"POST", "PUT", "PATCH", "DELETE"}
MAX_STORED_BYTES = 64 * 1024


class IdempotencyMiddleware(BaseHTTPMiddleware):
    """Make retried writes safe for clients that queue requests while offline.

    First request with a given key executes and its response is recorded.
    Any later request with the same key returns that recorded response instead
    of running again, so a phone replaying its queue cannot create duplicates.
    """

    async def dispatch(self, request: Request, call_next):
        key = request.headers.get(HEADER)
        if not key or request.method not in REPLAYABLE_METHODS:
            return await call_next(request)

        body = await request.body()
        request_hash = hashlib.sha256(body).hexdigest()

        async with session_factory()() as db:
            user = await self._current_user(request, db)
            if user is None:
                return await call_next(request)

            existing = await db.scalar(
                select(IdempotencyKey).where(
                    IdempotencyKey.key == key, IdempotencyKey.user_id == user.id
                )
            )
            if existing is not None:
                if existing.request_hash != request_hash:
                    return JSONResponse(
                        status_code=422,
                        content={
                            "error_message": "This Idempotency-Key was used with a different request body"
                        },
                    )
                if existing.completed_at is None:
                    return JSONResponse(
                        status_code=409,
                        content={"error_message": "An identical request is still in progress"},
                    )
                logger.info("idempotency.replayed", key=key, endpoint=existing.endpoint)
                return JSONResponse(
                    status_code=existing.status_code,
                    content=existing.response_body,
                    headers={"Idempotent-Replay": "true"},
                )

            record = IdempotencyKey(
                key=key,
                user_id=user.id,
                endpoint=f"{request.method} {request.url.path}",
                request_hash=request_hash,
                created_at=datetime.now(timezone.utc),
            )
            db.add(record)
            try:
                await db.commit()
            except IntegrityError:
                # Another copy of the same request won the race.
                await db.rollback()
                return JSONResponse(
                    status_code=409,
                    content={"error_message": "An identical request is still in progress"},
                )
            record_id = record.id

        response = await call_next(request)
        payload = b"".join([chunk async for chunk in response.body_iterator])

        async with session_factory()() as db:
            record = await db.get(IdempotencyKey, record_id)
            if record is not None:
                if response.status_code < 500 and len(payload) <= MAX_STORED_BYTES:
                    record.status_code = response.status_code
                    record.response_body = _decode(payload)
                    record.completed_at = datetime.now(timezone.utc)
                    await db.commit()
                else:
                    # Server errors are not a settled outcome; let the client retry.
                    await db.delete(record)
                    await db.commit()

        return Response(
            content=payload,
            status_code=response.status_code,
            headers=dict(response.headers),
            media_type=response.media_type,
        )

    @staticmethod
    async def _current_user(request: Request, db):
        header = request.headers.get("authorization", "")
        if not header.lower().startswith("bearer "):
            return None
        try:
            return await AuthService(db).user_from_access_token(header.split(" ", 1)[1])
        except CustomException:
            return None


def _decode(payload: bytes):
    try:
        return json.loads(payload)
    except (json.JSONDecodeError, UnicodeDecodeError):
        return None
