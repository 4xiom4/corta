package com.corta.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PhoneNumberUtilsTest {

    @Test
    fun `blank input returns blank`() {
        assertEquals("", PhoneNumberUtils.normalize(""))
        assertEquals("", PhoneNumberUtils.normalize("   "))
    }

    @Test
    fun `preserves plus prefix when present`() {
        assertEquals("+56912345678", PhoneNumberUtils.normalize("+56912345678"))
    }

    @Test
    fun `strips separators and spaces`() {
        assertEquals("+56912345678", PhoneNumberUtils.normalize("+56 9 1234 5678"))
        assertEquals("+56912345678", PhoneNumberUtils.normalize("9-1234-5678"))
    }

    @Test
    fun `chilenian mobile with leading zero drops the zero`() {
        assertEquals("+56912345678", PhoneNumberUtils.normalize("0912345678"))
    }

    @Test
    fun `nine digit mobile starting with 9 is prefixed with 56`() {
        assertEquals("+56912345678", PhoneNumberUtils.normalize("912345678"))
    }

    @Test
    fun `600 service number is kept local`() {
        assertEquals("6003607777", PhoneNumberUtils.normalize("600 360 7777"))
    }

    @Test
    fun `809 service number is kept local`() {
        assertEquals("809035239", PhoneNumberUtils.normalize("809 035 239"))
    }

    @Test
    fun `critical short numbers are preserved`() {
        assertEquals("131", PhoneNumberUtils.normalize("131"))
        assertEquals("133", PhoneNumberUtils.normalize(" 133 "))
    }

    @Test
    fun `non numeric input returns trimmed input`() {
        assertEquals("foo", PhoneNumberUtils.normalize("  foo  "))
    }

    @Test
    fun `digits only input is normalized`() {
        val result = PhoneNumberUtils.normalize("+1 809 555 1234")
        assertTrue(result.contains("18095551234") || result.contains("+18095551234"))
    }
}
