package io.github.ytdw.android.util

import java.util.Locale

data class MediaStoreLocation(val volumeName: String, val relativePath: String)

object MediaStoreLocationParser {
    fun fromTreeDocumentId(documentId: String, requiredRoot: String): MediaStoreLocation {
        val separator = documentId.indexOf(':')
        require(separator > 0 && separator < documentId.lastIndex) {
            "Select a folder inside $requiredRoot"
        }
        val storageId = documentId.substring(0, separator)
        val segments = documentId.substring(separator + 1)
            .replace('\\', '/')
            .trim('/')
            .split('/')
            .filter(String::isNotBlank)
        require(segments.isNotEmpty() && segments.first().equals(requiredRoot, ignoreCase = true)) {
            "Select a folder inside $requiredRoot"
        }
        require(segments.none { it == "." || it == ".." || '\u0000' in it }) {
            "The selected folder is not valid"
        }
        val volumeName = if (storageId.equals("primary", ignoreCase = true)) {
            "external_primary"
        } else {
            storageId.lowercase(Locale.ROOT)
        }
        val relativePath = buildList {
            add(requiredRoot)
            addAll(segments.drop(1))
        }.joinToString("/")
        return MediaStoreLocation(volumeName, relativePath)
    }
}
