package com.corta.app.data

import com.corta.domain.CollaborativeRulePayload
import com.corta.domain.CollaborativeRulesGateway
import com.corta.domain.FilterAction
import com.corta.domain.SpamReportPayload

/**
 * Local mock data source used as backend placeholder.
 * Replace internals with real HTTP calls later without touching repository usage.
 */
class LocalMockCollaborativeRulesGateway : CollaborativeRulesGateway {

    override suspend fun fetchCuratedRules(): List<CollaborativeRulePayload> {
        return listOf(
            CollaborativeRulePayload(
                pattern = "^\\+?569[0-9]{8}$",
                action = FilterAction.WARN,
                isRegex = true,
                alias = "Móvil Chile desconocido"
            ),
            CollaborativeRulePayload(
                pattern = "^\\+?56(?:2|3[2-5]|4[1-5]|5[1-8]|6[1-8]|7[1-5])[0-9]{6,7}$",
                action = FilterAction.ALLOW,
                isRegex = true,
                alias = "Formato fijo Chile válido"
            )
        )
    }

    override suspend fun fetchCommunityRules(): List<CollaborativeRulePayload> {
        return listOf(
            CollaborativeRulePayload(
                pattern = "^\\+?5692784.*",
                action = FilterAction.BLOCK,
                isRegex = true,
                alias = "Reporte comunitario: estafa recurrente"
            )
        )
    }

    override suspend fun submitReport(report: SpamReportPayload): Boolean {
        return true
    }
}

