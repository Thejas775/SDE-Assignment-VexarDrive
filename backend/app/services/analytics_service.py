from datetime import date, datetime, time, timedelta, timezone
from decimal import ROUND_HALF_UP, Decimal

from sqlalchemy import func, literal_column, select
from sqlalchemy.ext.asyncio import AsyncSession

from app.models.enums import TripStatus, VehicleStatus
from app.models.fuel import FuelLog
from app.models.maintenance import MaintenanceRecord
from app.models.trip import Trip
from app.models.vehicle import Vehicle
from app.schemas.analytics import (
    FleetAnalytics,
    MonthlyPoint,
    Period,
    VehicleAnalytics,
)

TWO_DP = Decimal("0.01")


def _round(value) -> Decimal:
    return Decimal(value or 0).quantize(TWO_DP, rounding=ROUND_HALF_UP)


class AnalyticsService:
    def __init__(self, db: AsyncSession):
        self.db = db

    async def fleet(
        self, date_from: date, date_to: date, top: int = 5
    ) -> FleetAnalytics:
        days = (date_to - date_from).days + 1
        rows = await self.per_vehicle(date_from, date_to)

        distance = sum((r.distance_km for r in rows), Decimal("0"))
        maintenance = sum((r.maintenance_cost for r in rows), Decimal("0"))
        fuel = sum((r.fuel_cost for r in rows), Decimal("0"))
        total_cost = maintenance + fuel
        utilisation = (
            sum((r.utilization_percent for r in rows), Decimal("0")) / len(rows)
            if rows
            else Decimal("0")
        )
        rated = [r for r in rows if r.cost_per_km is not None]

        return FleetAnalytics(
            period=Period(date_from=date_from, date_to=date_to, days=days),
            vehicles=len(rows),
            trips=sum(r.trips for r in rows),
            distance_km=_round(distance),
            maintenance_cost=_round(maintenance),
            fuel_cost=_round(fuel),
            total_cost=_round(total_cost),
            cost_per_km=_round(total_cost / distance) if distance else None,
            average_utilization_percent=_round(utilisation),
            top_vehicles_by_distance=sorted(
                rows, key=lambda r: r.distance_km, reverse=True
            )[:top],
            least_efficient_by_cost_per_km=sorted(
                rated, key=lambda r: r.cost_per_km, reverse=True
            )[:top],
        )

    async def per_vehicle(self, date_from: date, date_to: date) -> list[VehicleAnalytics]:
        start = datetime.combine(date_from, time.min, tzinfo=timezone.utc)
        end = datetime.combine(date_to, time.max, tzinfo=timezone.utc)
        days = (date_to - date_from).days + 1

        vehicles = list(
            await self.db.scalars(
                select(Vehicle)
                .where(Vehicle.status != VehicleStatus.INACTIVE)
                .order_by(Vehicle.registration_number)
            )
        )

        trip_stats = dict(
            (row[0], row[1:])
            for row in (
                await self.db.execute(
                    select(
                        Trip.vehicle_id,
                        func.count(Trip.id),
                        func.coalesce(func.sum(Trip.distance_km), 0),
                        func.count(func.distinct(func.date(Trip.actual_start))),
                    )
                    .where(
                        Trip.status == TripStatus.COMPLETED,
                        Trip.actual_end >= start,
                        Trip.actual_end <= end,
                    )
                    .group_by(Trip.vehicle_id)
                )
            ).all()
        )

        maintenance = dict(
            (
                await self.db.execute(
                    select(
                        MaintenanceRecord.vehicle_id,
                        func.coalesce(func.sum(MaintenanceRecord.cost), 0),
                    )
                    .where(
                        MaintenanceRecord.service_date >= date_from,
                        MaintenanceRecord.service_date <= date_to,
                    )
                    .group_by(MaintenanceRecord.vehicle_id)
                )
            ).all()
        )

        fuel = dict(
            (row[0], row[1:])
            for row in (
                await self.db.execute(
                    select(
                        FuelLog.vehicle_id,
                        func.coalesce(func.sum(FuelLog.cost), 0),
                        func.coalesce(func.sum(FuelLog.quantity_litres), 0),
                    )
                    .where(FuelLog.fuel_date >= date_from, FuelLog.fuel_date <= date_to)
                    .group_by(FuelLog.vehicle_id)
                )
            ).all()
        )

        out: list[VehicleAnalytics] = []
        for vehicle in vehicles:
            trips, distance, active_days = trip_stats.get(vehicle.id, (0, Decimal("0"), 0))
            fuel_cost, litres = fuel.get(vehicle.id, (Decimal("0"), Decimal("0")))
            maint_cost = maintenance.get(vehicle.id, Decimal("0"))
            distance = Decimal(distance or 0)
            total_cost = Decimal(maint_cost or 0) + Decimal(fuel_cost or 0)
            out.append(
                VehicleAnalytics(
                    vehicle_id=vehicle.id,
                    registration_number=vehicle.registration_number,
                    make=vehicle.make,
                    model=vehicle.model,
                    trips=trips,
                    distance_km=_round(distance),
                    active_days=active_days,
                    utilization_percent=_round(Decimal(active_days) * 100 / days),
                    maintenance_cost=_round(maint_cost),
                    fuel_cost=_round(fuel_cost),
                    fuel_litres=_round(litres),
                    km_per_litre=_round(distance / litres) if litres else None,
                    cost_per_km=_round(total_cost / distance) if distance else None,
                )
            )
        return out

    async def monthly(self, months: int = 6) -> list[MonthlyPoint]:
        today = date.today()
        # Exact month arithmetic; subtracting 31-day chunks overshoots.
        absolute_month = today.year * 12 + (today.month - 1) - (months - 1)
        first = date(absolute_month // 12, absolute_month % 12 + 1, 1)

        # literal_column keeps the format string inline: as a bound parameter it
        # renders differently in SELECT and GROUP BY, and Postgres then refuses
        # to match the two expressions.
        fmt = literal_column("'YYYY-MM'")
        trip_month = func.to_char(Trip.actual_end, fmt)
        maint_month = func.to_char(MaintenanceRecord.service_date, fmt)
        fuel_month = func.to_char(FuelLog.fuel_date, fmt)

        trips = dict(
            (row[0], row[1:])
            for row in (
                await self.db.execute(
                    select(
                        trip_month,
                        func.count(Trip.id),
                        func.coalesce(func.sum(Trip.distance_km), 0),
                    )
                    .where(
                        Trip.status == TripStatus.COMPLETED,
                        Trip.actual_end
                        >= datetime.combine(first, time.min, tzinfo=timezone.utc),
                    )
                    .group_by(trip_month)
                )
            ).all()
        )
        maintenance = dict(
            (
                await self.db.execute(
                    select(maint_month, func.coalesce(func.sum(MaintenanceRecord.cost), 0))
                    .where(MaintenanceRecord.service_date >= first)
                    .group_by(maint_month)
                )
            ).all()
        )
        fuel = dict(
            (
                await self.db.execute(
                    select(fuel_month, func.coalesce(func.sum(FuelLog.cost), 0))
                    .where(FuelLog.fuel_date >= first)
                    .group_by(fuel_month)
                )
            ).all()
        )

        points: list[MonthlyPoint] = []
        cursor = first
        while cursor <= today:
            key = cursor.strftime("%Y-%m")
            count, distance = trips.get(key, (0, Decimal("0")))
            points.append(
                MonthlyPoint(
                    month=key,
                    trips=count,
                    distance_km=_round(distance),
                    maintenance_cost=_round(maintenance.get(key, 0)),
                    fuel_cost=_round(fuel.get(key, 0)),
                )
            )
            cursor = (cursor.replace(day=28) + timedelta(days=4)).replace(day=1)
        return points
