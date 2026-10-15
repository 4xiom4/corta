package com.corta.domain

/**
 * Classification of Chilean phone numbers according to SUBTEL National Numbering Plan,
 * municipal security lines (14xx), emergency services (13x, 4141), and geographic fixed codes.
 */
enum class ChileanNumberType {
    EMERGENCY_NATIONAL,       // 130-149, 4141, 1514
    MUNICIPAL_SECURITY_14XX,   // 1402 Las Condes, 1403 Santiago, 1414 Vitacura, 1445 Maipú...
    SUBTEL_TELEMARKETING_809,  // 809XXXXXX (Massive commercial telemarketing)
    SERVICE_CUSTOMER_CARE_600, // 600XXXXXXX (Shared cost client support)
    SERVICE_TOLL_FREE_800,     // 800XXXXXX (Free toll lines)
    PREMIUM_RATE_700,          // 700XXXXXX (Extra minute surcharge)
    FIXED_LANDLINE,            // Regional landlines (Santiago +56 2, Concepción +56 41, etc.)
    MOBILE,                    // Mobile (+56 9 XXXX XXXX)
    INTERNATIONAL,             // Foreign numbers
    UNKNOWN
}

data class ChileanTelecomMeta(
    val type: ChileanNumberType,
    val badgeLabel: String,
    val detailDescription: String,
    val cityOrRegion: String? = null,
    val isEmergencyOrSafety: Boolean = false,
    val isTelemarketing: Boolean = false,
    val isVerifiedOfficialService: Boolean = false,
    val hasSurchargeWarning: Boolean = false
)

object ChileanTelecomClassifier {

    // 14xx Municipal Security Directory (Chile)
    private val MUNICIPAL_14XX_NAMES = mapOf(
        "1402" to "Seguridad Las Condes",
        "1403" to "Seguridad Santiago Centro",
        "1405" to "Seguridad Lo Prado",
        "1406" to "Seguridad Maipú",
        "1407" to "Seguridad Estación Central",
        "1408" to "Seguridad Cerro Navia",
        "1409" to "Seguridad Renca",
        "1410" to "Seguridad La Florida",
        "1411" to "Seguridad Peñalolén",
        "1412" to "Seguridad La Granja",
        "1414" to "Seguridad Vitacura",
        "1416" to "Seguridad Providencia",
        "1417" to "Seguridad Macul",
        "1418" to "Seguridad Ñuñoa",
        "1419" to "Seguridad La Reina",
        "1420" to "Seguridad San Miguel",
        "1421" to "Seguridad San Joaquín",
        "1423" to "Seguridad Independencia",
        "1424" to "Seguridad Quinta Normal",
        "1425" to "Seguridad Recoleta",
        "1440" to "Seguridad San Bernardo",
        "1441" to "Seguridad Conchalí",
        "1442" to "Seguridad Huechuraba",
        "1445" to "Seguridad Maipú",
        "1446" to "Seguridad Pudahuel",
        "1447" to "Seguridad Quilicura",
        "1468" to "Seguridad Lo Barnechea",
        "1488" to "Seguridad Colina",
        "1470" to "Seguridad Puente Alto",
        "1477" to "Seguridad Talagante"
    )

    // National Emergency Numbers
    private val NATIONAL_EMERGENCY_NAMES = mapOf(
        "130" to "Conaf Incendios Forestales",
        "131" to "SAMU Ambulancia",
        "132" to "Bomberos de Chile",
        "133" to "Carabineros de Chile",
        "134" to "PDI Investigaciones",
        "135" to "Fono Drogas Carabineros",
        "137" to "Rescate Marítimo Armada",
        "139" to "Informaciones Policiales",
        "147" to "Fono Niños Protegidos",
        "149" to "Fono Familia Carabineros",
        "4141" to "Prevención del Suicidio (*4141)",
        "1514" to "Fono Ayuda y Violencia Mujer"
    )

