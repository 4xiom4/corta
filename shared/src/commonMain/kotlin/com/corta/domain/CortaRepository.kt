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
import com.corta.db.SpamKeyword
import com.corta.db.ArchivedConversation
import com.corta.db.TrashItem
import com.corta.db.ContactGroup
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow

class CortaRepository(
    private val database: CortaDatabase,
    private val collaborativeGateway: CollaborativeRulesGateway? = null,
    private val communityAccessPolicy: CommunityAccessPolicy? = null
) {
    private val queries = database.cortaDatabaseQueries
    
    // Cache para regex compilados - mejora de rendimiento
    private val regexCache = mutableMapOf<String, Regex>()
    
    // Trie para prefijos - optimización profesional
    private var subtelPrefixesTrie: PrefixTrie? = null
    private var userPrefixesTrie: PrefixTrie? = null

    // Cache para reglas por tipo - reduce consultas a DB
    private var cachedRules: List<FilterRule>? = null
    private var lastCacheUpdate = 0L
    private val cacheTimeoutMs = 60000L // 60 segundos

    // Cache en memoria para palabras clave de spam (< 2ms latencia en SMS)
    private var cachedActiveSpamKeywords: List<SpamKeyword>? = null
    private var lastSpamKeywordsUpdate = 0L

    private class PrefixTrie {
        private val root = Node()
        class Node {
            val children = mutableMapOf<Char, Node>()
            var rule: FilterRule? = null
        }
        fun insert(pattern: String, rule: FilterRule) {
            var curr = root
            for (ch in pattern) {
                curr = curr.children.getOrPut(ch) { Node() }
            }
            curr.rule = rule
        }
        fun findLongestPrefixMatch(number: String): FilterRule? {
            var curr = root
            var lastMatch: FilterRule? = null
            for (ch in number) {
                curr = curr.children[ch] ?: break
                if (curr.rule != null) lastMatch = curr.rule
            }
            return lastMatch
        }
    }

    init {
        seedDefaultRulesIfEmpty()
    }

    fun seedDefaultRulesIfEmpty() {
        val existing = queries.getRules().executeAsList()
        if (existing.none { it.source == "SUBTEL" }) {
            queries.transaction {
                // SUBTEL 809: Telemarketing masivo no solicitado (Chile) -> BLOCK
                queries.insertRule("809", FilterAction.BLOCK.name, false, "SUBTEL Masivo (809)", "SUBTEL")
                queries.insertRule("+56809", FilterAction.BLOCK.name, false, "SUBTEL Masivo (+56809)", "SUBTEL")
                // SUBTEL 600: Servicios contratados o de atención (Chile) -> WARN
                queries.insertRule("600", FilterAction.WARN.name, false, "SUBTEL Servicios (600)", "SUBTEL")
                queries.insertRule("+56600", FilterAction.WARN.name, false, "SUBTEL Servicios (+56600)", "SUBTEL")
            }
            invalidateCache()
        }

        // Sembrar palabras clave de smishing/fraude bancario en Chile si está vacío
        val existingKeywords = queries.getSpamKeywords().executeAsList()
        if (existingKeywords.isEmpty()) {
            queries.transaction {
                val now = System.currentTimeMillis()
                val defaults = listOf(
                    "bancoestado" to "SMISHING",
                    "banco estado" to "SMISHING",
                    "clave unica" to "SMISHING",
                    "clave única" to "SMISHING",
                    "santander" to "SMISHING",
                    "banco de chile" to "SMISHING",
                    "cuenta suspendida" to "SMISHING",
                    "actualiza tus datos" to "SMISHING",
                    "paquete retenido" to "FRAUD",
                    "devolucion impuestos" to "FRAUD",
                    "ganaste" to "MARKETING",
                    "premio" to "MARKETING",
                    "sorteo" to "MARKETING",
                    "ultimo dia" to "MARKETING"
                )
                defaults.forEach { (kw, cat) ->
                    queries.insertSpamKeyword(kw, cat, true, now)
                }
            }
            cachedActiveSpamKeywords = null
        }

        // Sembrar grupos de contacto predeterminados si está vacío
        val existingGroups = queries.getContactGroups().executeAsList()
        if (existingGroups.isEmpty()) {
            queries.transaction {
                queries.insertContactGroup("Familia", "family_restroom", "#10B981")
                queries.insertContactGroup("Trabajo", "work", "#3B82F6")
                queries.insertContactGroup("Amigos", "people", "#F59E0B")
                queries.insertContactGroup("Emergencias", "local_hospital", "#EF4444")
            }
        }
    }

    private fun rebuildTries(rules: List<FilterRule>) {
        val subtel = PrefixTrie()
        val user = PrefixTrie()
        rules.filter { it.isRegex != true }.forEach { rule ->
            val targetTrie = if (rule.source == "SUBTEL") subtel else user
            targetTrie.insert(rule.pattern, rule)
            if (rule.pattern.startsWith("+56")) {
                targetTrie.insert(rule.pattern.removePrefix("+56"), rule)
            } else if (rule.pattern.startsWith("56")) {
                targetTrie.insert(rule.pattern.removePrefix("56"), rule)
            } else {
                targetTrie.insert("+56${rule.pattern}", rule)
                targetTrie.insert("56${rule.pattern}", rule)
            }
        }
        subtelPrefixesTrie = subtel
        userPrefixesTrie = user
    }

    // Remitentes alfanuméricos esenciales y 2FA bancarios de Chile que NUNCA deben bloquearse
    private val whitelistAlphaSenders = setOf(
        "google", "microsoft", "mercadopago", "sii", "lipigas",
        "bancoestado", "bancochile", "santander", "bci", "scotiabank",
        "itau", "falabella", "ripley", "cencosud", "entel", "claro", "wom", "movistar",
        "uber", "didi", "cabify", "pedidosya", "rappi"
    )

    // Lista blanca crítica: emergencias, 14xx seguridad municipal y servicios 600/809 críticos
    private val whitelistExactDigits = setOf(
        "130", "131", "132", "133", "134", "135", "136", "137", "138", "139", "143", "147", "149", "911", "112",
        "1402", "1403", "1404", "1405", "1406", "1407", "1408", "1409", "1410", "1411", "1412", "1413", "1414", "1415",
        "1416", "1417", "1418", "1419", "1420", "1421", "1423", "1424", "1425", "1426", "1427", "1428", "1429",
        "1440", "1441", "1442", "1443", "1444", "1445", "1446", "1447", "1448", "1449",
        "1450", "1451", "1452", "1453", "1454", "1455", "1456", "1457", "1458", "1459",
        "1460", "1461", "1462", "1466", "1468", "1488", "1514", "4141",
        "6003607777", "6000061022", "6003603000", "6003702000", "6006002626", "6004000444", "6007120001",
        "6002007000", "6003203000", "6003203030", "6006373737", "6006860888", "6006700500", "6003906200",
        "6003906000", "6006005757", "6006960000", "6007777777", "6006005000", "6003378000", "6003311000",
        "6006006060", "6007120020", "6007186000", "6006008432", "6004012400", "6005708000", "6006266500",
        "6004000101", "800104008", "800202120", "6000868082", "809035239"
    )
    // Prefijos de emergencia absoluta: 13x (Bomberos, Carabineros, SAMU, etc.), 14xx (Seguridad Municipal), 4141, 1514
    private val whitelistPrefixesDigits = listOf("13", "14", "4141", "1514")

    private fun digitsOnly(input: String): String = buildString {
        input.forEach { ch -> if (ch.isDigit()) append(ch) }
    }

    fun isWhitelistedNumber(phoneNumber: String): Boolean {
        val trimmed = phoneNumber.trim()
        if (trimmed.isEmpty()) return false

        // 1. Remitentes alfanuméricos legítimos (SMS 2FA y servicios)
        if (trimmed.lowercase() in whitelistAlphaSenders) return true

        val candidates = PhoneNumberUtils.getSymmetricCandidates(trimmed)
        for (candidate in candidates) {
            val digits = digitsOnly(candidate)
            if (digits.isNotEmpty()) {
                if (whitelistExactDigits.contains(digits)) return true
                val without56 = if (digits.startsWith("56") && digits.length > 2) digits.drop(2) else digits
                if (whitelistExactDigits.contains(without56)) return true
                if (whitelistPrefixesDigits.any { without56.startsWith(it) }) return true
            }
        }
        return false
    }

    fun isContact(phoneNumber: String): Boolean {
        val candidates = PhoneNumberUtils.getSymmetricCandidates(phoneNumber)
        return candidates.any { candidate ->
            queries.getContactByPhone(candidate).executeAsOneOrNull() != null
        }
    }

    fun syncContacts(contacts: List<ContactInfo>) {
        queries.transaction {
            // No borramos todo para evitar fragmentación, usamos INSERT OR REPLACE
            contacts.forEach { contact ->
                contact.phoneNumbers.forEach { number ->
                    val normalized = PhoneNumberUtils.normalize(number)
                    queries.insertContact(contact.name, normalized)
                    PhoneNumberUtils.toChileanNational(number)?.let { national ->
                        if (national != normalized) {
                            queries.insertContact(contact.name, national)
                        }
                    }
                }
            }
        }
    }

    fun getRules(): List<FilterRule> {
        val currentTime = System.currentTimeMillis()
        return if (cachedRules != null && (currentTime - lastCacheUpdate) < cacheTimeoutMs) {
            cachedRules!!
        } else {
            val rules = queries.getRules().executeAsList()
            cachedRules = rules
            lastCacheUpdate = currentTime
            rebuildTries(rules)
            rules
        }
    }

    fun getActionForNumber(phoneNumber: String): FilterAction {
        if (isWhitelistedNumber(phoneNumber)) return FilterAction.ALLOW
        val rule = getRuleForNumber(phoneNumber)
        return if (rule != null) FilterAction.fromString(rule.action) else FilterAction.ALLOW
    }

    fun getActionForNumber(phoneNumber: String, rules: List<FilterRule>): FilterAction {
        if (rules.isEmpty()) return FilterAction.ALLOW
        if (isWhitelistedNumber(phoneNumber)) return FilterAction.ALLOW
        val rule = getRuleForNumber(phoneNumber, rules)
        return if (rule != null) FilterAction.fromString(rule.action) else FilterAction.ALLOW
    }
    
    fun getRuleForNumber(phoneNumber: String): FilterRule? {
        val rules = getRules()
        return getRuleForNumber(phoneNumber, rules)
    }

    fun getRuleForNumber(phoneNumber: String, rules: List<FilterRule>): FilterRule? {
        if (rules.isEmpty()) return null
        if (isWhitelistedNumber(phoneNumber)) return null
        val subtelRule = findMatchingRule(phoneNumber, rules.filter { it.source == "SUBTEL" })
        if (subtelRule != null) return subtelRule
        val userRule = findMatchingRule(phoneNumber, rules.filter { it.source == "USER" })
        if (userRule != null) return userRule
        val curatedRule = findMatchingRule(phoneNumber, rules.filter { it.source == "CURATED" })
        if (curatedRule != null) return curatedRule
        return findMatchingRule(phoneNumber, rules.filter { it.source == "COMMUNITY" })
    }

    private fun findMatchingRule(phoneNumber: String, rules: List<FilterRule>): FilterRule? {
        if (rules.isEmpty()) return null
        val candidates = PhoneNumberUtils.getSymmetricCandidates(phoneNumber)
        val source = rules.firstOrNull()?.source

        // 1. Trie match para prefijos exactos O(L) sobre todos los candidatos simétricos
        for (candidate in candidates) {
            val trieMatch = when (source) {
                "SUBTEL" -> subtelPrefixesTrie?.findLongestPrefixMatch(candidate)
                "USER"   -> userPrefixesTrie?.findLongestPrefixMatch(candidate)
                else     -> null
            }
            if (trieMatch != null) return trieMatch
        }

        // 2. Búsqueda lineal de prefijos
        for (candidate in candidates) {
            for (rule in rules) {
                if (rule.isRegex != true && candidate.startsWith(rule.pattern)) {
                    return rule
                }
            }
        }

        // 3. Regex al final para todas las fuentes
        for (candidate in candidates) {
            for (rule in rules) {
                if (rule.isRegex == true) {
                    val regex = regexCache.getOrPut(rule.pattern) { Regex(rule.pattern) }
                    if (regex.containsMatchIn(candidate)) {
                        return rule
                    }
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

    fun getCallLogs(): List<CallLogEntry> = queries.getCallLogs().executeAsList()

    fun getSmsLogs(): List<SmsLogEntry> = queries.getSmsLogs().executeAsList()

    fun getBlockedSms(): List<BlockedSms> = queries.getBlockedSms().executeAsList()

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
        queries.transaction {
            queries.insertRule(pattern, action.name, isRegex, alias, source)
        }
        // Invalidar cache al agregar nueva regla
        invalidateCache()
    }

    fun deleteRule(pattern: String) {
        queries.transaction {
            queries.deleteRule(pattern)
        }
        // Invalidar cache al eliminar regla
        invalidateCache()
    }
    
    private fun invalidateCache() {
        cachedRules = null
        cachedActiveSpamKeywords = null
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

    suspend fun syncCollaborativeRules() {
        val gateway = collaborativeGateway ?: return
        replaceRulesBySource("CURATED", gateway.fetchCuratedRules())

        val canUseCommunity = communityAccessPolicy?.canUseCommunityRules() == true
        val communityRules = if (canUseCommunity) gateway.fetchCommunityRules() else emptyList()
        replaceRulesBySource("COMMUNITY", communityRules)
    }

    fun queueSpamReport(phoneNumber: String, channel: String, reason: String) {
        val normalized = PhoneNumberUtils.normalize(phoneNumber)
        queries.insertCommunityReport(
            phoneNumber = normalized,
            channel = channel,
            reason = reason,
            createdAt = System.currentTimeMillis(),
            syncState = "PENDING"
        )
    }

    suspend fun flushPendingSpamReports() {
        val gateway = collaborativeGateway ?: return
        if (communityAccessPolicy?.canUseCommunityRules() != true) return

        val pending = queries.getPendingCommunityReports().executeAsList()
        pending.forEach { report ->
            val accepted = gateway.submitReport(
                SpamReportPayload(
                    phoneNumber = report.phoneNumber,
                    channel = report.channel,
                    reason = report.reason,
                    createdAt = report.createdAt
                )
            )
            if (accepted) {
                queries.updateCommunityReportSyncState("SYNCED", report.id)
            }
        }
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

    private fun replaceRulesBySource(source: String, rules: List<CollaborativeRulePayload>) {
        queries.transaction {
            queries.deleteRulesBySource(source)
            rules.forEach { rule ->
                addRule(
                    pattern = rule.pattern,
                    action = rule.action,
                    isRegex = rule.isRegex,
                    alias = rule.alias,
                    source = source
                )
            }
        }
    }

    // --- Palabras Clave de Spam (SMISHING / FRAUD / MARKETING) ---
    fun getSpamKeywords(): List<SpamKeyword> = queries.getSpamKeywords().executeAsList()
    
    fun observeSpamKeywords(): Flow<List<SpamKeyword>> =
        queries.getSpamKeywords().asFlow().mapToList(Dispatchers.IO)

    fun getActiveSpamKeywords(): List<SpamKeyword> {
        val now = System.currentTimeMillis()
        val cached = cachedActiveSpamKeywords
        if (cached != null && (now - lastSpamKeywordsUpdate) < cacheTimeoutMs) {
            return cached
        }
        val list = queries.getActiveSpamKeywords().executeAsList()
        cachedActiveSpamKeywords = list
        lastSpamKeywordsUpdate = now
        return list
    }

    fun addSpamKeyword(keyword: String, category: String = "SMISHING") {
        val trimmed = keyword.trim().lowercase()
        if (trimmed.isEmpty()) return
        queries.insertSpamKeyword(trimmed, category, true, System.currentTimeMillis())
        cachedActiveSpamKeywords = null
    }

    fun deleteSpamKeyword(id: Long) {
        queries.deleteSpamKeyword(id)
        cachedActiveSpamKeywords = null
    }

    fun toggleSpamKeyword(id: Long, isActive: Boolean) {
        queries.toggleSpamKeyword(isActive, id)
        cachedActiveSpamKeywords = null
    }

    // --- Conversaciones Archivadas (SMS) ---
    fun getArchivedConversations(): List<ArchivedConversation> =
        queries.getArchivedConversations().executeAsList()

    fun observeArchivedConversations(): Flow<List<ArchivedConversation>> =
        queries.getArchivedConversations().asFlow().mapToList(Dispatchers.IO)

    fun isConversationArchived(threadId: Long): Boolean =
        queries.isConversationArchived(threadId).executeAsOne() > 0

    fun archiveConversation(threadId: Long, address: String) {
        queries.archiveConversation(threadId, address, System.currentTimeMillis())
    }

    fun unarchiveConversation(threadId: Long) {
        queries.unarchiveConversation(threadId)
    }

    // --- Papelera de Reciclaje (SMS y Llamadas) ---
    fun getTrashItems(): List<TrashItem> = queries.getTrashItems().executeAsList()

    fun getTrashItemsByType(itemType: String): List<TrashItem> =
        queries.getTrashItemsByType(itemType).executeAsList()

    fun observeTrashItems(): Flow<List<TrashItem>> =
        queries.getTrashItems().asFlow().mapToList(Dispatchers.IO)

    fun moveToTrash(itemType: String, originalId: String, senderOrNumber: String, contentPreview: String?) {
        queries.insertTrashItem(itemType, originalId, senderOrNumber, contentPreview, System.currentTimeMillis())
    }

    fun deleteTrashItem(id: Long) {
        queries.deleteTrashItem(id)
    }

    fun clearTrash() {
        queries.clearTrash()
    }

    fun clearTrashByType(itemType: String) {
        queries.clearTrashByType(itemType)
    }

    // --- Grupos de Contactos ---
    fun getContactGroups(): List<ContactGroup> = queries.getContactGroups().executeAsList()

    fun observeContactGroups(): Flow<List<ContactGroup>> =
        queries.getContactGroups().asFlow().mapToList(Dispatchers.IO)

    fun addContactGroup(name: String, iconName: String = "group", colorHex: String = "#3B82F6") {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        queries.insertContactGroup(trimmed, iconName, colorHex)
    }

    fun deleteContactGroup(id: Long) {
        queries.deleteContactGroup(id)
    }

    fun getGroupMembers(groupId: Long): List<String> = queries.getGroupMembers(groupId).executeAsList()

    fun addContactToGroup(contactId: String, groupId: Long) {
        queries.addContactToGroup(contactId, groupId)
    }

    fun removeContactFromGroup(contactId: String, groupId: Long) {
        queries.removeContactFromGroup(contactId, groupId)
    }

    fun getContactGroupsForContact(contactId: String): List<ContactGroup> =
        queries.getContactGroupsForContact(contactId).executeAsList()
}
