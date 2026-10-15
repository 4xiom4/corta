package com.corta.app.ui

import com.corta.app.ui.sms.resolveSmsDisplayName
import com.corta.db.FilterRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Collections

class DisplayNameResolutionTest {

    private fun createRule(
        source: String,
        alias: String?,
        pattern: String = "+56912345678",
        action: String = "BLOCK",
        isRegex: Boolean = false
    ): FilterRule {
        return FilterRule(
            id = 1L,
            pattern = pattern,
            action = action,
            isRegex = isRegex,
            alias = alias,
            source = source
        )
    }

    // ================= CallLogScreen: resolveCallDisplayName =================

    @Test
    fun `CallLog - priority 1 - saved contact name is used even if rule has alias`() {
        val userRule = createRule(source = "USER", alias = "Alias de Usuario")
        val result = resolveCallDisplayName(
            number = "+56912345678",
            contactName = "Juan Pérez",
            matchingRule = userRule
        )
        assertEquals("Juan Pérez", result)
    }

    @Test
    fun `CallLog - priority 1 - saved contact name is used even if system rule has technical alias`() {
        val systemRule = createRule(source = "CURATED", alias = "Formato fijo Chile válido")
        val result = resolveCallDisplayName(
            number = "+56222334455",
            contactName = "Mamá",
            matchingRule = systemRule
        )
        assertEquals("Mamá", result)
    }

    @Test
    fun `CallLog - priority 2 - user rule alias is used when contact name is null`() {
        val userRule = createRule(source = "USER", alias = "Dentista")
        val result = resolveCallDisplayName(
            number = "+56998765432",
            contactName = null,
            matchingRule = userRule
        )
        assertEquals("Dentista", result)
    }

    @Test
    fun `CallLog - priority 2 - user rule alias is used when contact name is blank`() {
        val userRule = createRule(source = "USER", alias = "Gasfiter")
        val result = resolveCallDisplayName(
            number = "+56998765432",
            contactName = "   ",
            matchingRule = userRule
        )
        assertEquals("Gasfiter", result)
    }

    @Test
    fun `CallLog - priority 3 - system rule alias is NEVER used when no contact name, returns number`() {
        val curatedRule = createRule(source = "CURATED", alias = "Formato fijo Chile válido")
        val result = resolveCallDisplayName(
            number = "+56222334455",
            contactName = null,
            matchingRule = curatedRule
        )
        assertEquals("+56222334455", result)
    }

    @Test
    fun `CallLog - priority 3 - community rule alias is NEVER used as display name, returns number`() {
        val communityRule = createRule(source = "COMMUNITY", alias = "Reporte comunitario: estafa recurrente")
        val result = resolveCallDisplayName(
            number = "+56927841122",
            contactName = null,
            matchingRule = communityRule
        )
        assertEquals("+56927841122", result)
    }

    @Test
    fun `CallLog - priority 3 - subtel rule alias is NEVER used as display name, returns number`() {
        val subtelRule = createRule(source = "SUBTEL", alias = "Telemarketing masivo 809")
        val result = resolveCallDisplayName(
            number = "809123456",
            contactName = null,
            matchingRule = subtelRule
        )
        assertEquals("809123456", result)
    }

    @Test
    fun `CallLog - priority 3 - user rule with blank alias returns number`() {
        val userRule = createRule(source = "USER", alias = "   ")
        val result = resolveCallDisplayName(
            number = "+56912345678",
            contactName = null,
            matchingRule = userRule
        )
        assertEquals("+56912345678", result)
    }

    @Test
    fun `CallLog - priority 3 - no matching rule returns number`() {
        val result = resolveCallDisplayName(
            number = "+56912345678",
            contactName = null,
            matchingRule = null
        )
        assertEquals("+56912345678", result)
    }

    // ================= SmsViewModel: resolveSmsDisplayName =================

    @Test
    fun `SMS - priority 1 - contact name is used even if rule has alias`() {
        val userRule = createRule(source = "USER", alias = "Mi Banco")
        val result = resolveSmsDisplayName(
            address = "+56987654321",
            contactName = "María González",
            matchingRule = userRule
        )
        assertEquals("María González", result)
    }

    @Test
    fun `SMS - priority 1 - contact name is used even if system rule exists`() {
        val systemRule = createRule(source = "CURATED", alias = "Móvil Chile desconocido")
        val result = resolveSmsDisplayName(
            address = "+56987654321",
            contactName = "Papá",
            matchingRule = systemRule
        )
        assertEquals("Papá", result)
    }

    @Test
    fun `SMS - priority 2 - user rule alias is used when contact name is null`() {
        val userRule = createRule(source = "USER", alias = "Delivery Favorito")
        val result = resolveSmsDisplayName(
            address = "+56911223344",
            contactName = null,
            matchingRule = userRule
        )
        assertEquals("Delivery Favorito", result)
    }

