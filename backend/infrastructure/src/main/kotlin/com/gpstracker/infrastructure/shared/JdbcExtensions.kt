package com.gpstracker.infrastructure.shared

import java.sql.ResultSet
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset

// The PostgreSQL driver binds TIMESTAMPTZ as OffsetDateTime; it rejects Instant.
fun Instant.toUtc(): OffsetDateTime = atOffset(ZoneOffset.UTC)

fun ResultSet.getInstant(column: String): Instant? = getObject(column, OffsetDateTime::class.java)?.toInstant()

fun ResultSet.getNullableDouble(column: String): Double? = getBigDecimal(column)?.toDouble()
