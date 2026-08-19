package io.github.ytdw.android.domain.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ErrorMapperTest {
    @Test fun authenticationAndDiskErrorsAreActionable() {
        val authentication = ErrorMapper.map(IllegalStateException("Sign in to continue; cookies required"))
        assertEquals(ErrorCategory.AUTHENTICATION_REQUIRED, authentication.category)
        assertTrue(authentication.userMessage.contains("signing in"))
        assertEquals(
            ErrorCategory.SOURCE_REJECTED,
            ErrorMapper.map(IllegalStateException("HTTP Error 403: Forbidden")).category,
        )
        assertEquals(ErrorCategory.DISK_FULL, ErrorMapper.map(IllegalStateException("ENOSPC: no space left")).category)
    }

    @Test fun networkAndUnsupportedErrorsAreCategorized() {
        assertEquals(ErrorCategory.NETWORK, ErrorMapper.map(IllegalStateException("Network timeout")).category)
        assertEquals(ErrorCategory.UNSUPPORTED, ErrorMapper.map(IllegalStateException("Unsupported URL")).category)
    }
}
