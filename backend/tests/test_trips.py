from datetime import date, timedelta

import pytest

from app.models.enums import TripStatus
from app.models.enums_transitions import TRIP_TRANSITIONS, can_transition

API = "/api/v1/trips"
TODAY = date.today()


# ------------------------------------------------------- pure state machine --

@pytest.mark.parametrize(
    "current,target,allowed",
    [
        (TripStatus.SCHEDULED, TripStatus.STARTED, True),
        (TripStatus.SCHEDULED, TripStatus.CANCELLED, True),
        (TripStatus.SCHEDULED, TripStatus.IN_PROGRESS, False),
        (TripStatus.SCHEDULED, TripStatus.COMPLETED, False),
        (TripStatus.STARTED, TripStatus.IN_PROGRESS, True),
        (TripStatus.STARTED, TripStatus.COMPLETED, True),
        (TripStatus.STARTED, TripStatus.SCHEDULED, False),
        (TripStatus.IN_PROGRESS, TripStatus.COMPLETED, True),
        (TripStatus.IN_PROGRESS, TripStatus.STARTED, False),
        (TripStatus.COMPLETED, TripStatus.CANCELLED, False),
        (TripStatus.CANCELLED, TripStatus.SCHEDULED, False),
    ],
)
def test_transition_table(current, target, allowed):
    assert can_transition(current, target) is allowed


def test_terminal_states_have_no_exits():
    assert TRIP_TRANSITIONS[TripStatus.COMPLETED] == set()
    assert TRIP_TRANSITIONS[TripStatus.CANCELLED] == set()


def test_every_status_is_in_the_table():
    assert set(TRIP_TRANSITIONS) == set(TripStatus)


# ------------------------------------------------------------------- create --

async def test_create_trip(client, manager, trip):
    assert trip["status"] == "SCHEDULED"
    assert trip["trip_number"].startswith("TRP")


async def test_trip_numbers_are_unique(client, manager, vehicle, driver, assignment):
    numbers = set()
    for day in range(1, 4):
        r = await client.post(
            API,
            json={
                "vehicle_id": vehicle["id"],
                "driver_id": driver["id"],
                "source": "Bangalore",
                "destination": "Chennai",
                "scheduled_start": f"{TODAY + timedelta(days=day)}T08:00:00Z",
                "scheduled_end": f"{TODAY + timedelta(days=day)}T18:00:00Z",
            },
            headers=manager,
        )
        numbers.add(r.json()["trip_number"])
    assert len(numbers) == 3


async def test_trip_requires_an_active_assignment(client, manager, vehicle, driver):
    r = await client.post(
        API,
        json={
            "vehicle_id": vehicle["id"],
            "driver_id": driver["id"],
            "source": "Bangalore",
            "destination": "Chennai",
            "scheduled_start": f"{TODAY}T08:00:00Z",
            "scheduled_end": f"{TODAY}T18:00:00Z",
        },
        headers=manager,
    )
    assert r.status_code == 409
    assert "not assigned" in r.json()["error_message"]


async def test_driver_cannot_be_double_booked(client, manager, vehicle, driver, assignment, trip):
    r = await client.post(
        API,
        json={
            "vehicle_id": vehicle["id"],
            "driver_id": driver["id"],
            "source": "Bangalore",
            "destination": "Chennai",
            "scheduled_start": f"{TODAY}T12:00:00Z",
            "scheduled_end": f"{TODAY}T20:00:00Z",
        },
        headers=manager,
    )
    assert r.status_code == 409


async def test_scheduled_end_must_follow_start(client, manager, vehicle, driver, assignment):
    r = await client.post(
        API,
        json={
            "vehicle_id": vehicle["id"],
            "driver_id": driver["id"],
            "source": "Bangalore",
            "destination": "Chennai",
            "scheduled_start": f"{TODAY}T18:00:00Z",
            "scheduled_end": f"{TODAY}T08:00:00Z",
        },
        headers=manager,
    )
    assert r.status_code == 422


# ----------------------------------------------------------------- workflow --

async def test_start_records_odometer_and_marks_vehicle_on_trip(
    client, manager, driver_auth, vehicle, trip
):
    r = await client.post(
        f"{API}/{trip['id']}/start",
        json={"start_odometer": 48250, "latitude": "12.971599", "longitude": "77.594566"},
        headers=driver_auth,
    )
    assert r.status_code == 200
    assert r.json()["status"] == "STARTED"
    assert r.json()["actual_start"] is not None

    v = await client.get(f"/api/v1/vehicles/{vehicle['id']}", headers=manager)
    assert v.json()["status"] == "ON_TRIP"


