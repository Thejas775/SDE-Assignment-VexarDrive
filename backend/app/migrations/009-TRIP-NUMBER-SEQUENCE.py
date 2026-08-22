"""Sequence backing the human-facing trip number (TRP1024 in the spec).

A sequence rather than MAX(trip_number)+1 because nextval is atomic: two
concurrent trip creations can never be handed the same number.
"""

from yoyo import step

__depends__ = {"008-REFRESH-TOKENS"}

steps = [
    step(
        "CREATE SEQUENCE IF NOT EXISTS trip_number_seq START WITH 1000 INCREMENT BY 1;",
        "DROP SEQUENCE IF EXISTS trip_number_seq;",
    ),
]
