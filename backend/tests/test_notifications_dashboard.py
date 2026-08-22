from datetime import date, timedelta

NOTIF = "/api/v1/notifications"
DASH = "/api/v1/dashboard"


async def test_creating_a_trip_notifies_the_driver(client, driver_auth, trip):
    r = await client.get(NOTIF, headers=driver_auth)
    assert r.json()["total"] == 1
    assert r.json()["items"][0]["notification_type"] == "TRIP_ASSIGNED"
    assert trip["trip_number"] in r.json()["items"][0]["title"]


async def test_completing_a_trip_notifies_managers(client, manager, driver_auth, trip):
    await client.post(
        f"/api/v1/trips/{trip['id']}/start",
        json={"start_odometer": 48250},
        headers=driver_auth,
    )
    await client.post(
        f"/api/v1/trips/{trip['id']}/complete",
        json={"end_odometer": 48596},
        headers=driver_auth,
    )
    r = await client.get(f"{NOTIF}?unread_only=true", headers=manager)
    types = [n["notification_type"] for n in r.json()["items"]]
    assert "TRIP_COMPLETED" in types


async def test_reporting_an_incident_notifies_managers(client, manager, vehicle):
    await client.post(
        "/api/v1/incidents",
        json={
            "vehicle_id": vehicle["id"],
            "title": "Brake noise",
            "description": "Grinding",
            "severity": "HIGH",
        },
        headers=manager,
    )
    r = await client.get(NOTIF, headers=manager)
    assert any(n["notification_type"] == "INCIDENT_REPORTED" for n in r.json()["items"])


async def test_unread_count_and_mark_read(client, driver_auth, trip):
    assert (await client.get(f"{NOTIF}/unread-count", headers=driver_auth)).json()["unread"] == 1
    notification = (await client.get(NOTIF, headers=driver_auth)).json()["items"][0]
    r = await client.post(f"{NOTIF}/{notification['id']}/read", headers=driver_auth)
    assert r.json()["is_read"] is True and r.json()["read_at"] is not None
    assert (await client.get(f"{NOTIF}/unread-count", headers=driver_auth)).json()["unread"] == 0


async def test_mark_all_read(client, driver_auth, trip):
    r = await client.post(f"{NOTIF}/read-all", headers=driver_auth)
    assert "1 notification" in r.json()["message"]
    assert (await client.get(f"{NOTIF}/unread-count", headers=driver_auth)).json()["unread"] == 0


async def test_a_user_cannot_read_someone_elses_notification(client, manager, driver_auth, trip):
    notification = (await client.get(NOTIF, headers=driver_auth)).json()["items"][0]
    r = await client.post(f"{NOTIF}/{notification['id']}/read", headers=manager)
    assert r.status_code == 404


async def test_sweep_creates_expiry_alerts_once_per_day(client, manager, vehicle, driver):
    await client.put(
        f"/api/v1/vehicles/{vehicle['id']}",
        json={"insurance_expiry": str(date.today() + timedelta(days=5))},
        headers=manager,
    )
    first = await client.post(f"{NOTIF}/sweep", headers=manager)
    assert first.json()["insurance_expiring"] == 1
    assert first.json()["total_created"] > 0

    second = await client.post(f"{NOTIF}/sweep", headers=manager)
    assert second.json()["total_created"] == 0


async def test_dashboard_counts_vehicles_by_status(client, manager, vehicle):
    r = await client.get(DASH, headers=manager)
    counts = r.json()["vehicles"]
    assert counts["total"] == 1 and counts["available"] == 1
    assert counts["total"] == (
        counts["available"] + counts["on_trip"] + counts["in_maintenance"] + counts["inactive"]
    )


async def test_dashboard_reflects_a_running_trip(client, manager, driver_auth, trip):
    await client.post(
        f"/api/v1/trips/{trip['id']}/start",
        json={"start_odometer": 48250},
        headers=driver_auth,
    )
    body = (await client.get(DASH, headers=manager)).json()
    assert body["vehicles"]["on_trip"] == 1
    assert body["trips"]["active"] == 1


async def test_dashboard_sums_todays_distance(client, manager, driver_auth, trip):
    await client.post(
        f"/api/v1/trips/{trip['id']}/start",
        json={"start_odometer": 48250},
        headers=driver_auth,
    )
    await client.post(
        f"/api/v1/trips/{trip['id']}/complete",
        json={"end_odometer": 48596},
        headers=driver_auth,
    )
    body = (await client.get(DASH, headers=manager)).json()
    assert float(body["distance_today_km"]) == 346.0
    assert body["trips"]["completed_today"] == 1


async def test_dashboard_counts_expiring_documents(client, manager, vehicle, driver):
    await client.put(
        f"/api/v1/vehicles/{vehicle['id']}",
        json={"insurance_expiry": str(date.today() + timedelta(days=5))},
        headers=manager,
    )
    body = (await client.get(DASH, headers=manager)).json()
    assert body["expiring_documents"]["insurance"] == 1


async def test_dashboard_lists_recent_incidents(client, manager, vehicle):
    await client.post(
        "/api/v1/incidents",
        json={
            "vehicle_id": vehicle["id"],
            "title": "Brake noise",
            "description": "Grinding",
            "severity": "HIGH",
        },
        headers=manager,
    )
    body = (await client.get(DASH, headers=manager)).json()
    assert body["open_incidents"] == 1
    assert body["recent_incidents"][0]["title"] == "Brake noise"
