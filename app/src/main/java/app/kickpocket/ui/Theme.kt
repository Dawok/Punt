package app.kickpocket.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val KickGreen = Color(0xFF53FC18)
val AppBackground = Color(0xFF0C0E10)
val PanelColor = Color(0xFF14171B)
val SubtleColor = Color(0xFF949BA5)

private val Colors = darkColorScheme(
    primary = KickGreen,
    onPrimary = Color(0xFF102006),
    secondary = Color(0xFFA6E88F),
    background = AppBackground,
    onBackground = Color(0xFFF1F3F5),
    surface = PanelColor,
    surfaceContainer = PanelColor,
    onSurface = Color(0xFFF1F3F5),
    onSurfaceVariant = SubtleColor,
    outline = Color(0xFF343A42),
    error = Color(0xFFFFB4AB),
)

@Composable
fun PuntTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Colors, typography = Typography(), content = content)
}

