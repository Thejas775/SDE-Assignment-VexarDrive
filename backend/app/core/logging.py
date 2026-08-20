import logging
import sys
from logging.config import dictConfig

from app.core.config import settings

CONSOLE_FORMAT = "%(asctime)s | %(levelname)-8s | %(name)s | %(message)s"


def configure_logging() -> None:
    dictConfig(
        {
            "version": 1,
            "disable_existing_loggers": False,
            "formatters": {
                "console": {"format": CONSOLE_FORMAT, "datefmt": "%Y-%m-%d %H:%M:%S"},
            },
            "handlers": {
                "console": {
                    "class": "logging.StreamHandler",
                    "formatter": "console",
                    "stream": sys.stdout,
                },
            },
            "root": {"handlers": ["console"], "level": settings.LOG_LEVEL},
            # propagate=False stops uvicorn's own handlers double-printing every line
            "loggers": {
                "uvicorn": {"handlers": ["console"], "level": settings.LOG_LEVEL, "propagate": False},
                "uvicorn.error": {"handlers": ["console"], "level": settings.LOG_LEVEL, "propagate": False},
                "uvicorn.access": {"handlers": ["console"], "level": settings.LOG_LEVEL, "propagate": False},
                "sqlalchemy.engine": {
                    "handlers": ["console"],
                    "level": "INFO" if settings.DB_ECHO else "WARNING",
                    "propagate": False,
                },
            },
        }
    )


def get_logger(name: str) -> logging.Logger:
    return logging.getLogger(name)
