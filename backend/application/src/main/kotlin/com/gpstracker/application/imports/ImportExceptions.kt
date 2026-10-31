package com.gpstracker.application.imports

class InvalidStationFileException(message: String) : RuntimeException(message)

class StationImportNotFoundException(id: Long) : RuntimeException("Station import $id was not found")

class DuplicateImportException(val importId: Long) :
    RuntimeException("This file was already imported (import $importId)")
