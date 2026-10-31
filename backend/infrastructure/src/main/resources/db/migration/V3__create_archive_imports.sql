-- One uploaded INMET yearly ZIP. Each CSV inside is still recorded in station_imports by the single-file workflow;
-- this row only tracks the archive job and its per-file outcome counters.
CREATE TABLE archive_imports (
    id               BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    file_name        VARCHAR(255) NOT NULL,
    status           VARCHAR(20)  NOT NULL CHECK (status IN ('QUEUED', 'PROCESSING', 'COMPLETED', 'FAILED')),
    files_total      INT,
    files_imported   INT          NOT NULL DEFAULT 0,
    files_duplicated INT          NOT NULL DEFAULT 0,
    files_failed     INT          NOT NULL DEFAULT 0,
    files_ignored    INT          NOT NULL DEFAULT 0,
    rows_written     BIGINT       NOT NULL DEFAULT 0,
    failures         TEXT[]       NOT NULL DEFAULT '{}',
    failure_reason   TEXT,
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    started_at       TIMESTAMPTZ,
    finished_at      TIMESTAMPTZ
);

COMMENT ON COLUMN archive_imports.failures IS 'First failed files as "<file>: <reason>" (capped).';
