package com.gpstracker.api.shared

import org.springframework.http.ContentDisposition
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity

// RFC 4180 CSV with a dot decimal separator, the format QGIS and TerrSet import without extra options.
object Csv {
    val MEDIA_TYPE: MediaType = MediaType("text", "csv", Charsets.UTF_8)

    fun write(header: List<String>, rows: List<List<Any?>>): String = buildString {
        appendLine(header.joinToString(",") { cell(it) })
        rows.forEach { row -> appendLine(row.joinToString(",") { cell(it) }) }
    }

    fun attachment(fileName: String, body: String): ResponseEntity<String> =
        download(fileName, MEDIA_TYPE, body)

    fun <T : Any> download(fileName: String, mediaType: MediaType, body: T): ResponseEntity<T> =
        ResponseEntity.ok()
            .contentType(mediaType)
            .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(fileName).build().toString())
            .body(body)

    private fun cell(value: Any?): String {
        val text = value?.toString() ?: return ""
        return if (text.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) "\"${text.replace("\"", "\"\"")}\"" else text
    }
}
