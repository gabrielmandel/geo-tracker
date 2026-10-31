package com.gpstracker.infrastructure.shared

import com.gpstracker.application.shared.BaseRepository
import com.gpstracker.domain.shared.BaseEntity
import org.springframework.jdbc.core.simple.JdbcClient
import java.sql.ResultSet
import kotlin.jvm.optionals.getOrNull

abstract class JdbcRepository<T : BaseEntity<ID>, ID : Any>(
    protected val jdbc: JdbcClient,
    private val table: String,
) : BaseRepository<T, ID> {

    protected abstract fun mapRow(rs: ResultSet): T

    // Subclasses can add computed columns (for example values extracted from JSONB) needed by mapRow.
    protected open val columns: String = "*"

    override fun findAll(): List<T> = jdbc.sql("SELECT $columns FROM $table ORDER BY id").mapList()

    override fun findById(id: ID): T? = jdbc.sql("SELECT $columns FROM $table WHERE id = :id").param("id", id).mapOne()

    override fun deleteById(id: ID) {
        jdbc.sql("DELETE FROM $table WHERE id = :id").param("id", id).update()
    }

    protected fun JdbcClient.StatementSpec.mapList(): List<T> = query { rs, _ -> mapRow(rs) }.list()

    protected fun JdbcClient.StatementSpec.mapOne(): T? = query { rs, _ -> mapRow(rs) }.optional().getOrNull()

    protected fun JdbcClient.StatementSpec.mapSingle(): T = query { rs, _ -> mapRow(rs) }.single()
}
