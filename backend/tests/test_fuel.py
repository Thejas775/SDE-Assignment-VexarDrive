from datetime import date, timedelta

API = "/api/v1/fuel"
TODAY = date.today()


def _payload(vehicle, **overrides):
    body = {
        "vehicle_id": vehicle["id"],
        "fuel_date": str(TODAY),
        "quantity_litres": "40.00",
        "cost": "3600.00",
        "odometer": 48250,
        "full_tank": True,
        "station": "HP Petrol Pump",
    }
    body.update(overrides)
    return body


async def test_log_fuel(client, manager, vehicle):
    r = await client.post(API, json=_payload(vehicle), headers=manager)
    assert r.status_code == 201
    assert r.json()["cost_per_litre"] == "90.00"


async def test_zero_quantity_rejected(client, manager, vehicle):
    r = await client.post(API, json=_payload(vehicle, quantity_litres="0"), headers=manager)
    assert r.status_code == 422


async def test_negative_cost_rejected(client, manager, vehicle):
    r = await client.post(API, json=_payload(vehicle, cost="-1"), headers=manager)
    assert r.status_code == 422


async def test_odometer_cannot_go_backwards_between_fills(client, manager, vehicle):
    await client.post(API, json=_payload(vehicle, odometer=48250), headers=manager)
    r = await client.post(API, json=_payload(vehicle, odometer=48000), headers=manager)
    assert r.status_code == 422
    assert "below the last recorded fill" in r.json()["error_message"]


async def test_fill_updates_the_vehicle_mileage(client, manager, vehicle):
    await client.post(API, json=_payload(vehicle, odometer=49000), headers=manager)
    v = await client.get(f"/api/v1/vehicles/{vehicle['id']}", headers=manager)
    assert v.json()["current_mileage"] == 49000


async def test_efficiency_between_two_full_tanks(client, manager, vehicle):
    await client.post(
        API,
        json=_payload(vehicle, odometer=48250, fuel_date=str(TODAY - timedelta(days=7))),
        headers=manager,
    )
    await client.post(
        API,
        json=_payload(vehicle, odometer=48650, quantity_litres="40.00", cost="3600.00"),
        headers=manager,
    )
    r = await client.get(f"{API}/efficiency/{vehicle['id']}", headers=manager)
    body = r.json()
    assert body["fills"] == 2
    assert body["distance_km"] == 400
    assert body["average_km_per_litre"] == "10.00"   # 400 km / 40 L
    assert body["average_cost_per_km"] == "9.00"     # 3600 / 400
    assert len(body["entries"]) == 1


async def test_partial_fills_are_excluded_from_efficiency(client, manager, vehicle):
    await client.post(API, json=_payload(vehicle, odometer=48250), headers=manager)
    await client.post(
        API, json=_payload(vehicle, odometer=48450, full_tank=False), headers=manager
    )
    r = await client.get(f"{API}/efficiency/{vehicle['id']}", headers=manager)
    assert r.json()["entries"] == []
    assert r.json()["average_km_per_litre"] is None


async def test_single_fill_has_no_efficiency_yet(client, manager, vehicle):
    await client.post(API, json=_payload(vehicle), headers=manager)
    r = await client.get(f"{API}/efficiency/{vehicle['id']}", headers=manager)
    assert r.json()["fills"] == 1
    assert r.json()["average_km_per_litre"] is None


async def test_filter_by_date_range(client, manager, vehicle):
    await client.post(
        API,
        json=_payload(vehicle, odometer=48250, fuel_date=str(TODAY - timedelta(days=40))),
        headers=manager,
    )
    await client.post(API, json=_payload(vehicle, odometer=48900), headers=manager)
    r = await client.get(f"{API}?date_from={TODAY - timedelta(days=7)}", headers=manager)
    assert r.json()["total"] == 1


async def test_update_and_delete(client, manager, vehicle):
    entry = (await client.post(API, json=_payload(vehicle), headers=manager)).json()
    upd = await client.put(f"{API}/{entry['id']}", json={"cost": "4000.00"}, headers=manager)
    assert upd.json()["cost"] == "4000.00"
    assert (await client.delete(f"{API}/{entry['id']}", headers=manager)).status_code == 200
    assert (await client.get(f"{API}/{entry['id']}", headers=manager)).status_code == 404


async def test_driver_cannot_log_fuel(client, driver_auth, vehicle):
    r = await client.post(API, json=_payload(vehicle), headers=driver_auth)
    assert r.status_code == 403
