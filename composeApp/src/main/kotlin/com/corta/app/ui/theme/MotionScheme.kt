package com.corta.app.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween

/**
 * Motion tokens for the app.
 *
 * Two pre-canned easings (a snappy standard and a soft "expressive" one)
 * and three spring presets that cover the bulk of UI animations. These
 * tokens are intentionally simple: any composable that needs a custom
 * curve should still fall back to one of these unless there is a measured
 * reason not to.
 *
 * Naming follows Material 3 motion vocabulary so future migrations stay
 * one-to-one.
 */
object MotionScheme {

    /** Standard short easing for most UI transitions (180ms, slight overshoot). */
    val StandardEasing: Easing = CubicBezierEasing(0.2f, 0.0f, 0.0f, 1.0f)

    /** Soft easing for hero / entry transitions (300ms, gentle landing). */
    val EmphasizedEasing: Easing = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1.0f)

    /** Linear easing for time-bound progress (e.g. spinners, recorders). */
    val LinearEasing: Easing = CubicBezierEasing(0.0f, 0.0f, 1.0f, 1.0f)

    /** Short, snappy spring for taps and small element movements. */
    fun snappySpring() = spring<Float>(
        dampingRatio = Spring.DampingRatioMediumBouncy,
        stiffness = Spring.StiffnessMedium
    )

    /** Soft, expressive spring for page transitions and large sheets. */
    fun softSpring() = spring<Float>(
        dampingRatio = Spring.DampingRatioLowBouncy,
        stiffness = Spring.StiffnessLow
    )

    /** No-bounce spring for precision UI (sliders, value changes). */
    fun preciseSpring() = spring<Float>(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessHigh
    )

    /** Standard duration for fade / crossfade animations. */
    const val ShortDurationMs = 180
    const val MediumDurationMs = 300
    const val LongDurationMs = 500

    fun shortTween() = tween<Float>(ShortDurationMs, easing = StandardEasing)
    fun mediumTween() = tween<Float>(MediumDurationMs, easing = EmphasizedEasing)
    fun longTween() = tween<Float>(LongDurationMs, easing = EmphasizedEasing)
}
