from datetime import datetime, timezone
from uuid import UUID

from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession

from app.core.config import settings
from app.core.exceptions import ConflictError, UnauthorizedError
from app.core.logging import get_logger
from app.core.security import (
    ACCESS,
    REFRESH,
    RESET,
    create_access_token,
    create_refresh_token,
    create_reset_token,
    decode_token,
    fingerprint,
    hash_password,
    verify_password,
)
from app.models.refresh_token import RefreshToken
from app.models.user import User
from app.schemas.auth import TokenResponse
from app.schemas.user import UserCreate, UserResponse

logger = get_logger(__name__)


class AuthService:
    def __init__(self, db: AsyncSession):
        self.db = db

    async def _get_by_email(self, email: str) -> User | None:
        return await self.db.scalar(select(User).where(User.email == email))

    async def _get_active_user(self, user_id: UUID) -> User:
        user = await self.db.get(User, user_id)
        if user is None or not user.is_active:
            raise UnauthorizedError("Account is inactive or does not exist")
        return user

    async def _issue_tokens(self, user: User) -> TokenResponse:
        access, _ = create_access_token(user.id, user.role)
        refresh, refresh_expires = create_refresh_token(user.id)
        self.db.add(
            RefreshToken(
                user_id=user.id,
                token_hash=fingerprint(refresh),
                expires_at=refresh_expires,
                created_at=datetime.now(timezone.utc),
            )
        )
        await self.db.commit()
        return TokenResponse(
            access_token=access,
            refresh_token=refresh,
            expires_in=settings.ACCESS_TOKEN_EXPIRE_MINUTES * 60,
            user=UserResponse.model_validate(user),
        )

    async def register(self, payload: UserCreate) -> UserResponse:
        if await self._get_by_email(payload.email):
            raise ConflictError("An account with this email already exists")
        user = User(
            email=payload.email,
            hashed_password=hash_password(payload.password),
            full_name=payload.full_name,
            phone_number=payload.phone_number,
            role=payload.role,
        )
        self.db.add(user)
        await self.db.commit()
        logger.info("auth.registered", user_id=str(user.id), role=user.role)
        return UserResponse.model_validate(user)

    async def login(self, email: str, password: str) -> TokenResponse:
        user = await self._get_by_email(email)
        # Verify against a dummy hash when the user is missing so that a wrong
        # email and a wrong password take the same time to answer.
        stored = user.hashed_password if user else hash_password("invalid")
        if not verify_password(password, stored) or user is None:
            logger.warning("auth.login_failed", email=email)
            raise UnauthorizedError("Incorrect email or password")
        if not user.is_active:
            raise UnauthorizedError("Account is inactive")
        logger.info("auth.login", user_id=str(user.id))
        return await self._issue_tokens(user)

    async def refresh(self, token: str) -> TokenResponse:
        claims = decode_token(token, REFRESH)
        stored = await self.db.scalar(
            select(RefreshToken).where(RefreshToken.token_hash == fingerprint(token))
        )
        if stored is None or stored.revoked_at is not None:
            raise UnauthorizedError("Refresh token is no longer valid")
        stored.revoked_at = datetime.now(timezone.utc)
        user = await self._get_active_user(UUID(claims["sub"]))
        return await self._issue_tokens(user)

    async def logout(self, token: str) -> None:
        stored = await self.db.scalar(
            select(RefreshToken).where(RefreshToken.token_hash == fingerprint(token))
        )
        if stored is not None and stored.revoked_at is None:
            stored.revoked_at = datetime.now(timezone.utc)
            await self.db.commit()
            logger.info("auth.logout", user_id=str(stored.user_id))

    async def change_password(self, user: User, current: str, new: str) -> None:
        if not verify_password(current, user.hashed_password):
            raise UnauthorizedError("Current password is incorrect")
        user.hashed_password = hash_password(new)
        await self._revoke_all(user.id)
        await self.db.commit()
        logger.info("auth.password_changed", user_id=str(user.id))

    async def forgot_password(self, email: str) -> str | None:
        user = await self._get_by_email(email)
        if user is None or not user.is_active:
            return None
        token, _ = create_reset_token(user.id)
        logger.info("auth.reset_requested", user_id=str(user.id))
        return token

    async def reset_password(self, token: str, new_password: str) -> None:
        claims = decode_token(token, RESET)
        user = await self._get_active_user(UUID(claims["sub"]))
        user.hashed_password = hash_password(new_password)
        await self._revoke_all(user.id)
        await self.db.commit()
        logger.info("auth.password_reset", user_id=str(user.id))

    async def _revoke_all(self, user_id: UUID) -> None:
        tokens = await self.db.scalars(
            select(RefreshToken).where(
                RefreshToken.user_id == user_id, RefreshToken.revoked_at.is_(None)
            )
        )
        now = datetime.now(timezone.utc)
        for t in tokens:
            t.revoked_at = now

    async def user_from_access_token(self, token: str) -> User:
        claims = decode_token(token, ACCESS)
        return await self._get_active_user(UUID(claims["sub"]))
