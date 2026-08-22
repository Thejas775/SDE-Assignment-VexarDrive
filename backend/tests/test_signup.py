"""Self-service signup, including the driver profile it has to create."""

from datetime import date, timedelta

import pytest

API = "/api/v1/auth"
FUTURE = str(date.today() + timedelta(days=365))


def driver_payload(**overrides):
    body = {
        "email": "newdriver@test.in",
        "password": "pass-word-1",
        "full_name": "New Driver",
        "phone_number": "+919000000123",
        "role": "DRIVER",
        "license_number": "KA0120260005555",
        "license_expiry": FUTURE,
    }
    body.update(overrides)
    return body


async def test_manager_signup_needs_no_licence(client):
    r = await client.post(
        f"{API}/register",
        json={
            "email": "newmanager@test.in",
            "password": "pass-word-1",
            "full_name": "New Manager",
            "role": "FLEET_MANAGER",
        },
    )
    assert r.status_code == 201
    assert r.json()["role"] == "FLEET_MANAGER"


async def test_driver_signup_creates_a_usable_account(client):
    r = await client.post(f"{API}/register", json=driver_payload())
    assert r.status_code == 201

    login = await client.post(
        f"{API}/login", json={"email": "newdriver@test.in", "password": "pass-word-1"}
    )
    headers = {"Authorization": f"Bearer {login.json()['access_token']}"}

    # The whole point: these used to 404 for a self-registered driver.
    me = await client.get("/api/v1/drivers/me", headers=headers)
    assert me.status_code == 200
    assert me.json()["license_number"] == "KA0120260005555"
    assert me.json()["status"] == "ACTIVE"


async def test_licence_number_is_normalised(client):
    await client.post(
        f"{API}/register", json=driver_payload(license_number=" ka01 2026 0005555 ")
    )
    login = await client.post(
        f"{API}/login", json={"email": "newdriver@test.in", "password": "pass-word-1"}
    )
    me = await client.get(
        "/api/v1/drivers/me",
        headers={"Authorization": f"Bearer {login.json()['access_token']}"},
    )
    assert me.json()["license_number"] == "KA0120260005555"


@pytest.mark.parametrize("missing", ["phone_number", "license_number", "license_expiry"])
async def test_driver_signup_rejects_missing_details(client, missing):
    r = await client.post(f"{API}/register", json=driver_payload(**{missing: None}))
    assert r.status_code == 422
    assert missing in r.text


async def test_expired_licence_rejected(client):
    r = await client.post(
        f"{API}/register",
        json=driver_payload(license_expiry=str(date.today() - timedelta(days=1))),
    )
    assert r.status_code == 422
    assert "expired" in r.json()["error_message"]


async def test_duplicate_email_rejected(client):
    assert (await client.post(f"{API}/register", json=driver_payload())).status_code == 201
    r = await client.post(
        f"{API}/register", json=driver_payload(license_number="KA0120260009999")
    )
    assert r.status_code == 409
    assert "email" in r.json()["error_message"]


async def test_duplicate_licence_rejected(client):
    assert (await client.post(f"{API}/register", json=driver_payload())).status_code == 201
    r = await client.post(f"{API}/register", json=driver_payload(email="other@test.in"))
    assert r.status_code == 409
    assert "Licence" in r.json()["error_message"]


async def test_a_rejected_signup_leaves_nothing_behind(client, db):
    """A duplicate licence must not leave an orphan user with no profile."""
    from sqlalchemy import func, select

    from app.models.user import User

    await client.post(f"{API}/register", json=driver_payload())
    before = await db.scalar(select(func.count()).select_from(User))

    r = await client.post(f"{API}/register", json=driver_payload(email="other@test.in"))
    assert r.status_code == 409

    assert await db.scalar(select(func.count()).select_from(User)) == before


async def test_short_password_rejected(client):
    r = await client.post(f"{API}/register", json=driver_payload(password="short"))
    assert r.status_code == 422


async def test_a_signed_up_driver_can_be_assigned_a_vehicle(client, manager, vehicle):
    """End to end: signup, then the manager can use them like any other driver."""
    signup = await client.post(f"{API}/register", json=driver_payload())
    assert signup.status_code == 201

    listed = await client.get("/api/v1/drivers?search=New+Driver", headers=manager)
    assert listed.json()["total"] == 1
    driver_id = listed.json()["items"][0]["id"]

    assignment = await client.post(
        "/api/v1/assignments",
        json={
            "vehicle_id": vehicle["id"],
            "driver_id": driver_id,
            "start_date": str(date.today()),
            "end_date": FUTURE,
        },
        headers=manager,
    )
    assert assignment.status_code == 201
