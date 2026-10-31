package com.gpstracker.domain.shared

abstract class BaseEntity<ID : Any> {
    abstract val id: ID

    // Entities are equal by identity, not by attribute values.
    final override fun equals(other: Any?): Boolean =
        this === other || (other is BaseEntity<*> && other.javaClass == javaClass && other.id == id)

    final override fun hashCode(): Int = id.hashCode()
}
