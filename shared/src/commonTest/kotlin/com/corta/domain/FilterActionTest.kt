package com.corta.domain

import kotlin.test.Test
import kotlin.test.assertEquals

class FilterActionTest {

    @Test
    fun `fromString returns matching enum value`() {
        assertEquals(FilterAction.ALLOW, FilterAction.fromString("ALLOW"))
        assertEquals(FilterAction.BLOCK, FilterAction.fromString("BLOCK"))
        assertEquals(FilterAction.MUTE, FilterAction.fromString("MUTE"))
        assertEquals(FilterAction.WARN, FilterAction.fromString("WARN"))
    }

    @Test
    fun `fromString defaults to ALLOW on unknown value`() {
        assertEquals(FilterAction.ALLOW, FilterAction.fromString("UNKNOWN"))
        assertEquals(FilterAction.ALLOW, FilterAction.fromString(""))
        assertEquals(FilterAction.ALLOW, FilterAction.fromString("allow"))
    }

    @Test
    fun `enum name roundtrips through fromString`() {
        FilterAction.entries.forEach { action ->
            assertEquals(action, FilterAction.fromString(action.name))
        }
    }
}
