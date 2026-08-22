from pydantic import BaseModel, EmailStr, Field, field_validator

from app.core.config import settings
from app.schemas.user import UserResponse

_PASSWORD = Field(
    min_length=settings.PASSWORD_MIN_LENGTH, max_length=settings.PASSWORD_MAX_LENGTH
)


class LoginRequest(BaseModel):
    email: EmailStr
    password: str

    @field_validator("email")
    @classmethod
    def _normalise_email(cls, v: str) -> str:
        return v.strip().lower()


class TokenResponse(BaseModel):
    access_token: str
    refresh_token: str
    token_type: str = "bearer"
    expires_in: int
    user: UserResponse


class RefreshRequest(BaseModel):
    refresh_token: str


class LogoutRequest(BaseModel):
    refresh_token: str


class ChangePasswordRequest(BaseModel):
    current_password: str
    new_password: str = _PASSWORD


class ForgotPasswordRequest(BaseModel):
    email: EmailStr

    @field_validator("email")
    @classmethod
    def _normalise_email(cls, v: str) -> str:
        return v.strip().lower()


class ForgotPasswordResponse(BaseModel):
    message: str
    reset_token: str | None = None


class ResetPasswordRequest(BaseModel):
    reset_token: str
    new_password: str = _PASSWORD
