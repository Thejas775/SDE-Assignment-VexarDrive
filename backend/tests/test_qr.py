from uuid import uuid4

from app.core.qr import parse_vehicle_code, vehicle_payload

API = "/api/v1/vehicles"


def test_payload_is_a_deep_link():
    vid = uuid4()
    assert vehicle_payload(vid) == f"fleet://vehicle/{vid}"


def test_parse_accepts_deep_link_and_bare_uuid():
    vid = uuid4()
    assert parse_vehicle_code(f"fleet://vehicle/{vid}") == vid
    assert parse_vehicle_code(str(vid)) == vid
    assert parse_vehicle_code(f"  {vid}  ") == vid


async def test_qr_returns_a_png(client, manager, vehicle):
    r = await client.get(f"{API}/{vehicle['id']}/qr", headers=manager)
    assert r.status_code == 200
    assert r.headers["content-type"] == "image/png"
    assert r.content.startswith(b"\x89PNG\r\n\x1a\n")


async def test_qr_returns_an_svg(client, manager, vehicle):
    r = await client.get(f"{API}/{vehicle['id']}/qr?format=svg", headers=manager)
    assert r.status_code == 200
    assert r.headers["content-type"].startswith("image/svg+xml")
    assert b"<svg" in r.content


async def test_qr_rejects_an_unknown_format(client, manager, vehicle):
    r = await client.get(f"{API}/{vehicle['id']}/qr?format=gif", headers=manager)
    assert r.status_code == 422


async def test_qr_for_unknown_vehicle_is_404(client, manager):
    r = await client.get(f"{API}/{uuid4()}/qr", headers=manager)
    assert r.status_code == 404


async def test_scanning_resolves_to_the_vehicle(client, manager, vehicle):
    code = vehicle_payload(vehicle["id"])
    r = await client.get(f"{API}/lookup?code={code}", headers=manager)
    assert r.status_code == 200
    assert r.json()["registration_number"] == vehicle["registration_number"]


async def test_scanning_a_bare_uuid_also_works(client, manager, vehicle):
    r = await client.get(f"{API}/lookup?code={vehicle['id']}", headers=manager)
    assert r.json()["id"] == vehicle["id"]


async def test_scanning_junk_is_rejected(client, manager):
    r = await client.get(f"{API}/lookup?code=not-a-code", headers=manager)
    assert r.status_code == 422


async def test_driver_scanning_an_unassigned_vehicle_is_forbidden(
    client, driver_auth, vehicle
):
    r = await client.get(f"{API}/lookup?code={vehicle['id']}", headers=driver_auth)
    assert r.status_code == 403


async def test_driver_scanning_their_own_vehicle_works(
    client, driver_auth, vehicle, assignment
):
    r = await client.get(f"{API}/lookup?code={vehicle['id']}", headers=driver_auth)
    assert r.status_code == 200


async def test_qr_generation_is_manager_only(client, driver_auth, vehicle):
    r = await client.get(f"{API}/{vehicle['id']}/qr", headers=driver_auth)
    assert r.status_code == 403
