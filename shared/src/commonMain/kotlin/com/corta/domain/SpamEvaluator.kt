package com.corta.domain

class SpamEvaluator(private val repository: CortaRepository) {

    private val recognizedCountryCodes = listOf(
        "+56", "+1", "+52", "+54", "+55", "+57", "+58", "+591", "+592", "+593", "+594",
        "+595", "+596", "+597", "+598", "+599", "+20", "+27", "+30", "+31", "+32", "+33",
        "+34", "+39", "+40", "+41", "+43", "+44", "+45", "+46", "+47", "+48", "+49", "+51",
        "+60", "+61", "+62", "+63", "+64", "+65", "+66", "+7", "+81", "+82", "+84", "+86",
        "+90", "+91", "+92", "+93", "+94", "+95", "+98"
    ).sortedByDescending { it.length }

    fun evaluateCall(
        phoneNumber: String,
        onlyContacts: Boolean = false,
        blockInternational: Boolean = false,
        chao809Aggressive: Boolean = true
    ): FilterAction {
        if (phoneNumber.isBlank()) return FilterAction.WARN
        if (repository.isWhitelistedNumber(phoneNumber)) {
            return FilterAction.ALLOW
        }
        val actionFromRules = repository.getActionForNumber(phoneNumber)
        if (actionFromRules != FilterAction.ALLOW) {
            return actionFromRules
        }
        
        // Si es contacto conocido, permitir
        if (repository.isContact(phoneNumber)) {
            return FilterAction.ALLOW
        }
        
        // Regla avanzada: Solo contactos (excepto lista blanca de emergencia inmutable)
        if (onlyContacts) {
            return FilterAction.BLOCK
        }

        // Regla avanzada: Bloquear llamadas internacionales que no sean de Chile (+56)
        if (blockInternational && phoneNumber.startsWith("+") && !phoneNumber.startsWith("+56")) {
            return FilterAction.BLOCK
        }

        // Heurísticas para números desconocidos
        return evaluateUnknownNumber(phoneNumber, chao809Aggressive)
    }

    fun evaluateSms(phoneNumber: String, body: String): FilterAction {
        if (phoneNumber.isBlank()) return FilterAction.WARN
        if (repository.isWhitelistedNumber(phoneNumber)) {
            return FilterAction.ALLOW
        }
        val actionFromRules = repository.getActionForNumber(phoneNumber)
        if (actionFromRules != FilterAction.ALLOW) {
            return actionFromRules
        }
        
        // Si es contacto conocido, permitir
        if (repository.isContact(phoneNumber)) {
            return FilterAction.ALLOW
        }
        
        // Evaluar contenido del SMS para heurísticas avanzadas
        val smsRisk = evaluateSmsContent(body)
        val numberRisk = evaluateUnknownNumber(phoneNumber)
        
        // Usar el nivel de riesgo más alto entre número y contenido
        return when {
            smsRisk == FilterAction.BLOCK || numberRisk == FilterAction.BLOCK -> FilterAction.BLOCK
            smsRisk == FilterAction.MUTE || numberRisk == FilterAction.MUTE -> FilterAction.MUTE
            smsRisk == FilterAction.WARN || numberRisk == FilterAction.WARN -> FilterAction.WARN
            else -> FilterAction.ALLOW
        }
    }
    
