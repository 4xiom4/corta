package com.corta.domain

import androidx.compose.runtime.Immutable

@Immutable
data class SmsMessage(
    val id: String,
    val threadId: String,
    val address: String, // Sender or Receiver
    val body: String,
    val date: Long,
    val type: Int // 1 = Inbox, 2 = Sent
)
