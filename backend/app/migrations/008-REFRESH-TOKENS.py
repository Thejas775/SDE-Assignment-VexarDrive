"""Server-side refresh token store so logout can actually revoke a session."""

from yoyo import step

__depends__ = {"007-ASSIGNMENT-OVERLAP-EXCLUSION"}

steps = [
    step(
        """
        CREATE TABLE refresh_tokens (
            id UUID NOT NULL,
            user_id UUID NOT NULL,
            token_hash VARCHAR(64) NOT NULL,
            expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
            revoked_at TIMESTAMP WITH TIME ZONE,
            created_at TIMESTAMP WITH TIME ZONE NOT NULL,
            CONSTRAINT pk_refresh_tokens PRIMARY KEY (id),
            CONSTRAINT uq_refresh_tokens_token_hash UNIQUE (token_hash),
            CONSTRAINT fk_refresh_tokens_user_id_users
                FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
        );
        CREATE INDEX ix_refresh_tokens_user_id ON refresh_tokens (user_id);
        """,
        """
        DROP TABLE IF EXISTS refresh_tokens CASCADE;
        """,
    ),
]
