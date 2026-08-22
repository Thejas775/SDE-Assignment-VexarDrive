from datetime import date, datetime, timedelta, timezone

import pytest
import pytest_asyncio

API = "/api/v1/locations"
NOW = datetime.now(timezone.utc)


def _ping(minutes_ago=0, lat="12.971599", lon="77.594566", **extra):
    return {
        "latitude": lat,
        "longitude": lon,
        "recorded_at": (NOW - timedelta(minutes=minutes_ago)).isoformat(),
        **extra,
    }


@pytest_asyncio.fixture
async def running_trip(client, driver_auth, trip):
    await client.post(
        f"/api/v1/trips/{trip['id']}/start",
        json={"start_odometer": 48250},
        headers=driver_auth,
    )
    return trip


async def test_pings_rejected_before_the_trip_starts(client, driver_auth, trip):
    r = await client.post(
        API, json={"trip_id": trip["id"], "pings": [_ping()]}, headers=driver_auth
    )
    assert r.status_code == 409


async def test_ingest_stores_pings(client, driver_auth, running_trip, db):
    from sqlalchemy import func, select

    from app.models.location import Location

    r = await client.post(
        API,
        json={"trip_id": running_trip["id"], "pings": [_ping(30), _ping(20), _ping(10)]},
        headers=driver_auth,
    )
    assert r.status_code == 202
    assert r.json()["accepted"] == 3
    stored = await db.scalar(select(func.count()).select_from(Location))
    assert stored == 3


async def test_first_ping_moves_the_trip_to_in_progress(client, driver_auth, running_trip):
    r = await client.post(
        API, json={"trip_id": running_trip["id"], "pings": [_ping()]}, headers=driver_auth
    )
    assert r.json()["trip_status"] == "IN_PROGRESS"


async def test_resending_the_same_batch_is_idempotent(client, driver_auth, running_trip):
    batch = {"trip_id": running_trip["id"], "pings": [_ping(30), _ping(20)]}
    first = await client.post(API, json=batch, headers=driver_auth)
    second = await client.post(API, json=batch, headers=driver_auth)
    assert first.json() == {"accepted": 2, "duplicates": 0, "trip_status": "IN_PROGRESS"}
    assert second.json() == {"accepted": 0, "duplicates": 2, "trip_status": "IN_PROGRESS"}


async def test_route_is_returned_in_chronological_order(client, manager, driver_auth, running_trip):
    await client.post(
        API,
        json={
            "trip_id": running_trip["id"],
            "pings": [_ping(10, lat="13.0"), _ping(30, lat="12.9"), _ping(20, lat="12.95")],
        },
        headers=driver_auth,
    )
    r = await client.get(f"/api/v1/trips/{running_trip['id']}/route", headers=manager)
    latitudes = [float(p["latitude"]) for p in r.json()]
    assert latitudes == sorted(latitudes)
    assert latitudes == [12.9, 12.95, 13.0]


async def test_vehicle_id_is_taken_from_the_trip(client, driver_auth, running_trip, vehicle, db):
    from sqlalchemy import select

    from app.models.location import Location

    await client.post(
        API, json={"trip_id": running_trip["id"], "pings": [_ping()]}, headers=driver_auth
    )
    stored = await db.scalar(select(Location.vehicle_id))
    assert str(stored) == vehicle["id"]


async def test_latest_position_for_a_vehicle(client, manager, driver_auth, running_trip, vehicle):
    await client.post(
        API,
        json={
            "trip_id": running_trip["id"],
            "pings": [_ping(30, lat="12.9"), _ping(1, lat="13.5")],
        },
        headers=driver_auth,
    )
    r = await client.get(f"/api/v1/vehicles/{vehicle['id']}/location", headers=manager)
    assert r.status_code == 200
    assert float(r.json()["latitude"]) == 13.5


async def test_latest_position_404_when_nothing_recorded(client, manager, vehicle):
    r = await client.get(f"/api/v1/vehicles/{vehicle['id']}/location", headers=manager)
    assert r.status_code == 404


async def test_live_positions_lists_moving_vehicles(client, manager, driver_auth, running_trip):
    await client.post(
        API, json={"trip_id": running_trip["id"], "pings": [_ping()]}, headers=driver_auth
    )
    r = await client.get("/api/v1/tracking/live", headers=manager)
    assert len(r.json()) == 1
    assert r.json()[0]["trip_number"] == running_trip["trip_number"]


@pytest.mark.parametrize(
    "lat,lon", [("91", "77.5"), ("-91", "77.5"), ("12.9", "181"), ("12.9", "-181")]
)
async def test_coordinates_outside_the_valid_range_rejected(
    client, driver_auth, running_trip, lat, lon
):
    r = await client.post(
        API,
        json={"trip_id": running_trip["id"], "pings": [_ping(lat=lat, lon=lon)]},
        headers=driver_auth,
    )
    assert r.status_code == 422


async def test_timestamp_too_far_in_the_future_rejected(client, driver_auth, running_trip):
    future = (NOW + timedelta(hours=2)).isoformat()
    r = await client.post(
        API,
        json={
            "trip_id": running_trip["id"],
            "pings": [{"latitude": "12.9", "longitude": "77.5", "recorded_at": future}],
        },
        headers=driver_auth,
    )
    assert r.status_code == 422


async def test_empty_batch_rejected(client, driver_auth, running_trip):
    r = await client.post(
        API, json={"trip_id": running_trip["id"], "pings": []}, headers=driver_auth
    )
    assert r.status_code == 422


async def test_another_driver_cannot_post_to_this_trip(client, manager, running_trip):
    await client.post(
        "/api/v1/drivers",
        json={
            "email": "spoof@test.in",
            "password": "pass-word-1",
            "full_name": "Spoof Driver",
            "phone_number": "+919000000011",
            "license_number": "KA0120230004444",
            "license_expiry": str(date.today() + timedelta(days=365)),
        },
        headers=manager,
    )
    login = await client.post(
        "/api/v1/auth/login", json={"email": "spoof@test.in", "password": "pass-word-1"}
    )
    headers = {"Authorization": f"Bearer {login.json()['access_token']}"}
    r = await client.post(
        API, json={"trip_id": running_trip["id"], "pings": [_ping()]}, headers=headers
    )
    assert r.status_code == 403
