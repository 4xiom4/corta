package com.corta.domain

class CallValidator(private val evaluator: SpamEvaluator) {

    fun validateIncomingCall(
        phoneNumber: String,
        onlyContacts: Boolean = false,
        blockInternational: Boolean = false,
        chao809Aggressive: Boolean = true
    ): FilterAction {
        return evaluator.evaluateCall(phoneNumber, onlyContacts, blockInternational, chao809Aggressive)
    }
}
