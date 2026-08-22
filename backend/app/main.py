from contextlib import asynccontextmanager
from typing import AsyncIterator

from fastapi import FastAPI, Request
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import JSONResponse

from app.api.v1.router import api_router
from app.core.config import settings
from app.core.exceptions import CustomException
from app.core.logging import configure_logging, get_logger
from app.core.middleware import RequestIdMiddleware
from app.db.migrations import apply_migrations
from app.db.session import dispose_engine

configure_logging()
logger = get_logger(__name__)


@asynccontextmanager
async def lifespan(app: FastAPI) -> AsyncIterator[None]:
    logger.info("app.startup", project=settings.PROJECT_NAME, env=settings.ENVIRONMENT)
    if settings.RUN_MIGRATIONS_ON_STARTUP:
        apply_migrations()
    yield
    await dispose_engine()
    logger.info("app.shutdown")


app = FastAPI(
    title=settings.PROJECT_NAME,
    version="0.1.0",
    description="Backend service for managing vehicles, drivers, trips and maintenance.",
    lifespan=lifespan,
    docs_url=None if settings.is_production else "/docs",
    redoc_url=None if settings.is_production else "/redoc",
    openapi_url=None if settings.is_production else "/openapi.json",
)

app.add_middleware(RequestIdMiddleware)
app.add_middleware(
    CORSMiddleware,
    allow_origins=settings.cors_origin_list,
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)


@app.exception_handler(CustomException)
async def custom_exception_handler(request: Request, exc: CustomException) -> JSONResponse:
    logger.warning("request.failed", path=request.url.path, code=exc.code, error=exc.message)
    return JSONResponse(status_code=exc.code, content={"error_message": exc.message})


app.include_router(api_router, prefix=settings.API_V1_PREFIX)


@app.get("/health", tags=["system"])
async def health() -> dict[str, str]:
    return {"status": "ok"}


@app.get("/", tags=["system"])
async def root() -> dict[str, str]:
    return {
        "service": settings.PROJECT_NAME,
        "environment": settings.ENVIRONMENT,
        "docs": "disabled" if settings.is_production else "/docs",
    }
