package com.corta.domain

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.corta.db.CortaDatabase
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

class SpamEvaluatorCommonTest {

    private lateinit var db: CortaDatabase
    private lateinit var repository: CortaRepository
    private lateinit var evaluator: SpamEvaluator

    @BeforeTest
    fun setup() {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        CortaDatabase.Schema.create(driver)
        db = CortaDatabase(driver)
        repository = CortaRepository(db)
        evaluator = SpamEvaluator(repository)
    }

    @Test
    fun evaluateCall_allows_known_contact() {
        db.cortaDatabaseQueries.insertContact(name = "Contacto", phoneNumber = "+56912345678")

        val result = evaluator.evaluateCall("+56 9 1234 5678")

        assertEquals(FilterAction.ALLOW, result)
    }

    @Test
    fun evaluateCall_blocks_based_on_user_rule() {
        repository.addRule(pattern = "^809.*", action = FilterAction.BLOCK, isRegex = true, source = "USER")

        val result = evaluator.evaluateCall("8091234567")

        assertEquals(FilterAction.BLOCK, result)
    }

    @Test
    fun evaluateSms_blocks_obvious_spam_content() {
        val result = evaluator.evaluateSms(
            phoneNumber = "+56999999999",
            body = "GANA un premio URGENTE con este enlace http://spam.com y otro https://more-spam.com"
        )

        assertEquals(FilterAction.BLOCK, result)
    }

    @Test
    fun evaluateCall_does_not_treat_international_1809_as_chilean_809() {
        val result = evaluator.evaluateCall("+1 809 555 1234")

        assertEquals(FilterAction.ALLOW, result)
    }

    @Test
    fun evaluateCall_warns_for_unknown_600_massive_service() {
        val result = evaluator.evaluateCall("600 999 9999")

        assertEquals(FilterAction.WARN, result)
    }

    @Test
    fun evaluateCall_allows_critical_emergency_131() {
        val result = evaluator.evaluateCall("131")

        assertEquals(FilterAction.ALLOW, result)
    }

    @Test
    fun evaluateCall_does_not_allow_blocked_pattern_for_whitelisted_service() {
        repository.addRule(pattern = "^600.*", action = FilterAction.BLOCK, isRegex = true, source = "USER")

        val action = repository.getActionForNumber("6003607777")

        assertEquals(FilterAction.ALLOW, action)
    }
}
