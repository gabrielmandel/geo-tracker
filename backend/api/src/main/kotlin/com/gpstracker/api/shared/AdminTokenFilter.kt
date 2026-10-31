package com.gpstracker.api.shared

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import java.security.MessageDigest

// Stop-gap until JWT authentication: bulk-write endpoints need X-Admin-Token unless geo.admin.open is true (dev).
@Component
class AdminTokenFilter(
    @Value("\${geo.admin.open:false}") private val open: Boolean,
    @Value("\${geo.admin.token:}") private val token: String,
) : OncePerRequestFilter() {

    override fun shouldNotFilter(request: HttpServletRequest): Boolean {
        if (open || request.method != "POST") return true
        val path = request.requestURI.removePrefix(request.contextPath)
        return ADMIN_PATHS.none { path == it || path.startsWith("$it/") }
    }

    override fun doFilterInternal(request: HttpServletRequest, response: HttpServletResponse, chain: FilterChain) {
        val presented = request.getHeader(HEADER).orEmpty()
        if (token.isNotBlank() && MessageDigest.isEqual(presented.toByteArray(), token.toByteArray())) {
            chain.doFilter(request, response)
            return
        }
        response.status = HttpStatus.FORBIDDEN.value()
        response.contentType = MediaType.APPLICATION_PROBLEM_JSON_VALUE
        response.writer.write(
            """{"type":"about:blank","title":"Forbidden","status":403,"detail":"This operation needs the $HEADER header"}""",
        )
    }

    private companion object {
        const val HEADER = "X-Admin-Token"
        val ADMIN_PATHS = listOf("/imports", "/erosivity-maps/cache/refresh", "/admin")
    }
}
