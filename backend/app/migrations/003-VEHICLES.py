"""Fleet vehicles."""

from yoyo import step

__depends__ = {"002-USERS-AND-DRIVERS"}

steps = [
    step(
        """
        CREATE TABLE vehicles (
        	registration_number VARCHAR(20) NOT NULL, 
        	vehicle_type vehicle_type NOT NULL, 
        	make VARCHAR(50) NOT NULL, 
        	model VARCHAR(50) NOT NULL, 
        	year SMALLINT NOT NULL, 
        	fuel_type fuel_type NOT NULL, 
        	current_mileage INTEGER DEFAULT '0' NOT NULL, 
        	status vehicle_status DEFAULT 'AVAILABLE' NOT NULL, 
        	insurance_expiry DATE NOT NULL, 
        	registration_expiry DATE NOT NULL, 
        	id UUID NOT NULL, 
        	created_at TIMESTAMP WITH TIME ZONE DEFAULT now() NOT NULL, 
        	updated_at TIMESTAMP WITH TIME ZONE DEFAULT now() NOT NULL, 
        	CONSTRAINT pk_vehicles PRIMARY KEY (id), 
        	CONSTRAINT ck_vehicles_year_range CHECK (year >= 1900 AND year <= 2100), 
        	CONSTRAINT ck_vehicles_mileage_non_negative CHECK (current_mileage >= 0), 
        	CONSTRAINT uq_vehicles_registration_number UNIQUE (registration_number)
        );
        CREATE INDEX ix_vehicles_status ON vehicles (status);
        """,
        """
        DROP TABLE IF EXISTS vehicles CASCADE;
        """,
    ),
]
