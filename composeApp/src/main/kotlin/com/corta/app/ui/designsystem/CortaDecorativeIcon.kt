package com.corta.app.ui.designsystem

import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Wrapper around [Icon] that explicitly marks the icon as decorative.
 *
 * Decorative icons sit next to text or another control that already
 * carries the semantic meaning. Passing a non-null `contentDescription`
 * here would be noise for screen readers, so this helper exists to make
 * the intent visible at the call site.
 */
@Composable
fun CortaDecorativeIcon(
    imageVector: ImageVector,
    modifier: Modifier = Modifier,
    tint: Color = LocalContentColor.current
) {
    Icon(
        imageVector = imageVector,
        contentDescription = null,
        modifier = modifier,
        tint = tint
    )
}
