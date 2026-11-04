package ru.diary.app.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import ru.diary.app.data.settings.ThemeConfig
import ru.diary.app.data.settings.ThemePresets

private val DarkBg = Color(0xFF111014)
private val DarkOnBg = Color(0xFFE6E1E5)
private val DarkSurfaceVariant = Color(0xFF49454F)
private val DarkOnSurfaceVariant = Color(0xFFCAC4D0)
private val DarkOutline = Color(0xFF938F99)

@Composable
fun DiaryTheme(
    config: ThemeConfig = ThemeConfig(),
    content: @Composable () -> Unit
) {
    val accent = ThemePresets.findAccent(config.accentColorKey)
    val darkTheme = if (config.isDarkMode) true else isSystemInDarkTheme()

    val primaryLight = Color(accent.primaryLight)
    val primaryDark = Color(accent.primaryDark)

    val lightColors = lightColorScheme(
        primary = primaryLight,
        onPrimary = Color(0xFFFFFBFE),
        primaryContainer = primaryLight.copy(alpha = 0.3f),
        onPrimaryContainer = primaryLight,
        secondary = primaryLight,
        background = Color(0xFFFFFBFE),
        onBackground = Color(0xFF1C1B1F),
        surface = Color(0xFFFFFBFE),
        onSurface = Color(0xFF1C1B1F),
        surfaceVariant = Color(0xFFE7E0EC),
        onSurfaceVariant = Color(0xFF49454F),
        outline = Color(0xFF79747E)
    )

    val darkColors = darkColorScheme(
        primary = primaryDark,
        onPrimary = Color(0xFF4F378B),
        primaryContainer = Color(0xFF4F378B),
        onPrimaryContainer = primaryDark,
        secondary = primaryDark,
        background = DarkBg,
        onBackground = DarkOnBg,
        surface = DarkBg,
        onSurface = DarkOnBg,
        surfaceVariant = DarkSurfaceVariant,
        onSurfaceVariant = DarkOnSurfaceVariant,
        outline = DarkOutline
    )

    val colors = if (darkTheme) darkColors else lightColors
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            // Начиная с Android 15 цвет системных панелей задаётся темой/контентом
            // (edge-to-edge), поэтому управляем только цветом иконок статус-бара.
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }
    MaterialTheme(
        colorScheme = colors,
        typography = AppTypography,
        content = content
    )
}
