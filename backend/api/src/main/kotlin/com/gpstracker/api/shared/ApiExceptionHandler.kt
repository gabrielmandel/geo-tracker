package com.gpstracker.api.shared

import com.gpstracker.application.erosivity.CacheRefreshNotFoundException
import com.gpstracker.application.erosivity.ErosivityCalculationNotFoundException
import com.gpstracker.application.erosivity.NoPrecipitationDataException
import com.gpstracker.application.imports.ArchiveImportNotFoundException
import com.gpstracker.application.imports.DuplicateImportException
import com.gpstracker.application.imports.InvalidStationFileException
import com.gpstracker.application.imports.StationImportNotFoundException
import com.gpstracker.application.station.StationNotFoundException
import org.apache.commons.logging.LogFactory
import org.springframework.http.HttpStatus
import org.springframework.http.HttpStatusCode
import org.springframework.http.ProblemDetail
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler

// One ProblemDetail (RFC 9457) shape for every endpoint; framework errors come from ResponseEntityExceptionHandler.
@RestControllerAdvice
class ApiExceptionHandler : ResponseEntityExceptionHandler() {

    @ExceptionHandler(
        StationNotFoundException::class,
        ErosivityCalculationNotFoundException::class,
        ArchiveImportNotFoundException::class,
        StationImportNotFoundException::class,
        CacheRefreshNotFoundException::class,
    )
    fun handleNotFound(e: RuntimeException): ProblemDetail = problem(HttpStatus.NOT_FOUND, e, "Not found")

    @ExceptionHandler(InvalidStationFileException::class, NoPrecipitationDataException::class)
    fun handleUnprocessable(e: RuntimeException): ProblemDetail = problem(HttpStatusCode.valueOf(422), e, "Unprocessable request")

    @ExceptionHandler
    fun handleDuplicate(e: DuplicateImportException): ProblemDetail =
        problem(HttpStatus.CONFLICT, e, "File already imported").apply { setProperty("importId", e.importId) }

    // Domain and request validation (require) failures.
    @ExceptionHandler
    fun handleInvalidArgument(e: IllegalArgumentException): ProblemDetail = problem(HttpStatus.BAD_REQUEST, e, "Invalid request")

    @ExceptionHandler
    fun handleUnexpected(e: Exception): ProblemDetail {
        log.error("Unexpected API error", e)
        return ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR, "Unexpected error")
    }

    private fun problem(status: HttpStatusCode, e: RuntimeException, fallback: String) =
        ProblemDetail.forStatusAndDetail(status, e.message ?: fallback)

    private companion object {
        val log = LogFactory.getLog(ApiExceptionHandler::class.java)
    }
}
