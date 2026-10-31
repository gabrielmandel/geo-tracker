-- Annual erosivity per station for the map's parameter set. Maps aggregate these rows for any year range
-- instead of recomputing hourly series; rows are replaced when newer imports reach the station.
CREATE TABLE erosivity_station_years (
    parameters_key      VARCHAR(120)   NOT NULL,
    station_id          BIGINT         NOT NULL REFERENCES stations (id),
    year                SMALLINT       NOT NULL,
    hours_in_year       INT            NOT NULL,
    precipitation_hours INT            NOT NULL,
    precipitation_mm    NUMERIC(7, 1)  NOT NULL,
    erosive_events      INT            NOT NULL,
    ei30_sum            NUMERIC(10, 1) NOT NULL,
    computed_at         TIMESTAMPTZ    NOT NULL DEFAULT now(),
    PRIMARY KEY (parameters_key, station_id, year)
);

CREATE INDEX erosivity_station_years_year_idx ON erosivity_station_years (parameters_key, year);

-- Background rebuild of the cache, polled by the front end.
CREATE TABLE erosivity_cache_refreshes (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    parameters_key  VARCHAR(120) NOT NULL,
    only_stale      BOOLEAN      NOT NULL,
    status          VARCHAR(20)  NOT NULL CHECK (status IN ('QUEUED', 'PROCESSING', 'COMPLETED', 'FAILED')),
    stations_total  INT,
    stations_done   INT          NOT NULL DEFAULT 0,
    stations_failed INT          NOT NULL DEFAULT 0,
    failure_reason  TEXT,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    started_at      TIMESTAMPTZ,
    finished_at     TIMESTAMPTZ
);

-- Speeds up the "imported after the cache was computed" staleness check.
CREATE INDEX station_imports_station_idx ON station_imports (station_id, finished_at) WHERE status = 'COMPLETED';
