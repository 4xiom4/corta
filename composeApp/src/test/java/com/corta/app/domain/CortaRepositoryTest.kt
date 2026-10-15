package com.corta.app.domain

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.corta.db.CortaDatabase
import com.corta.domain.CollaborativeRulePayload
import com.corta.domain.CollaborativeRulesGateway
import com.corta.domain.CommunityAccessPolicy
import com.corta.domain.CortaRepository
import com.corta.domain.FilterAction
import com.corta.domain.SpamReportPayload
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class CortaRepositoryTest {

    private lateinit var db: CortaDatabase
    private lateinit var repository: CortaRepository

    @Before
    fun setup() {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        CortaDatabase.Schema.create(driver)
        db = CortaDatabase(driver)
        repository = CortaRepository(db)
    }

    @Test
    fun `addRule and getActionForNumber returns rule action`() {
        repository.addRule(pattern = "^809.*", action = FilterAction.BLOCK, isRegex = true, source = "USER")

        val action = repository.getActionForNumber("8091234567")

        assertEquals(FilterAction.BLOCK, action)
    }

    @Test
    fun `deleteRule removes rule and allow by default`() {
        repository.addRule(pattern = "^888.*", action = FilterAction.BLOCK, isRegex = true, source = "USER")
        repository.deleteRule("^888.*")

        val action = repository.getActionForNumber("8881234567")

        assertEquals(FilterAction.ALLOW, action)
    }

    @Test
    fun `isContact detects contact by normalized number`() {
        db.cortaDatabaseQueries.insertContact(name = "Test", phoneNumber = "+56911111111")

        val isContact = repository.isContact("+56 9 1111 1111")

        assertTrue(isContact)
    }

    @Test
    fun `syncCollaborativeRules adds curated and community based on access`() = runBlocking {
        val gateway = object : CollaborativeRulesGateway {
            override suspend fun fetchCuratedRules(): List<CollaborativeRulePayload> =
                listOf(CollaborativeRulePayload("^555.*", FilterAction.BLOCK, isRegex = true))

            override suspend fun fetchCommunityRules(): List<CollaborativeRulePayload> =
                listOf(CollaborativeRulePayload("^777.*", FilterAction.BLOCK, isRegex = true))

            override suspend fun submitReport(report: SpamReportPayload): Boolean = true
        }
        val withAccess = CortaRepository(
            database = db,
            collaborativeGateway = gateway,
            communityAccessPolicy = object : CommunityAccessPolicy {
                override fun canUseCommunityRules(): Boolean = true
            }
        )

        withAccess.syncCollaborativeRules()

        assertEquals(FilterAction.BLOCK, withAccess.getActionForNumber("555123456"))
        assertEquals(FilterAction.BLOCK, withAccess.getActionForNumber("777123456"))
    }

    @Test
    fun `whitelisted critical number is not blocked by user regex`() {
        repository.addRule(pattern = "^131$", action = FilterAction.BLOCK, isRegex = true, source = "USER")

        val action = repository.getActionForNumber("131")

        assertEquals(FilterAction.ALLOW, action)
    }

    @Test
    fun `whitelisted 600 service number remains allowed despite regex`() {
        repository.addRule(pattern = "^600.*", action = FilterAction.BLOCK, isRegex = true, source = "USER")

        val action = repository.getActionForNumber("6003607777")

        assertEquals(FilterAction.ALLOW, action)
    }
}
