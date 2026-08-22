"""Role-based access: every manager-only route must reject a driver."""

import pytest

MANAGER_ONLY = [
    ("get", "/api/v1/vehicles"),
    ("get", "/api/v1/drivers"),
    ("get", "/api/v1/assignments"),
    ("get", "/api/v1/trips"),
    ("get", "/api/v1/maintenance"),
    ("get", "/api/v1/maintenance/due"),
    ("get", "/api/v1/incidents"),
    ("get", "/api/v1/dashboard"),
    ("get", "/api/v1/tracking/live"),
    ("post", "/api/v1/notifications/sweep"),
]


@pytest.mark.parametrize("method,path", MANAGER_ONLY)
async def test_driver_is_forbidden(client, driver_auth, method, path):
    r = await getattr(client, method)(path, headers=driver_auth)
    assert r.status_code == 403


@pytest.mark.parametrize("method,path", MANAGER_ONLY)
async def test_manager_is_allowed(client, manager, method, path):
    r = await getattr(client, method)(path, headers=manager)
    assert r.status_code == 200


@pytest.mark.parametrize("method,path", MANAGER_ONLY)
async def test_anonymous_is_unauthorised(client, method, path):
    r = await getattr(client, method)(path)
    assert r.status_code == 401
