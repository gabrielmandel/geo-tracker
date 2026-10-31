package com.gpstracker

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.scheduling.annotation.EnableScheduling

// Lives in the root package so component scanning reaches every module.
@SpringBootApplication
@EnableScheduling
class GeoTrackerApplication

fun main(args: Array<String>) {
    runApplication<GeoTrackerApplication>(*args)
}
