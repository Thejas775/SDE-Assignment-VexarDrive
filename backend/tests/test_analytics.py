from datetime import date, timedelta

import pytest_asyncio

ANALYTICS = "/api/v1/analytics"
TODAY = date.today()


@pytest_asyncio.fixture
async def completed_trip(client, manager, driver_auth, trip):
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
    return trip


# ------------------------------------------------- driver performance (§22) --

async def test_performance_counts_trips_and_distance(client, manager, driver, completed_trip):
    r = await client.get(f"/api/v1/drivers/{driver['id']}/performance", headers=manager)
    body = r.json()
    assert body["total_trips"] == 1
    assert body["completed_trips"] == 1
    assert body["total_distance_km"] == 346.0
    assert body["average_distance_km"] == 346.0


async def test_performance_reports_average_duration(client, manager, driver, completed_trip):
    r = await client.get(f"/api/v1/drivers/{driver['id']}/performance", headers=manager)
    assert r.json()["average_trip_duration_minutes"] is not None


async def test_performance_counts_incidents_reported(
    client, manager, driver, driver_auth, vehicle, assignment
):
    await client.post(
        "/api/v1/incidents",
        json={
            "vehicle_id": vehicle["id"],
            "title": "Brake noise",
            "description": "Grinding sound",
            "severity": "HIGH",
        },
        headers=driver_auth,
    )
    r = await client.get(f"/api/v1/drivers/{driver['id']}/performance", headers=manager)
    assert r.json()["incidents_reported"] == 1


async def test_history_includes_the_performance_metrics(client, manager, driver, completed_trip):
    r = await client.get(f"/api/v1/drivers/{driver['id']}/history", headers=manager)
    body = r.json()
    assert body["total_distance_km"] == 346.0
    assert "average_trip_duration_minutes" in body
    assert "incidents_reported" in body
    assert len(body["assignments"]) == 1


async def test_leaderboard_is_ranked_by_distance(client, manager, driver, completed_trip):
    r = await client.get("/api/v1/drivers/performance", headers=manager)
    rows = r.json()
    assert rows[0]["total_distance_km"] == 346.0
    assert [row["total_distance_km"] for row in rows] == sorted(
        [row["total_distance_km"] for row in rows], reverse=True
    )


# --------------------------------------------------- fleet analytics (§22) --

async def test_vehicle_analytics_reports_distance_and_utilisation(
    client, manager, vehicle, completed_trip
):
    r = await client.get(f"{ANALYTICS}/vehicles", headers=manager)
    row = next(v for v in r.json() if v["vehicle_id"] == vehicle["id"])
    assert row["trips"] == 1
    assert row["distance_km"] == "346.00"
    assert row["active_days"] == 1
    assert float(row["utilization_percent"]) > 0


async def test_cost_per_km_combines_fuel_and_maintenance(
    client, manager, vehicle, completed_trip
):
    await client.post(
        "/api/v1/fuel",
        json={
            "vehicle_id": vehicle["id"],
            "fuel_date": str(TODAY),
            "quantity_litres": "40.00",
            "cost": "3600.00",
            "odometer": 48600,
        },
        headers=manager,
    )
    await client.post(
        "/api/v1/maintenance",
        json={
            "vehicle_id": vehicle["id"],
            "maintenance_type": "OIL_CHANGE",
            "description": "Routine oil change",
            "service_date": str(TODAY),
            "cost": "1000.00",
            "odometer": 48600,
        },
        headers=manager,
    )
    r = await client.get(f"{ANALYTICS}/vehicles", headers=manager)
    row = next(v for v in r.json() if v["vehicle_id"] == vehicle["id"])
    assert row["fuel_cost"] == "3600.00"
    assert row["maintenance_cost"] == "1000.00"
    # (3600 + 1000) / 346 km
    assert row["cost_per_km"] == "13.29"


async def test_fleet_summary_aggregates_every_vehicle(client, manager, vehicle, completed_trip):
    r = await client.get(f"{ANALYTICS}/fleet", headers=manager)
    body = r.json()
    assert body["vehicles"] == 1
    assert body["trips"] == 1
    assert body["distance_km"] == "346.00"
    assert body["period"]["days"] == 30


async def test_fleet_summary_ranks_top_vehicles(client, manager, vehicle, completed_trip):
    r = await client.get(f"{ANALYTICS}/fleet", headers=manager)
    top = r.json()["top_vehicles_by_distance"]
    assert top[0]["registration_number"] == vehicle["registration_number"]


async def test_custom_date_range_excludes_older_activity(client, manager, completed_trip):
    past = TODAY - timedelta(days=90)
    r = await client.get(
        f"{ANALYTICS}/fleet?date_from={past}&date_to={past + timedelta(days=10)}",
        headers=manager,
    )
    assert r.json()["trips"] == 0
    assert r.json()["distance_km"] == "0.00"


async def test_monthly_series_has_one_point_per_month(client, manager, completed_trip):
    r = await client.get(f"{ANALYTICS}/monthly?months=3", headers=manager)
    points = r.json()
    assert len(points) == 3
    assert points[-1]["month"] == TODAY.strftime("%Y-%m")
    assert points[-1]["distance_km"] == "346.00"


async def test_analytics_are_manager_only(client, driver_auth):
    for path in ("/fleet", "/vehicles", "/monthly"):
        assert (await client.get(f"{ANALYTICS}{path}", headers=driver_auth)).status_code == 403
