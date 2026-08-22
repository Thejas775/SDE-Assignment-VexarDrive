from datetime import datetime, timedelta, timezone

import pytest_asyncio

SYNC = "/api/v1/sync"
NOW = datetime.now(timezone.utc)


def _ping(minutes_ago=0, lat="12.971599"):
    return {
        "latitude": lat,
        "longitude": "77.594566",
        "recorded_at": (NOW - timedelta(minutes=minutes_ago)).isoformat(),
    }


@pytest_asyncio.fixture
async def running_trip(client, driver_auth, trip):
    await client.post(
        f"/api/v1/trips/{trip['id']}/start",
        json={"start_odometer": 48250},
        headers=driver_auth,
    )
    return trip


# ------------------------------------------------------- idempotency keys --

async def test_replayed_request_is_not_executed_twice(client, driver_auth, trip):
    body = {"start_odometer": 48250}
    headers = {**driver_auth, "Idempotency-Key": "queued-op-1"}

    first = await client.post(f"/api/v1/trips/{trip['id']}/start", json=body, headers=headers)
    second = await client.post(f"/api/v1/trips/{trip['id']}/start", json=body, headers=headers)

    assert first.status_code == 200
    assert second.status_code == 200
    assert second.headers.get("idempotent-replay") == "true"
    assert first.json() == second.json()


async def test_without_a_key_the_retry_conflicts(client, driver_auth, trip):
    body = {"start_odometer": 48250}
    await client.post(f"/api/v1/trips/{trip['id']}/start", json=body, headers=driver_auth)
    retry = await client.post(f"/api/v1/trips/{trip['id']}/start", json=body, headers=driver_auth)
    assert retry.status_code == 409


async def test_same_key_with_a_different_body_is_rejected(client, driver_auth, trip):
    headers = {**driver_auth, "Idempotency-Key": "queued-op-2"}
    await client.post(
        f"/api/v1/trips/{trip['id']}/start", json={"start_odometer": 48250}, headers=headers
    )
    r = await client.post(
        f"/api/v1/trips/{trip['id']}/start", json={"start_odometer": 99999}, headers=headers
    )
    assert r.status_code == 422
    assert "different request body" in r.json()["error_message"]


async def test_keys_are_scoped_to_the_user(client, manager, driver_auth, vehicle, trip):
    headers_driver = {**driver_auth, "Idempotency-Key": "shared-key"}
    await client.post(
        f"/api/v1/trips/{trip['id']}/start", json={"start_odometer": 48250}, headers=headers_driver
    )
    # the manager reusing the same key string must not get the driver's response
    r = await client.post(
        "/api/v1/incidents",
        json={
            "vehicle_id": vehicle["id"],
            "title": "Unrelated report",
            "description": "different request entirely",
            "severity": "LOW",
        },
        headers={**manager, "Idempotency-Key": "shared-key"},
    )
    assert r.status_code == 201


async def test_a_failed_request_is_replayed_as_the_same_failure(client, driver_auth, trip):
    headers = {**driver_auth, "Idempotency-Key": "queued-op-3"}
    body = {"end_odometer": 50000}
    first = await client.post(f"/api/v1/trips/{trip['id']}/complete", json=body, headers=headers)
    second = await client.post(f"/api/v1/trips/{trip['id']}/complete", json=body, headers=headers)
    assert first.status_code == 409
    assert second.status_code == 409
    assert second.headers.get("idempotent-replay") == "true"


async def test_get_requests_ignore_the_header(client, manager, vehicle):
    headers = {**manager, "Idempotency-Key": "not-for-reads"}
    first = await client.get(f"/api/v1/vehicles/{vehicle['id']}", headers=headers)
    second = await client.get(f"/api/v1/vehicles/{vehicle['id']}", headers=headers)
    assert first.status_code == second.status_code == 200
    assert "idempotent-replay" not in second.headers


# ------------------------------------------------------------ batch sync --