    // Chilean Fixed Landline Area Codes (Códigos de área)
    private val FIXED_AREA_CODES = mapOf(
        "2" to "Santiago (Red fija)",
        "32" to "Valparaíso / Viña del Mar",
        "33" to "Quillota / Marga Marga",
        "34" to "San Felipe / Los Andes",
        "35" to "San Antonio",
        "41" to "Concepción / Talcahuano",
        "42" to "Chillán / Ñuble",
        "43" to "Los Ángeles / Biobío",
        "45" to "Temuco / La Araucanía",
        "51" to "La Serena / Coquimbo",
        "52" to "Copiapó / Atacama",
        "53" to "Ovalle / Limarí",
        "55" to "Antofagasta / Calama",
        "57" to "Iquique / Tarapacá",
        "58" to "Arica y Parinacota",
        "61" to "Punta Arenas / Magallanes",
        "63" to "Valdivia / Los Ríos",
        "64" to "Osorno",
        "65" to "Puerto Montt / Chiloé",
        "67" to "Coyhaique / Aysén",
        "71" to "Talca / Maule",
        "72" to "Rancagua / O'Higgins",
        "73" to "Linares / Cauquenes",
        "75" to "Curicó"
    )

    fun classify(raw: String): ChileanTelecomMeta {
        val trimmed = raw.trim()
        val digits = trimmed.filter { it.isDigit() }

        if (digits.isEmpty()) {
            return ChileanTelecomMeta(
                type = ChileanNumberType.UNKNOWN,
                badgeLabel = "Desconocido",
                detailDescription = "Número no identificado"
            )
        }

        // 1. Short numbers: 3-4 digits (Emergency & Municipal)
        if (digits.length in 3..4) {
            val emergencyName = NATIONAL_EMERGENCY_NAMES[digits]
            if (emergencyName != null) {
                return ChileanTelecomMeta(
                    type = ChileanNumberType.EMERGENCY_NATIONAL,
                    badgeLabel = "Emergencia ($digits)",
                    detailDescription = emergencyName,
                    isEmergencyOrSafety = true,
                    isVerifiedOfficialService = true
                )
            }
            if (digits.startsWith("14")) {
                val municipalName = MUNICIPAL_14XX_NAMES[digits] ?: "Seguridad Ciudadana ($digits)"
                return ChileanTelecomMeta(
                    type = ChileanNumberType.MUNICIPAL_SECURITY_14XX,
                    badgeLabel = "Seguridad Comunal ($digits)",
                    detailDescription = municipalName,
                    isEmergencyOrSafety = true,
                    isVerifiedOfficialService = true
                )
            }
        }

        // Extract Chilean national digits (strip 56 or leading 0)
        val nationalDigits = when {
            digits.startsWith("56") && digits.length in 11..12 -> digits.drop(2)
            digits.startsWith("0") && digits.length >= 10 -> digits.drop(1)
            digits.length in 9..10 -> digits
            else -> digits
        }

        // 2. Telemarketing SUBTEL 809
        if (nationalDigits.startsWith("809")) {
            return ChileanTelecomMeta(
                type = ChileanNumberType.SUBTEL_TELEMARKETING_809,
                badgeLabel = "Telemarketing 809",
                detailDescription = "Llamada comercial masiva autorizada SUBTEL",
                isTelemarketing = true
            )
        }

        // 3. Toll-free 800
        if (nationalDigits.startsWith("800")) {
            return ChileanTelecomMeta(
                type = ChileanNumberType.SERVICE_TOLL_FREE_800,
                badgeLabel = "Línea Gratuita (800)",
                detailDescription = "Llamada sin costo para el usuario",
                isVerifiedOfficialService = true
            )
        }

        // 4. Client service 600
        if (nationalDigits.startsWith("600")) {
            return ChileanTelecomMeta(
                type = ChileanNumberType.SERVICE_CUSTOMER_CARE_600,
                badgeLabel = "Atención a Clientes (600)",
                detailDescription = "Servicio comercial o bancario a costo local",
                isVerifiedOfficialService = true
            )
        }

        // 5. Surcharge 700
        if (nationalDigits.startsWith("700")) {
            return ChileanTelecomMeta(
                type = ChileanNumberType.PREMIUM_RATE_700,
                badgeLabel = "Ojo: Tarifa Extra (700)",
                detailDescription = "Número con costo adicional por minuto",
                hasSurchargeWarning = true
            )
        }

        // 6. Mobile (+56 9 XXXX XXXX)
        if (nationalDigits.length == 9 && nationalDigits.startsWith("9")) {
            return ChileanTelecomMeta(
                type = ChileanNumberType.MOBILE,
                badgeLabel = "Móvil Chile",
                detailDescription = "Línea celular nacional"
            )
        }

        // 7. Fixed Landline (Red fija)
        if (nationalDigits.length == 9) {
            // Check Santiago (+56 2)
            if (nationalDigits.startsWith("2")) {
                val city = FIXED_AREA_CODES["2"] ?: "Santiago"
                return ChileanTelecomMeta(
                    type = ChileanNumberType.FIXED_LANDLINE,
                    badgeLabel = "Santiago (Red fija)",
                    detailDescription = "Llamada de red fija",
                    cityOrRegion = city
                )
            }
            // Check 2-digit regional codes
            val prefix2 = nationalDigits.take(2)
            val city = FIXED_AREA_CODES[prefix2]
            if (city != null) {
                return ChileanTelecomMeta(
                    type = ChileanNumberType.FIXED_LANDLINE,
                    badgeLabel = city,
                    detailDescription = "Red fija regional",
                    cityOrRegion = city
                )
            }
        }

        // 8. International
        if (trimmed.startsWith("+") && !trimmed.startsWith("+56")) {
            return ChileanTelecomMeta(
                type = ChileanNumberType.INTERNATIONAL,
                badgeLabel = "Internacional",
                detailDescription = "Llamada desde el extranjero"
            )
        }

        return ChileanTelecomMeta(
            type = ChileanNumberType.UNKNOWN,
            badgeLabel = "Llamada entrante",
            detailDescription = "Línea telefónica"
        )
    }

