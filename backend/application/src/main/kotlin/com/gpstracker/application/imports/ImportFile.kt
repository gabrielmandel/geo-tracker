package com.gpstracker.application.imports

import java.io.InputStream

class ImportFile(name: String, val size: Long, private val content: () -> InputStream) {
    // Some browsers send the full client path; only the file name is kept.
    val name: String = name.substringAfterLast('/').substringAfterLast('\\').take(255)

    fun open(): InputStream = content()
}
