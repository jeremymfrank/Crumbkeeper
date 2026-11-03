package com.crumbkeeper.app.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val Gold = Color(0xFFD8B679)
private val Ivory = Color(0xFFF1E9DC)

internal val GildedTypography = Typography(
    headlineMedium = TextStyle(fontFamily = FontFamily.Serif, fontSize = 30.sp, lineHeight = 36.sp),
    headlineSmall = TextStyle(fontFamily = FontFamily.Serif, fontSize = 26.sp, lineHeight = 32.sp),
    titleLarge = TextStyle(fontFamily = FontFamily.Serif, fontSize = 22.sp, lineHeight = 28.sp),
    titleMedium = TextStyle(fontFamily = FontFamily.Serif, fontSize = 20.sp, lineHeight = 26.sp),
    bodyLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 18.sp, lineHeight = 27.sp),
    bodyMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 16.sp, lineHeight = 23.sp),
    bodySmall = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 13.sp, lineHeight = 19.sp),
    labelLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium, fontSize = 15.sp, lineHeight = 21.sp),
    labelMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium, fontSize = 13.sp, lineHeight = 18.sp),
    labelSmall = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 12.sp, lineHeight = 17.sp)
)

@Composable
internal fun GildedTheme(dark: Boolean = true, content: @Composable () -> Unit) {
    val colors = if (dark) darkColorScheme(
        primary = Gold, onPrimary = Color(0xFF241B0E),
        primaryContainer = Color(0xFF4B3A23), onPrimaryContainer = Ivory,
        secondary = Gold, onSecondary = Color(0xFF241B0E),
        tertiary = Color(0xFFC5A2E8), onTertiary = Color(0xFF251432),
        background = Color(0xFF17151A), onBackground = Ivory,
        surface = Color(0xFF17151A), onSurface = Ivory,
        surfaceVariant = Color(0xFF29242B), onSurfaceVariant = Color(0xFFC9BDCB),
        surfaceContainer = Color(0xFF29242B), surfaceContainerHigh = Color(0xFF322B35),
        surfaceContainerHighest = Color(0xFF3B323E),
        outline = Color(0xFF9B899D), outlineVariant = Color(0xFF51434F)
    ) else lightColorScheme(
        primary = Color(0xFF74531F), onPrimary = Color.White,
        secondary = Color(0xFF74531F), tertiary = Color(0xFF744393), onTertiary = Color.White,
        background = Color(0xFFF7F0E5), onBackground = Color(0xFF28212A),
        surface = Color(0xFFF7F0E5), onSurface = Color(0xFF28212A),
        surfaceVariant = Color(0xFFEDE1D3), onSurfaceVariant = Color(0xFF625565),
        outline = Color(0xFF8A7984), outlineVariant = Color(0xFFD0BFCA)
    )
    MaterialTheme(colorScheme = colors, typography = GildedTypography,
        shapes = Shapes(small = RoundedCornerShape(9.dp), medium = RoundedCornerShape(14.dp),
            large = RoundedCornerShape(18.dp)), content = content)
}
