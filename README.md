# Fleet Management Platform

Backend, Android app and IOS App for a logistics company that runs a fleet of delivery
vehicles: vehicles, drivers, assignments, trips, live GPS tracking, maintenance,
incidents and reporting.

Repository layout:

    backend/   FastAPI + PostgreSQL, 74 REST endpoints and 1 WebSocket
    android/   Kotlin app (primary mobile platform)
    ios/       Xcode project for the Swift subset, scaffolded only

## Problem

Vehicles, drivers, trips, fuel and maintenance are tracked on paper. Nobody
knows where a vehicle is mid-route, insurance lapses go unnoticed, and the same
vehicle can be handed to two drivers for overlapping periods.

There are two roles. A fleet manager adds vehicles and drivers, assigns them to
each other, creates trips, and watches the fleet. A driver sees the vehicle and
trips assigned to them, starts and ends trips, and reports faults.

## Running it

You need Docker. Nothing else.

    cd backend
    cp .env.example .env
    docker compose up -d --build

That starts PostgreSQL 17, applies the migrations and serves the API on
http://localhost:8000. Interactive docs are at `/docs`.

Create a manager to sign in with:

    curl -X POST localhost:8000/api/v1/auth/register \
      -H 'content-type: application/json' \
      -d '{"email":"ops@fleet.in","password":"pass-word-1",
           "full_name":"Priya Nair","role":"FLEET_MANAGER"}'

For the Android app, open `android/` in Android Studio and run it. The debug
build points at `10.0.2.2:8000`, which is how the emulator reaches the host. On
a physical device, change `BASE_URL` in `app/build.gradle.kts` to your machine's
LAN address and add that address to `res/xml/network_security_config.xml`.

`backend/scripts/api_walkthrough.sh` exercises every endpoint with curl and
prints the responses. It creates its own users, so it is safe to re-run.

## Architecture

The backend is a monolith. Most rules here span entities (an assignment is legal
only if neither the vehicle nor the driver is already booked; a trip is legal
only if the driver already holds that vehicle), and splitting those across
services would turn single transactions into distributed ones for no benefit at
this size.

Inside it, dependencies point one way:

    app/api/v1/endpoints   HTTP only: status codes, request and response shapes
    app/services           business rules, the only layer that decides anything
    app/models             SQLAlchemy tables, enums, the trip state machine
    app/db                 engine, session, migration runner
    app/migrations         yoyo migrations, raw SQL

An endpoint knows nothing about SQL and a service knows nothing about HTTP,
which is why the same `TripService.start()` backs a REST call, a test, and
later a WebSocket handler without duplication.

The Android app mirrors that split: `data/remote` (Retrofit, interceptors),
`data/repository`, `ui/<feature>` with a ViewModel per screen. Dependencies are
wired by hand in `di/ServiceLocator.kt` rather than Hilt; there are about a
dozen process-scoped objects and one readable file beats an annotation
processor.

## Technology

|            |                                              |                                                                 |
| ---------- | -------------------------------------------- | --------------------------------------------------------------- |
| Backend    | FastAPI, Python 3.13                         | type hints give validation and OpenAPI docs for free            |
| DB         | PostgreSQL 17                                | `EXCLUDE USING gist` makes the overlap rule a schema constraint |
| ORM        | SQLAlchemy 2.0, async                        | GPS ingest and WebSockets are I/O bound                         |
| Driver     | psycopg 3                                    | async capable and also a yoyo backend, so one DSN serves both   |
| Migrations | yoyo                                         | plain reviewable SQL                                            |
| Auth       | bcrypt, PyJWT                                | passlib is unmaintained and breaks on modern bcrypt             |
| Android    | Kotlin, Views, Material 3                    |                                                                 |
|            | Retrofit, OkHttp, kotlinx-serialization      |                                                                 |
|            | Coroutines, ViewModel, StateFlow, Navigation |                                                                 |

## Database

Twelve tables. `users` holds identity and role; `drivers` extends it one-to-one
with licence details, so a manager cannot acquire a licence expiry and a driver
cannot exist without a login. Vehicles, assignments, trips, locations,
maintenance records, incidents, notifications, refresh tokens, fuel logs and
idempotency keys make up the rest.

Every table has a UUID primary key. Human-facing identifiers like
`registration_number`, `trip_number` and `license_number` are unique columns,
never keys, because they change: correcting a typo in a registration number must
not cascade through foreign keys. UUIDs also let an offline client generate an
id locally, and stop `/vehicles/1` being walkable.

Derived values are not stored. A driver's current vehicle is a date-bounded
query against `vehicle_assignments`, not a column, because a column can only
hold today's answer and destroys the history the spec asks for. The one
exception is `trips.distance_km`: the dashboard sums it, and both inputs are
frozen once a trip completes, so the copy cannot drift.

## API

Base path `/api/v1`, bearer token auth. Full reference at `/docs`, generated
from the same type hints that do the validation, so it cannot drift.

    auth           register, login, refresh, logout, me, change/forgot/reset password
    vehicles       CRUD, search and filters, activate/deactivate, QR code, lookup
    drivers        CRUD, activate/suspend/deactivate, history, performance
    assignments    create, list, end, cancel
    trips          create, list, start, status, complete, cancel, route
    locations      batch ingest; tracking/live; WebSocket at tracking/ws
    maintenance    CRUD, due list
    incidents      report, list, assign, resolve
    notifications  list, unread count, mark read, sweep
    fuel           CRUD, efficiency
    analytics      fleet, per vehicle, monthly
    dashboard      the section 12 metrics
    sync           replay a mobile client's offline queue

Business failures return `{"error_message": "..."}`; validation failures use
FastAPI's `{"detail": [...]}` so field-level information survives. 409 means a
conflict with existing state and the message names it, for example
`KA-01-AB-1234 is already assigned from 2026-08-17 to 2026-08-25`. Every
response carries an `x-request-id` that also appears in the logs.

Access tokens last 30 minutes; refresh tokens rotate on use and are stored
server-side as SHA-256 digests so logout can actually revoke them.

## Deployment

Containerised and verified locally.

    cd backend && docker compose up -d --build

Multi-stage build: Poetry resolves dependencies in the builder stage, the
runtime image carries only site-packages and `app/`. Dependencies are installed
before the source is copied so a code change rebuilds in seconds. The process
runs as a non-root user, `.env` is excluded from the image and configuration
arrives at runtime, and the API waits on the database healthcheck rather than
its container merely having started.
