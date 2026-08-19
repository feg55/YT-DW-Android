package io.github.ytdw.android.domain.service

import io.github.ytdw.android.domain.model.DownloadProgress

object ProgressParser {
    private val speedPattern = Regex("at\\s+([0-9.]+)(KiB|MiB|GiB)/s", RegexOption.IGNORE_CASE)
    private val totalPattern = Regex("of\\s+(?:~\\s*)?([0-9.]+)(KiB|MiB|GiB)", RegexOption.IGNORE_CASE)

    fun fromCallback(percentage: Float, etaSeconds: Long, line: String): DownloadProgress {
        val total = totalPattern.find(line)?.let { bytes(it.groupValues[1], it.groupValues[2]) }
        val speed = speedPattern.find(line)?.let { bytes(it.groupValues[1], it.groupValues[2]).toDouble() }
        val bounded = percentage.toDouble().coerceIn(0.0, 100.0)
        val downloaded = total?.let { (it * bounded / 100.0).toLong() } ?: 0L
        val phase = when {
            "merging" in line.lowercase() -> "Merging"
            "extractaudio" in line.lowercase() || "destination" in line.lowercase() && "m4a" in line.lowercase() -> "Converting"
            "metadata" in line.lowercase() -> "Writing metadata"
            else -> "Downloading media"
        }
        return DownloadProgress(bounded, downloaded, total, speed, etaSeconds.takeIf { it >= 0 }, phase)
    }

    private fun bytes(value: String, unit: String): Long {
        val multiplier = when (unit.lowercase()) {
            "kib" -> 1024.0
            "mib" -> 1024.0 * 1024.0
            else -> 1024.0 * 1024.0 * 1024.0
        }
        return (value.toDoubleOrNull() ?: 0.0).times(multiplier).toLong()
    }
}
