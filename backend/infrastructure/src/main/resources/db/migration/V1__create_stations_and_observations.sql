-- Stations and hourly data imported from INMET automatic-station CSV files.

CREATE TABLE stations (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    code          VARCHAR(10)      NOT NULL UNIQUE,
    name          VARCHAR(120)     NOT NULL,
    region        VARCHAR(2)       NOT NULL,
    state         CHAR(2)          NOT NULL,
    latitude      DOUBLE PRECISION NOT NULL CHECK (latitude BETWEEN -90 AND 90),
    longitude     DOUBLE PRECISION NOT NULL CHECK (longitude BETWEEN -180 AND 180),
    altitude_m    NUMERIC(7, 2),
    founded_on    DATE,
    details_as_of TIMESTAMPTZ      NOT NULL,
    created_at    TIMESTAMPTZ      NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ      NOT NULL DEFAULT now()
);

COMMENT ON COLUMN stations.details_as_of IS
    'Last observation of the file that supplied name, coordinates and altitude. Files with older data never overwrite them.';

CREATE TABLE station_imports (
    id                         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    file_name                  VARCHAR(255) NOT NULL,
    file_hash                  CHAR(64)     NOT NULL,
    status                     VARCHAR(20)  NOT NULL CHECK (status IN ('PROCESSING', 'COMPLETED', 'FAILED')),
    station_id                 BIGINT REFERENCES stations (id),
    period_start               TIMESTAMPTZ,
    period_end                 TIMESTAMPTZ,
    rows_read                  INT,
    rows_written               INT,
    rows_skipped               INT,
    rows_rejected              INT,
    missing_precipitation_rows INT,
    rejections                 TEXT[]       NOT NULL DEFAULT '{}',
    failure_reason             TEXT,
    started_at                 TIMESTAMPTZ  NOT NULL DEFAULT now(),
    finished_at                TIMESTAMPTZ
);

-- The same file (SHA-256) can only be completed once; failed attempts may be retried.
CREATE UNIQUE INDEX station_imports_completed_hash_uk ON station_imports (file_hash) WHERE status = 'COMPLETED';

CREATE TABLE station_observations (
    station_id                BIGINT        NOT NULL REFERENCES stations (id),
    observed_at               TIMESTAMPTZ   NOT NULL,
    import_id                 BIGINT        NOT NULL REFERENCES station_imports (id),
    precipitation_mm          NUMERIC(5, 1),
    station_pressure_mb       NUMERIC(5, 1),
    max_pressure_mb           NUMERIC(5, 1),
    min_pressure_mb           NUMERIC(5, 1),
    global_radiation_kj_m2    NUMERIC(6, 1),
    air_temperature_c         NUMERIC(4, 1),
    dew_point_c               NUMERIC(4, 1),
    max_air_temperature_c     NUMERIC(4, 1),
    min_air_temperature_c     NUMERIC(4, 1),
    max_dew_point_c           NUMERIC(4, 1),
    min_dew_point_c           NUMERIC(4, 1),
    max_relative_humidity_pct SMALLINT,
    min_relative_humidity_pct SMALLINT,
    relative_humidity_pct     SMALLINT,
    wind_direction_deg        SMALLINT,
    wind_gust_ms              NUMERIC(4, 1),
    wind_speed_ms             NUMERIC(4, 1),
    PRIMARY KEY (station_id, observed_at)
);

COMMENT ON TABLE station_observations IS
    'One row per station and UTC hour. Accumulated and min/max values cover the hour ending at observed_at. NULL means missing, never zero.';
