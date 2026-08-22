from datetime import date, timedelta

import pytest

API = "/api/v1/assignments"
TODAY = date.today()


def _payload(vehicle, driver, start=0, end=10, **overrides):
    body = {
        "vehicle_id": vehicle["id"],
        "driver_id": driver["id"],
        "start_date": str(TODAY + timedelta(days=start)),
        "end_date": str(TODAY + timedelta(days=end)) if end is not None else None,
    }
    body.update(overrides)
    return body


async def test_create_assignment(client, manager, vehicle, driver):
    r = await client.post(API, json=_payload(vehicle, driver), headers=manager)
    assert r.status_code == 201
    assert r.json()["status"] == "ACTIVE"
    assert r.json()["is_current"] is True


@pytest.mark.parametrize(
    "start,end,label",
    [
        (5, 15, "starts inside the existing range"),
        (-5, 5, "ends inside the existing range"),
        (-5, 15, "fully contains the existing range"),
        (2, 8, "fully inside the existing range"),
        (10, 20, "touches the end date"),
        (0, 10, "identical range"),
        (5, None, "open ended, overlapping"),
    ],
)
async def test_overlapping_vehicle_assignment_rejected(
    client, manager, vehicle, driver, assignment, start, end, label
):
    """Spec section 8: one vehicle cannot be held by two drivers at once."""
    second = await client.post(
        "/api/v1/drivers",
        json={
            "email": "second@test.in",
            "password": "pass-word-1",
            "full_name": "Second Driver",
            "phone_number": "+919000000003",
            "license_number": "KA0120230007777",
            "license_expiry": str(TODAY + timedelta(days=365)),
        },
        headers=manager,
    )
    r = await client.post(
        API, json=_payload(vehicle, second.json(), start, end), headers=manager
    )
    assert r.status_code == 409, label


@pytest.mark.parametrize("start,end", [(31, 40), (-20, -1)])
async def test_non_overlapping_assignment_accepted(
    client, manager, vehicle, driver, assignment, start, end
):
    second = (
        await client.post(
            "/api/v1/drivers",
            json={
                "email": "third@test.in",
                "password": "pass-word-1",
                "full_name": "Third Driver",
                "phone_number": "+919000000004",
                "license_number": "KA0120230006666",
                "license_expiry": str(TODAY + timedelta(days=365)),
            },
            headers=manager,
        )
    ).json()
    r = await client.post(API, json=_payload(vehicle, second, start, end), headers=manager)
    assert r.status_code == 201


async def test_driver_cannot_hold_two_vehicles_at_once(client, manager, vehicle, driver, assignment):
    other = (
        await client.post(
            "/api/v1/vehicles",
            json={
                "registration_number": "KA-09-ZZ-0001",
                "vehicle_type": "VAN",
                "make": "Ashok",
                "model": "Dost",
                "year": 2021,
                "fuel_type": "CNG",
                "insurance_expiry": str(TODAY + timedelta(days=365)),
                "registration_expiry": str(TODAY + timedelta(days=730)),
            },
            headers=manager,
        )
    ).json()
    r = await client.post(API, json=_payload(other, driver, 5, 15), headers=manager)
    assert r.status_code == 409


async def test_database_rejects_overlap_even_without_the_service_check(
    db, manager, client, vehicle, driver, assignment
):
    """The EXCLUDE constraint is the real guarantee, not the pre-flight query."""
    from sqlalchemy.exc import IntegrityError

    from app.models.vehicle_assignment import VehicleAssignment

    db.add(
        VehicleAssignment(
            vehicle_id=vehicle["id"],
            driver_id=driver["id"],
            start_date=TODAY + timedelta(days=5),
            end_date=TODAY + timedelta(days=15),
        )
    )
    with pytest.raises(IntegrityError) as exc:
        await db.commit()
    assert "overlap" in str(exc.value.orig)
    await db.rollback()


async def test_end_date_before_start_rejected(client, manager, vehicle, driver):
    r = await client.post(API, json=_payload(vehicle, driver, 10, 5), headers=manager)
    assert r.status_code == 422


async def test_suspended_driver_cannot_be_assigned(client, manager, vehicle, driver):
    await client.post(f"/api/v1/drivers/{driver['id']}/suspend", headers=manager)
    r = await client.post(API, json=_payload(vehicle, driver), headers=manager)
    assert r.status_code == 409


async def test_inactive_vehicle_cannot_be_assigned(client, manager, vehicle, driver):
    await client.post(f"/api/v1/vehicles/{vehicle['id']}/deactivate", headers=manager)
    r = await client.post(API, json=_payload(vehicle, driver), headers=manager)
    assert r.status_code == 409


async def test_assignment_beyond_licence_expiry_rejected(client, manager, vehicle, driver):
    r = await client.post(API, json=_payload(vehicle, driver, 400, 410), headers=manager)
    assert r.status_code == 422


async def test_ending_an_assignment_frees_the_slot(client, manager, vehicle, driver, assignment):
    end = await client.post(
        f"{API}/{assignment['id']}/end", json={"end_date": str(TODAY)}, headers=manager
    )
    assert end.json()["status"] == "COMPLETED"
    r = await client.post(API, json=_payload(vehicle, driver, 1, 20), headers=manager)
    assert r.status_code == 201


async def test_cancelling_frees_the_slot(client, manager, vehicle, driver, assignment):
    await client.post(f"{API}/{assignment['id']}/cancel", headers=manager)
    r = await client.post(API, json=_payload(vehicle, driver, 0, 10), headers=manager)
    assert r.status_code == 201


async def test_cannot_end_twice(client, manager, assignment):
    await client.post(f"{API}/{assignment['id']}/end", json={}, headers=manager)
    r = await client.post(f"{API}/{assignment['id']}/end", json={}, headers=manager)
    assert r.status_code == 409


async def test_active_on_filter(client, manager, assignment):
    inside = await client.get(f"{API}?active_on={TODAY + timedelta(days=5)}", headers=manager)
    outside = await client.get(f"{API}?active_on={TODAY + timedelta(days=99)}", headers=manager)
    assert inside.json()["total"] == 1
    assert outside.json()["total"] == 0


async def test_driver_sees_only_their_own_assignments(client, driver_auth, assignment):
    r = await client.get(f"{API}/my", headers=driver_auth)
    assert r.status_code == 200 and r.json()["total"] == 1
