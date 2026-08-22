import datetime
import uuid

from sqlalchemy import Column, DateTime, MetaData, Uuid, func
from sqlalchemy.orm import declarative_base

NAMING_CONVENTION = {
    "ix": "ix_%(table_name)s_%(column_0_N_name)s",
    "uq": "uq_%(table_name)s_%(column_0_N_name)s",
    "ck": "ck_%(table_name)s_%(constraint_name)s",
    "fk": "fk_%(table_name)s_%(column_0_name)s_%(referred_table_name)s",
    "pk": "pk_%(table_name)s",
}

Base = declarative_base(metadata=MetaData(naming_convention=NAMING_CONVENTION))


class UUIDPrimaryKeyMixin:
    id = Column(Uuid, primary_key=True, default=uuid.uuid4)


class TimestampMixin:
    created_at = Column(
        DateTime(timezone=True), nullable=False, server_default=func.now(),
        default=datetime.datetime.now,
    )
    updated_at = Column(
        DateTime(timezone=True), nullable=False, server_default=func.now(),
        default=datetime.datetime.now, onupdate=datetime.datetime.now,
    )