    private fun evaluateUnknownNumber(phoneNumber: String, chao809Aggressive: Boolean = true): FilterAction {
        // Heurísticas basadas en patrones de números
        val digitsOnly = phoneNumber.filter { it.isDigit() }

        // Chile-first: 809 (Telemarketing masivo no deseado) -> Bloqueo obligatorio según SUBTEL
        if (chao809Aggressive && (digitsOnly.startsWith("809") || digitsOnly.startsWith("56809"))) {
            return FilterAction.BLOCK
        }
        if ((digitsOnly.startsWith("809") && digitsOnly.length == 9) || (digitsOnly.startsWith("56809") && digitsOnly.length == 11)) {
            return FilterAction.BLOCK
        }

        // 0. Servicios masivos regulados en Chile (no bloquear ciegamente)
        if (isChileanMassiveServiceNumber(phoneNumber, digitsOnly)) {
            return FilterAction.WARN
        }
        
        // 1. Números internacionales sospechosos (sin prefijo reconocido)
        if (phoneNumber.startsWith("+")) {
            val countryCode = extractCountryCode(phoneNumber)
            if (countryCode == null || !isRecognizedCountryCode(countryCode)) {
                return FilterAction.WARN
            }
        }
        
        // 2. Números con patrones de spam comunes
        val spamPatterns = listOf(
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
        val marketingPrefixes = listOf("800", "900")
        if (marketingPrefixes.any { phoneNumber.startsWith(it) }) {
            return FilterAction.WARN
        }

        // Chile-first: 809 (Telemarketing masivo no deseado) -> Bloqueo obligatorio según SUBTEL
        if ((digitsOnly.startsWith("809") && digitsOnly.length == 9) || (digitsOnly.startsWith("56809") && digitsOnly.length == 11)) {
            return FilterAction.BLOCK
        }

        // 600 (Servicios comerciales contratados) -> Advertir
        if ((digitsOnly.startsWith("600") && digitsOnly.length in 9..10) || (digitsOnly.startsWith("56600") && digitsOnly.length in 11..12)) {
            return FilterAction.WARN
        }

        // +56 9 (móvil chileno) desconocido: permitir/advertir según contexto
        if (digitsOnly.startsWith("569") && digitsOnly.length == 11) {
            return FilterAction.WARN
        }
        
        return FilterAction.ALLOW
    }
    
    private fun evaluateSmsContent(body: String): FilterAction {
        val lowerBody = body.lowercase()

        // 1. Evaluar palabras clave de alto riesgo (SMISHING / FRAUD / ESTAFA) -> Bloqueo directo
        val activeDbKeywords = repository.getActiveSpamKeywords()
        var hasMarketingKeyword = false
        for (kw in activeDbKeywords) {
            val word = kw.keyword.trim().lowercase()
            if (word.isNotEmpty() && lowerBody.contains(word)) {
                when (kw.category.uppercase()) {
                    "SMISHING", "FRAUD", "ESTAFA" -> return FilterAction.BLOCK
                    "MARKETING", "PROMO" -> hasMarketingKeyword = true
                }
            }
        }

        // 2. Heurísticas basadas en contenido del mensaje
        val spamKeywords = listOf(
            "ganaste", "premio", "sorteo", "lotería", "oferta", "descuento",
            "urgente", "último día", "click aquí", "visita", "promo", "gratis",
            "dinero", "transferencia", "banco", "cuenta", "seguridad", "verifica",
            "http://", "https://", "www.", ".com", "link", "enlace"
        )
        
        val spamKeywordCount = spamKeywords.count { keyword -> keyword in lowerBody }
        
        // Detectar URLs sospechosas
        val urlCount = Regex("https?://[\\w\\.-]+\\.[a-zA-Z]{2,}").findAll(lowerBody).count()
        
        // Detectar caracteres sospechosos o mayúsculas excesivas
        val excessiveCaps = body.count { it.isUpperCase() } > body.length * 0.7
        val suspiciousChars = setOf('$', '€', '£', '¥', '%', '@', '#')
        val suspiciousCharCount = body.count { it in suspiciousChars }
        
        return when {
            spamKeywordCount >= 3 || urlCount >= 2 -> FilterAction.BLOCK
            hasMarketingKeyword -> FilterAction.MUTE
            spamKeywordCount >= 1 || urlCount >= 1 || excessiveCaps || suspiciousCharCount >= 3 -> FilterAction.WARN
            else -> FilterAction.ALLOW
        }
    }
    
    private fun isRecognizedCountryCode(code: String): Boolean {
        return recognizedCountryCodes.any { code == it }
    }

    private fun isChileanMassiveServiceNumber(phoneNumber: String, digitsOnly: String): Boolean {
        if (digitsOnly.startsWith("600") && digitsOnly.length == 10) return true
        if (!phoneNumber.startsWith("+") && digitsOnly.startsWith("809") && digitsOnly.length == 9) return true
        if (digitsOnly.startsWith("56809") && digitsOnly.length == 11) return true
        return false
    }

    private fun extractCountryCode(phoneNumber: String): String? {
        if (!phoneNumber.startsWith("+")) return null
        return recognizedCountryCodes.firstOrNull { phoneNumber.startsWith(it) }
    }
}
