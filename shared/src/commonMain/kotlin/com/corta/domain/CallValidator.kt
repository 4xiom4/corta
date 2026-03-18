package com.corta.domain

class CallValidator(private val evaluator: SpamEvaluator) {

    fun validateIncomingCall(phoneNumber: String): FilterAction {
        return evaluator.evaluateCall(phoneNumber)
    }
}
