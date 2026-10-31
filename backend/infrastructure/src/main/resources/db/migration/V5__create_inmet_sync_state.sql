-- Last INMET yearly ZIP seen per year, so the scheduled sync only downloads archives that changed.
CREATE TABLE inmet_sync_state (
    year              SMALLINT PRIMARY KEY,
    last_modified     TEXT,
    etag              TEXT,
    checked_at        TIMESTAMPTZ NOT NULL,
    archive_import_id BIGINT REFERENCES archive_imports (id)
);
