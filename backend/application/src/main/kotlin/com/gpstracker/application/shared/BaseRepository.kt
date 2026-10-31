package com.gpstracker.application.shared

import com.gpstracker.domain.shared.BaseEntity

interface BaseRepository<T : BaseEntity<ID>, ID : Any> {
    fun findAll(): List<T>

    fun findById(id: ID): T?

    fun save(entity: T): T

    fun deleteById(id: ID)
}
