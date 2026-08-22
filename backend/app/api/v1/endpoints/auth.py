from fastapi import APIRouter, status

from app.api.deps import CurrentUser, DbSession
from app.core.logging import get_logger
from app.schemas.auth import (
    ChangePasswordRequest,
    ForgotPasswordRequest,
    ForgotPasswordResponse,
    LoginRequest,
    LogoutRequest,
    RefreshRequest,
    ResetPasswordRequest,
    TokenResponse,
)
from app.schemas.common import MessageResponse
from app.schemas.user import UserCreate, UserResponse
from app.services.auth_service import AuthService

router = APIRouter(prefix="/auth", tags=["Auth"])
logger = get_logger(__name__)


@router.post("/register", response_model=UserResponse, status_code=status.HTTP_201_CREATED)
async def register(payload: UserCreate, db: DbSession) -> UserResponse:
    return await AuthService(db).register(payload)


@router.post("/login", response_model=TokenResponse)
async def login(payload: LoginRequest, db: DbSession) -> TokenResponse:
    return await AuthService(db).login(payload.email, payload.password)


@router.post("/refresh", response_model=TokenResponse)
async def refresh(payload: RefreshRequest, db: DbSession) -> TokenResponse:
    return await AuthService(db).refresh(payload.refresh_token)


@router.post("/logout", response_model=MessageResponse)
async def logout(payload: LogoutRequest, db: DbSession) -> MessageResponse:
    await AuthService(db).logout(payload.refresh_token)
    return MessageResponse(message="Logged out")


@router.get("/me", response_model=UserResponse)
async def me(user: CurrentUser) -> UserResponse:
    return UserResponse.model_validate(user)


@router.post("/change-password", response_model=MessageResponse)
async def change_password(
    payload: ChangePasswordRequest, user: CurrentUser, db: DbSession
) -> MessageResponse:
    await AuthService(db).change_password(user, payload.current_password, payload.new_password)
    return MessageResponse(message="Password changed. Please sign in again.")


@router.post("/forgot-password", response_model=ForgotPasswordResponse)
async def forgot_password(payload: ForgotPasswordRequest, db: DbSession) -> ForgotPasswordResponse:
    from app.core.config import settings

    token = await AuthService(db).forgot_password(payload.email)
    # Always the same answer, so the endpoint cannot be used to discover which
    # email addresses have accounts.
    return ForgotPasswordResponse(
        message="If that account exists, a reset link has been sent.",
        reset_token=None if settings.is_production else token,
    )


@router.post("/reset-password", response_model=MessageResponse)
async def reset_password(payload: ResetPasswordRequest, db: DbSession) -> MessageResponse:
    await AuthService(db).reset_password(payload.reset_token, payload.new_password)
    return MessageResponse(message="Password reset. Please sign in again.")
