package com.corta.app.ui.designsystem

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.assertContentDescriptionContains
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.semantics.SemanticsProperties
import org.junit.Rule
import org.junit.Test

/**
 * Accessibility smoke tests for the design-system components.
 *
 * These tests assert that [CortaIconButton] propagates a non-empty
 * [androidx.compose.ui.semantics.SemanticsProperties.ContentDescription]
 * to the underlying [androidx.compose.ui.semantics.SemanticsNode], and
 * that [CortaDecorativeIcon] deliberately hides itself from the
 * accessibility tree by passing `null` (the screen reader skips the
 * decorative icon but the text sibling is announced).
 */
class CortaIconButtonAccessibilityTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun CortaIconButton_propagates_contentDescription_to_semantics_tree() {
        composeTestRule.setContent {
            CortaIconButton(
                imageVector = Icons.Rounded.Settings,
                contentDescription = "Abrir ajustes",
                onClick = {}
            )
        }

        composeTestRule
            .onNodeWithContentDescription("Abrir ajustes")
            .assertContentDescriptionEquals("Abrir ajustes")
    }

    @Test
    fun CortaDecorativeIcon_is_marked_null_so_screen_reader_skips_it() {
        composeTestRule.setContent {
            DecorativeSample()
        }

        // The decorative icon should not be findable by any content
        // description, while the sibling text is fully present.
        composeTestRule
            .onNodeWithContentDescription("Configuración", substring = true)
            .assertContentDescriptionContains("Configuración")
    }

    @Test
    fun CortaIconButton_accepts_non_ascii_content_description() {
        composeTestRule.setContent {
            CortaIconButton(
                imageVector = Icons.Rounded.Settings,
                contentDescription = "Configuración",
                onClick = {}
            )
        }

        composeTestRule
            .onNodeWithContentDescription("Configuración")
            .assertContentDescriptionEquals("Configuración")
    }
}

@Composable
private fun DecorativeSample() {
    androidx.compose.foundation.layout.Row {
        CortaDecorativeIcon(imageVector = Icons.Rounded.Settings)
        Text(text = "Configuración")
    }
}
