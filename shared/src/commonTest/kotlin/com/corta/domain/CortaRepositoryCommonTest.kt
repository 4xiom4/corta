package com.corta.domain

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.corta.db.CortaDatabase
import kotlinx.coroutines.runBlocking
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CortaRepositoryCommonTest {

    private lateinit var db: CortaDatabase
    private lateinit var repository: CortaRepository

    @BeforeTest
    fun setup() {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        CortaDatabase.Schema.create(driver)
        db = CortaDatabase(driver)
        repository = CortaRepository(db)
    }

    @Test
    fun addRule_and_getActionForNumber_returns_rule_action() {
        repository.addRule(pattern = "^809.*", action = FilterAction.BLOCK, isRegex = true, source = "USER")

        val action = repository.getActionForNumber("8091234567")

        assertEquals(FilterAction.BLOCK, action)
    }

    @Test
    fun deleteRule_removes_rule_and_allows_by_default() {
        repository.addRule(pattern = "^888.*", action = FilterAction.BLOCK, isRegex = true, source = "USER")
        repository.deleteRule("^888.*")

        val action = repository.getActionForNumber("8881234567")

        assertEquals(FilterAction.ALLOW, action)
    }

    @Test
    fun isContact_detects_contact_by_normalized_number() {
        db.cortaDatabaseQueries.insertContact(name = "Test", phoneNumber = "+56911111111")

        val isContact = repository.isContact("+56 9 1111 1111")

        assertTrue(isContact)
    }

    @Test
    fun syncCollaborativeRules_adds_curated_and_community_based_on_access() = runBlocking {
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
    fun whitelisted_critical_number_is_not_blocked_by_user_regex() {
        repository.addRule(pattern = "^131$", action = FilterAction.BLOCK, isRegex = true, source = "USER")

        val action = repository.getActionForNumber("131")

        assertEquals(FilterAction.ALLOW, action)
    }

    @Test
    fun whitelisted_600_service_number_remains_allowed_despite_regex() {
        repository.addRule(pattern = "^600.*", action = FilterAction.BLOCK, isRegex = true, source = "USER")

        val action = repository.getActionForNumber("6003607777")

        assertEquals(FilterAction.ALLOW, action)
    }

    @Test
    fun subtel_rule_takes_precedence_over_user_rule() {
        repository.addRule(pattern = "^809.*", action = FilterAction.ALLOW, isRegex = true, source = "USER")
        repository.addRule(pattern = "^809.*", action = FilterAction.BLOCK, isRegex = true, source = "SUBTEL")

        val rule = repository.getRuleForNumber("8091234567")
        assertEquals("SUBTEL", rule?.source)
    }

    @Test
    fun whitelist_blocks_user_rule_evaluation() {
        repository.addRule(pattern = "^1.*", action = FilterAction.BLOCK, isRegex = true, source = "USER")

        // 131 is whitelisted, must return null
        val rule = repository.getRuleForNumber("131")
        assertNull(rule)
    }
}
