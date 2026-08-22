import asyncio
from typing import Any

from fastapi import WebSocket

from app.core.logging import get_logger

logger = get_logger(__name__)


class ConnectionManager:
    """In-memory fan-out of live position updates to connected dashboards.

    Single-process only: with more than one uvicorn worker each process holds
    its own set, so a ping ingested by worker A never reaches a socket held by
    worker B. Redis pub/sub is the fix when that day comes.
    """

    def __init__(self) -> None:
        self._connections: set[WebSocket] = set()
        self._lock = asyncio.Lock()

    async def connect(self, websocket: WebSocket) -> None:
        await websocket.accept()
        async with self._lock:
            self._connections.add(websocket)
        logger.info("ws.connected", clients=len(self._connections))

    async def disconnect(self, websocket: WebSocket) -> None:
        async with self._lock:
            self._connections.discard(websocket)
        logger.info("ws.disconnected", clients=len(self._connections))

    async def broadcast(self, message: dict[str, Any]) -> None:
        async with self._lock:
            targets = list(self._connections)
        dead = []
        for ws in targets:
            try:
                await ws.send_json(message)
            except Exception:
                dead.append(ws)
        if dead:
            async with self._lock:
                self._connections.difference_update(dead)

    @property
    def client_count(self) -> int:
        return len(self._connections)


tracking_manager = ConnectionManager()
