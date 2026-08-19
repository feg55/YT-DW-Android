package io.github.ytdw.android.domain.service

import org.junit.Assert.assertEquals
import org.junit.Test

class MetadataCleanerTest {
    @Test fun removesDistinctChannelEdges() {
        assertEquals("Night Drive", MetadataCleaner.cleanTrackTitle("Cool Music Channel — Night Drive", "Cool Music Channel"))
        assertEquals("Night Drive", MetadataCleaner.cleanTrackTitle("Night Drive | cool music channel", "Cool Music Channel"))
        assertEquals("Night Drive", MetadataCleaner.cleanTrackTitle("[Cool Music Channel] Night Drive", "Cool Music Channel"))
    }

    @Test fun removesSupportedLabelsAndPreservesUnicode() {
        assertEquals("Ночной экспресс", MetadataCleaner.cleanTrackTitle("  Музыка Канал — Ночной\u00a0экспресс [Lyrics] ", "МУЗЫКА КАНАЛ"))
        assertEquals("Song", MetadataCleaner.cleanTrackTitle("Song (Official Music Video)", ""))
    }

    @Test fun channelInMiddleIsPreservedAndEmptyCleaningFallsBack() {
        val middle = "A Night with Cool Music Channel in Berlin"
        assertEquals(middle, MetadataCleaner.cleanTrackTitle(middle, "Cool Music Channel"))
        val original = "[Cool Music Channel] (Official Video)"
        assertEquals(original, MetadataCleaner.cleanTrackTitle(original, "Cool Music Channel"))
    }
}
