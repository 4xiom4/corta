package com.corta.app.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext

// --- Enums & preference mapping -----------------------------------------

enum class ThemeMode {
    LIGHT, DARK, SYSTEM
}

enum class AccentColor {
    DYNAMIC,
    STYLE_1,
    STYLE_2,
    STYLE_3,
    STYLE_4,
    STYLE_5,
    STITCH_AMBER,
    STITCH_PURPLE,
    STITCH_CORAL,
    STITCH_SAGE,
    NATIVE_CLARITY;

    companion object {
        fun fromPreference(raw: String?): AccentColor {
            return when (raw) {
                "CLARITY", "NATIVE_CLARITY" -> NATIVE_CLARITY
                "EXECUTIVE" -> STYLE_1
                "FOREST" -> STYLE_2
                "OCEAN" -> STYLE_5
                "AMBER" -> STITCH_AMBER
                "PURPLE" -> STITCH_PURPLE
                "CORAL" -> STITCH_CORAL
                "SAGE" -> STITCH_SAGE
                null -> NATIVE_CLARITY
                else -> entries.firstOrNull { it.name == raw } ?: NATIVE_CLARITY
            }
        }
    }
}

enum class UiScale(val factor: Float) {
    COMPACT(0.85f),
    NORMAL(1.0f),
    LARGE(1.2f),
    EXTRA_LARGE(1.5f);

    companion object {
        fun fromPreference(raw: String?): UiScale {
            return entries.find { it.name == raw } ?: NORMAL
        }
    }
}

enum class Handedness {
    LEFT, RIGHT;

    companion object {
        fun fromPreference(raw: String?): Handedness {
            return entries.find { it.name == raw } ?: RIGHT
        }
    }
}

val LocalUiScale = staticCompositionLocalOf { UiScale.NORMAL }
val LocalHandedness = staticCompositionLocalOf { Handedness.RIGHT }
val LocalDetailedMode = staticCompositionLocalOf { false }

// --- Theme root ---------------------------------------------------------

@Composable
fun AppTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    accentColor: AccentColor = AccentColor.NATIVE_CLARITY,
    uiScale: UiScale = UiScale.NORMAL,
    handedness: Handedness = Handedness.RIGHT,
    detailedMode: Boolean = false,
    content: @Composable () -> Unit
) {
    val darkTheme = when (themeMode) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }

    val context = LocalContext.current
    val dynamicColor = accentColor == AccentColor.DYNAMIC && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    // The user's system font scale (Settings > Display > Font size) is
    // multiplied with our app-level [UiScale] so the two settings compose
    // rather than fight each other. This is the recommended pattern from
    // the Material 3 a11y guidelines.
    val effectiveScale = uiScale.factor

    val colorScheme = when {
        dynamicColor && darkTheme -> dynamicDarkColorScheme(context)
        dynamicColor && !darkTheme -> dynamicLightColorScheme(context)
        accentColor == AccentColor.NATIVE_CLARITY && darkTheme -> NativeTelecomClarityDarkSchema
        accentColor == AccentColor.NATIVE_CLARITY && !darkTheme -> NativeTelecomClarityLightSchema
        accentColor == AccentColor.STYLE_1 && darkTheme -> Style1DarkSchema
        accentColor == AccentColor.STYLE_1 && !darkTheme -> Style1LightSchema
        accentColor == AccentColor.STYLE_2 && darkTheme -> Style2DarkSchema
        accentColor == AccentColor.STYLE_2 && !darkTheme -> Style2LightSchema
        accentColor == AccentColor.STYLE_3 && darkTheme -> Style3DarkSchema
        accentColor == AccentColor.STYLE_3 && !darkTheme -> Style3LightSchema
        accentColor == AccentColor.STYLE_4 && darkTheme -> Style4DarkSchema
        accentColor == AccentColor.STYLE_4 && !darkTheme -> Style4LightSchema
        accentColor == AccentColor.STYLE_5 && darkTheme -> Style5DarkSchema
        accentColor == AccentColor.STYLE_5 && !darkTheme -> Style5LightSchema
        accentColor == AccentColor.STITCH_AMBER && darkTheme -> StitchAmberDarkSchema
        accentColor == AccentColor.STITCH_AMBER && !darkTheme -> StitchAmberLightSchema
        accentColor == AccentColor.STITCH_PURPLE && darkTheme -> StitchPurpleDarkSchema
        accentColor == AccentColor.STITCH_PURPLE && !darkTheme -> StitchPurpleLightSchema
        accentColor == AccentColor.STITCH_CORAL && darkTheme -> StitchCoralDarkSchema
        accentColor == AccentColor.STITCH_CORAL && !darkTheme -> StitchCoralLightSchema
        accentColor == AccentColor.STITCH_SAGE && darkTheme -> StitchSageDarkSchema
        accentColor == AccentColor.STITCH_SAGE && !darkTheme -> StitchSageLightSchema
        darkTheme -> NativeTelecomClarityDarkSchema
        else -> NativeTelecomClarityLightSchema
    }

    val scaledTypography = Typography(
        displayLarge = AppTypography.displayLarge.copy(
            fontSize = AppTypography.displayLarge.fontSize * effectiveScale,
            lineHeight = AppTypography.displayLarge.lineHeight * effectiveScale
        ),
        displayMedium = AppTypography.displayMedium.copy(
            fontSize = AppTypography.displayMedium.fontSize * effectiveScale,
            lineHeight = AppTypography.displayMedium.lineHeight * effectiveScale
        ),
        headlineSmall = AppTypography.headlineSmall.copy(
            fontSize = AppTypography.headlineSmall.fontSize * effectiveScale,
            lineHeight = AppTypography.headlineSmall.lineHeight * effectiveScale
        ),
        titleLarge = AppTypography.titleLarge.copy(
            fontSize = AppTypography.titleLarge.fontSize * effectiveScale,
            lineHeight = AppTypography.titleLarge.lineHeight * effectiveScale
        ),
        titleMedium = AppTypography.titleMedium.copy(
            fontSize = AppTypography.titleMedium.fontSize * effectiveScale,
            lineHeight = AppTypography.titleMedium.lineHeight * effectiveScale
        ),
        bodyLarge = AppTypography.bodyLarge.copy(
            fontSize = AppTypography.bodyLarge.fontSize * effectiveScale,
            lineHeight = AppTypography.bodyLarge.lineHeight * effectiveScale
        ),
        bodyMedium = AppTypography.bodyMedium.copy(
            fontSize = AppTypography.bodyMedium.fontSize * effectiveScale,
            lineHeight = AppTypography.bodyMedium.lineHeight * effectiveScale
        ),
        labelLarge = AppTypography.labelLarge.copy(
            fontSize = AppTypography.labelLarge.fontSize * effectiveScale
        ),
        labelMedium = AppTypography.labelMedium.copy(
            fontSize = AppTypography.labelMedium.fontSize * effectiveScale
        )
    )

    CompositionLocalProvider(
        LocalUiScale provides uiScale,
        LocalHandedness provides handedness,
        LocalDetailedMode provides detailedMode
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = scaledTypography,
            shapes = AppShapes,
            content = content
        )
    }
}
