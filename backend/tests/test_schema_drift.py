"""yoyo migrations are hand-written, so nothing stops them drifting from the
models. This test fails the build if they do."""

from sqlalchemy import inspect

from app.db.base import Base


async def test_every_model_table_exists_with_matching_columns(engine):
    async with engine.connect() as conn:
        live = await conn.run_sync(
            lambda sync: {
                name: {c["name"] for c in inspect(sync).get_columns(name)}
                for name in inspect(sync).get_table_names()
            }
        )

    missing_tables = [t.name for t in Base.metadata.sorted_tables if t.name not in live]
    assert not missing_tables, f"tables in models but not in migrations: {missing_tables}"

    drift = {}
    for table in Base.metadata.sorted_tables:
        expected = {c.name for c in table.columns}
        actual = live[table.name]
        if expected != actual:
            drift[table.name] = {
                "missing_in_db": sorted(expected - actual),
                "extra_in_db": sorted(actual - expected),
            }
    assert not drift, f"model/migration drift: {drift}"
