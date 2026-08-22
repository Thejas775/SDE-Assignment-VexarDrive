from typing import Annotated

from fastapi import Depends, Request
from fastapi.security import HTTPAuthorizationCredentials, HTTPBearer
from sqlalchemy.ext.asyncio import AsyncSession

from app.core.exceptions import ForbiddenError, UnauthorizedError
from app.db.session import get_db
from app.models.enums import UserRole
from app.models.user import User
from app.services.auth_service import AuthService

bearer_scheme = HTTPBearer(auto_error=False)

DbSession = Annotated[AsyncSession, Depends(get_db)]


def get_auth_service(db: DbSession) -> AuthService:
    return AuthService(db=db)


async def get_current_user(
    request: Request,
    db: DbSession,
    credentials: HTTPAuthorizationCredentials | None = Depends(bearer_scheme),
) -> User:
    if credentials is None or not credentials.credentials:
        raise UnauthorizedError("Authorization header is missing")
    user = await AuthService(db=db).user_from_access_token(credentials.credentials)
    request.state.user = {"id": str(user.id), "role": user.role, "email": user.email}
    return user


CurrentUser = Annotated[User, Depends(get_current_user)]


def require_roles(*roles: UserRole):
    async def guard(user: CurrentUser) -> User:
        if user.role not in roles:
            raise ForbiddenError(
                f"This action requires role: {', '.join(r.value for r in roles)}"
            )
        return user

    return guard


FleetManager = Annotated[User, Depends(require_roles(UserRole.FLEET_MANAGER))]
DriverUser = Annotated[User, Depends(require_roles(UserRole.DRIVER))]
