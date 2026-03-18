package com.corta.domain

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import com.corta.data.network.SubtelApiClient
import com.corta.data.network.SubtelRuleDto
import com.corta.db.CallLogEntry
import com.corta.db.BlockedSms
import com.corta.db.CortaDatabase
import com.corta.db.FilterRule
import com.corta.db.SmsLogEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow

class CortaRepository(private val database: CortaDatabase) {
    private val queries = database.cortaDatabaseQueries
    
    // Cache para regex compilados - mejora de rendimiento
    private val regexCache = mutableMapOf<String, Regex>()
    
    // Cache para reglas por tipo - reduce consultas a DB
    private var cachedRules: List<FilterRule>? = null
    private var lastCacheUpdate = 0L
    private val cacheTimeoutMs = 60000L // 60 segundos - más tiempo para mejor rendimiento

    fun isContact(phoneNumber: String): Boolean {
        val normalized = PhoneNumberUtils.normalize(phoneNumber)
        val contact = queries.getContactByPhone(normalized).executeAsOneOrNull()
        return contact != null
    }

    fun getRules(): List<FilterRule> {
    val currentTime = System.currentTimeMillis()
    return if (cachedRules != null && (currentTime - lastCacheUpdate) < cacheTimeoutMs) {
        cachedRules!!
    } else {
        val rules = queries.getRules().executeAsList()
        cachedRules = rules
        lastCacheUpdate = currentTime
        rules
    }
}

    fun getActionForNumber(phoneNumber: String): FilterAction {
        val normalized = PhoneNumberUtils.normalize(phoneNumber)
        val rule = getRuleForNumber(normalized)
        return if (rule != null) FilterAction.fromString(rule.action) else FilterAction.ALLOW
    }

    fun getActionForNumber(phoneNumber: String, rules: List<FilterRule>): FilterAction {
        if (rules.isEmpty()) return FilterAction.ALLOW
        val normalized = PhoneNumberUtils.normalize(phoneNumber)
        val rule = getRuleForNumber(normalized, rules)
        return if (rule != null) FilterAction.fromString(rule.action) else FilterAction.ALLOW
    }
    
    fun getRuleForNumber(phoneNumber: String): FilterRule? {
        val rules = getRules()
        return getRuleForNumber(phoneNumber, rules)
    }

    fun getRuleForNumber(phoneNumber: String, rules: List<FilterRule>): FilterRule? {
        if (rules.isEmpty()) return null
        val normalized = PhoneNumberUtils.normalize(phoneNumber)
        val subtelRule = findMatchingRule(normalized, rules.filter { it.source == "SUBTEL" })
        if (subtelRule != null) return subtelRule
        return findMatchingRule(normalized, rules.filter { it.source == "USER" })
    }

    private fun findMatchingRule(phoneNumber: String, rules: List<FilterRule>): FilterRule? {
        if (rules.isEmpty()) return null
        
        // Optimización: buscar primero reglas exactas, luego prefijos, finalmente regex
        for (rule in rules) {
            val isRegex = rule.isRegex ?: false
            
            if (!isRegex) {
                // Búsqueda exacta primero (más rápida)
                if (phoneNumber == rule.pattern) {
                    return rule
                }
            }
        }
        
        for (rule in rules) {
            val isRegex = rule.isRegex ?: false
            
            if (!isRegex) {
                // Búsqueda por prefijo
                if (phoneNumber.startsWith(rule.pattern)) {
                    return rule
                }
            }
        }
        
        // Regex al final (más costoso)
        for (rule in rules) {
            val isRegex = rule.isRegex ?: false
            if (isRegex) {
                val regex = regexCache.getOrPut(rule.pattern) { Regex(rule.pattern) }
                if (regex.containsMatchIn(phoneNumber)) {
                    return rule
                }
            }
        }
        
        return null
    }

    fun logCall(phoneNumber: String, timestamp: Long, action: FilterAction, duration: Long = 0) {
        queries.insertCallLog(phoneNumber, timestamp, action.name, duration)
    }

    fun logBlockedSms(sender: String, body: String, timestamp: Long) {
        queries.insertBlockedSms(sender, body, timestamp)
    }

    fun logSmsAction(sender: String, timestamp: Long, action: FilterAction, body: String) {
        val preview = if (body.length <= 120) body else body.take(120)
        queries.insertSmsLog(sender, timestamp, action.name, preview)
    }

    fun observeBlockedSms(): Flow<List<BlockedSms>> {
        return queries.getBlockedSms()
            .asFlow()
            .mapToList(Dispatchers.IO)
    }

    fun observeCallLogs(): Flow<List<CallLogEntry>> {
        return queries.getCallLogs()
            .asFlow()
            .mapToList(Dispatchers.IO)
    }

    fun observeRules(): Flow<List<FilterRule>> {
        return queries.getRules()
            .asFlow()
            .mapToList(Dispatchers.IO)
    }

    fun observeSmsLogs(): Flow<List<SmsLogEntry>> {
        return queries.getSmsLogs()
            .asFlow()
            .mapToList(Dispatchers.IO)
    }

    fun addRule(
        pattern: String,
        action: FilterAction,
        isRegex: Boolean,
        alias: String? = null,
        source: String = "USER"
    ) {
        queries.insertRule(pattern, action.name, isRegex, alias, source)
        // Invalidar cache al agregar nueva regla
        invalidateCache()
    }

    fun deleteRule(pattern: String) {
        queries.deleteRule(pattern)
        // Invalidar cache al eliminar regla
        invalidateCache()
    }
    
    private fun invalidateCache() {
        cachedRules = null
        regexCache.clear()
    }

    fun deleteRulesBySource(source: String) {
        queries.deleteRulesBySource(source)
    }

    suspend fun syncSubtelRules(enabled: Boolean, apiClient: SubtelApiClient) {
        if (!enabled) {
            deleteRulesBySource("SUBTEL")
            return
        }

        val remoteRules = apiClient.fetchLatestSpamList()
        replaceSubtelRules(remoteRules)
    }

    private fun replaceSubtelRules(rules: List<SubtelRuleDto>) {
        queries.transaction {
            queries.deleteRulesBySource("SUBTEL")
            rules.forEach { dto ->
                addRule(
                    pattern = dto.pattern,
                    action = FilterAction.fromString(dto.action),
                    isRegex = dto.isRegex,
                    alias = "SUBTEL",
                    source = "SUBTEL"
                )
            }
        }
    }
}
