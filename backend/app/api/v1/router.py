from fastapi import APIRouter

from app.api.v1.endpoints import assignments, auth, drivers, locations, trips, vehicles

api_router = APIRouter()
api_router.include_router(auth.router)
api_router.include_router(vehicles.router)
api_router.include_router(drivers.router)
api_router.include_router(assignments.router)
api_router.include_router(trips.router)
api_router.include_router(locations.router)