    /**
     * Returns pre-seeded list of emergency and municipal contacts for quick directory access in Chile.
     */
    fun getAllEmergencyAndMunicipalEntries(): List<Pair<String, String>> {
        val list = mutableListOf<Pair<String, String>>()
        // National emergencies first
        listOf("133", "131", "132", "134", "130", "137", "147", "149", "4141", "1514").forEach { num ->
            NATIONAL_EMERGENCY_NAMES[num]?.let { list.add(num to it) }
        }
        // Municipalities sorted by code
        MUNICIPAL_14XX_NAMES.entries.sortedBy { it.key }.forEach { (num, name) ->
            list.add(num to name)
        }
        return list
    }

    // Categorized Official Senders for Accurate UI Avatars and Badges
    private val CARRIER_SENDERS = setOf("entel", "wom", "claro", "movistar", "vtr", "mundo")
    private val COURIER_SENDERS = setOf("chilexpress", "correoschile", "correos", "blueexpress", "uber", "didi", "cabify", "pedidosya", "rappi")
    private val GOV_SENDERS = setOf("claveunica", "previred", "servel", "fonasa", "tesoreria", "sii", "sernac", "aguasandinas", "esval", "essbio", "enel", "cge")
    private val BANK_SENDERS = setOf(
        "bancoestado", "santander", "bci", "bancochile", "scotiabank", "itau",
        "bancofalabella", "bancoripley", "tenpo", "mach", "mercadopago", "transbank",
        "google", "microsoft", "apple", "whatsapp"
    )

    private val KNOWN_OFFICIAL_SMS_SENDERS = CARRIER_SENDERS + COURIER_SENDERS + GOV_SENDERS + BANK_SENDERS

