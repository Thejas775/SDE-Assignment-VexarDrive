from datetime import date, datetime, timedelta, timezone
from uuid import UUID

from sqlalchemy import func, select, update
from sqlalchemy.ext.asyncio import AsyncSession

from app.core.config import settings
from app.core.exceptions import NotFoundError
from app.core.logging import get_logger
from app.models.driver import Driver
from app.models.enums import NotificationType, UserRole, VehicleStatus
from app.models.notification import Notification
from app.models.user import User
from app.models.vehicle import Vehicle
from app.schemas.common import Page, PageParams
from app.schemas.notification import NotificationResponse, SweepResult

logger = get_logger(__name__)


class NotificationService:
    def __init__(self, db: AsyncSession):
        self.db = db

    async def notify(
        self,
        user_id: UUID,
        notification_type: NotificationType,
        title: str,
        body: str,
        *,
        entity_type: str | None = None,
        entity_id: UUID | None = None,
    ) -> None:
        """Queue a notification. Caller owns the commit."""
        self.db.add(
            Notification(
                user_id=user_id,
                notification_type=notification_type,
                title=title,
                body=body,
                related_entity_type=entity_type,
                related_entity_id=entity_id,
            )
        )

    async def notify_managers(
        self,
        notification_type: NotificationType,
        title: str,
        body: str,
        *,
        entity_type: str | None = None,
        entity_id: UUID | None = None,
    ) -> int:
        managers = list(
            await self.db.scalars(
                select(User.id).where(
                    User.role == UserRole.FLEET_MANAGER, User.is_active.is_(True)
                )
            )
        )
        for manager_id in managers:
            await self.notify(
                manager_id, notification_type, title, body,
                entity_type=entity_type, entity_id=entity_id,
            )
        return len(managers)

    async def list_for_user(
        self, params: PageParams, user: User, *, unread_only: bool = False
    ) -> Page[NotificationResponse]:
        stmt = select(Notification).where(Notification.user_id == user.id)
        if unread_only:
            stmt = stmt.where(Notification.is_read.is_(False))
        total = await self.db.scalar(
            select(func.count()).select_from(stmt.order_by(None).subquery())
        )
        rows = await self.db.scalars(
            stmt.order_by(Notification.created_at.desc())
            .offset(params.offset)
            .limit(params.page_size)
        )
        items = [NotificationResponse.model_validate(n) for n in rows]
        return Page.build(items, total or 0, params)

    async def unread_count(self, user: User) -> int:
        return await self.db.scalar(
            select(func.count())
            .select_from(Notification)
            .where(Notification.user_id == user.id, Notification.is_read.is_(False))
        ) or 0

    async def mark_read(self, notification_id: UUID, user: User) -> NotificationResponse:
        notification = await self.db.scalar(
            select(Notification).where(
                Notification.id == notification_id, Notification.user_id == user.id
            )
        )
        if notification is None:
            raise NotFoundError("Notification not found")
        if not notification.is_read:
            notification.is_read = True
            notification.read_at = datetime.now(timezone.utc)
            await self.db.commit()
        return NotificationResponse.model_validate(notification)

    async def mark_all_read(self, user: User) -> int:
        result = await self.db.execute(
            update(Notification)
            .where(Notification.user_id == user.id, Notification.is_read.is_(False))
            .values(is_read=True, read_at=datetime.now(timezone.utc))
        )
        await self.db.commit()
        return result.rowcount or 0

    async def sweep(self) -> SweepResult:
        """Generate the time-based alerts a scheduler would trigger nightly.

        Exposed as an endpoint because this deployment has no cron; the same
        function is what a scheduled job would call.
        """
        today = date.today()
        doc_cutoff = today + timedelta(days=settings.DOCUMENT_EXPIRY_WARNING_DAYS)
        counts = {"insurance": 0, "registration": 0, "license": 0, "maintenance": 0}
        created = 0

        vehicles = list(
            await self.db.scalars(
                select(Vehicle).where(Vehicle.status != VehicleStatus.INACTIVE)
            )
        )
        for vehicle in vehicles:
            if vehicle.insurance_expiry <= doc_cutoff:
                counts["insurance"] += 1
                created += await self._announce(
                    NotificationType.INSURANCE_EXPIRING,
                    f"Insurance expiring: {vehicle.registration_number}",
                    f"Insurance expires on {vehicle.insurance_expiry}.",
                    "vehicle", vehicle.id, today,
                )
            if vehicle.registration_expiry <= doc_cutoff:
                counts["registration"] += 1
                created += await self._announce(
                    NotificationType.REGISTRATION_EXPIRING,
                    f"Registration expiring: {vehicle.registration_number}",
                    f"Registration expires on {vehicle.registration_expiry}.",
                    "vehicle", vehicle.id, today,
                )

        drivers = list(
            await self.db.scalars(
                select(Driver).where(Driver.license_expiry <= doc_cutoff)
            )
        )
        for driver in drivers:
            counts["license"] += 1
            user = await self.db.get(User, driver.user_id)
            created += await self._announce(
                NotificationType.LICENSE_EXPIRING,
                f"Licence expiring: {user.full_name if user else driver.license_number}",
                f"Licence {driver.license_number} expires on {driver.license_expiry}.",
                "driver", driver.id, today,
            )

        from app.services.maintenance_service import MaintenanceService

        for item in await MaintenanceService(self.db).due():
            counts["maintenance"] += 1
            created += await self._announce(
                NotificationType.MAINTENANCE_DUE,
                f"Maintenance due: {item.vehicle.registration_number}",
                ", ".join(item.reasons),
                "vehicle", item.vehicle.id, today,
            )

        await self.db.commit()
        logger.info("notification.sweep", **counts, created=created)
        return SweepResult(
            insurance_expiring=counts["insurance"],
            registration_expiring=counts["registration"],
            license_expiring=counts["license"],
            maintenance_due=counts["maintenance"],
            total_created=created,
        )

    async def _announce(
        self,
        notification_type: NotificationType,
        title: str,
        body: str,
        entity_type: str,
        entity_id: UUID,
        today: date,
    ) -> int:
        """Notify every manager once per entity per day."""
        already = await self.db.scalar(
            select(func.count())
            .select_from(Notification)
            .where(
                Notification.notification_type == notification_type,
                Notification.related_entity_id == entity_id,
                func.date(Notification.created_at) == today,
            )
        )
        if already:
            return 0
        return await self.notify_managers(
            notification_type, title, body, entity_type=entity_type, entity_id=entity_id
        )
