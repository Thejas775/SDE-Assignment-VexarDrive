"""Fuel purchases, the input for efficiency and cost-per-kilometre reporting."""

from yoyo import step

__depends__ = {"009-TRIP-NUMBER-SEQUENCE"}

steps = [
    step(
        """
        CREATE TABLE fuel_logs (
            id UUID NOT NULL,
            vehicle_id UUID NOT NULL,
            driver_id UUID,
            trip_id UUID,
            fuel_date DATE NOT NULL,
            quantity_litres NUMERIC(8, 2) NOT NULL,
            cost NUMERIC(12, 2) NOT NULL,
            odometer INTEGER NOT NULL,
            full_tank BOOLEAN DEFAULT true NOT NULL,
            station VARCHAR(150),
            notes VARCHAR(500),
            created_by_id UUID,
            created_at TIMESTAMP WITH TIME ZONE DEFAULT now() NOT NULL,
            updated_at TIMESTAMP WITH TIME ZONE DEFAULT now() NOT NULL,
            CONSTRAINT pk_fuel_logs PRIMARY KEY (id),
            CONSTRAINT ck_fuel_logs_quantity_positive CHECK (quantity_litres > 0),
            CONSTRAINT ck_fuel_logs_cost_non_negative CHECK (cost >= 0),
            CONSTRAINT ck_fuel_logs_odometer_non_negative CHECK (odometer >= 0),
            CONSTRAINT fk_fuel_logs_vehicle_id_vehicles
                FOREIGN KEY (vehicle_id) REFERENCES vehicles (id) ON DELETE CASCADE,
            CONSTRAINT fk_fuel_logs_driver_id_drivers
                FOREIGN KEY (driver_id) REFERENCES drivers (id) ON DELETE SET NULL,
            CONSTRAINT fk_fuel_logs_trip_id_trips
                FOREIGN KEY (trip_id) REFERENCES trips (id) ON DELETE SET NULL,
            CONSTRAINT fk_fuel_logs_created_by_id_users
                FOREIGN KEY (created_by_id) REFERENCES users (id) ON DELETE SET NULL
        );
        CREATE INDEX ix_fuel_logs_vehicle_fuel_date ON fuel_logs (vehicle_id, fuel_date);
        """,
        """
        DROP TABLE IF EXISTS fuel_logs CASCADE;
        """,
    ),
]
