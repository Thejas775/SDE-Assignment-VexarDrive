"""Postgres enum types shared across the fleet schema."""

from yoyo import step

__depends__ = set()

steps = [
    step(
        """
        CREATE TYPE driver_status AS ENUM ('ACTIVE', 'INACTIVE', 'SUSPENDED');
        CREATE TYPE incident_severity AS ENUM ('LOW', 'MEDIUM', 'HIGH', 'CRITICAL');
        CREATE TYPE incident_status AS ENUM ('OPEN', 'IN_PROGRESS', 'RESOLVED');
        CREATE TYPE maintenance_type AS ENUM ('OIL_CHANGE', 'BRAKE_SERVICE', 'TYRE_REPLACEMENT', 'ENGINE_SERVICE', 'GENERAL_INSPECTION', 'OTHER');
        CREATE TYPE notification_type AS ENUM ('MAINTENANCE_DUE', 'INSURANCE_EXPIRING', 'REGISTRATION_EXPIRING', 'LICENSE_EXPIRING', 'TRIP_ASSIGNED', 'TRIP_COMPLETED', 'INCIDENT_REPORTED', 'INCIDENT_RESOLVED');
        CREATE TYPE trip_status AS ENUM ('SCHEDULED', 'STARTED', 'IN_PROGRESS', 'COMPLETED', 'CANCELLED');
        CREATE TYPE user_role AS ENUM ('FLEET_MANAGER', 'DRIVER');
        CREATE TYPE vehicle_type AS ENUM ('TRUCK', 'VAN', 'CAR', 'PICKUP', 'BUS', 'TWO_WHEELER', 'TRAILER');
        CREATE TYPE fuel_type AS ENUM ('PETROL', 'DIESEL', 'CNG', 'LPG', 'ELECTRIC', 'HYBRID');
        CREATE TYPE vehicle_status AS ENUM ('AVAILABLE', 'ON_TRIP', 'IN_MAINTENANCE', 'INACTIVE');
        CREATE TYPE assignment_status AS ENUM ('ACTIVE', 'COMPLETED', 'CANCELLED');
        """,
        """
        DROP TYPE IF EXISTS driver_status CASCADE;
        DROP TYPE IF EXISTS incident_severity CASCADE;
        DROP TYPE IF EXISTS incident_status CASCADE;
        DROP TYPE IF EXISTS maintenance_type CASCADE;
        DROP TYPE IF EXISTS notification_type CASCADE;
        DROP TYPE IF EXISTS trip_status CASCADE;
        DROP TYPE IF EXISTS user_role CASCADE;
        DROP TYPE IF EXISTS vehicle_type CASCADE;
        DROP TYPE IF EXISTS fuel_type CASCADE;
        DROP TYPE IF EXISTS vehicle_status CASCADE;
        DROP TYPE IF EXISTS assignment_status CASCADE;
        """,
    ),
]
