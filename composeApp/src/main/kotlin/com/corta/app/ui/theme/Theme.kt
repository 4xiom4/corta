package com.corta.app.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

enum class ThemeMode {
    LIGHT, DARK, SYSTEM
}

enum class AccentColor {
    DYNAMIC, EXECUTIVE, OCEAN, FOREST
}

// Executive Class Brand Colors - Polished & Professional
private val BrandPrimary = Color(0xFF1A2F4A)      // Deep Navy
private val BrandSecondary = Color(0xFF4A5568)    // Cool Gray
private val BrandAccent = Color(0xFF8B0000)       // Deep Red (Subtle)
private val BrandText = Color(0xFF1A202C)         // Almost Black
private val BrandBackground = Color(0xFFF8FAFC)   // Very Light Gray/White

private val ExecutiveLightSchema = lightColorScheme(
    primary = BrandPrimary,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE2E8F0),
    onPrimaryContainer = BrandPrimary,
    secondary = BrandSecondary,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFEDF2F7),
    onSecondaryContainer = BrandSecondary,
    tertiary = BrandAccent,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFE5E5),
    onTertiaryContainer = BrandAccent,
    background = BrandBackground,
    onBackground = BrandText,
    surface = Color.White,
    onSurface = BrandText,
    surfaceVariant = Color(0xFFF1F5F9),
    onSurfaceVariant = BrandSecondary,
    outline = Color(0xFFCBD5E0),
    error = Color(0xFFE53E3E),
    onError = Color.White,
    errorContainer = Color(0xFFFFF5F5),
    onErrorContainer = Color(0xFFC53030)
)

private val ExecutiveDarkSchema = darkColorScheme(
    primary = Color(0xFFA3BFFA), // Softer blue for dark mode
    onPrimary = Color(0xFF1A2F4A),
    primaryContainer = Color(0xFF2D3748),
    onPrimaryContainer = Color(0xFFEDF2F7),
    secondary = Color(0xFFA0AEC0),
    onSecondary = Color(0xFF1A202C),
    secondaryContainer = Color(0xFF2D3748),
    onSecondaryContainer = Color(0xFFE2E8F0),
    tertiary = Color(0xFFFC8181),
    onTertiary = Color(0xFF63171B),
    tertiaryContainer = Color(0xFF9B2C2C),
    onTertiaryContainer = Color(0xFFFFF5F5),
    background = Color(0xFF0F172A),
    onBackground = Color(0xFFF1F5F9),
    surface = Color(0xFF1E293B),
    onSurface = Color(0xFFF8FAFC),
    surfaceVariant = Color(0xFF334155),
    onSurfaceVariant = Color(0xFFE2E8F0),
    outline = Color(0xFF475569)
)

private val OceanLightSchema = lightColorScheme(
    primary = Color(0xFF0F4C81),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD3E4FF),
    onPrimaryContainer = Color(0xFF001C36),
    background = Color(0xFFF7FAFC),
    surface = Color.White
)

private val OceanDarkSchema = darkColorScheme(
    primary = Color(0xFF9FCAFF),
    onPrimary = Color(0xFF003258),
    background = Color(0xFF0B1117),
    surface = Color(0xFF11181F)
)

private val ForestLightSchema = lightColorScheme(
    primary = Color(0xFF1D5B47),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFC1F0DD),
    onPrimaryContainer = Color(0xFF002117),
    background = Color(0xFFF7FBF8),
    surface = Color.White
)

private val ForestDarkSchema = darkColorScheme(
    primary = Color(0xFFA6DCC6),
    onPrimary = Color(0xFF003829),
    background = Color(0xFF0E1311),
    surface = Color(0xFF151B18)
)

private val AppShapes = Shapes(
    extraSmall = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
    small = androidx.compose.foundation.shape.RoundedCornerShape(14.dp),
    medium = androidx.compose.foundation.shape.RoundedCornerShape(20.dp),
    large = androidx.compose.foundation.shape.RoundedCornerShape(28.dp),
    extraLarge = androidx.compose.foundation.shape.RoundedCornerShape(36.dp)
)

private val AppTypography = Typography(
    displayMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Bold,
        fontSize = 34.sp,
        letterSpacing = (-0.5).sp
    ),
    headlineSmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Bold,
        fontSize = 26.sp,
        letterSpacing = 0.sp
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Bold,
        fontSize = 22.sp,
        letterSpacing = 0.sp
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 19.sp,
        letterSpacing = 0.1.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 17.sp,
        lineHeight = 26.sp,
        letterSpacing = 0.25.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 22.sp,
        letterSpacing = 0.2.sp
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Bold,
        fontSize = 15.sp,
        letterSpacing = 0.1.sp
    ),
    labelMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 13.sp,
        letterSpacing = 0.5.sp
    )
)

@Composable
fun AppTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    accentColor: AccentColor = AccentColor.EXECUTIVE,
    content: @Composable () -> Unit
) {
    val darkTheme = when (themeMode) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }

    val context = LocalContext.current
    val dynamicColor = accentColor == AccentColor.DYNAMIC && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    val colorScheme = when {
        dynamicColor && darkTheme -> dynamicDarkColorScheme(context)
        dynamicColor && !darkTheme -> dynamicLightColorScheme(context)
        accentColor == AccentColor.OCEAN && darkTheme -> OceanDarkSchema
        accentColor == AccentColor.OCEAN && !darkTheme -> OceanLightSchema
        accentColor == AccentColor.FOREST && darkTheme -> ForestDarkSchema
        accentColor == AccentColor.FOREST && !darkTheme -> ForestLightSchema
        darkTheme -> ExecutiveDarkSchema
        else -> ExecutiveLightSchema
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = AppTypography,
        shapes = AppShapes,
        content = content
    )
}
