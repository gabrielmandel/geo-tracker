package com.gpstracker.application.station

class StationNotFoundException(code: String) : RuntimeException("Station $code was not found")
