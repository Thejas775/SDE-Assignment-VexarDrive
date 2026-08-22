"""Maintenance history, incident reports and user notifications."""

from yoyo import step

__depends__ = {"005-TRIPS"}

steps = [
    step(
        """
        CREATE TABLE maintenance_records (
        	vehicle_id UUID NOT NULL, 
        	maintenance_type maintenance_type NOT NULL, 
        	description TEXT NOT NULL, 
        	service_date DATE NOT NULL, 
        	cost NUMERIC(12, 2) DEFAULT '0' NOT NULL, 
        	odometer INTEGER NOT NULL, 
        	next_service_date DATE, 
        	next_service_mileage INTEGER, 
        	performed_by VARCHAR(150), 
        	created_by_id UUID, 
        	id UUID NOT NULL, 
        	created_at TIMESTAMP WITH TIME ZONE DEFAULT now() NOT NULL, 
        	updated_at TIMESTAMP WITH TIME ZONE DEFAULT now() NOT NULL, 
        	CONSTRAINT pk_maintenance_records PRIMARY KEY (id), 
        	CONSTRAINT ck_maintenance_records_cost_non_negative CHECK (cost >= 0), 
        	CONSTRAINT ck_maintenance_records_odometer_non_negative CHECK (odometer >= 0), 
        	CONSTRAINT fk_maintenance_records_vehicle_id_vehicles FOREIGN KEY(vehicle_id) REFERENCES vehicles (id) ON DELETE CASCADE, 
        	CONSTRAINT fk_maintenance_records_created_by_id_users FOREIGN KEY(created_by_id) REFERENCES users (id) ON DELETE SET NULL
        );
        CREATE INDEX ix_maintenance_records_vehicle_service_date ON maintenance_records (vehicle_id, service_date);
        CREATE TABLE incidents (
        	vehicle_id UUID NOT NULL, 
        	trip_id UUID, 
        	reported_by_id UUID NOT NULL, 
        	assigned_to_id UUID, 
        	title VARCHAR(200) NOT NULL, 
        	description TEXT NOT NULL, 
        	severity incident_severity NOT NULL, 
        	status incident_status DEFAULT 'OPEN' NOT NULL, 
        	reported_at TIMESTAMP WITH TIME ZONE DEFAULT now() NOT NULL, 
        	resolved_at TIMESTAMP WITH TIME ZONE, 
        	resolution_notes TEXT, 
        	id UUID NOT NULL, 
        	created_at TIMESTAMP WITH TIME ZONE DEFAULT now() NOT NULL, 
        	updated_at TIMESTAMP WITH TIME ZONE DEFAULT now() NOT NULL, 
        	CONSTRAINT pk_incidents PRIMARY KEY (id), 
        	CONSTRAINT fk_incidents_vehicle_id_vehicles FOREIGN KEY(vehicle_id) REFERENCES vehicles (id) ON DELETE CASCADE, 
        	CONSTRAINT fk_incidents_trip_id_trips FOREIGN KEY(trip_id) REFERENCES trips (id) ON DELETE SET NULL, 
        	CONSTRAINT fk_incidents_reported_by_id_users FOREIGN KEY(reported_by_id) REFERENCES users (id), 
        	CONSTRAINT fk_incidents_assigned_to_id_users FOREIGN KEY(assigned_to_id) REFERENCES users (id) ON DELETE SET NULL
        );
        CREATE INDEX ix_incidents_vehicle_status ON incidents (vehicle_id, status);
        CREATE INDEX ix_incidents_status_severity ON incidents (status, severity);
        CREATE TABLE notifications (
        	user_id UUID NOT NULL, 
        	notification_type notification_type NOT NULL, 
        	title VARCHAR(200) NOT NULL, 
        	body TEXT NOT NULL, 
        	is_read BOOLEAN DEFAULT false NOT NULL, 
        	read_at TIMESTAMP WITH TIME ZONE, 
        	related_entity_type VARCHAR(50), 
        	related_entity_id UUID, 
        	id UUID NOT NULL, 
        	created_at TIMESTAMP WITH TIME ZONE DEFAULT now() NOT NULL, 
        	updated_at TIMESTAMP WITH TIME ZONE DEFAULT now() NOT NULL, 
        	CONSTRAINT pk_notifications PRIMARY KEY (id), 
        	CONSTRAINT fk_notifications_user_id_users FOREIGN KEY(user_id) REFERENCES users (id) ON DELETE CASCADE
        );
        CREATE INDEX ix_notifications_user_is_read ON notifications (user_id, is_read);
        """,
        """
        DROP TABLE IF EXISTS notifications CASCADE;
        DROP TABLE IF EXISTS incidents CASCADE;
        DROP TABLE IF EXISTS maintenance_records CASCADE;
        """,
    ),
]
