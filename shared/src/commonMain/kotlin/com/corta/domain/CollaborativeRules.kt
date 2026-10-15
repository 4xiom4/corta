package com.corta.domain

data class CollaborativeRulePayload(
    val pattern: String,
    val action: FilterAction,
    val isRegex: Boolean,
    val alias: String? = null
)

data class SpamReportPayload(
    val phoneNumber: String,
    val channel: String,
    val reason: String,
    val createdAt: Long
)

/**
 * Gateway contract kept in shared so the app can swap from local mock to real backend without changing domain code.
 */
interface CollaborativeRulesGateway {
    suspend fun fetchCuratedRules(): List<CollaborativeRulePayload>
    suspend fun fetchCommunityRules(): List<CollaborativeRulePayload>
    suspend fun submitReport(report: SpamReportPayload): Boolean
}

interface CommunityAccessPolicy {
    fun canUseCommunityRules(): Boolean
}

