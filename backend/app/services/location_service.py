from uuid import UUID

from sqlalchemy import and_, func, select
from sqlalchemy.ext.asyncio import AsyncSession
from sqlalchemy.orm import selectinload

from app.core.exceptions import ConflictError, ForbiddenError, NotFoundError
from app.core.logging import get_logger
from app.core.ws_manager import tracking_manager
from app.models.driver import Driver
from app.models.enums import TripStatus, UserRole
from app.models.enums_transitions import ACTIVE_TRIP_STATUSES
from app.models.location import Location
from app.models.trip import Trip
from app.models.user import User
from app.models.vehicle import Vehicle
from app.schemas.location import (
    LocationBatch,
    LocationIngestResult,
    LocationResponse,
    VehiclePosition,
)

logger = get_logger(__name__)


class LocationService:
    def __init__(self, db: AsyncSession):
        self.db = db

    async def ingest(self, payload: LocationBatch, user: User) -> LocationIngestResult:
        trip = await self.db.scalar(
            select(Trip)
            .options(selectinload(Trip.vehicle), selectinload(Trip.driver).selectinload(Driver.user))
            .where(Trip.id == payload.trip_id)
        )
        if trip is None:
            raise NotFoundError("Trip not found")
        if user.role is not UserRole.FLEET_MANAGER:
            if user.driver is None or trip.driver_id != user.driver.id:
                raise ForbiddenError("This trip is not assigned to you")
        if trip.status not in ACTIVE_TRIP_STATUSES:
            raise ConflictError(f"Trip is {trip.status}; location updates are not accepted")

        # A resent batch after a flaky upload must not duplicate rows.
        existing = set(
            await self.db.scalars(
                select(Location.recorded_at).where(
                    Location.trip_id == trip.id,
                    Location.recorded_at.in_([p.recorded_at for p in payload.pings]),
                )
            )
        )
        fresh = [p for p in payload.pings if p.recorded_at not in existing]

        for ping in fresh:
            self.db.add(
                Location(
                    trip_id=trip.id,
                    vehicle_id=trip.vehicle_id,  # taken from the trip, never from the client
                    **ping.model_dump(),
                )
            )

        # First ping proves the vehicle is moving.
        if fresh and trip.status is TripStatus.STARTED:
            trip.status = TripStatus.IN_PROGRESS

        await self.db.commit()

        if fresh:
            last = fresh[-1]
            await tracking_manager.broadcast(
                {
                    "event": "position",
                    "data": VehiclePosition(
                        vehicle_id=trip.vehicle_id,
                        registration_number=trip.vehicle.registration_number,
                        trip_id=trip.id,
                        trip_number=trip.trip_number,
                        driver_name=trip.driver.user.full_name,
                        latitude=last.latitude,
                        longitude=last.longitude,
                        speed_kmph=last.speed_kmph,
                        heading=last.heading,
                        recorded_at=last.recorded_at,
                    ).model_dump(mode="json"),
                }
            )
        logger.info("location.ingested", trip_number=trip.trip_number,
                    accepted=len(fresh), duplicates=len(payload.pings) - len(fresh))
        return LocationIngestResult(
            accepted=len(fresh),
            duplicates=len(payload.pings) - len(fresh),
            trip_status=trip.status,
        )

    async def route(self, trip_id: UUID, user: User, limit: int = 1000) -> list[LocationResponse]:
        trip = await self.db.get(Trip, trip_id)
        if trip is None:
            raise NotFoundError("Trip not found")
        if user.role is not UserRole.FLEET_MANAGER:
            if user.driver is None or trip.driver_id != user.driver.id:
                raise ForbiddenError("This trip is not assigned to you")
        rows = await self.db.scalars(
            select(Location)
            .where(Location.trip_id == trip_id)
            .order_by(Location.recorded_at)
            .limit(limit)
        )
        return [LocationResponse.model_validate(r, from_attributes=True) for r in rows]

    async def latest_for_vehicle(self, vehicle_id: UUID) -> VehiclePosition:
        vehicle = await self.db.get(Vehicle, vehicle_id)
        if vehicle is None:
            raise NotFoundError("Vehicle not found")
        row = (
            await self.db.execute(
                select(Location, Trip, Driver, User)
                .join(Trip, Location.trip_id == Trip.id)
                .join(Driver, Trip.driver_id == Driver.id)
                .join(User, Driver.user_id == User.id)
                .where(Location.vehicle_id == vehicle_id)
                .order_by(Location.recorded_at.desc())
                .limit(1)
            )
        ).first()
        if row is None:
            raise NotFoundError("No location recorded for this vehicle")
        loc, trip, _, user = row
        return VehiclePosition(
            vehicle_id=vehicle_id,
            registration_number=vehicle.registration_number,
            trip_id=trip.id,
            trip_number=trip.trip_number,
            driver_name=user.full_name,
            latitude=loc.latitude,
            longitude=loc.longitude,
            speed_kmph=loc.speed_kmph,
            heading=loc.heading,
            recorded_at=loc.recorded_at,
        )

    async def live_positions(self) -> list[VehiclePosition]:
        """Latest ping per vehicle for every trip currently running."""
        newest = (
            select(
                Location.vehicle_id,
                func.max(Location.recorded_at).label("recorded_at"),
            )
            .join(Trip, Location.trip_id == Trip.id)
            .where(Trip.status.in_(ACTIVE_TRIP_STATUSES))
            .group_by(Location.vehicle_id)
            .subquery()
        )
        rows = await self.db.execute(
            select(Location, Vehicle, Trip, User)
            .join(
                newest,
                and_(
                    Location.vehicle_id == newest.c.vehicle_id,
                    Location.recorded_at == newest.c.recorded_at,
                ),
            )
            .join(Vehicle, Location.vehicle_id == Vehicle.id)
            .join(Trip, Location.trip_id == Trip.id)
            .join(Driver, Trip.driver_id == Driver.id)
            .join(User, Driver.user_id == User.id)
        )
        return [
            VehiclePosition(
                vehicle_id=v.id,
                registration_number=v.registration_number,
                trip_id=t.id,
                trip_number=t.trip_number,
                driver_name=u.full_name,
                latitude=loc.latitude,
                longitude=loc.longitude,
                speed_kmph=loc.speed_kmph,
                heading=loc.heading,
                recorded_at=loc.recorded_at,
            )
            for loc, v, t, u in rows.all()
        ]
