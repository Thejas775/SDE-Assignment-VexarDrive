from pathlib import Path

from yoyo import get_backend, read_migrations

from app.core.config import settings
from app.core.logging import get_logger

logger = get_logger(__name__)

MIGRATIONS_DIR = Path(__file__).resolve().parent.parent / "migrations"


def apply_migrations() -> None:
    backend = get_backend(settings.yoyo_url)
    migrations = read_migrations(str(MIGRATIONS_DIR))
    with backend.lock():
        pending = backend.to_apply(migrations)
        if not pending:
            logger.info("migrations.up_to_date", count=len(migrations))
            return
        logger.info("migrations.applying", pending=[m.id for m in pending])
        backend.apply_migrations(pending)
        logger.info("migrations.applied", count=len(pending))


def rollback_migrations() -> None:
    backend = get_backend(settings.yoyo_url)
    migrations = read_migrations(str(MIGRATIONS_DIR))
    with backend.lock():
        backend.rollback_migrations(backend.to_rollback(migrations))
