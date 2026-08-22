"""Driver-to-vehicle assignments over a date period."""

from yoyo import step

__depends__ = {"003-VEHICLES"}

steps = [
    step(
        """
        CREATE TABLE vehicle_assignments (
        	vehicle_id UUID NOT NULL, 
        	driver_id UUID NOT NULL, 
        	start_date DATE NOT NULL, 
        	end_date DATE, 
        	status assignment_status DEFAULT 'ACTIVE' NOT NULL, 
        	notes VARCHAR(500), 
        	id UUID NOT NULL, 
        	created_at TIMESTAMP WITH TIME ZONE DEFAULT now() NOT NULL, 
        	updated_at TIMESTAMP WITH TIME ZONE DEFAULT now() NOT NULL, 
        	CONSTRAINT pk_vehicle_assignments PRIMARY KEY (id), 
        	CONSTRAINT ck_vehicle_assignments_valid_period CHECK (end_date IS NULL OR end_date >= start_date), 
        	CONSTRAINT fk_vehicle_assignments_vehicle_id_vehicles FOREIGN KEY(vehicle_id) REFERENCES vehicles (id) ON DELETE CASCADE, 
        	CONSTRAINT fk_vehicle_assignments_driver_id_drivers FOREIGN KEY(driver_id) REFERENCES drivers (id) ON DELETE CASCADE
        );
        CREATE INDEX ix_vehicle_assignments_vehicle_period ON vehicle_assignments (vehicle_id, start_date, end_date);
        CREATE INDEX ix_vehicle_assignments_driver_period ON vehicle_assignments (driver_id, start_date, end_date);
        """,
        """
        DROP TABLE IF EXISTS vehicle_assignments CASCADE;
        """,
    ),
]
