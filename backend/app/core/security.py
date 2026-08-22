import hashlib
import uuid
from datetime import datetime, timedelta, timezone

import bcrypt
import jwt

from app.core.config import settings
from app.core.exceptions import UnauthorizedError

ACCESS = "access"
REFRESH = "refresh"
RESET = "reset"


def hash_password(password: str) -> str:
    return bcrypt.hashpw(password.encode(), bcrypt.gensalt()).decode()


def verify_password(password: str, hashed: str) -> bool:
    try:
        return bcrypt.checkpw(password.encode(), hashed.encode())
    except ValueError:
        return False


def fingerprint(token: str) -> str:
    """Refresh tokens are stored as a digest so a database leak cannot replay them."""
    return hashlib.sha256(token.encode()).hexdigest()


def _encode(claims: dict, expires_at: datetime) -> str:
    payload = {
        **claims,
        "iat": datetime.now(timezone.utc),
        "exp": expires_at,
        "jti": str(uuid.uuid4()),
    }
    return jwt.encode(
        payload, settings.SECRET_KEY.get_secret_value(), algorithm=settings.JWT_ALGORITHM
    )


def create_access_token(user_id: uuid.UUID, role: str) -> tuple[str, datetime]:
    expires_at = datetime.now(timezone.utc) + timedelta(
        minutes=settings.ACCESS_TOKEN_EXPIRE_MINUTES
    )
    return _encode({"sub": str(user_id), "role": role, "type": ACCESS}, expires_at), expires_at


def create_refresh_token(user_id: uuid.UUID) -> tuple[str, datetime]:
    expires_at = datetime.now(timezone.utc) + timedelta(days=settings.REFRESH_TOKEN_EXPIRE_DAYS)
    return _encode({"sub": str(user_id), "type": REFRESH}, expires_at), expires_at


def create_reset_token(user_id: uuid.UUID) -> tuple[str, datetime]:
    expires_at = datetime.now(timezone.utc) + timedelta(
        minutes=settings.PASSWORD_RESET_TOKEN_EXPIRE_MINUTES
    )
    return _encode({"sub": str(user_id), "type": RESET}, expires_at), expires_at


def decode_token(token: str, expected_type: str) -> dict:
    try:
        claims = jwt.decode(
            token,
            settings.SECRET_KEY.get_secret_value(),
            algorithms=[settings.JWT_ALGORITHM],
        )
    except jwt.ExpiredSignatureError:
        raise UnauthorizedError("Token has expired")
    except jwt.InvalidTokenError:
        raise UnauthorizedError("Invalid token")

    if claims.get("type") != expected_type:
        raise UnauthorizedError("Invalid token")
    return claims
