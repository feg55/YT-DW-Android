package io.github.ytdw.android.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import io.github.ytdw.android.domain.model.ThemePreference

private val dark = darkColorScheme(primary = Color(0xFFFFB4AB), secondary = Color(0xFFE7BDB8))
private val light = lightColorScheme(primary = Color(0xFF9C423B), secondary = Color(0xFF775653))
private val original = darkColorScheme(
    primary = Color(0xFFE53935), secondary = Color(0xFFFF5252), background = Color(0xFF080808),
    surface = Color(0xFF141414), surfaceVariant = Color(0xFF242020), onPrimary = Color.White,
)

@Composable
fun YtdwTheme(preference: ThemePreference, content: @Composable () -> Unit) {
    val colors = when (preference) {
        ThemePreference.DARK -> dark
        ThemePreference.LIGHT -> light
        ThemePreference.ORIGINAL -> original
        ThemePreference.SYSTEM -> if (isSystemInDarkTheme()) dark else light
    }
    MaterialTheme(colorScheme = colors, content = content)
}
