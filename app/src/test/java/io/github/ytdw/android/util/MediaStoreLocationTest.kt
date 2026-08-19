package io.github.ytdw.android.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class MediaStoreLocationTest {
    @Test fun `maps primary and removable storage document ids`() {
        assertEquals(
            MediaStoreLocation("external_primary", "Music/My audio"),
            MediaStoreLocationParser.fromTreeDocumentId("primary:Music/My audio", "Music"),
        )
        assertEquals(
            MediaStoreLocation("0123-4567", "Movies/YT-DW"),
            MediaStoreLocationParser.fromTreeDocumentId("0123-4567:Movies/YT-DW", "Movies"),
        )
    }

    @Test fun `rejects wrong media root and traversal`() {
        assertThrows(IllegalArgumentException::class.java) {
            MediaStoreLocationParser.fromTreeDocumentId("primary:Download", "Music")
        }
        assertThrows(IllegalArgumentException::class.java) {
            MediaStoreLocationParser.fromTreeDocumentId("primary:Music/../Other", "Music")
        }
    }
}
