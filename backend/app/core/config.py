from functools import lru_cache
from typing import Literal
from urllib.parse import parse_qsl, urlencode, urlsplit, urlunsplit

from pydantic import SecretStr, computed_field, field_validator, model_validator
from pydantic_settings import BaseSettings, SettingsConfigDict

DEV_SECRET_KEY = "thejas-elandassery-super-secret-key-marvel-jesus"


class Settings(BaseSettings):
    model_config = SettingsConfigDict(
        env_file=".env",
        env_file_encoding="utf-8",
        case_sensitive=True,
        extra="ignore",
    )

    # --- app ---
    PROJECT_NAME: str = "Fleet Management API"
    API_V1_PREFIX: str = "/api/v1"
    ENVIRONMENT: Literal["development", "staging", "production"] = "development"
    DEBUG: bool = False
    LOG_LEVEL: Literal["DEBUG", "INFO", "WARNING", "ERROR", "CRITICAL"] = "INFO"

    # --- security ---
    SECRET_KEY: SecretStr = SecretStr(DEV_SECRET_KEY)
    JWT_ALGORITHM: str = "HS256"
    ACCESS_TOKEN_EXPIRE_MINUTES: int = 30
    REFRESH_TOKEN_EXPIRE_DAYS: int = 30
    PASSWORD_MIN_LENGTH: int = 8

    # --- database ---
    POSTGRES_HOST: str = "localhost"
    POSTGRES_PORT: int = 5432
    POSTGRES_USER: str = "fleet"
    POSTGRES_PASSWORD: SecretStr = SecretStr("fleet")
    POSTGRES_DB: str = "fleet_db"
    DATABASE_URL: str | None = None
    DB_ECHO: bool = False
    DB_POOL_SIZE: int = 10
    DB_MAX_OVERFLOW: int = 20

    # --- cors ---
    CORS_ORIGINS: str = "http://localhost:3000,http://localhost:5173"

    # --- fleet policy ---
    DOCUMENT_EXPIRY_WARNING_DAYS: int = 30
    MAINTENANCE_DUE_WARNING_DAYS: int = 7
    MAINTENANCE_DUE_MILEAGE_BUFFER: int = 500
    LOCATION_PING_INTERVAL_SECONDS: int = 30

    @computed_field  # type: ignore[prop-decorator]
    @property
    def is_production(self) -> bool:
        return self.ENVIRONMENT == "production"

    @computed_field  # type: ignore[prop-decorator]
    @property
    def cors_origin_list(self) -> list[str]:
        return [o.strip() for o in self.CORS_ORIGINS.split(",") if o.strip()]

    @computed_field  # type: ignore[prop-decorator]
    @property
    def database_url_async(self) -> str:
        return self._dsn("postgresql+asyncpg", drop_ssl_query=True)

    @computed_field  # type: ignore[prop-decorator]
    @property
    def database_url_sync(self) -> str:
        return self._dsn("postgresql+psycopg", drop_ssl_query=False)

    @computed_field  # type: ignore[prop-decorator]
    @property
    def db_ssl_required(self) -> bool:
        if not self.DATABASE_URL:
            return False
        query = dict(parse_qsl(urlsplit(self.DATABASE_URL).query))
        return query.get("sslmode", "").lower() in {"require", "verify-ca", "verify-full"}

    def _dsn(self, driver: str, *, drop_ssl_query: bool) -> str:
        if self.DATABASE_URL:
            parts = urlsplit(self.DATABASE_URL)
            # asyncpg rejects libpq's sslmode/channel_binding params; TLS is
            # re-applied as a connect arg in app/db/session.py
            query = [
                (k, v)
                for k, v in parse_qsl(parts.query)
                if not (drop_ssl_query and k in {"sslmode", "channel_binding"})
            ]
            return urlunsplit((driver, parts.netloc, parts.path, urlencode(query), parts.fragment))
        password = self.POSTGRES_PASSWORD.get_secret_value()
        return (
            f"{driver}://{self.POSTGRES_USER}:{password}"
            f"@{self.POSTGRES_HOST}:{self.POSTGRES_PORT}/{self.POSTGRES_DB}"
        )

    @field_validator("API_V1_PREFIX")
    @classmethod
    def _normalise_prefix(cls, v: str) -> str:
        v = "/" + v.strip("/")
        return "" if v == "/" else v

    @model_validator(mode="after")
    def _enforce_production_safety(self) -> "Settings":
        if not self.is_production:
            return self
        problems: list[str] = []
        if self.SECRET_KEY.get_secret_value() == DEV_SECRET_KEY:
            problems.append("SECRET_KEY is the shared development key")
        if len(self.SECRET_KEY.get_secret_value()) < 32:
            problems.append("SECRET_KEY must be at least 32 characters")
        if self.DEBUG:
            problems.append("DEBUG must be false in production")
        if "*" in self.cors_origin_list:
            problems.append("CORS_ORIGINS must not be '*' in production")
        if problems:
            raise ValueError("Unsafe production configuration: " + "; ".join(problems))
        return self


@lru_cache
def get_settings() -> Settings:
    return Settings()


settings = get_settings()
