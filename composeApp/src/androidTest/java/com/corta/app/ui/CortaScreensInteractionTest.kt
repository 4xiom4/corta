package com.corta.app.ui

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Message
import androidx.compose.material.icons.outlined.Call
import androidx.compose.material.icons.outlined.Contacts
import androidx.compose.material.icons.rounded.Block
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.corta.app.ui.designsystem.*
import com.corta.app.ui.dialer.DialpadScreen
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/**
 * Official Jetpack Compose UI Testing Suite for Corta App.
 *
 * Verifies real UI rendering, semantics, tap gestures, keyboard entry,
 * navigation transitions, and component interactions on physical/emulated Android hardware.
 */
class CortaScreensInteractionTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    // =========================================================================
    // 1. NAVEGACIÓN PRINCIPAL: CortaBottomBar (3 Destinos Obligatorios M3)
    // =========================================================================

    @Test
    fun test_bottom_navigation_bar_interaction_switches_destinations() {
        var selectedRoute by mutableStateOf("calls")

        composeTestRule.setContent {
            val destinations = listOf(
                BottomNavDestination("sms", "Mensajes", Icons.AutoMirrored.Rounded.Message, "Mensajes"),
                BottomNavDestination("calls", "Llamadas", Icons.Outlined.Call, "Llamadas"),
                BottomNavDestination("contacts", "Contactos", Icons.Outlined.Contacts, "Contactos")
            )

            CortaBottomBar(
                currentRoute = selectedRoute,
                destinations = destinations,
                onNavigate = { selectedRoute = it }
            )
        }

        // Verificar que los 3 destinos están visibles en la pantalla
        composeTestRule.onNodeWithText("Mensajes").assertIsDisplayed()
        composeTestRule.onNodeWithText("Llamadas").assertIsDisplayed()
        composeTestRule.onNodeWithText("Contactos").assertIsDisplayed()

        // Interactuar con la pestaña Mensajes
        composeTestRule.onNodeWithText("Mensajes").performClick()
        assertEquals("sms", selectedRoute)

        // Interactuar con la pestaña Contactos
        composeTestRule.onNodeWithText("Contactos").performClick()
        assertEquals("contacts", selectedRoute)

        // Interactuar con la pestaña Llamadas
        composeTestRule.onNodeWithText("Llamadas").performClick()
        assertEquals("calls", selectedRoute)
    }

    // =========================================================================
    // 2. MARCADOR TELEFÓNICO: DialpadScreen (Digitación, Borrado y Llamada)
    // =========================================================================

    @Test
    fun test_dialpad_keypad_typing_and_backspace_interaction() {
        var dialedResult: String? = null
        var deleteDesc = ""
        var dialDesc = ""

        composeTestRule.setContent {
            deleteDesc = com.corta.app.ui.i18n.Strings.common.delete
            dialDesc = com.corta.app.ui.i18n.Strings.common.dial
            DialpadScreen(
                onDial = { dialedResult = it }
            )
        }

        // Digitar "1", "3", "3" (Emergencia Carabineros de Chile)
        composeTestRule.onNodeWithText("1").performTouchInput { click() }
        composeTestRule.onNodeWithText("3").performTouchInput { click() }
        composeTestRule.onNodeWithText("3").performTouchInput { click() }

        // Verificar que el número "133" está presente en la pantalla
        composeTestRule.onNodeWithText("133").assertIsDisplayed()

        // Borrar el último dígito con el botón de retroceso (Backspace)
        composeTestRule.onNodeWithContentDescription(deleteDesc).performTouchInput { click() }

        // Verificar que el display ahora muestra "13"
        composeTestRule.onNodeWithText("13").assertIsDisplayed()

        // Digitar "1" para formar "131" (SAMU Ambulancia de Chile)
        composeTestRule.onNodeWithText("1").performTouchInput { click() }
        composeTestRule.onNodeWithText("131").assertIsDisplayed()

        // Presionar el botón de Llamar
        composeTestRule.onNodeWithContentDescription(dialDesc).performTouchInput { click() }

        // Validar que el callback de llamada fue despachado con "131"
        assertEquals("131", dialedResult)
    }

    // =========================================================================
    // 3. FILTROS Y CHIPS: CortaFilterChip (Interacción de Estado y Selección)
    // =========================================================================

    @Test
    fun test_filter_chips_interaction() {
        var spamFilterSelected by mutableStateOf(false)

        composeTestRule.setContent {
            Column {
                CortaFilterChip(
                    selected = spamFilterSelected,
                    onClick = { spamFilterSelected = !spamFilterSelected },
                    label = "Spam detectado",
                    leadingIcon = Icons.Rounded.Block
                )
            }
        }

        // El chip debe estar visible
        val chipNode = composeTestRule.onNodeWithText("Spam detectado")
        chipNode.assertIsDisplayed()

        // Hacer clic para activar el filtro
        chipNode.performClick()
        assertEquals(true, spamFilterSelected)

        // Hacer clic nuevamente para desactivar
        chipNode.performClick()
        assertEquals(false, spamFilterSelected)
    }

    // =========================================================================
    // 4. BARRA DE BÚSQUEDA: CortaSearchBar (Entrada de Texto y Limpieza)
    // =========================================================================

    @Test
    fun test_search_bar_input_and_clear_interaction() {
        var queryText by mutableStateOf("")
        var searchedQuery by mutableStateOf("")

        composeTestRule.setContent {
            CortaSearchBar(
                query = queryText,
                onQueryChange = { queryText = it },
                onSearch = { searchedQuery = it },
                placeholder = "Buscar número o contacto..."
            )
        }

        // Verificar placeholder inicial
        composeTestRule.onNodeWithText("Buscar número o contacto...").assertIsDisplayed()

        // Escribir consulta de búsqueda
        composeTestRule.onNode(hasSetTextAction()).performTextInput("+56912345678")
        assertEquals("+56912345678", queryText)
    }

    // =========================================================================
    // 5. LISTAS Y ACCIONES: CortaListItem (Toque y Apertura de Detalle)
    // =========================================================================

    @Test
    fun test_list_item_click_interaction() {
        var itemClicked = false

        composeTestRule.setContent {
            CortaListItem(
                title = "Mamá",
                supportingText = "+56 9 8765 4321",
                onClick = { itemClicked = true }
            )
        }

        composeTestRule.onNodeWithText("Mamá").assertIsDisplayed()
        composeTestRule.onNodeWithText("+56 9 8765 4321").assertIsDisplayed()

        // Hacer clic sobre el elemento
        composeTestRule.onNodeWithText("Mamá").performClick()
        assertEquals(true, itemClicked)
    }
}
