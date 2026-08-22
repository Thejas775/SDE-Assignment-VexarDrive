from datetime import date, timedelta

API = "/api/v1/maintenance"
TODAY = date.today()


def _payload(vehicle, **overrides):
    body = {
        "vehicle_id": vehicle["id"],
        "maintenance_type": "BRAKE_SERVICE",
        "description": "Front pads replaced",
        "service_date": str(TODAY),
        "cost": "4500.00",
        "odometer": 48600,
        "next_service_date": str(TODAY + timedelta(days=180)),
        "next_service_mileage": 58600,
        "performed_by": "Sharma Motors",
    }
    body.update(overrides)
    return body


async def test_create_record(client, manager, vehicle):
    r = await client.post(API, json=_payload(vehicle), headers=manager)
    assert r.status_code == 201
    assert r.json()["cost"] == "4500.00"


async def test_a_higher_workshop_odometer_updates_the_vehicle(client, manager, vehicle):
    await client.post(API, json=_payload(vehicle, odometer=49000), headers=manager)
    v = await client.get(f"/api/v1/vehicles/{vehicle['id']}", headers=manager)
    assert v.json()["current_mileage"] == 49000


async def test_a_lower_odometer_does_not_reduce_the_vehicle_reading(client, manager, vehicle):
    await client.post(API, json=_payload(vehicle, odometer=100), headers=manager)
    v = await client.get(f"/api/v1/vehicles/{vehicle['id']}", headers=manager)
    assert v.json()["current_mileage"] == vehicle["current_mileage"]


async def test_next_service_date_before_service_date_rejected(client, manager, vehicle):
    r = await client.post(
        API,
        json=_payload(vehicle, next_service_date=str(TODAY - timedelta(days=1))),
        headers=manager,
    )
    assert r.status_code == 422


async def test_next_service_mileage_below_odometer_rejected(client, manager, vehicle):
    r = await client.post(API, json=_payload(vehicle, next_service_mileage=1), headers=manager)
    assert r.status_code == 422


async def test_negative_cost_rejected(client, manager, vehicle):
    r = await client.post(API, json=_payload(vehicle, cost="-1"), headers=manager)
    assert r.status_code == 422


async def test_vehicle_with_no_history_is_due(client, manager, vehicle):
    r = await client.get(f"{API}/due", headers=manager)
    entry = next(d for d in r.json() if d["vehicle"]["id"] == vehicle["id"])
    assert entry["reasons"] == ["no maintenance history"]


async def test_due_by_date(client, manager, vehicle):
    await client.post(
        API,
        json=_payload(
            vehicle,
            next_service_date=str(TODAY + timedelta(days=3)),
            next_service_mileage=999999,
        ),
        headers=manager,
    )
    r = await client.get(f"{API}/due", headers=manager)
    entry = next(d for d in r.json() if d["vehicle"]["id"] == vehicle["id"])
    assert entry["days_until_due"] == 3
    assert any("due in 3 days" in reason for reason in entry["reasons"])


async def test_due_by_mileage(client, manager, vehicle):
    await client.post(
        API,
        json=_payload(
            vehicle,
            odometer=48600,
            next_service_mileage=48700,
            next_service_date=str(TODAY + timedelta(days=900)),
        ),
        headers=manager,
    )
    r = await client.get(f"{API}/due", headers=manager)
    entry = next(d for d in r.json() if d["vehicle"]["id"] == vehicle["id"])
    assert entry["km_until_due"] == 100
    assert any("km" in reason for reason in entry["reasons"])


async def test_not_due_when_both_thresholds_are_far_away(client, manager, vehicle):
    await client.post(
        API,
        json=_payload(
            vehicle,
            next_service_date=str(TODAY + timedelta(days=900)),
            next_service_mileage=999999,
        ),
        headers=manager,
    )
    r = await client.get(f"{API}/due", headers=manager)
    assert all(d["vehicle"]["id"] != vehicle["id"] for d in r.json())


async def test_inactive_vehicles_are_not_reported_as_due(client, manager, vehicle):
    await client.post(f"/api/v1/vehicles/{vehicle['id']}/deactivate", headers=manager)
    r = await client.get(f"{API}/due", headers=manager)
    assert all(d["vehicle"]["id"] != vehicle["id"] for d in r.json())


async def test_filter_by_vehicle_and_type(client, manager, vehicle):
    await client.post(API, json=_payload(vehicle), headers=manager)
    await client.post(
        API, json=_payload(vehicle, maintenance_type="OIL_CHANGE"), headers=manager
    )
    r = await client.get(f"{API}?maintenance_type=OIL_CHANGE", headers=manager)
    assert r.json()["total"] == 1


async def test_update_record(client, manager, vehicle):
    record = (await client.post(API, json=_payload(vehicle), headers=manager)).json()
    r = await client.put(f"{API}/{record['id']}", json={"cost": "5200.00"}, headers=manager)
    assert r.json()["cost"] == "5200.00"
