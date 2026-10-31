package com.gpstracker.api.imports

import com.gpstracker.application.imports.ImportFile
import com.gpstracker.application.imports.StationImportService
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestPart
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile

@RestController
@RequestMapping("/imports")
class StationImportController(private val service: StationImportService) {

    @PostMapping(consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    @ResponseStatus(HttpStatus.CREATED)
    fun import(@RequestPart("file") file: MultipartFile): StationImportResponse =
        service.import(ImportFile(file.originalFilename ?: file.name, file.size, file::getInputStream)).toResponse()
}
