package com.corta.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertFalse

class ChileanTelecomClassifierTest {

    @Test
    fun testNationalEmergencyNumbers() {
        val carabineros = ChileanTelecomClassifier.classify("133")
        assertEquals(ChileanNumberType.EMERGENCY_NATIONAL, carabineros.type)
        assertEquals("Carabineros de Chile", carabineros.detailDescription)
        assertTrue(carabineros.isEmergencyOrSafety)
        assertTrue(carabineros.isVerifiedOfficialService)

        val bomberos = ChileanTelecomClassifier.classify("132")
        assertEquals(ChileanNumberType.EMERGENCY_NATIONAL, bomberos.type)
        assertEquals("Bomberos de Chile", bomberos.detailDescription)

        val suicidio = ChileanTelecomClassifier.classify("4141")
        assertEquals(ChileanNumberType.EMERGENCY_NATIONAL, suicidio.type)
        assertTrue(suicidio.isEmergencyOrSafety)
    }

    @Test
    fun testMunicipalSecurity14xx() {
        val lasCondes = ChileanTelecomClassifier.classify("1402")
        assertEquals(ChileanNumberType.MUNICIPAL_SECURITY_14XX, lasCondes.type)
        assertEquals("Seguridad Las Condes", lasCondes.detailDescription)
        assertTrue(lasCondes.isEmergencyOrSafety)

        val providencia = ChileanTelecomClassifier.classify("1416")
        assertEquals(ChileanNumberType.MUNICIPAL_SECURITY_14XX, providencia.type)
        assertEquals("Seguridad Providencia", providencia.detailDescription)

        val maipu = ChileanTelecomClassifier.classify("1445")
        assertEquals(ChileanNumberType.MUNICIPAL_SECURITY_14XX, maipu.type)
        assertEquals("Seguridad Maipú", maipu.detailDescription)
    }

    @Test
    fun testSubtel809Telemarketing() {
        val sub1 = ChileanTelecomClassifier.classify("8092301199")
        assertEquals(ChileanNumberType.SUBTEL_TELEMARKETING_809, sub1.type)
        assertTrue(sub1.isTelemarketing)

        val sub2 = ChileanTelecomClassifier.classify("+568092301199")
        assertEquals(ChileanNumberType.SUBTEL_TELEMARKETING_809, sub2.type)
        assertTrue(sub2.isTelemarketing)
    }

    @Test
    fun testCustomerCare600AndTollFree800() {
        val banco600 = ChileanTelecomClassifier.classify("+566006003000")
        assertEquals(ChileanNumberType.SERVICE_CUSTOMER_CARE_600, banco600.type)
        assertTrue(banco600.isVerifiedOfficialService)

        val tollFree800 = ChileanTelecomClassifier.classify("800104008")
        assertEquals(ChileanNumberType.SERVICE_TOLL_FREE_800, tollFree800.type)
        assertTrue(tollFree800.isVerifiedOfficialService)
    }

    @Test
    fun testPremiumRate700Surcharge() {
        val rate700 = ChileanTelecomClassifier.classify("700123456")
        assertEquals(ChileanNumberType.PREMIUM_RATE_700, rate700.type)
        assertTrue(rate700.hasSurchargeWarning)
    }

    @Test
    fun testFixedLandlinesByAreaCode() {
        val santiago = ChileanTelecomClassifier.classify("+56223557900")
        assertEquals(ChileanNumberType.FIXED_LANDLINE, santiago.type)
        assertEquals("Santiago (Red fija)", santiago.badgeLabel)

        val concepcion = ChileanTelecomClassifier.classify("+56412345678")
        assertEquals(ChileanNumberType.FIXED_LANDLINE, concepcion.type)
        assertTrue(concepcion.badgeLabel.contains("Concepción"))

        val valparaiso = ChileanTelecomClassifier.classify("+56322987654")
        assertEquals(ChileanNumberType.FIXED_LANDLINE, valparaiso.type)
        assertTrue(valparaiso.badgeLabel.contains("Valparaíso"))
    }

    @Test
    fun testMobileNumbers() {
        val mobile = ChileanTelecomClassifier.classify("+56984102231")
        assertEquals(ChileanNumberType.MOBILE, mobile.type)
        assertEquals("Móvil Chile", mobile.badgeLabel)
        assertFalse(mobile.isTelemarketing)
    }

    @Test
    fun testInternationalNumbers() {
        val usa = ChileanTelecomClassifier.classify("+14155552671")
        assertEquals(ChileanNumberType.INTERNATIONAL, usa.type)
        assertEquals("Internacional", usa.badgeLabel)
    }

    @Test
    fun testSmsBankAndOtpClassification() {
        val bankEstado = ChileanTelecomClassifier.classifySms(
            address = "BancoEstado",
            body = "Tu clave dinámica es 492102. No la compartas con nadie."
        )
        assertEquals(ChileanSmsCategory.BANKS_OTP, bankEstado.category)
        assertTrue(bankEstado.isVerifiedBankOrService)
        assertEquals("492102", bankEstado.detectedOtpCode)

        val shortcode = ChileanTelecomClassifier.classifySms(
            address = "2020",
            body = "Corta: Tu código de verificación es 8419."
        )
        assertEquals(ChileanSmsCategory.BANKS_OTP, shortcode.category)
        assertTrue(shortcode.isVerifiedBankOrService)
        assertEquals("8419", shortcode.detectedOtpCode)
    }

    @Test
    fun testSmsSmishingAndCommercial809() {
        val smishing = ChileanTelecomClassifier.classifySms(
            address = "+56961823910",
            body = "BancoEstado: Su cuenta suspendida. Ingrese al enlace https://ban-estado-seguro.cl/login para reactivar"
        )
        assertEquals(ChileanSmsCategory.SPAM_SMISHING, smishing.category)
        assertTrue(smishing.isSmishing)

        val commercial809 = ChileanTelecomClassifier.classifySms(
            address = "+568093341122",
            body = "Aprovecha hoy portabilidad móvil con descuento imperdible."
        )
        assertEquals(ChileanSmsCategory.SPAM_SMISHING, commercial809.category)
        assertTrue(commercial809.isCommercial809)
    }

    @Test
    fun testSmsPersonalClassification() {
        val contactSms = ChileanTelecomClassifier.classifySms(
            address = "+56987654321",
            body = "Hijo, ¿a qué hora llegas a almorzar?",
            isKnownContact = true
        )
        assertEquals(ChileanSmsCategory.PERSONAL, contactSms.category)
        assertTrue(contactSms.isPersonal)
    }
}