    private val SMISHING_KEYWORDS = listOf(
        "cuenta suspendida", "bloqueo preventivo", "clave bloqueada",
        "actualice sus datos", "multa pendiente", "paquete retenido",
        "devolucion de impuestos", "ingrese al enlace", "haga clic",
        "evite el corte", "regularice urgente", "clic aqui", "verifique su cuenta"
    )

    /**
     * Classifies an SMS conversation according to Chilean telecom patterns:
     * - BANK_OTP (Verified shortcodes, official bank sender IDs, 2FA codes)
     * - SPAM_SMISHING (Blocked, SUBTEL 809 commercial spam, or phishing heuristics)
     * - PERSONAL (Known contacts or legitimate private mobile numbers)
     */
    private val smsClassificationCache = mutableMapOf<String, ChileanSmsMeta>()

    fun classifySms(
        address: String,
        body: String,
        isKnownContact: Boolean = false,
        isBlocked: Boolean = false
    ): ChileanSmsMeta {
        val cacheKey = "$address|$isKnownContact|$isBlocked|${body.hashCode()}"
        smsClassificationCache[cacheKey]?.let { return it }

        val result = classifySmsInternal(address, body, isKnownContact, isBlocked)
        if (smsClassificationCache.size > 1500) smsClassificationCache.clear()
        smsClassificationCache[cacheKey] = result
        return result
    }