async def test_start_odometer_below_vehicle_reading_rejected(client, driver_auth, trip):
    r = await client.post(
        f"{API}/{trip['id']}/start", json={"start_odometer": 100}, headers=driver_auth
    )
    assert r.status_code == 422


async def test_cannot_start_twice(client, driver_auth, trip):
    await client.post(
        f"{API}/{trip['id']}/start", json={"start_odometer": 48250}, headers=driver_auth
    )
    r = await client.post(
        f"{API}/{trip['id']}/start", json={"start_odometer": 48250}, headers=driver_auth
    )
    assert r.status_code == 409


async def test_cannot_complete_a_trip_that_never_started(client, driver_auth, trip):
    r = await client.post(
        f"{API}/{trip['id']}/complete", json={"end_odometer": 50000}, headers=driver_auth
    )
    assert r.status_code == 409


async def test_complete_calculates_distance_and_updates_the_vehicle(
    client, manager, driver_auth, vehicle, trip
):
    await client.post(
        f"{API}/{trip['id']}/start", json={"start_odometer": 48250}, headers=driver_auth
    )
    r = await client.post(
        f"{API}/{trip['id']}/complete", json={"end_odometer": 48596}, headers=driver_auth
    )
    assert r.status_code == 200
    assert r.json()["status"] == "COMPLETED"
    assert float(r.json()["distance_km"]) == 346.0

    v = await client.get(f"/api/v1/vehicles/{vehicle['id']}", headers=manager)
    assert v.json()["status"] == "AVAILABLE"
    assert v.json()["current_mileage"] == 48596


async def test_end_odometer_below_start_rejected(client, driver_auth, trip):
    await client.post(
        f"{API}/{trip['id']}/start", json={"start_odometer": 48250}, headers=driver_auth
    )
    r = await client.post(
        f"{API}/{trip['id']}/complete", json={"end_odometer": 48000}, headers=driver_auth
    )
    assert r.status_code == 422


async def test_status_endpoint_refuses_terminal_transitions(client, driver_auth, trip):
    await client.post(
        f"{API}/{trip['id']}/start", json={"start_odometer": 48250}, headers=driver_auth
    )
    r = await client.post(
        f"{API}/{trip['id']}/status", json={"status": "COMPLETED"}, headers=driver_auth
    )
    assert r.status_code == 422


async def test_cancel_a_scheduled_trip(client, manager, trip):
    r = await client.post(f"{API}/{trip['id']}/cancel", json={"reason": "customer"}, headers=manager)
    assert r.json()["status"] == "CANCELLED"


async def test_cancel_releases_the_vehicle(client, manager, driver_auth, vehicle, trip):
    await client.post(
        f"{API}/{trip['id']}/start", json={"start_odometer": 48250}, headers=driver_auth
    )
    await client.post(f"{API}/{trip['id']}/cancel", json={"reason": "breakdown"}, headers=manager)
    v = await client.get(f"/api/v1/vehicles/{vehicle['id']}", headers=manager)
    assert v.json()["status"] == "AVAILABLE"


async def test_cannot_cancel_a_completed_trip(client, manager, driver_auth, trip):
    await client.post(
        f"{API}/{trip['id']}/start", json={"start_odometer": 48250}, headers=driver_auth
    )
    await client.post(
        f"{API}/{trip['id']}/complete", json={"end_odometer": 48300}, headers=driver_auth
    )
    r = await client.post(f"{API}/{trip['id']}/cancel", json={"reason": "x"}, headers=manager)
    assert r.status_code == 409


async def test_another_driver_cannot_start_someone_elses_trip(client, manager, trip):
    await client.post(
        "/api/v1/drivers",
        json={
            "email": "stranger@test.in",
            "password": "pass-word-1",
            "full_name": "Stranger Driver",
            "phone_number": "+919000000009",
            "license_number": "KA0120230005555",
            "license_expiry": str(TODAY + timedelta(days=365)),
        },
        headers=manager,
    )
    login = await client.post(
        "/api/v1/auth/login", json={"email": "stranger@test.in", "password": "pass-word-1"}
    )
    headers = {"Authorization": f"Bearer {login.json()['access_token']}"}
    r = await client.post(
        f"{API}/{trip['id']}/start", json={"start_odometer": 48250}, headers=headers
    )
    assert r.status_code == 403


async def test_driver_sees_only_their_own_trips(client, driver_auth, trip):
    r = await client.get(f"{API}/my", headers=driver_auth)
    assert r.json()["total"] == 1
