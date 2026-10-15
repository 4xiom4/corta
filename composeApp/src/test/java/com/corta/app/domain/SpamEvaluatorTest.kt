package com.corta.app.domain

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.corta.db.CortaDatabase
import com.corta.domain.CortaRepository
import com.corta.domain.FilterAction
import com.corta.domain.SpamEvaluator
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class SpamEvaluatorTest {

    private lateinit var db: CortaDatabase
    private lateinit var repository: CortaRepository
    private lateinit var evaluator: SpamEvaluator

    @Before
    fun setup() {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        CortaDatabase.Schema.create(driver)
        db = CortaDatabase(driver)
        repository = CortaRepository(db)
        evaluator = SpamEvaluator(repository)
    }

    @Test
    fun `evaluateCall allows known contact`() {
        db.cortaDatabaseQueries.insertContact(name = "Contacto", phoneNumber = "+56912345678")

        val result = evaluator.evaluateCall("+56 9 1234 5678")

        assertEquals(FilterAction.ALLOW, result)
    }

    @Test
    fun `evaluateCall blocks based on rule`() {
        repository.addRule(pattern = "^809.*", action = FilterAction.BLOCK, isRegex = true, source = "USER")

        val result = evaluator.evaluateCall("8091234567")

        assertEquals(FilterAction.BLOCK, result)
    }

    @Test
    fun `evaluateSms warns for suspicious content`() {
        val result = evaluator.evaluateSms("+56999999999", "¡GANA un premio URGENTE con este enlace http://spam.com!")

        assertEquals(FilterAction.BLOCK, result)
    }

    @Test
    fun `evaluateCall does not mark plus1 809 as local chile 809 fallback`() {
        val result = evaluator.evaluateCall("+1 809 555 1234")

        assertEquals(FilterAction.ALLOW, result)
    }

    @Test
    fun `evaluateCall warns for chilean 600 massive service unknown`() {
        val result = evaluator.evaluateCall("600 999 9999")

        assertEquals(FilterAction.WARN, result)
    }

    @Test
    fun `evaluateCall allows critical emergency whitelist`() {
        val result = evaluator.evaluateCall("131")

        assertEquals(FilterAction.ALLOW, result)
    }
}