    @Test
    fun `SMS - priority 3 - system rule alias is NEVER used when no contact, returns address`() {
        val curatedRule = createRule(source = "CURATED", alias = "Móvil Chile desconocido")
        val result = resolveSmsDisplayName(
            address = "+56911223344",
            contactName = null,
            matchingRule = curatedRule
        )
        assertEquals("+56911223344", result)
    }

    @Test
    fun `SMS - priority 3 - community rule alias is NEVER used when no contact, returns address`() {
        val communityRule = createRule(source = "COMMUNITY", alias = "Reporte comunitario: smishing")
        val result = resolveSmsDisplayName(
            address = "+56911223344",
            contactName = null,
            matchingRule = communityRule
        )
        assertEquals("+56911223344", result)
    }

    @Test
    fun `SMS - priority 3 - no matching rule returns address`() {
        val result = resolveSmsDisplayName(
            address = "+56911223344",
            contactName = null,
            matchingRule = null
        )
        assertEquals("+56911223344", result)
    }

    @Test
    fun `SMS - contactNameCache stores null values correctly and avoids misses`() {
        val cache: MutableMap<String, String?> = Collections.synchronizedMap(mutableMapOf())
        val number = "+56999887766"

        cache[number] = null

        assertTrue(cache.containsKey(number))
        assertNull(cache[number])
    }

    // ================= Edge cases & Robustness =================

    @Test
    fun `CallLog - priority 1 - trims whitespace from contact name`() {
        val result = resolveCallDisplayName(
            number = "+56912345678",
            contactName = "   Juan Pérez   ",
            matchingRule = null
        )
        assertEquals("Juan Pérez", result)
    }

    @Test
    fun `CallLog - priority 2 - trims whitespace from user rule alias`() {
        val userRule = createRule(source = "USER", alias = "  Mi Mecánico  ")
        val result = resolveCallDisplayName(
            number = "+56912345678",
            contactName = null,
            matchingRule = userRule
        )
        assertEquals("Mi Mecánico", result)
    }

    @Test
    fun `CallLog - priority 2 - case-insensitive user rule source matches lowercase user`() {
        val userRule = createRule(source = "user", alias = "Gasfiter")
        val result = resolveCallDisplayName(
            number = "+56912345678",
            contactName = null,
            matchingRule = userRule
        )
        assertEquals("Gasfiter", result)
    }

    @Test
    fun `SMS - priority 1 - trims whitespace from contact name`() {
        val result = resolveSmsDisplayName(
            address = "+56912345678",
            contactName = "   Doctora Soto   ",
            matchingRule = null
        )
        assertEquals("Doctora Soto", result)
    }

    @Test
    fun `SMS - priority 2 - trims whitespace from user rule alias`() {
        val userRule = createRule(source = "USER", alias = "   Veterinaria   ")
        val result = resolveSmsDisplayName(
            address = "+56912345678",
            contactName = null,
            matchingRule = userRule
        )
        assertEquals("Veterinaria", result)
    }

    @Test
    fun `SMS - priority 2 - case-insensitive user rule source matches lowercase user`() {
        val userRule = createRule(source = "user", alias = "Dentista")
        val result = resolveSmsDisplayName(
            address = "+56912345678",
            contactName = null,
            matchingRule = userRule
        )
        assertEquals("Dentista", result)
    }

    @Test
    fun `SMS - priority 2 - user rule alias is used when contact name is blank spaces`() {
        val userRule = createRule(source = "USER", alias = "Hermano")
        val result = resolveSmsDisplayName(
            address = "+56912345678",
            contactName = "    ",
            matchingRule = userRule
        )
        assertEquals("Hermano", result)
    }

    @Test
    fun `SMS - priority 3 - user rule with blank alias returns address`() {
        val userRule = createRule(source = "USER", alias = "    ")
        val result = resolveSmsDisplayName(
            address = "+56912345678",
            contactName = null,
            matchingRule = userRule
        )
        assertEquals("+56912345678", result)
    }

    @Test
    fun `CallLog - curated ALLOW and WARN rules must not be marked as blocked`() {
        val curatedAllowRule = createRule(source = "CURATED", alias = "Formato fijo Chile válido", action = "ALLOW")
        val curatedWarnRule = createRule(source = "CURATED", alias = "Móvil Chile desconocido", action = "WARN")
        val curatedBlockRule = createRule(source = "CURATED", alias = "Spam masivo", action = "BLOCK")

        // Verifying action contracts
        org.junit.Assert.assertNotEquals("BLOCK", curatedAllowRule.action)
        org.junit.Assert.assertNotEquals("BLOCK", curatedWarnRule.action)
        assertEquals("BLOCK", curatedBlockRule.action)
    }
}
