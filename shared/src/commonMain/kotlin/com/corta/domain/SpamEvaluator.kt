package com.corta.domain

class SpamEvaluator(private val repository: CortaRepository) {

    fun evaluateCall(phoneNumber: String): FilterAction {
        val normalized = PhoneNumberUtils.normalize(phoneNumber)
        val actionFromRules = repository.getActionForNumber(normalized)
        if (actionFromRules != FilterAction.ALLOW) {
            return actionFromRules
        }
        
        // Si es contacto conocido, permitir
        if (repository.isContact(normalized)) {
            return FilterAction.ALLOW
        }
        
        // Heurísticas para números desconocidos
        return evaluateUnknownNumber(normalized)
    }

    fun evaluateSms(phoneNumber: String, body: String): FilterAction {
        val normalized = PhoneNumberUtils.normalize(phoneNumber)
        val actionFromRules = repository.getActionForNumber(normalized)
        if (actionFromRules != FilterAction.ALLOW) {
            return actionFromRules
        }
        
        // Si es contacto conocido, permitir
        if (repository.isContact(normalized)) {
            return FilterAction.ALLOW
        }
        
        // Evaluar contenido del SMS para heurísticas avanzadas
        val smsRisk = evaluateSmsContent(body)
        val numberRisk = evaluateUnknownNumber(normalized)
        
        // Usar el nivel de riesgo más alto entre número y contenido
        return when {
            smsRisk == FilterAction.BLOCK || numberRisk == FilterAction.BLOCK -> FilterAction.BLOCK
            smsRisk == FilterAction.MUTE || numberRisk == FilterAction.MUTE -> FilterAction.MUTE
            smsRisk == FilterAction.WARN || numberRisk == FilterAction.WARN -> FilterAction.WARN
            else -> FilterAction.ALLOW
        }
    }
    
    private fun evaluateUnknownNumber(phoneNumber: String): FilterAction {
        // Heurísticas basadas en patrones de números
        
        // 1. Números internacionales sospechosos (sin prefijo reconocido)
        if (phoneNumber.startsWith("+") && phoneNumber.length > 12) {
            val countryCode = phoneNumber.take(3)
            if (!isRecognizedCountryCode(countryCode)) {
                return FilterAction.WARN
            }
        }
        
        // 2. Números con patrones de spam comunes
        val spamPatterns = listOf(
            Regex("^800.*"), // Números 800
            Regex("^900.*"), // Números de tarificación especial
            Regex("^123.*"), // Patrones secuenciales
            Regex("^(.)\\1{6,}"), // Mismo dígito repetido
            Regex("^[0-9]{10}$"), // Números de 10 dígitos sin prefijo país
        )
        
        spamPatterns.forEach { pattern ->
            if (pattern.matches(phoneNumber)) {
                return FilterAction.WARN
            }
        }
        
        // 3. Números muy cortos o muy largos
        if (phoneNumber.length < 7 || phoneNumber.length > 15) {
            return FilterAction.WARN
        }
        
        // 4. Números que empiezan con patrones de marketing
        val marketingPrefixes = listOf("800", "900", "809", "829", "849")
        if (marketingPrefixes.any { phoneNumber.startsWith(it) }) {
            return FilterAction.WARN
        }
        
        return FilterAction.ALLOW
    }
    
    private fun evaluateSmsContent(body: String): FilterAction {
        // Heurísticas basadas en contenido del mensaje
        
        // Palabras clave de spam
        val spamKeywords = listOf(
            "ganaste", "premio", "sorteo", "lotería", "oferta", "descuento",
            "urgente", "último día", "click aquí", "visita", "promo", "gratis",
            "dinero", "transferencia", "banco", "cuenta", "seguridad", "verifica",
            "http://", "https://", "www.", ".com", "link", "enlace"
        )
        
        val lowerBody = body.lowercase()
        val spamKeywordCount = spamKeywords.count { keyword -> keyword in lowerBody }
        
        // Detectar URLs sospechosas
        val urlCount = Regex("https?://[\\w\\.-]+\\.[a-zA-Z]{2,}").findAll(lowerBody).count()
        
        // Detectar caracteres sospechosos o mayúsculas excesivas
        val excessiveCaps = body.count { it.isUpperCase() } > body.length * 0.7
        val suspiciousChars = setOf('$', '€', '£', '¥', '%', '@', '#')
        val suspiciousCharCount = body.count { it in suspiciousChars }
        
        return when {
            spamKeywordCount >= 3 || urlCount >= 2 -> FilterAction.BLOCK
            spamKeywordCount >= 1 || urlCount >= 1 || excessiveCaps || suspiciousCharCount >= 3 -> FilterAction.WARN
            else -> FilterAction.ALLOW
        }
    }
    
    private fun isRecognizedCountryCode(code: String): Boolean {
        // Códigos de país reconocidos (Chile y principales)
        val recognizedCodes = listOf(
            "+56", // Chile
            "+1",  // USA/Canadá
            "+52", // México
            "+54", // Argentina
            "+55", // Brasil
            "+57", // Colombia
            "+58", // Venezuela
            "+593", // Ecuador
            "+595", // Paraguay
            "+598", // Uruguay
            "+51", // Perú
            "+34", // España
            "+39", // Italia
            "+33", // Francia
            "+49", // Alemania
            "+44", // Reino Unido
        )
        return recognizedCodes.any { code.startsWith(it) }
    }
}
