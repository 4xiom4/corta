package com.corta.domain

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.corta.db.CortaDatabase
import kotlin.system.measureNanoTime
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CortaComprehensiveSimulationSuiteTest {

    private lateinit var db: CortaDatabase
    private lateinit var repository: CortaRepository
    private lateinit var evaluator: SpamEvaluator
    private lateinit var validator: CallValidator

    @BeforeTest
    fun setup() {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        CortaDatabase.Schema.create(driver)
        db = CortaDatabase(driver)
        repository = CortaRepository(db)
        evaluator = SpamEvaluator(repository)
        validator = CallValidator(evaluator)
    }

    // =========================================================================
    // 1. LISTA BLANCA CRÍTICA INMUTABLE (CHILE)
    // =========================================================================

    @Test
    fun test_emergency_whitelist_is_infallible_and_never_blocked() {
        val emergencyNumbers = listOf(
            "130", // CONAF Incendios Forestales
            "131", // SAMU Ambulancia
            "132", // Bomberos
            "133", // Carabineros
            "134", // PDI
            "137", // Rescate Marítimo
            "147", // Fono Niños Carabineros
            "149", // Fono Familia Carabineros
            "4141" // Prevención del Suicidio Minsal
        )

        for (num in emergencyNumbers) {
            val action = validator.validateIncomingCall(num)
            assertEquals(FilterAction.ALLOW, action, "Emergency $num must always be ALLOW")
        }
    }

    @Test
    fun test_municipal_security_lines_14xx_are_never_blocked() {
        val municipalLines = listOf(
            "1402", // Las Condes
            "1403", // Providencia
            "1405", // Santiago
            "1406", // Vitacura
            "1414", // Lo Barnechea
            "1416", // La Reina
            "1417", // Peñalolén
            "1418", // La Florida
            "1419", // Macul
            "1445", // Maipú
            "1468"  // San Miguel
        )

        for (num in municipalLines) {
            val action = validator.validateIncomingCall(num)
            assertEquals(FilterAction.ALLOW, action, "Municipal line $num must always be ALLOW")
        }
    }

    @Test
    fun test_essential_health_and_public_utility_whitelist() {
        val healthLines = listOf(
            "6003607777", // Salud Responde Minsal
            "+566003607777",
            "600 360 7777",
            "+56 600 360 7777"
        )

        for (num in healthLines) {
            val action = validator.validateIncomingCall(num)
            assertEquals(FilterAction.ALLOW, action, "Salud Responde ($num) must always be ALLOW")
        }
    }

    @Test
    fun test_emergency_remains_allowed_even_under_aggressive_user_block_rules() {
        // Un usuario agrega regla para bloquear todo número que empiece con 13 o 600
        repository.addRule(pattern = "^13.*", action = FilterAction.BLOCK, isRegex = true, source = "USER")
        repository.addRule(pattern = "^600.*", action = FilterAction.BLOCK, isRegex = true, source = "USER")

        assertEquals(FilterAction.ALLOW, validator.validateIncomingCall("133"))
        assertEquals(FilterAction.ALLOW, validator.validateIncomingCall("131"))
        assertEquals(FilterAction.ALLOW, validator.validateIncomingCall("6003607777"))
    }

    // =========================================================================
    // 2. REGLAS SUBTEL (CHILE-FIRST) PRESEMBRADAS
    // =========================================================================

    @Test
    fun test_subtel_809_telemarketing_masivo_is_strictly_blocked() {
        val telemarketing809 = listOf(
            "809123456",
            "809 123 456",
            "+56809123456",
            "+56 809 123 456",
            "56809123456"
        )

        for (num in telemarketing809) {
            val action = validator.validateIncomingCall(num, chao809Aggressive = true)
            assertEquals(FilterAction.BLOCK, action, "SUBTEL 809 ($num) must be strictly BLOCK")
        }
    }

    @Test
    fun test_international_1809_is_not_falsely_blocked_as_chilean_subtel() {
        // Código de área República Dominicana (+1 809)
        val action = validator.validateIncomingCall("+1 809 555 1234")
        assertEquals(FilterAction.ALLOW, action, "Dominican Republic +1 809 must NOT be blocked as Chilean SUBTEL")
    }

    @Test
    fun test_subtel_600_commercial_services_trigger_warn() {
        val commercial600 = listOf(
            "6001234567",
            "600 123 4567",
            "+566001234567",
            "+56 600 123 4567"
        )

        for (num in commercial600) {
            val action = validator.validateIncomingCall(num)
            assertEquals(FilterAction.WARN, action, "Commercial 600 ($num) must trigger WARN")
        }
    }

    // =========================================================================
    // 3. CONTACTOS Y NORMALIZACIÓN SIMÉTRICA (+56)
    // =========================================================================

    @Test
    fun test_known_contacts_are_always_allowed_symmetrically() {
        // Guardado como nacional
        db.cortaDatabaseQueries.insertContact("Mamá", "912345678")
        // Guardado con +56
        db.cortaDatabaseQueries.insertContact("Papá", "+56987654321")

        // Llamadas entrantes en formato contrario o con espacios
        assertEquals(FilterAction.ALLOW, validator.validateIncomingCall("+56 9 1234 5678"))
        assertEquals(FilterAction.ALLOW, validator.validateIncomingCall("+56912345678"))
        assertEquals(FilterAction.ALLOW, validator.validateIncomingCall("987654321"))
        assertEquals(FilterAction.ALLOW, validator.validateIncomingCall("9 8765 4321"))
    }

    @Test
    fun test_advanced_rule_only_contacts_blocks_unknown_but_preserves_emergency() {
        db.cortaDatabaseQueries.insertContact("Amigo", "+56911223344")

        // Contacto conocido permitido
        assertEquals(FilterAction.ALLOW, validator.validateIncomingCall("+56911223344", onlyContacts = true))

        // Emergencia siempre permitida aunque esté en "Solo Contactos"
        assertEquals(FilterAction.ALLOW, validator.validateIncomingCall("133", onlyContacts = true))
        assertEquals(FilterAction.ALLOW, validator.validateIncomingCall("1402", onlyContacts = true))
        assertEquals(FilterAction.ALLOW, validator.validateIncomingCall("6003607777", onlyContacts = true))

        // Número desconocido bloqueado
        assertEquals(FilterAction.BLOCK, validator.validateIncomingCall("+56999887766", onlyContacts = true))
    }

    @Test
    fun test_advanced_rule_block_international_blocks_foreign_numbers() {
        // Internacionales bloqueados
        assertEquals(FilterAction.BLOCK, validator.validateIncomingCall("+12025550199", blockInternational = true))
        assertEquals(FilterAction.BLOCK, validator.validateIncomingCall("+234800123456", blockInternational = true))
        assertEquals(FilterAction.BLOCK, validator.validateIncomingCall("+442071838750", blockInternational = true))

        // Chilenos (+56) no son bloqueados por la regla internacional y se permiten normalmente
        assertEquals(FilterAction.ALLOW, validator.validateIncomingCall("+56987654321", blockInternational = true))

        // Emergencias no se bloquean
        assertEquals(FilterAction.ALLOW, validator.validateIncomingCall("133", blockInternational = true))
    }

    // =========================================================================
    // 4. LATENCIA LOCAL CRÍTICA (< 2 MILISEGUNDOS)
    // =========================================================================

    @Test
    fun test_call_evaluation_latency_is_under_2_milliseconds() {
        // Pre-cargar contactos y reglas
        for (i in 1..50) {
            db.cortaDatabaseQueries.insertContact("Contacto $i", "+5691000${1000 + i}")
        }
        repository.addRule("^809.*", FilterAction.BLOCK, isRegex = true, source = "USER")
        repository.addRule("^900.*", FilterAction.BLOCK, isRegex = true, source = "USER")

        val sampleNumbers = listOf(
            "133",
            "1402",
            "809123456",
            "6003607777",
            "6001234567",
            "+56910001010", // Contacto
            "+56988887777", // Desconocido móvil
            "+12025550199"   // Internacional
        )

        // Warmup JIT
        repeat(50) {
            for (num in sampleNumbers) {
                validator.validateIncomingCall(num)
            }
        }

        // Benchmark de 1000 evaluaciones consecutivas
        val iterations = 1000
        val totalNanos = measureNanoTime {
            for (i in 1..iterations) {
                val num = sampleNumbers[i % sampleNumbers.size]
                validator.validateIncomingCall(num)
            }
        }

        val totalMs = totalNanos / 1_000_000.0
        val avgMs = totalMs / iterations

        println("BENCHMARK CORTA: $iterations evaluaciones en ${totalMs}ms (Promedio: ${avgMs}ms por llamada)")
        assertTrue(avgMs < 2.0, "La evaluación en memoria debe ser < 2.0 ms (actual: ${avgMs}ms)")
    }

    // =========================================================================
    // 5. MOTOR SMS: SMISHING, FRAUDE Y PALABRAS CLAVE BANCARIAS CHILENAS
    // =========================================================================

    @Test
    fun test_sms_bancoestado_phishing_is_strictly_blocked() {
        val smsText = "Estimado cliente BancoEstado: Su clave internet ha sido suspendida. Ingrese a https://bancoestado-renovacion.cl para reactivar su cuenta."
        val action = evaluator.evaluateSms("+56911223344", smsText)

        assertEquals(FilterAction.BLOCK, action, "BancoEstado phishing SMS must be BLOCK")
    }

    @Test
    fun test_sms_santander_fraud_is_strictly_blocked() {
        val smsText = "Santander Alerta: Se detectó una transferencia sospechosa por $250.000. Si no fue usted, cancele de inmediato aquí: https://santander-seguridad.net"
        val action = evaluator.evaluateSms("+56922334455", smsText)

        assertEquals(FilterAction.BLOCK, action, "Santander phishing SMS must be BLOCK")
    }

    @Test
    fun test_sms_clave_unica_phishing_is_strictly_blocked() {
        val smsText = "Registro Civil informa: Su Clave Única caducará en 24 horas. Para mantener sus beneficios actualice en https://claveunica-portal.com"
        val action = evaluator.evaluateSms("+56933445566", smsText)

        assertEquals(FilterAction.BLOCK, action, "Clave Única phishing SMS must be BLOCK")
    }

    @Test
    fun test_sms_parcel_delivery_fraud_is_strictly_blocked() {
        val smsText = "CorreosChile: Su envío N 482910 no pudo ser entregado debido a aranceles pendientes de pago. Ingrese a https://correos-despacho.com para liberar."
        val action = evaluator.evaluateSms("+56944556677", smsText)

        assertEquals(FilterAction.BLOCK, action, "Parcel delivery fraud SMS must be BLOCK")
    }

    @Test
    fun test_sms_promotional_spam_triggers_block_or_mute() {
        val promoText = "¡Felicidades! GANASTE un bono exclusivo de $500.000 y un auto 0km con CLARO. Participa último día gratis en http://promo-sorteo.com link"
        val action = evaluator.evaluateSms("+56955667788", promoText)

        assertTrue(action == FilterAction.BLOCK || action == FilterAction.MUTE, "Promotional spam should be BLOCK or MUTE")
    }

    @Test
    fun test_sms_legitimate_2fa_code_is_allowed() {
        val auth2faText = "Tu código de verificación de BancoEstado es 492810. Recuerda que nunca te solicitaremos este código por teléfono."
        // Número legítimo del banco (servicio masivo corto o 600)
        db.cortaDatabaseQueries.insertContact("BancoEstado Oficial", "6002007000")

        val action = evaluator.evaluateSms("6002007000", auth2faText)
        assertEquals(FilterAction.ALLOW, action, "Legitimate 2FA from official contact must be ALLOW")
    }

    @Test
    fun test_sms_legitimate_personal_conversation_is_allowed() {
        db.cortaDatabaseQueries.insertContact("Hermana", "+56987654321")

        val messageText = "Hola hermano, ¿vas a ir a almorzar el domingo a la casa de la mamá? Avísame para comprar postre."
        val action = evaluator.evaluateSms("+56987654321", messageText)

        assertEquals(FilterAction.ALLOW, action, "Personal message from contact must be ALLOW")
    }
}
