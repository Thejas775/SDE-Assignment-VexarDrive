"""Trips plus their GPS breadcrumb trail."""

from yoyo import step

__depends__ = {"004-VEHICLE-ASSIGNMENTS"}

steps = [
    step(
        """
        CREATE TABLE trips (
        	trip_number VARCHAR(20) NOT NULL, 
        	vehicle_id UUID NOT NULL, 
        	driver_id UUID NOT NULL, 
        	source VARCHAR(200) NOT NULL, 
        	destination VARCHAR(200) NOT NULL, 
        	scheduled_start TIMESTAMP WITH TIME ZONE NOT NULL, 
        	scheduled_end TIMESTAMP WITH TIME ZONE NOT NULL, 
        	status trip_status DEFAULT 'SCHEDULED' NOT NULL, 
        	actual_start TIMESTAMP WITH TIME ZONE, 
        	actual_end TIMESTAMP WITH TIME ZONE, 
        	start_odometer INTEGER, 
        	end_odometer INTEGER, 
        	start_latitude NUMERIC(9, 6), 
        	start_longitude NUMERIC(9, 6), 
        	end_latitude NUMERIC(9, 6), 
        	end_longitude NUMERIC(9, 6), 
        	distance_km NUMERIC(10, 2), 
        	notes TEXT, 
        	id UUID NOT NULL, 
        	created_at TIMESTAMP WITH TIME ZONE DEFAULT now() NOT NULL, 
        	updated_at TIMESTAMP WITH TIME ZONE DEFAULT now() NOT NULL, 
        	CONSTRAINT pk_trips PRIMARY KEY (id), 
        	CONSTRAINT ck_trips_odometer_not_decreasing CHECK (end_odometer IS NULL OR start_odometer IS NULL OR end_odometer >= start_odometer), 
        	CONSTRAINT ck_trips_schedule_ordered CHECK (scheduled_end > scheduled_start), 
        	CONSTRAINT uq_trips_trip_number UNIQUE (trip_number), 
        	CONSTRAINT fk_trips_vehicle_id_vehicles FOREIGN KEY(vehicle_id) REFERENCES vehicles (id), 
        	CONSTRAINT fk_trips_driver_id_drivers FOREIGN KEY(driver_id) REFERENCES drivers (id)
        );
        CREATE INDEX ix_trips_vehicle_status ON trips (vehicle_id, status);
        CREATE INDEX ix_trips_driver_status ON trips (driver_id, status);
        CREATE INDEX ix_trips_status ON trips (status);
        CREATE TABLE locations (
        	trip_id UUID NOT NULL, 
        	vehicle_id UUID NOT NULL, 
        	latitude NUMERIC(9, 6) NOT NULL, 
        	longitude NUMERIC(9, 6) NOT NULL, 
        	speed_kmph NUMERIC(6, 2), 
        	heading NUMERIC(5, 2), 
        	accuracy_m NUMERIC(7, 2), 
        	recorded_at TIMESTAMP WITH TIME ZONE NOT NULL, 
        	received_at TIMESTAMP WITH TIME ZONE DEFAULT now() NOT NULL, 
        	id UUID NOT NULL, 
        	CONSTRAINT pk_locations PRIMARY KEY (id), 
        	CONSTRAINT ck_locations_latitude_range CHECK (latitude BETWEEN -90 AND 90), 
        	CONSTRAINT ck_locations_longitude_range CHECK (longitude BETWEEN -180 AND 180), 
        	CONSTRAINT fk_locations_trip_id_trips FOREIGN KEY(trip_id) REFERENCES trips (id) ON DELETE CASCADE, 
        	CONSTRAINT fk_locations_vehicle_id_vehicles FOREIGN KEY(vehicle_id) REFERENCES vehicles (id) ON DELETE CASCADE
        );
        CREATE INDEX ix_locations_vehicle_recorded_at ON locations (vehicle_id, recorded_at);
        CREATE INDEX ix_locations_trip_recorded_at ON locations (trip_id, recorded_at);
        """,
        """
        DROP TABLE IF EXISTS locations CASCADE;
        DROP TABLE IF EXISTS trips CASCADE;
        """,
    ),
]