    private fun classifySmsInternal(
        address: String,
        body: String,
        isKnownContact: Boolean,
        isBlocked: Boolean
    ): ChileanSmsMeta {
        val lowerAddress = address.trim().lowercase()
        val lowerBody = body.lowercase()
        val digits = address.filter { it.isDigit() }

        // 1. If explicitly blocked
        if (isBlocked) {
            val is809 = digits.contains("809")
            return ChileanSmsMeta(
                category = ChileanSmsCategory.SPAM_SMISHING,
                badgeLabel = if (is809) "Comercial" else "Estafa",
                isSmishing = !is809,
                isCommercial809 = is809,
                senderKind = if (is809) ChileanSenderKind.COMMERCIAL else ChileanSenderKind.SMISHING
            )
        }

        // 2. Telemarketing SUBTEL 809
        val nationalDigits = when {
            digits.startsWith("56") && digits.length in 11..12 -> digits.drop(2)
            digits.startsWith("0") && digits.length >= 10 -> digits.drop(1)
            else -> digits
        }
        if (nationalDigits.startsWith("809")) {
            return ChileanSmsMeta(
                category = ChileanSmsCategory.SPAM_SMISHING,
                badgeLabel = "Comercial",
                isCommercial809 = true,
                senderKind = ChileanSenderKind.COMMERCIAL
            )
        }

        // 3. Official Banks, Utilities and OTP Shortcodes
        val isAlphanumeric = address.any { it.isLetter() }
        val isCarrier = CARRIER_SENDERS.any { lowerAddress.contains(it) }
        val isCourier = COURIER_SENDERS.any { lowerAddress.contains(it) }
        val isGov = GOV_SENDERS.any { lowerAddress.contains(it) }
        val isBank = BANK_SENDERS.any { lowerAddress.contains(it) }
        val isKnownOfficialSender = isCarrier || isCourier || isGov || isBank
        val isChileanShortcode = digits.isNotEmpty() && digits.length in 3..6 && !isAlphanumeric
        val hasOtpIndicator = lowerBody.contains("clave dinámica") ||
                lowerBody.contains("código") ||
                lowerBody.contains("codigo") ||
                lowerBody.contains("otp") ||
                lowerBody.contains("seguridad") ||
                lowerBody.contains("autoriza") ||
                lowerBody.contains("verificación")

        if (isKnownOfficialSender || (isChileanShortcode && (hasOtpIndicator || digits.length <= 5))) {
            // Extract possible OTP code
            val otpRegex = Regex("""\b\d{4,8}\b""")
            val otpMatch = if (hasOtpIndicator) otpRegex.find(body)?.value else null

            val kind = when {
                isCarrier -> ChileanSenderKind.CARRIER
                isCourier -> ChileanSenderKind.DELIVERY
                isGov -> ChileanSenderKind.GOVERNMENT
                else -> ChileanSenderKind.BANK
            }

            return ChileanSmsMeta(
                category = ChileanSmsCategory.BANKS_OTP,
                badgeLabel = "Oficial",
                isVerifiedBankOrService = true,
                detectedOtpCode = otpMatch,
                senderKind = kind
            )
        }

        // 4. Smishing / Fraud Heuristic
        val hasUrl = body.contains("http://", ignoreCase = true) ||
                body.contains("https://", ignoreCase = true)
        val hasUrlShortener = body.contains("bit.ly", ignoreCase = true) ||
                body.contains("tinyurl", ignoreCase = true) ||
                body.contains("is.gd", ignoreCase = true) ||
                body.contains("t.co", ignoreCase = true) ||
                body.contains("cutt.ly", ignoreCase = true)
        val hasFraudKeyword = SMISHING_KEYWORDS.any { lowerBody.contains(it) }

        if (!isKnownContact && !isKnownOfficialSender && (hasUrlShortener || (hasUrl && (hasFraudKeyword || nationalDigits.startsWith("9") || nationalDigits.startsWith("44"))) || hasFraudKeyword)) {
            return ChileanSmsMeta(
                category = ChileanSmsCategory.SPAM_SMISHING,
                badgeLabel = "Estafa",
                isSmishing = true,
                senderKind = ChileanSenderKind.SMISHING
            )
        }


        // 5. Personal / Known Contact
        if (isKnownContact) {
            return ChileanSmsMeta(
                category = ChileanSmsCategory.PERSONAL,
                badgeLabel = "Contacto",
                isPersonal = true,
                senderKind = ChileanSenderKind.CONTACT
            )
        }

        if (nationalDigits.startsWith("9") && nationalDigits.length == 9) {
            return ChileanSmsMeta(
                category = ChileanSmsCategory.PERSONAL,
                badgeLabel = "Móvil",
                isPersonal = true,
                senderKind = ChileanSenderKind.UNKNOWN_NUMBER
            )
        }

        return ChileanSmsMeta(
            category = ChileanSmsCategory.ALL,
            badgeLabel = "SMS",
            isPersonal = false,
            senderKind = ChileanSenderKind.UNKNOWN_NUMBER
        )
    }
}

enum class ChileanSmsCategory {
    ALL,
    SPAM_SMISHING,
    BANKS_OTP,
    PERSONAL
}

enum class ChileanSenderKind {
    CARRIER,       // Telecom carriers (WOM, Entel, Claro, Movistar, VTR...)
    BANK,          // Banks, financial & tech (BancoEstado, Santander, BCI, Transbank, Google...)
    DELIVERY,      // Logistics & courier (Chilexpress, CorreosChile, BlueExpress, Uber...)
    GOVERNMENT,    // Essential public services (ClaveÚnica, Fonasa, Servel, SII...)
    COMMERCIAL,    // Telemarketing (SUBTEL 809, retail promos)
    SMISHING,      // Fraudulent attempts & smishing scams
    CONTACT,       // Phonebook contacts
    UNKNOWN_NUMBER // Standard mobile numbers
}

data class ChileanSmsMeta(
    val category: ChileanSmsCategory,
    val badgeLabel: String,
    val isVerifiedBankOrService: Boolean = false,
    val isSmishing: Boolean = false,
    val isCommercial809: Boolean = false,
    val isPersonal: Boolean = false,
    val detectedOtpCode: String? = null,
    val senderKind: ChileanSenderKind = ChileanSenderKind.UNKNOWN_NUMBER
)

