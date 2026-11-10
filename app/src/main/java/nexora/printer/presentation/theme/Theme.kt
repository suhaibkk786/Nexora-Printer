package nexora.printer.presentation.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
import nexora.printer.domain.model.ThemeMode

val Navy = Color(0xFF0B2545)
val NavyLight = Color(0xFF12355B)
val Teal = Color(0xFF13A89E)

private val LightColors = lightColorScheme(
    primary = NavyLight,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD6E4F7),
    onPrimaryContainer = Color(0xFF001D36),
    secondary = Color(0xFF00897B),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFB2EFE6),
    onSecondaryContainer = Color(0xFF00201C),
    background = Color(0xFFF6F9FC),
    onBackground = Color(0xFF111A24),
    surface = Color(0xFFF6F9FC),
    onSurface = Color(0xFF111A24),
    surfaceVariant = Color(0xFFDDE3EA),
    onSurfaceVariant = Color(0xFF414A55)
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF9CC3F0),
    onPrimary = Color(0xFF00315A),
    primaryContainer = Color(0xFF1B4A7A),
    onPrimaryContainer = Color(0xFFD6E4F7),
    secondary = Color(0xFF4DD0C1),
    onSecondary = Color(0xFF003732),
    secondaryContainer = Color(0xFF005048),
    onSecondaryContainer = Color(0xFFB2EFE6),
    background = Color(0xFF0A1524),
    onBackground = Color(0xFFE3E8EE),
    surface = Color(0xFF0A1524),
    onSurface = Color(0xFFE3E8EE),
    surfaceVariant = Color(0xFF2A3644),
    onSurfaceVariant = Color(0xFFBCC5D0)
)

private val NexoraShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp)
)

@Composable
fun NexoraTheme(mode: ThemeMode, content: @Composable () -> Unit) {
    val dark = when (mode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    MaterialTheme(
        colorScheme = if (dark) DarkColors else LightColors,
        shapes = NexoraShapes,
        content = content
    )
}
