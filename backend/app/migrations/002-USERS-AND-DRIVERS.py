"""Identity (users) and the driver profile that extends it."""

from yoyo import step

__depends__ = {"001-ENUM-TYPES"}

steps = [
    step(
        """
        CREATE TABLE users (
        	email VARCHAR(255) NOT NULL, 
        	hashed_password VARCHAR(255) NOT NULL, 
        	full_name VARCHAR(120) NOT NULL, 
        	phone_number VARCHAR(20), 
        	role user_role NOT NULL, 
        	is_active BOOLEAN DEFAULT true NOT NULL, 
        	id UUID NOT NULL, 
        	created_at TIMESTAMP WITH TIME ZONE DEFAULT now() NOT NULL, 
        	updated_at TIMESTAMP WITH TIME ZONE DEFAULT now() NOT NULL, 
        	CONSTRAINT pk_users PRIMARY KEY (id), 
        	CONSTRAINT uq_users_email UNIQUE (email)
        );
        CREATE TABLE drivers (
        	user_id UUID NOT NULL, 
        	license_number VARCHAR(30) NOT NULL, 
        	license_expiry DATE NOT NULL, 
        	status driver_status DEFAULT 'ACTIVE' NOT NULL, 
        	id UUID NOT NULL, 
        	created_at TIMESTAMP WITH TIME ZONE DEFAULT now() NOT NULL, 
        	updated_at TIMESTAMP WITH TIME ZONE DEFAULT now() NOT NULL, 
        	CONSTRAINT pk_drivers PRIMARY KEY (id), 
        	CONSTRAINT uq_drivers_user_id UNIQUE (user_id), 
        	CONSTRAINT fk_drivers_user_id_users FOREIGN KEY(user_id) REFERENCES users (id) ON DELETE CASCADE, 
        	CONSTRAINT uq_drivers_license_number UNIQUE (license_number)
        );
        """,
        """
        DROP TABLE IF EXISTS drivers CASCADE;
        DROP TABLE IF EXISTS users CASCADE;
        """,
    ),
]
