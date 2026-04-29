package ru.bizsupport.desktop.ui.theme

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// ── Colors matching web design ────────────────────────────────
val Navy = Color(0xFF0F172A)
val NavyLight = Color(0xFF1E293B)
val Accent = Color(0xFF3B82F6)
val AccentLight = Color(0xFFEFF6FF)
val Success = Color(0xFF10B981)
val SuccessLight = Color(0xFFECFDF5)
val Warning = Color(0xFFF59E0B)
val WarningLight = Color(0xFFFFFBEB)
val Danger = Color(0xFFEF4444)
val DangerLight = Color(0xFFFEF2F2)
val Info = Color(0xFF06B6D4)
val InfoLight = Color(0xFFECFEFF)
val Purple = Color(0xFF8B5CF6)
val PurpleLight = Color(0xFFF5F3FF)
val TextPrimary = Color(0xFF1E293B)
val TextMuted = Color(0xFF64748B)
val Border = Color(0xFFE2E8F0)
val Background = Color(0xFFF8FAFC)
val CardBg = Color.White

private val LightColorScheme = lightColorScheme(
    primary = Accent,
    onPrimary = Color.White,
    primaryContainer = AccentLight,
    secondary = Success,
    secondaryContainer = SuccessLight,
    tertiary = Info,
    tertiaryContainer = InfoLight,
    background = Background,
    surface = CardBg,
    surfaceVariant = Color(0xFFF1F5F9),
    onBackground = TextPrimary,
    onSurface = TextPrimary,
    onSurfaceVariant = TextMuted,
    outline = Border,
    error = Danger,
    errorContainer = DangerLight,
)

@Composable
fun BizSupportTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = LightColorScheme,
        typography = Typography(
            headlineLarge = TextStyle(fontSize = 28.sp, fontWeight = FontWeight.Bold, color = TextPrimary),
            headlineMedium = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.Bold, color = TextPrimary),
            headlineSmall = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary),
            titleLarge = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary),
            titleMedium = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Medium, color = TextPrimary),
            bodyLarge = TextStyle(fontSize = 15.sp, color = TextPrimary),
            bodyMedium = TextStyle(fontSize = 14.sp, color = TextPrimary),
            bodySmall = TextStyle(fontSize = 12.sp, color = TextMuted),
            labelLarge = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Medium, color = TextMuted),
            labelSmall = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Medium, color = TextMuted, letterSpacing = 0.5.sp),
        ),
        content = content
    )
}
