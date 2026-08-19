package io.github.ytdw.android.domain.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ProgressParserTest {
    @Test fun callbackParsesProgressSpeedEtaAndTotal() {
        val value = ProgressParser.fromCallback(25f, 6, "[download] 25.0% of 100.0MiB at 12.5MiB/s ETA 00:06")
        assertEquals(25.0, value.percentage, 0.001)
        assertEquals(100L * 1024 * 1024, value.totalBytes)
        assertEquals(25L * 1024 * 1024, value.downloadedBytes)
        assertEquals(12.5 * 1024 * 1024, value.speedBytesPerSecond!!, 0.1)
        assertEquals(6L, value.etaSeconds)
    }

    @Test fun unknownSizesRemainNullAndPercentageIsClamped() {
        val value = ProgressParser.fromCallback(120f, -1, "download")
        assertEquals(100.0, value.percentage, 0.001)
        assertNull(value.totalBytes)
        assertNull(value.etaSeconds)
    }
}
