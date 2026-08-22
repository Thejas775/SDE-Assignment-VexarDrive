"""Reject overlapping assignments for the same vehicle or driver (spec section 8).

A CHECK constraint only sees one row, so the overlap rule cannot be expressed
as one. EXCLUDE compares the candidate row against every other row's date
range and is enforced by the index, which also closes the race two concurrent
requests would otherwise slip through.
"""

from yoyo import step

__depends__ = {"006-MAINTENANCE-INCIDENTS-NOTIFICATIONS"}

steps = [
    step(
        """
        CREATE EXTENSION IF NOT EXISTS btree_gist;

        ALTER TABLE vehicle_assignments
            ADD CONSTRAINT ex_vehicle_assignments_no_vehicle_overlap
            EXCLUDE USING gist (
                vehicle_id WITH =,
                daterange(start_date, COALESCE(end_date, 'infinity'::date), '[]') WITH &&
            ) WHERE (status = 'ACTIVE');

        ALTER TABLE vehicle_assignments
            ADD CONSTRAINT ex_vehicle_assignments_no_driver_overlap
            EXCLUDE USING gist (
                driver_id WITH =,
                daterange(start_date, COALESCE(end_date, 'infinity'::date), '[]') WITH &&
            ) WHERE (status = 'ACTIVE');
        """,
        """
        ALTER TABLE vehicle_assignments
            DROP CONSTRAINT IF EXISTS ex_vehicle_assignments_no_driver_overlap;
        ALTER TABLE vehicle_assignments
            DROP CONSTRAINT IF EXISTS ex_vehicle_assignments_no_vehicle_overlap;
        """,
    ),
]
