# Fleet Management — iOS

The secondary platform for the Fleet Management assignment: a native SwiftUI app
covering **Login**, **Vehicle List** and **Trip Details**, against the same
FastAPI backend as the Android app.

Native Swift only — no cross-platform framework.

---

## Requirements

| | |
| --- | --- |
| Xcode | 26 or newer (built with 27.0) |
| Deployment target | iOS 16.0 |
| Dependencies | none — URLSession and Codable only |

## Running it

1. **Start the backend** from the repository root:

   ```bash
   cd backend
   cp .env.example .env
   docker compose up -d --build
   curl localhost:8000/health          # {"status":"ok"}
   ```

2. **Create a fleet manager**, if the database is empty:

   ```bash
   curl -X POST localhost:8000/api/v1/auth/register \
     -H 'content-type: application/json' \
     -d '{"email":"ops@fleet.in","password":"pass-word-1",
          "full_name":"Priya Nair","role":"FLEET_MANAGER"}'
   ```

3. **Open and run** `Fleet Management Test.xcodeproj`, scheme
   *Fleet Management Test*, on any iOS 16+ simulator.

The vehicle list is a fleet-manager endpoint. Signing in as a `DRIVER` will
reach the app but the list returns 403.

### Setting the base URL

One line, in `Fleet Management Test/Core/AppConfig.swift`:

```swift
static let apiBaseURL = URL(string: "http://localhost:8000/api/v1/")!
```

| Target | Base URL |
| --- | --- |
| Simulator, backend on the same Mac | `http://localhost:8000/api/v1/` |
| Physical device | `http://<mac-lan-ip>:8000/api/v1/` |

`localhost` works on the Simulator because it shares the host's network stack.

### HTTP in development

The dev backend is plain HTTP, which App Transport Security blocks. The
exception lives in `Config/Info.plist` and is wired into the **Debug
configuration only** — it allows insecure loads to `localhost` and enables
`NSAllowsLocalNetworking` for a device hitting the Mac's LAN IP.
`NSAllowsArbitraryLoads` is deliberately not used. Release builds carry no ATS
exception at all.

## Running the tests

```bash
# simulator
xcodebuild test \
  -project "Fleet Management Test.xcodeproj" \
  -scheme "Fleet Management Test" \
  -destination 'platform=iOS Simulator,name=iPhone 16'

# or a connected device
xcodebuild test \
  -project "Fleet Management Test.xcodeproj" \
  -scheme "Fleet Management Test" \
  -destination 'id=<device-udid>' -allowProvisioningUpdates
```

70 tests, all passing. Every one stubs the network with `URLProtocol`; none opens
a socket.

## Layout

```
Fleet Management Test/
├── App/           entry point, composition root, Login-vs-app routing
├── Core/          APIClient, APIError, KeychainStore, JSON coding, config
├── Models/        Vehicle, Trip, auth models, generic Page<T>
└── Features/
    ├── Login/     LoginView + LoginViewModel
    ├── Auth/      AuthRepository, SessionController
    ├── Vehicles/  VehicleListView + VehicleListViewModel + repository
    ├── Trips/     TripListView + TripListViewModel + repository
    └── TripDetail/TripDetailView + TripDetailViewModel + route map
```

MVVM: View → ViewModel → Repository → APIClient, mirroring the Android app.

## Apple platform notes

Everything is first-party; there is not a single third-party dependency.

| Concern | What it uses |
| --- | --- |
| UI | SwiftUI, `NavigationStack`, `List`, `.searchable`, `.refreshable` |
| Concurrency | Swift `async`/`await`, with an `actor` guarding token refresh |
| Networking | `URLSession` and `Codable` - no Alamofire, no third-party JSON |
| Secure storage | Keychain Services (`kSecClassGenericPassword`) from the Security framework |
| Maps | MapKit, `MKPolyline` through `UIViewRepresentable` |
| Tests | XCTest and XCUITest, with `URLProtocol` stubbing the network |

## What is implemented

**Login** — client-side validation before any request; email lowercased and
trimmed; password reveal toggle; double submit guarded in the view model, not
only by disabling the button; a 401 shown as a banner rather than attached to a
field (marking the password would confirm the email exists); a 422 attached to
the field named by the tail of `loc`; session persisted across launches.

**Vehicle List** — infinite scroll stopping at the last page, search debounced
300 ms, status filter, pull to refresh, expiring-document marker, empty state
distinct from loading. Duplicate page requests are suppressed, rows already on
screen are filtered out of later pages, and a response for a query the user has
moved past is discarded.

**Trip Details** — trip and route fetched concurrently; every field a
`SCHEDULED` trip lacks is optional and its section is hidden rather than shown
with dashes; `distance_km` printed exactly as the server sent it; an empty route
is an empty state, not a spinner; 403 has its own message and no Retry.

**Auth** — tokens in the Keychain (`kSecClassGenericPassword`,
`kSecAttrAccessibleAfterFirstUnlock`), never `UserDefaults`. Refresh is
serialised through an actor: three simultaneous 401s produce one refresh, and
the others reuse its result. Rotated tokens are persisted before the retry. One
retry per request. A rejected refresh wipes the Keychain and returns to Login.

**Bonus** — MapKit polyline of the route (`MKMapView` via
`UIViewRepresentable`, because `MapPolyline` is iOS 17), and a trip list to
reach Trip Details from.

## What is not implemented

- Everything outside the three screens: vehicle create/edit, driver management,
  assignments, trip creation, documents, maintenance.
- Vehicle detail. Tapping a row does nothing; the brief marks it optional.
- Offline caching. The app talks to the network on every load.
- Driver-role screens. A driver can sign in, but the app is built around the
  fleet-manager view.
- `expiring_documents`, `vehicle_type` and `fuel_type` filters — the API
  supports them and `Endpoint` is ready, but only `search` and `status` are
  surfaced in the UI.

## Verification

Run on a physical device (iPhone, iOS 27):

- **86 unit tests, 0 failures** - network stubbed with `URLProtocol`
- **9 UI tests, 0 failures** - XCUITest driving the real app against the live
  backend: sign in, wrong password, search, status filter, infinite scroll,
  trip details, and a terminate-and-relaunch proving the session persists

`Screenshots/` is produced by that UI run, so the images are of the real app on
real hardware showing real fleet data rather than mock-ups.

The API contract was also exercised against a live backend, and the app's own
`Codable` models were used to decode the captured responses. Confirmed against
the running server rather than assumed:

- refresh tokens really do rotate — replaying the previous one returns
  `401 "Refresh token is no longer valid"`, which is what makes serialising
  refreshes necessary rather than merely tidy
- decimals arrive as strings (`"346.00"` survives a round trip unchanged)
- `recorded_at` has no fractional seconds while `received_at` does, in the same
  object
- a non-running trip carries ten null fields and still decodes
- `/trips/{id}/route` is a plain array, empty for a trip that never started
- every live error shape maps correctly, including a 422 carrying `input` and
  `ctx` fields beyond those documented, and the 403s
  (`"This trip is not assigned to you"`)

Two things worth knowing if you run the UI tests:

- iOS offers to save the password after a sign-in, in a SpringBoard alert that
  covers the app. Until it is dismissed every element beneath it reports itself
  unhittable and the next tap is swallowed dismissing it - which is why the
  tests dismiss it explicitly rather than tapping blind.
- `typeText` drops the last character on a device often enough to matter, so
  text is entered a character at a time and verified.
