-- Rainfall erosivity (R factor). Filled by calculation runs, never by the CSV import:
-- event rules (separation gap, erosive threshold, energy equation, I30 estimate) are run parameters.

CREATE TABLE erosivity_calculations (
    id             BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    station_id     BIGINT      NOT NULL REFERENCES stations (id),
    period_start   DATE        NOT NULL,
    period_end     DATE        NOT NULL,
    method         VARCHAR(40) NOT NULL,
    method_version INT         NOT NULL,
    parameters     JSONB       NOT NULL,
    status         VARCHAR(20) NOT NULL CHECK (status IN ('PENDING', 'RUNNING', 'COMPLETED', 'FAILED')),
    r_factor       NUMERIC(10, 1),
    years_used     SMALLINT,
    failure_reason TEXT,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    finished_at    TIMESTAMPTZ,
    CHECK (period_end >= period_start)
);

COMMENT ON COLUMN erosivity_calculations.r_factor IS 'Mean annual erosivity, MJ mm ha-1 h-1 yr-1.';

CREATE INDEX erosivity_calculations_station_idx ON erosivity_calculations (station_id);

-- Every rainfall event found by the run, erosive or not, so the classification can be audited.
CREATE TABLE erosivity_events (
    id                   BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    calculation_id       BIGINT        NOT NULL REFERENCES erosivity_calculations (id) ON DELETE CASCADE,
    started_at           TIMESTAMPTZ   NOT NULL,
    ended_at             TIMESTAMPTZ   NOT NULL,
    precipitation_mm     NUMERIC(6, 1) NOT NULL,
    max_hourly_mm        NUMERIC(5, 1) NOT NULL,
    missing_hours        SMALLINT      NOT NULL DEFAULT 0,
    kinetic_energy_mj_ha NUMERIC(10, 3),
    i30_mm_h             NUMERIC(6, 1),
    ei30                 NUMERIC(10, 1),
    erosive              BOOLEAN       NOT NULL,
    CHECK (ended_at >= started_at)
);

CREATE INDEX erosivity_events_calculation_idx ON erosivity_events (calculation_id, started_at);

-- R is the mean of these sums over included years; a valid year without erosive events still counts as zero.
CREATE TABLE erosivity_annual_results (
    calculation_id   BIGINT         NOT NULL REFERENCES erosivity_calculations (id) ON DELETE CASCADE,
    year             SMALLINT       NOT NULL,
    precipitation_mm NUMERIC(7, 1),
    missing_hours    INT            NOT NULL,
    erosive_events   INT            NOT NULL,
    ei30_sum         NUMERIC(10, 1) NOT NULL,
    included         BOOLEAN        NOT NULL,
    PRIMARY KEY (calculation_id, year)
);
