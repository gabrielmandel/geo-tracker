package com.gpstracker.api.imports

import com.gpstracker.application.imports.InmetSyncService
import com.gpstracker.application.imports.SyncOutcome
import com.gpstracker.application.imports.SyncState
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/admin/inmet-sync")
class InmetSyncController(private val sync: InmetSyncService) {

    @GetMapping
    fun states(): List<SyncState> = sync.states()

    // Without a year: the current year (and the previous one until March), as the scheduled job does.
    @PostMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    fun sync(@RequestParam(required = false) year: Int?): List<SyncOutcome> =
        if (year == null) sync.syncRecentYears() else listOf(sync.sync(year))
}
