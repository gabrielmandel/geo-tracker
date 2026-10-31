package com.gpstracker.infrastructure.station

import com.gpstracker.application.station.StationFilter
import com.gpstracker.application.station.WeatherStationRepository
import com.gpstracker.domain.shared.Coordinate
import com.gpstracker.domain.station.StationDetails
import com.gpstracker.domain.station.WeatherStation
import com.gpstracker.infrastructure.shared.JdbcRepository
import com.gpstracker.infrastructure.shared.getInstant
import com.gpstracker.infrastructure.shared.getNullableDouble
import com.gpstracker.infrastructure.shared.toUtc
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository
import java.sql.ResultSet
import java.sql.Types
import java.time.Instant
import java.time.LocalDate

@Repository
class JdbcWeatherStationRepository(jdbc: JdbcClient) :
    JdbcRepository<WeatherStation, Long>(jdbc, "stations"),
    WeatherStationRepository {

    override fun findByCode(code: String): WeatherStation? =
        jdbc.sql("SELECT * FROM stations WHERE code = :code").param("code", code).mapOne()

    override fun search(filter: StationFilter): List<WeatherStation> {
        val conditions = mutableListOf("TRUE")
        val params = mutableMapOf<String, Any>()
        filter.state?.let { conditions += "state = :state"; params["state"] = it.trim().uppercase() }
        filter.region?.let { conditions += "region = :region"; params["region"] = it.trim().uppercase() }
        filter.text?.trim()?.takeIf { it.isNotEmpty() }?.let {
            conditions += "(code ILIKE :text OR name ILIKE :text)"
            params["text"] = "%${it.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")}%"
        }
        filter.foundedOnOrBefore?.let { conditions += "founded_on <= :foundedOn"; params["foundedOn"] = it }
        return jdbc.sql("SELECT * FROM stations WHERE ${conditions.joinToString(" AND ")} ORDER BY name")
            .params(params)
            .mapList()
    }

    override fun create(details: StationDetails, detailsAsOf: Instant): WeatherStation {
        val id = jdbc.sql(
            """
            INSERT INTO stations (code, name, region, state, latitude, longitude, altitude_m, founded_on, details_as_of)
            VALUES (:code, :name, :region, :state, :latitude, :longitude, :altitude, :foundedOn, :detailsAsOf)
            RETURNING id
            """,
        ).bindDetails(details, detailsAsOf).query(Long::class.javaObjectType).single()
        return WeatherStation(id, details, detailsAsOf)
    }

    override fun save(entity: WeatherStation): WeatherStation {
        jdbc.sql(
            """
            UPDATE stations
            SET name = :name, region = :region, state = :state, latitude = :latitude, longitude = :longitude,
                altitude_m = :altitude, founded_on = :foundedOn, details_as_of = :detailsAsOf, updated_at = now()
            WHERE id = :id
            """,
        ).bindDetails(entity.details, entity.detailsAsOf).param("id", entity.id).update()
        return entity
    }

    override fun mapRow(rs: ResultSet) = WeatherStation(
        id = rs.getLong("id"),
        details = StationDetails(
            code = rs.getString("code"),
            name = rs.getString("name"),
            region = rs.getString("region"),
            state = rs.getString("state"),
            coordinate = Coordinate(rs.getDouble("latitude"), rs.getDouble("longitude")),
            altitudeMeters = rs.getNullableDouble("altitude_m"),
            foundedOn = rs.getObject("founded_on", LocalDate::class.java),
        ),
        detailsAsOf = checkNotNull(rs.getInstant("details_as_of")),
    )

    private fun JdbcClient.StatementSpec.bindDetails(details: StationDetails, asOf: Instant) =
        param("code", details.code)
            .param("name", details.name)
            .param("region", details.region)
            .param("state", details.state)
            .param("latitude", details.coordinate.latitude)
            .param("longitude", details.coordinate.longitude)
            .param("altitude", details.altitudeMeters, Types.NUMERIC)
            .param("foundedOn", details.foundedOn, Types.DATE)
            .param("detailsAsOf", asOf.toUtc())
}
