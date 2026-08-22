import os

os.environ.setdefault("BCRYPT_ROUNDS", "4")  # tests hash constantly; 12 rounds is the bottleneck

from datetime import date, timedelta

import psycopg
import pytest
import pytest_asyncio
from httpx import ASGITransport, AsyncClient
from sqlalchemy import text
from sqlalchemy.ext.asyncio import async_sessionmaker, create_async_engine
from yoyo import get_backend, read_migrations

from app.core.config import settings
from app.db.base import Base
from app.db.migrations import MIGRATIONS_DIR
from app.db.session import get_db
from app.main import app

TEST_DB = f"{settings.POSTGRES_DB}_test"
_PW = settings.POSTGRES_PASSWORD.get_secret_value()
_HOST = f"{settings.POSTGRES_USER}:{_PW}@{settings.POSTGRES_HOST}:{settings.POSTGRES_PORT}"
ADMIN_DSN = f"postgresql://{_HOST}/postgres"
TEST_DSN_SYNC = f"postgresql+psycopg://{_HOST}/{TEST_DB}"
TEST_DSN_ASYNC = TEST_DSN_SYNC

TABLES = ", ".join(t.name for t in Base.metadata.sorted_tables)


@pytest.fixture(scope="session", autouse=True)
def _database():
    """Drop, recreate and migrate a throwaway database once per test session."""
    with psycopg.connect(ADMIN_DSN, autocommit=True) as conn:
        conn.execute(f'DROP DATABASE IF EXISTS "{TEST_DB}" WITH (FORCE)')
        conn.execute(f'CREATE DATABASE "{TEST_DB}"')

    backend = get_backend(TEST_DSN_SYNC)
    migrations = read_migrations(str(MIGRATIONS_DIR))
    with backend.lock():
        backend.apply_migrations(backend.to_apply(migrations))
    yield


@pytest_asyncio.fixture(scope="session")
async def engine():
    eng = create_async_engine(TEST_DSN_ASYNC, poolclass=None)
    yield eng
    await eng.dispose()


@pytest_asyncio.fixture
async def db(engine):
    factory = async_sessionmaker(engine, expire_on_commit=False)
    async with factory() as session:
        await session.execute(text(f"TRUNCATE TABLE {TABLES} RESTART IDENTITY CASCADE"))
        await session.execute(text("ALTER SEQUENCE trip_number_seq RESTART WITH 1000"))
        await session.commit()
        yield session


@pytest_asyncio.fixture
async def client(engine, db, monkeypatch):
    factory = async_sessionmaker(engine, expire_on_commit=False)
    # middleware and WebSocket handlers sit outside the DI graph
    monkeypatch.setattr("app.db.session.session_factory", lambda: factory)
    monkeypatch.setattr("app.core.idempotency.session_factory", lambda: factory)

    async def override_get_db():
        async with factory() as session:
            try:
                yield session
            except Exception:
                await session.rollback()
                raise

    app.dependency_overrides[get_db] = override_get_db
    async with AsyncClient(transport=ASGITransport(app=app), base_url="http://test") as c:
        yield c
    app.dependency_overrides.clear()


# --------------------------------------------------------------------- data --

async def _register_and_login(client, email, role, full_name="Test User"):
    await client.post(
        "/api/v1/auth/register",
        json={"email": email, "password": "pass-word-1", "full_name": full_name, "role": role},
    )
    r = await client.post(
        "/api/v1/auth/login", json={"email": email, "password": "pass-word-1"}
    )
    return {"Authorization": f"Bearer {r.json()['access_token']}"}


@pytest_asyncio.fixture
async def manager(client):
    return await _register_and_login(client, "manager@test.in", "FLEET_MANAGER", "Priya Nair")


@pytest_asyncio.fixture
async def vehicle(client, manager):
    r = await client.post(
        "/api/v1/vehicles",
        json={
            "registration_number": "KA-01-AB-1234",
            "vehicle_type": "TRUCK",
            "make": "Tata",
            "model": "Ace",
            "year": 2022,
            "fuel_type": "DIESEL",
            "current_mileage": 48250,
            "insurance_expiry": str(date.today() + timedelta(days=365)),
            "registration_expiry": str(date.today() + timedelta(days=730)),
        },
        headers=manager,
    )
    return r.json()


@pytest_asyncio.fixture
async def driver(client, manager):
    r = await client.post(
        "/api/v1/drivers",
        json={
            "email": "rahul@test.in",
            "password": "pass-word-1",
            "full_name": "Rahul Sharma",
            "phone_number": "+919876543210",
            "license_number": "KA0120230001234",
            "license_expiry": str(date.today() + timedelta(days=365)),
        },
        headers=manager,
    )
    return r.json()


@pytest_asyncio.fixture
async def driver_auth(client, driver):
    r = await client.post(
        "/api/v1/auth/login", json={"email": "rahul@test.in", "password": "pass-word-1"}
    )
    return {"Authorization": f"Bearer {r.json()['access_token']}"}


@pytest_asyncio.fixture
async def assignment(client, manager, vehicle, driver):
    r = await client.post(
        "/api/v1/assignments",
        json={
            "vehicle_id": vehicle["id"],
            "driver_id": driver["id"],
            "start_date": str(date.today()),
            "end_date": str(date.today() + timedelta(days=30)),
        },
        headers=manager,
    )
    return r.json()


@pytest_asyncio.fixture
async def trip(client, manager, vehicle, driver, assignment):
    today = date.today()
    r = await client.post(
        "/api/v1/trips",
        json={
            "vehicle_id": vehicle["id"],
            "driver_id": driver["id"],
            "source": "Bangalore",
            "destination": "Chennai",
            "scheduled_start": f"{today}T08:00:00Z",
            "scheduled_end": f"{today}T23:00:00Z",
        },
        headers=manager,
    )
    return r.json()
