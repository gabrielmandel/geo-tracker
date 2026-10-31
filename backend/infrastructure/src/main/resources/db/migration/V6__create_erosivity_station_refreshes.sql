-- When each station was last computed for a parameter set, even when it produced no years
-- (stations without rain readings), so the staleness check doesn't retry them forever.
CREATE TABLE erosivity_station_refreshes (
    parameters_key VARCHAR(120) NOT NULL,
    station_id     BIGINT       NOT NULL REFERENCES stations (id),
    computed_at    TIMESTAMPTZ  NOT NULL,
    PRIMARY KEY (parameters_key, station_id)
);

INSERT INTO erosivity_station_refreshes (parameters_key, station_id, computed_at)
SELECT parameters_key, station_id, MIN(computed_at)
FROM erosivity_station_years
GROUP BY parameters_key, station_id;
