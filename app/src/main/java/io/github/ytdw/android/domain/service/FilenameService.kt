package io.github.ytdw.android.domain.service

import java.text.Normalizer

object FilenameService {
    private val whitespace = Regex("\\s+")
    private val invalid = Regex("[<>:\"/\\\\|?*\\u0000-\\u001f]")
    private val reserved = Regex(
        "^(?:CON|PRN|AUX|NUL|CLOCK\\$|CONIN\\$|CONOUT\\$|COM[1-9¹²³]|LPT[1-9¹²³])$",
        RegexOption.IGNORE_CASE,
    )

    fun sanitize(value: String, maxLength: Int = 240): String {
        var candidate = Normalizer.normalize(value, Normalizer.Form.NFC)
            .replace(whitespace, " ").trim().replace(invalid, "").trimEnd(' ', '.')
        if (candidate.isEmpty() || candidate == "." || candidate == "..") candidate = "untitled"
        val dot = candidate.indexOf('.')
        val stem = if (dot >= 0) candidate.substring(0, dot).trimEnd(' ', '.') else candidate
        if (reserved.matches(stem)) {
            candidate = if (dot >= 0) "${stem}_${candidate.substring(dot)}" else "${stem}_"
        }
        if (candidate.length > maxLength) {
            val lastDot = candidate.lastIndexOf('.')
            val suffix = if (lastDot in 1 until candidate.length && candidate.length - lastDot <= 20) {
                candidate.substring(lastDot)
            } else ""
            candidate = candidate.take(maxLength - suffix.length).trimEnd(' ', '.') + suffix
        }
        return candidate.ifEmpty { "untitled" }
    }

    fun uniqueName(proposed: String, exists: (String) -> Boolean, preferredSuffix: String? = null): String {
        if (!exists(proposed)) return proposed
        val dot = proposed.lastIndexOf('.')
        val stem = if (dot > 0) proposed.substring(0, dot) else proposed
        val extension = if (dot > 0) proposed.substring(dot) else ""
        preferredSuffix?.let {
            val preferred = "$stem (${sanitize(it)})$extension"
            if (!exists(preferred)) return preferred
        }
        for (index in 2..10_000) {
            val candidate = "$stem ($index)$extension"
            if (!exists(candidate)) return candidate
        }
        throw IllegalStateException("Could not allocate a unique filename")
    }
}
