package io.github.ytdw.android.domain.service

import java.text.Normalizer

object MetadataCleaner {
    private val whitespace = Regex("[\\s\\u00A0]+")
    private val trailingLabel = Regex(
        "\\s*[\\[(]\\s*(?:official\\s+(?:music\\s+)?video|official\\s+audio|lyrics|lyric\\s+video|visualizer|audio)\\s*[\\])]\\s*$",
        RegexOption.IGNORE_CASE,
    )

    fun normalizeText(value: String): String =
        Normalizer.normalize(value, Normalizer.Form.NFC).replace(whitespace, " ").trim()

    fun cleanTrackTitle(originalTitle: String, channelName: String, removeLabels: Boolean = true): String {
        val original = normalizeText(originalTitle)
        if (original.isEmpty()) return original
        var candidate = original
        val channel = normalizeText(channelName)
        if (channel.isNotEmpty()) {
            val escaped = Regex.escape(channel)
            val separator = "(?:-|–|—|\\|)"
            val options = setOf(RegexOption.IGNORE_CASE)
            val patterns = listOf(
                Regex("^\\[\\s*$escaped\\s*]\\s*(?:$separator\\s*)?(.+)$", options),
                Regex("^$escaped\\s*$separator\\s*(.+)$", options),
                Regex("^(.+?)\\s*$separator\\s*$escaped$", options),
            )
            for (pattern in patterns) {
                val match = pattern.matchEntire(candidate) ?: continue
                candidate = normalizeText(match.groupValues[1])
                break
            }
        }
        if (removeLabels) {
            while (trailingLabel.containsMatchIn(candidate)) {
                candidate = normalizeText(trailingLabel.replace(candidate, ""))
            }
        }
        return candidate.ifEmpty { original }
    }
}
