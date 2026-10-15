package com.corta.domain

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.corta.db.CortaDatabase
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

class CallValidatorTest {

    private lateinit var validator: CallValidator

    @BeforeTest
    fun setup() {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        CortaDatabase.Schema.create(driver)
        val db = CortaDatabase(driver)
        val repository = CortaRepository(db)
        validator = CallValidator(SpamEvaluator(repository))
    }

    @Test
    fun validateIncomingCall_delegates_to_spam_evaluator() {
        val action = validator.validateIncomingCall("131")
        assertEquals(FilterAction.ALLOW, action)
    }
}
