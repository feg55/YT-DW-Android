package io.github.ytdw.android.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UrlParsingTest {
    @Test fun urlsAreDeduplicatedInOrder() {
        assertEquals(
            listOf("https://example.test/a", "https://example.test/b"),
            AppViewModel.parseUrls("https://example.test/a\n https://example.test/b\nhttps://example.test/a").getOrThrow(),
        )
    }

    @Test fun nonHttpInputIsRejected() {
        assertTrue(AppViewModel.parseUrls("not a url").isFailure)
    }
}
