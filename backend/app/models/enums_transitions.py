from app.models.enums import TripStatus

# STARTED is the moment of departure; IN_PROGRESS begins once the vehicle is
# actually moving and location pings are arriving (spec section 10).
TRIP_TRANSITIONS: dict[TripStatus, set[TripStatus]] = {
    TripStatus.SCHEDULED: {TripStatus.STARTED, TripStatus.CANCELLED},
    TripStatus.STARTED: {TripStatus.IN_PROGRESS, TripStatus.COMPLETED, TripStatus.CANCELLED},
    TripStatus.IN_PROGRESS: {TripStatus.COMPLETED, TripStatus.CANCELLED},
    TripStatus.COMPLETED: set(),
    TripStatus.CANCELLED: set(),
}

ACTIVE_TRIP_STATUSES = (TripStatus.STARTED, TripStatus.IN_PROGRESS)
OPEN_TRIP_STATUSES = (TripStatus.SCHEDULED, *ACTIVE_TRIP_STATUSES)


def can_transition(current: TripStatus, target: TripStatus) -> bool:
    return target in TRIP_TRANSITIONS[current]
