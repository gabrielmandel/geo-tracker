package com.gpstracker.api.station

import com.gpstracker.domain.observation.ObservationVariable
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

data class ObservationVariableResponse(val code: String, val column: String, val unit: String, val description: String, val inmetHeader: String)

@RestController
class ObservationVariableController {
    @GetMapping("/variables")
    fun variables(): List<ObservationVariableResponse> =
        ObservationVariable.entries.map { ObservationVariableResponse(it.name, it.column, it.unit, it.description, it.inmetHeader) }
}
