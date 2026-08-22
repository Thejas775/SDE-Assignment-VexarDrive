from datetime import date, timedelta

API = "/api/v1/drivers"


def _payload(**overrides):
    body = {
        "email": "arjun@test.in",
        "password": "pass-word-1",
        "full_name": "Arjun Rao",
        "phone_number": "+919000000002",
        "license_number": "KA0120230009999",
        "license_expiry": str(date.today() + timedelta(days=365)),
    }
    body.update(overrides)
    return body


async def test_create_driver_creates_a_login(client, manager):
    r = await client.post(API, json=_payload(), headers=manager)
    assert r.status_code == 201
    assert r.json()["can_login"] is True
    login = await client.post(
        "/api/v1/auth/login", json={"email": "arjun@test.in", "password": "pass-word-1"}
    )
    assert login.status_code == 200
    assert login.json()["user"]["role"] == "DRIVER"


async def test_licence_number_is_normalised(client, manager):
    r = await client.post(API, json=_payload(license_number=" ka01 2023 0009999 "), headers=manager)
    assert r.json()["license_number"] == "KA0120230009999"


async def test_duplicate_licence_rejected(client, manager, driver):
    r = await client.post(
        API, json=_payload(license_number=driver["license_number"]), headers=manager
    )
    assert r.status_code == 409


async def test_duplicate_email_rejected(client, manager, driver):
    r = await client.post(API, json=_payload(email=driver["email"]), headers=manager)
    assert r.status_code == 409


async def test_expired_licence_rejected(client, manager):
    r = await client.post(
        API, json=_payload(license_expiry=str(date.today() - timedelta(days=1))), headers=manager
    )
    assert r.status_code == 422


async def test_failed_creation_leaves_no_orphan_user(client, manager, driver, db):
    from sqlalchemy import func, select

    from app.models.user import User

    before = await db.scalar(select(func.count()).select_from(User))
    await client.post(API, json=_payload(license_number=driver["license_number"]), headers=manager)
    after = await db.scalar(select(func.count()).select_from(User))
    assert before == after


async def test_deactivate_blocks_login(client, manager, driver):
    r = await client.post(f"{API}/{driver['id']}/deactivate", headers=manager)
    assert r.json()["status"] == "INACTIVE" and r.json()["can_login"] is False
    login = await client.post(
        "/api/v1/auth/login", json={"email": driver["email"], "password": "pass-word-1"}
    )
    assert login.status_code == 401


async def test_suspend_keeps_login_but_changes_status(client, manager, driver):
    r = await client.post(f"{API}/{driver['id']}/suspend", headers=manager)
    assert r.json()["status"] == "SUSPENDED" and r.json()["can_login"] is True
    login = await client.post(
        "/api/v1/auth/login", json={"email": driver["email"], "password": "pass-word-1"}
    )
    assert login.status_code == 200


async def test_cannot_deactivate_with_an_open_trip(client, manager, driver, trip):
    r = await client.post(f"{API}/{driver['id']}/deactivate", headers=manager)
    assert r.status_code == 409


async def test_assigned_vehicle_is_derived_not_stored(client, manager, driver, vehicle, assignment):
    r = await client.get(f"{API}/{driver['id']}", headers=manager)
    assert r.json()["assigned_vehicle"]["registration_number"] == vehicle["registration_number"]


async def test_no_assigned_vehicle_before_assignment(client, manager, driver):
    r = await client.get(f"{API}/{driver['id']}", headers=manager)
    assert r.json()["assigned_vehicle"] is None


async def test_licence_expiring_filter(client, manager, driver):
    await client.post(
        API,
        json=_payload(license_expiry=str(date.today() + timedelta(days=5))),
        headers=manager,
    )
    r = await client.get(f"{API}?license_expiring=true", headers=manager)
    assert r.json()["total"] == 1
    assert r.json()["items"][0]["license_expiring_soon"] is True


async def test_search_by_name_and_phone(client, manager, driver):
    for term in ("rahul", "9876"):
        r = await client.get(f"{API}?search={term}", headers=manager)
        assert r.json()["total"] == 1, term


async def test_driver_can_read_own_profile_only(client, driver_auth, driver, manager):
    other = (await client.post(API, json=_payload(), headers=manager)).json()
    assert (await client.get(f"{API}/me", headers=driver_auth)).status_code == 200
    assert (await client.get(f"{API}/{driver['id']}", headers=driver_auth)).status_code == 200
    assert (await client.get(f"{API}/{other['id']}", headers=driver_auth)).status_code == 403


async def test_history_includes_assignments_and_trip_stats(client, manager, driver, assignment):
    r = await client.get(f"{API}/{driver['id']}/history", headers=manager)
    body = r.json()
    assert len(body["assignments"]) == 1
    assert body["total_trips"] == 0 and body["total_distance_km"] == 0.0
