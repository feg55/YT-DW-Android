package io.github.ytdw.android.domain.service

enum class ErrorCategory { AUTHENTICATION_REQUIRED, SOURCE_REJECTED, NETWORK, DISK_FULL, UNSUPPORTED, METADATA, CANCELLED, UNKNOWN }

data class MappedError(val category: ErrorCategory, val userMessage: String, val technical: String)

object ErrorMapper {
    fun map(error: Throwable): MappedError {
        val technical = error.stackTraceToString()
        val text = generateSequence(error) { it.cause }.joinToString(" ") { it.message.orEmpty() }.lowercase()
        val category = when {
            "cancel" in text || error is kotlinx.coroutines.CancellationException -> ErrorCategory.CANCELLED
            "sign in" in text || "login" in text || "authentication" in text || "cookies" in text ||
                "not a bot" in text || "http error 401" in text -> ErrorCategory.AUTHENTICATION_REQUIRED
            "http error 403" in text || "forbidden" in text -> ErrorCategory.SOURCE_REJECTED
            "no space" in text || "enospc" in text -> ErrorCategory.DISK_FULL
            "unsupported url" in text || "no suitable extractor" in text -> ErrorCategory.UNSUPPORTED
            "metadata" in text || "cover" in text -> ErrorCategory.METADATA
            "timeout" in text || "network" in text || "connection" in text || "http error" in text -> ErrorCategory.NETWORK
            else -> ErrorCategory.UNKNOWN
        }
        val message = when (category) {
            ErrorCategory.AUTHENTICATION_REQUIRED ->
                "This media requires signing in to the source."
            ErrorCategory.SOURCE_REJECTED ->
                "The media server rejected the download (HTTP 403). Retry without a VPN or on another network."
            ErrorCategory.NETWORK -> "Network error. Check the connection and retry."
            ErrorCategory.DISK_FULL -> "There is not enough storage space."
            ErrorCategory.UNSUPPORTED -> "This URL is not supported."
            ErrorCategory.METADATA -> "The media metadata could not be written or verified."
            ErrorCategory.CANCELLED -> "Cancelled"
            ErrorCategory.UNKNOWN -> error.message ?: "Unexpected error"
        }
        return MappedError(category, message, technical)
    }
}
