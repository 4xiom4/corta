package com.corta.domain

import com.corta.db.FilterRule

interface SmsRepository {
    suspend fun getConversations(rules: List<FilterRule>): List<SmsMessage>
    suspend fun getBlockedConversations(rules: List<FilterRule>): List<SmsMessage>
    suspend fun getMessagesForThread(threadId: String): List<SmsMessage>
}
