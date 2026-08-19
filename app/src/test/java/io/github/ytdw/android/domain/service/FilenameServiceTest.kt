package io.github.ytdw.android.domain.service

import org.junit.Assert.assertEquals
import org.junit.Test

class FilenameServiceTest {
    @Test fun invalidCharactersReservedNamesAndEmptyNamesAreSafe() {
        assertEquals("ABC", FilenameService.sanitize(" A<B>:\"C\"/\\|?* . "))
        assertEquals("CON_", FilenameService.sanitize("CON"))
        assertEquals("con_.txt", FilenameService.sanitize("con.txt"))
        assertEquals("untitled", FilenameService.sanitize("... "))
    }

    @Test fun duplicatesUsePreferredThenNumericSuffixes() {
        val existing = mutableSetOf("Night Drive.m4a")
        assertEquals("Night Drive (abc123).m4a", FilenameService.uniqueName("Night Drive.m4a", existing::contains, "abc123"))
        existing += "Night Drive (2).m4a"
        assertEquals("Night Drive (3).m4a", FilenameService.uniqueName("Night Drive.m4a", existing::contains))
    }
}
