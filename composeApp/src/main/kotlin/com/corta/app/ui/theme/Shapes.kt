package com.corta.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * Shape scale used across the app.
 *
 * 4 / 8 / 12 / 16 / 24 dp — consistent with the latest Material 3
 * recommendations for dense, content-first UIs. Cards and containers use
 * `medium` (12dp), sheets use `large` (16dp), modal dialogs use `extraLarge`
 * (24dp), and `extraSmall`/`small` (4dp / 8dp) are reserved for chips and
 * tight pill controls.
 *
 * The previous scale (12 / 16 / 20 / 28 / 36 dp) was too heavy and made
 * cards feel like buttons; the new scale restores proper visual hierarchy.
 */
val AppShapes: Shapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(24.dp)
)
