import pytest

API = "/api/v1/auth"


async def test_register_returns_created_user(client):
    r = await client.post(
        f"{API}/register",
        json={"email": "New@Test.in", "password": "pass-word-1", "full_name": "New User"},
    )
    assert r.status_code == 201
    assert r.json()["email"] == "new@test.in"
    assert "hashed_password" not in r.json()


async def test_password_is_hashed_not_stored_plainly(client, db):
    from sqlalchemy import select

    from app.models.user import User

    await client.post(
        f"{API}/register",
        json={"email": "hash@test.in", "password": "pass-word-1", "full_name": "Hash User"},
    )
    stored = await db.scalar(select(User.hashed_password).where(User.email == "hash@test.in"))
    assert stored != "pass-word-1"
    assert stored.startswith("$2b$")


async def test_duplicate_email_rejected(client):
    body = {"email": "dupe@test.in", "password": "pass-word-1", "full_name": "First"}
    assert (await client.post(f"{API}/register", json=body)).status_code == 201
    r = await client.post(f"{API}/register", json=body)
    assert r.status_code == 409


@pytest.mark.parametrize("password", ["short", "1234567"])
async def test_short_password_rejected(client, password):
    r = await client.post(
        f"{API}/register",
        json={"email": "weak@test.in", "password": password, "full_name": "Weak User"},
    )
    assert r.status_code == 422


async def test_login_returns_token_pair(client, manager):
    r = await client.post(
        f"{API}/login", json={"email": "manager@test.in", "password": "pass-word-1"}
    )
    body = r.json()
    assert r.status_code == 200
    assert body["access_token"] and body["refresh_token"]
    assert body["user"]["role"] == "FLEET_MANAGER"


async def test_login_with_wrong_password_rejected(client, manager):
    r = await client.post(
        f"{API}/login", json={"email": "manager@test.in", "password": "wrong-password"}
    )
    assert r.status_code == 401
    assert "password" in r.json()["error_message"].lower()


async def test_unknown_email_gives_same_error_as_wrong_password(client, manager):
    unknown = await client.post(
        f"{API}/login", json={"email": "ghost@test.in", "password": "pass-word-1"}
    )
    wrong = await client.post(
        f"{API}/login", json={"email": "manager@test.in", "password": "nope-nope-1"}
    )
    assert unknown.status_code == wrong.status_code == 401
    assert unknown.json() == wrong.json()


async def test_me_requires_a_token(client):
    assert (await client.get(f"{API}/me")).status_code == 401


async def test_me_returns_current_user(client, manager):
    r = await client.get(f"{API}/me", headers=manager)
    assert r.status_code == 200
    assert r.json()["email"] == "manager@test.in"


async def test_tampered_token_rejected(client):
    r = await client.get(f"{API}/me", headers={"Authorization": "Bearer not.a.token"})
    assert r.status_code == 401


async def test_refresh_rotates_and_invalidates_the_old_token(client, manager):
    login = (
        await client.post(
            f"{API}/login", json={"email": "manager@test.in", "password": "pass-word-1"}
        )
    ).json()
    first = await client.post(f"{API}/refresh", json={"refresh_token": login["refresh_token"]})
    assert first.status_code == 200
    assert first.json()["refresh_token"] != login["refresh_token"]

    replay = await client.post(f"{API}/refresh", json={"refresh_token": login["refresh_token"]})
    assert replay.status_code == 401


async def test_logout_revokes_the_refresh_token(client, manager):
    login = (
        await client.post(
            f"{API}/login", json={"email": "manager@test.in", "password": "pass-word-1"}
        )
    ).json()
    assert (
        await client.post(f"{API}/logout", json={"refresh_token": login["refresh_token"]})
    ).status_code == 200
    r = await client.post(f"{API}/refresh", json={"refresh_token": login["refresh_token"]})
    assert r.status_code == 401


async def test_change_password_then_login_with_the_new_one(client, manager):
    r = await client.post(
        f"{API}/change-password",
        json={"current_password": "pass-word-1", "new_password": "brand-new-pass"},
        headers=manager,
    )
    assert r.status_code == 200
    old = await client.post(
        f"{API}/login", json={"email": "manager@test.in", "password": "pass-word-1"}
    )
    new = await client.post(
        f"{API}/login", json={"email": "manager@test.in", "password": "brand-new-pass"}
    )
    assert old.status_code == 401
    assert new.status_code == 200


async def test_change_password_needs_the_current_one(client, manager):
    r = await client.post(
        f"{API}/change-password",
        json={"current_password": "not-my-password", "new_password": "brand-new-pass"},
        headers=manager,
    )
    assert r.status_code == 401


async def test_forgot_password_does_not_reveal_whether_the_account_exists(client, manager):
    known = await client.post(f"{API}/forgot-password", json={"email": "manager@test.in"})
    unknown = await client.post(f"{API}/forgot-password", json={"email": "ghost@test.in"})
    assert known.json()["message"] == unknown.json()["message"]
    assert known.json()["reset_token"] is not None
    assert unknown.json()["reset_token"] is None


async def test_reset_password_flow(client, manager):
    token = (
        await client.post(f"{API}/forgot-password", json={"email": "manager@test.in"})
    ).json()["reset_token"]
    r = await client.post(
        f"{API}/reset-password", json={"reset_token": token, "new_password": "reset-pass-99"}
    )
    assert r.status_code == 200
    login = await client.post(
        f"{API}/login", json={"email": "manager@test.in", "password": "reset-pass-99"}
    )
    assert login.status_code == 200


async def test_reset_token_cannot_be_used_as_an_access_token(client, manager):
    token = (
        await client.post(f"{API}/forgot-password", json={"email": "manager@test.in"})
    ).json()["reset_token"]
    r = await client.get(f"{API}/me", headers={"Authorization": f"Bearer {token}"})
    assert r.status_code == 401
