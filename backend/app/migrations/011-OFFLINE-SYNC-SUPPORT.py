"""Replay protection and client-side reconciliation for offline mobile clients."""

from yoyo import step

__depends__ = {"010-FUEL-LOGS"}

steps = [
    step(
        """
        CREATE TABLE idempotency_keys (
            id UUID NOT NULL,
            key VARCHAR(128) NOT NULL,
            user_id UUID NOT NULL,
            endpoint VARCHAR(200) NOT NULL,
            request_hash VARCHAR(64) NOT NULL,
            status_code INTEGER,
            response_body JSONB,
            created_at TIMESTAMP WITH TIME ZONE NOT NULL,
            completed_at TIMESTAMP WITH TIME ZONE,
            CONSTRAINT pk_idempotency_keys PRIMARY KEY (id),
            CONSTRAINT uq_idempotency_keys_key_user_id UNIQUE (key, user_id),
            CONSTRAINT fk_idempotency_keys_user_id_users
                FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
        );
        """,
        "DROP TABLE IF EXISTS idempotency_keys CASCADE;",
    ),
    step(
        """
        ALTER TABLE incidents ADD COLUMN client_reference_id VARCHAR(64);
        ALTER TABLE incidents
            ADD CONSTRAINT uq_incidents_reported_by_id_client_reference_id
            UNIQUE (reported_by_id, client_reference_id);
        """,
        """
        ALTER TABLE incidents
            DROP CONSTRAINT IF EXISTS uq_incidents_reported_by_id_client_reference_id;
        ALTER TABLE incidents DROP COLUMN IF EXISTS client_reference_id;
        """,
    ),
]