async def test_sync_applies_a_queue_in_order(client, driver_auth, vehicle, trip, assignment):
    r = await client.post(
        SYNC,
        json={
            "operations": [
                {
                    "client_id": "local-1",
                    "type": "TRIP_START",
                    "trip_id": trip["id"],
                    "payload": {"start_odometer": 48250},
                },
                {
                    "client_id": "local-2",
                    "type": "LOCATION_BATCH",
                    "trip_id": trip["id"],
                    "payload": {"pings": [_ping(30), _ping(20)]},
                },
                {
                    "client_id": "local-3",
                    "type": "INCIDENT_REPORT",
                    "payload": {
                        "vehicle_id": vehicle["id"],
                        "title": "Brake noise",
                        "description": "Grinding sound",
                        "severity": "HIGH",
                    },
                },
                {
                    "client_id": "local-4",
                    "type": "TRIP_COMPLETE",
                    "trip_id": trip["id"],
                    "payload": {"end_odometer": 48596},
                },
            ]
        },
        headers=driver_auth,
    )
    body = r.json()
    assert r.status_code == 200
    assert body["applied"] == 4 and body["failed"] == 0
    assert [x["client_id"] for x in body["results"]] == ["local-1", "local-2", "local-3", "local-4"]

    trip_after = await client.get(f"/api/v1/trips/{trip['id']}", headers=driver_auth)
    assert trip_after.json()["status"] == "COMPLETED"
    assert float(trip_after.json()["distance_km"]) == 346.0


async def test_one_bad_operation_does_not_abort_the_batch(
    client, driver_auth, vehicle, running_trip, assignment
):
    r = await client.post(
        SYNC,
        json={
            "operations": [
                {
                    "client_id": "bad-1",
                    "type": "TRIP_START",
                    "trip_id": running_trip["id"],
                    "payload": {"start_odometer": 48250},
                },
                {
                    "client_id": "good-1",
                    "type": "LOCATION_BATCH",
                    "trip_id": running_trip["id"],
                    "payload": {"pings": [_ping(10)]},
                },
            ]
        },
        headers=driver_auth,
    )
    body = r.json()
    assert body["applied"] == 1 and body["failed"] == 1
    failed, applied = body["results"]
    assert failed["status"] == "failed" and failed["code"] == 409
    assert applied["status"] == "applied"


async def test_resent_queue_does_not_duplicate_incidents(
    client, driver_auth, vehicle, assignment, db
):
    from sqlalchemy import func, select

    from app.models.incident import Incident

    queue = {
        "operations": [
            {
                "client_id": "incident-local-77",
                "type": "INCIDENT_REPORT",
                "payload": {
                    "vehicle_id": vehicle["id"],
                    "title": "Brake noise",
                    "description": "Grinding sound",
                    "severity": "HIGH",
                },
            }
        ]
    }
    first = await client.post(SYNC, json=queue, headers=driver_auth)
    second = await client.post(SYNC, json=queue, headers=driver_auth)

    assert first.json()["applied"] == second.json()["applied"] == 1
    assert first.json()["results"][0]["result"]["id"] == second.json()["results"][0]["result"]["id"]
    assert await db.scalar(select(func.count()).select_from(Incident)) == 1


async def test_missing_trip_id_is_reported_per_operation(client, driver_auth):
    r = await client.post(
        SYNC,
        json={
            "operations": [
                {"client_id": "x", "type": "TRIP_START", "payload": {"start_odometer": 1}}
            ]
        },
        headers=driver_auth,
    )
    result = r.json()["results"][0]
    assert result["status"] == "failed"
    assert "trip_id" in result["error_message"]


async def test_empty_queue_is_rejected(client, driver_auth):
    r = await client.post(SYNC, json={"operations": []}, headers=driver_auth)
    assert r.status_code == 422


async def test_sync_requires_authentication(client, trip):
    r = await client.post(
        SYNC,
        json={
            "operations": [
                {
                    "client_id": "x",
                    "type": "TRIP_START",
                    "trip_id": trip["id"],
                    "payload": {"start_odometer": 1},
                }
            ]
        },
    )
    assert r.status_code == 401
