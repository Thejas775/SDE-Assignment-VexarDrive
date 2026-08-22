from datetime import date, timedelta

API = "/api/v1/incidents"


def _payload(vehicle, **overrides):
    body = {
        "vehicle_id": vehicle["id"],
        "title": "Brake noise on descent",
        "description": "Grinding sound from the front wheels",
        "severity": "HIGH",
    }
    body.update(overrides)
    return body


async def test_driver_cannot_report_on_an_unassigned_vehicle(client, driver_auth, vehicle):
    r = await client.post(API, json=_payload(vehicle), headers=driver_auth)
    assert r.status_code == 403


async def test_driver_can_report_on_their_assigned_vehicle(
    client, driver_auth, vehicle, assignment
):
    r = await client.post(API, json=_payload(vehicle), headers=driver_auth)
    assert r.status_code == 201
    assert r.json()["status"] == "OPEN"
    assert r.json()["reported_by"]["full_name"] == "Rahul Sharma"


async def test_critical_incident_takes_an_idle_vehicle_off_the_road(client, manager, vehicle):
    await client.post(API, json=_payload(vehicle, severity="CRITICAL"), headers=manager)
    v = await client.get(f"/api/v1/vehicles/{vehicle['id']}", headers=manager)
    assert v.json()["status"] == "IN_MAINTENANCE"


async def test_critical_incident_leaves_a_running_vehicle_alone(
    client, manager, driver_auth, vehicle, trip
):
    await client.post(
        f"/api/v1/trips/{trip['id']}/start",
        json={"start_odometer": 48250},
        headers=driver_auth,
    )
    await client.post(API, json=_payload(vehicle, severity="CRITICAL"), headers=manager)
    v = await client.get(f"/api/v1/vehicles/{vehicle['id']}", headers=manager)
    assert v.json()["status"] == "ON_TRIP"


async def test_resolving_requires_notes(client, manager, vehicle):
    incident = (await client.post(API, json=_payload(vehicle), headers=manager)).json()
    r = await client.post(
        f"{API}/{incident['id']}/status", json={"status": "RESOLVED"}, headers=manager
    )
    assert r.status_code == 422


async def test_resolve_sets_the_timestamp(client, manager, vehicle):
    incident = (await client.post(API, json=_payload(vehicle), headers=manager)).json()
    r = await client.post(
        f"{API}/{incident['id']}/status",
        json={"status": "RESOLVED", "resolution_notes": "Pads replaced"},
        headers=manager,
    )
    assert r.json()["status"] == "RESOLVED"
    assert r.json()["resolved_at"] is not None


async def test_assign_populates_the_assignee_and_advances_status(client, manager, vehicle):
    me = (await client.get("/api/v1/auth/me", headers=manager)).json()
    incident = (await client.post(API, json=_payload(vehicle), headers=manager)).json()
    r = await client.post(
        f"{API}/{incident['id']}/assign", json={"assigned_to_id": me["id"]}, headers=manager
    )
    assert r.json()["status"] == "IN_PROGRESS"
    assert r.json()["assigned_to"]["id"] == me["id"]


async def test_incidents_cannot_be_assigned_to_a_driver(client, manager, driver, vehicle):
    incident = (await client.post(API, json=_payload(vehicle), headers=manager)).json()
    r = await client.post(
        f"{API}/{incident['id']}/assign",
        json={"assigned_to_id": driver["user_id"]},
        headers=manager,
    )
    assert r.status_code == 422


async def test_resolved_incident_cannot_go_back_to_in_progress(client, manager, vehicle):
    incident = (await client.post(API, json=_payload(vehicle), headers=manager)).json()
    await client.post(
        f"{API}/{incident['id']}/status",
        json={"status": "RESOLVED", "resolution_notes": "done"},
        headers=manager,
    )
    r = await client.post(
        f"{API}/{incident['id']}/status", json={"status": "IN_PROGRESS"}, headers=manager
    )
    assert r.status_code == 409


async def test_open_only_filter(client, manager, vehicle):
    first = (await client.post(API, json=_payload(vehicle), headers=manager)).json()
    await client.post(API, json=_payload(vehicle, title="Second issue"), headers=manager)
    await client.post(
        f"{API}/{first['id']}/status",
        json={"status": "RESOLVED", "resolution_notes": "done"},
        headers=manager,
    )
    r = await client.get(f"{API}?open_only=true", headers=manager)
    assert r.json()["total"] == 1


async def test_driver_sees_only_their_own_reports(
    client, manager, driver_auth, vehicle, assignment
):
    await client.post(API, json=_payload(vehicle), headers=driver_auth)
    await client.post(API, json=_payload(vehicle, title="Manager reported"), headers=manager)
    r = await client.get(f"{API}/my", headers=driver_auth)
    assert r.json()["total"] == 1
