from datetime import date, timedelta

import pytest

API = "/api/v1/vehicles"


def _payload(**overrides):
    body = {
        "registration_number": "KA-05-XY-9999",
        "vehicle_type": "VAN",
        "make": "Ashok",
        "model": "Dost",
        "year": 2021,
        "fuel_type": "CNG",
        "current_mileage": 1000,
        "insurance_expiry": str(date.today() + timedelta(days=365)),
        "registration_expiry": str(date.today() + timedelta(days=730)),
    }
    body.update(overrides)
    return body


async def test_create_vehicle(client, manager):
    r = await client.post(API, json=_payload(), headers=manager)
    assert r.status_code == 201
    assert r.json()["status"] == "AVAILABLE"


async def test_registration_number_is_normalised(client, manager):
    r = await client.post(
        API, json=_payload(registration_number="  ka-05 xy-9999 "), headers=manager
    )
    assert r.json()["registration_number"] == "KA-05 XY-9999"


async def test_duplicate_registration_rejected(client, manager, vehicle):
    r = await client.post(
        API, json=_payload(registration_number=vehicle["registration_number"]), headers=manager
    )
    assert r.status_code == 409
    assert "already registered" in r.json()["error_message"]


async def test_duplicate_registration_rejected_case_insensitively(client, manager, vehicle):
    r = await client.post(
        API,
        json=_payload(registration_number=vehicle["registration_number"].lower()),
        headers=manager,
    )
    assert r.status_code == 409


async def test_renaming_onto_an_existing_registration_rejected(client, manager, vehicle):
    other = (await client.post(API, json=_payload(), headers=manager)).json()
    r = await client.put(
        f"{API}/{other['id']}",
        json={"registration_number": vehicle["registration_number"]},
        headers=manager,
    )
    assert r.status_code == 409


@pytest.mark.parametrize("year", [1899, 2101])
async def test_year_outside_the_allowed_range_rejected(client, manager, year):
    r = await client.post(API, json=_payload(year=year), headers=manager)
    assert r.status_code == 422


async def test_update_changes_only_the_given_fields(client, manager, vehicle):
    r = await client.put(f"{API}/{vehicle['id']}", json={"make": "TATA Motors"}, headers=manager)
    assert r.status_code == 200
    assert r.json()["make"] == "TATA Motors"
    assert r.json()["model"] == vehicle["model"]


async def test_mileage_cannot_decrease(client, manager, vehicle):
    r = await client.put(
        f"{API}/{vehicle['id']}",
        json={"current_mileage": vehicle["current_mileage"] - 1},
        headers=manager,
    )
    assert r.status_code == 422
    assert "cannot decrease" in r.json()["error_message"]


async def test_on_trip_cannot_be_set_manually(client, manager, vehicle):
    r = await client.put(f"{API}/{vehicle['id']}", json={"status": "ON_TRIP"}, headers=manager)
    assert r.status_code == 422


async def test_deactivate_then_activate(client, manager, vehicle):
    off = await client.post(f"{API}/{vehicle['id']}/deactivate", headers=manager)
    assert off.json()["status"] == "INACTIVE"
    on = await client.post(f"{API}/{vehicle['id']}/activate", headers=manager)
    assert on.json()["status"] == "AVAILABLE"


async def test_cannot_deactivate_a_vehicle_with_a_scheduled_trip(client, manager, vehicle, trip):
    r = await client.post(f"{API}/{vehicle['id']}/deactivate", headers=manager)
    assert r.status_code == 409
    assert "trip" in r.json()["error_message"]


async def test_search_matches_registration_make_and_model(client, manager, vehicle):
    await client.post(API, json=_payload(), headers=manager)
    for term, expected in [("tata", 1), ("dost", 1), ("KA-", 2)]:
        r = await client.get(f"{API}?search={term}", headers=manager)
        assert r.json()["total"] == expected, term


async def test_filter_by_fuel_type(client, manager, vehicle):
    await client.post(API, json=_payload(), headers=manager)
    r = await client.get(f"{API}?fuel_type=CNG", headers=manager)
    assert [v["fuel_type"] for v in r.json()["items"]] == ["CNG"]


async def test_expiring_documents_filter(client, manager, vehicle):
    await client.post(
        API,
        json=_payload(insurance_expiry=str(date.today() + timedelta(days=5))),
        headers=manager,
    )
    r = await client.get(f"{API}?expiring_documents=true", headers=manager)
    assert r.json()["total"] == 1
    assert r.json()["items"][0]["insurance_expiring_soon"] is True


async def test_pagination(client, manager):
    for i in range(5):
        await client.post(API, json=_payload(registration_number=f"KA-07-PP-000{i}"), headers=manager)
    r = await client.get(f"{API}?page=2&page_size=2", headers=manager)
    body = r.json()
    assert body["total"] == 5 and body["pages"] == 3 and len(body["items"]) == 2


async def test_unknown_vehicle_returns_404(client, manager):
    r = await client.get(f"{API}/00000000-0000-0000-0000-000000000000", headers=manager)
    assert r.status_code == 404


async def test_driver_cannot_read_an_unassigned_vehicle(client, driver_auth, vehicle):
    r = await client.get(f"{API}/{vehicle['id']}", headers=driver_auth)
    assert r.status_code == 403


async def test_driver_can_read_an_assigned_vehicle(client, driver_auth, vehicle, assignment):
    r = await client.get(f"{API}/{vehicle['id']}", headers=driver_auth)
    assert r.status_code == 200


async def test_my_vehicle_returns_the_current_assignment(client, driver_auth, vehicle, assignment):
    r = await client.get(f"{API}/my-vehicle", headers=driver_auth)
    assert r.json()["registration_number"] == vehicle["registration_number"]
