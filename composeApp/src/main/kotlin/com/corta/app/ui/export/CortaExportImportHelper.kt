package com.corta.app.ui.export

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.ContactsContract
import android.widget.Toast
import androidx.core.content.FileProvider
import com.corta.app.ui.CallLogUiModel
import com.corta.domain.ContactInfo
import com.corta.domain.SmsMessage
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object CortaExportImportHelper {

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
    private val fileTimestampFormat = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())

    // ==========================================
    // 1. EXPORTACIÓN E IMPORTACIÓN DE SMS
    // ==========================================

    fun exportSmsToCsv(context: Context, messages: List<SmsMessage>) {
        try {
            val fileName = "corta_sms_${fileTimestampFormat.format(Date())}.csv"
            val file = File(context.cacheDir, fileName)
            FileOutputStream(file).bufferedWriter().use { writer ->
                writer.write("Fecha,Remitente,Tipo,Mensaje\n")
                messages.forEach { sms ->
                    val dateStr = dateFormat.format(Date(sms.date))
                    val typeStr = if (sms.type == 2) "Enviado" else "Recibido"
                    val cleanBody = sms.body.replace("\"", "\"\"").replace("\n", " ")
                    writer.write("\"$dateStr\",\"${sms.address}\",\"$typeStr\",\"$cleanBody\"\n")
                }
            }
            shareFile(context, file, "text/csv", "Exportar SMS (CSV)")
        } catch (e: Exception) {
            Toast.makeText(context, "Error al exportar SMS: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
        }
    }

    fun exportSmsToJson(context: Context, messages: List<SmsMessage>) {
        try {
            val fileName = "corta_sms_${fileTimestampFormat.format(Date())}.json"
            val file = File(context.cacheDir, fileName)
            FileOutputStream(file).bufferedWriter().use { writer ->
                writer.write("[\n")
                messages.forEachIndexed { index, sms ->
                    val isLast = index == messages.size - 1
                    val cleanBody = sms.body
                        .replace("\\", "\\\\")
                        .replace("\"", "\\\"")
                        .replace("\n", "\\n")
                        .replace("\r", "")
                    writer.write(
                        """  {
    "id": "${sms.id}",
    "threadId": "${sms.threadId}",
    "address": "${sms.address}",
    "body": "$cleanBody",
    "date": ${sms.date},
    "type": ${sms.type}
  }${if (isLast) "" else ","}
"""
                    )
                }
                writer.write("]\n")
            }
            shareFile(context, file, "application/json", "Exportar SMS (JSON)")
        } catch (e: Exception) {
            Toast.makeText(context, "Error al exportar SMS: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
        }
    }

    // ==========================================
    // 2. EXPORTACIÓN DE HISTORIAL DE LLAMADAS
    // ==========================================

    fun exportCallsToCsv(context: Context, calls: List<CallLogUiModel>) {
        try {
            val fileName = "corta_llamadas_${fileTimestampFormat.format(Date())}.csv"
            val file = File(context.cacheDir, fileName)
            FileOutputStream(file).bufferedWriter().use { writer ->
                writer.write("Fecha,Numero,Nombre,Tipo,Duracion_Segundos\n")
                calls.forEach { call ->
                    val dateStr = dateFormat.format(Date(call.date))
                    val typeStr = when {
                        call.isBlocked -> "Bloqueada"
                        call.isMissed -> "Perdida"
                        call.isIncoming -> "Entrante"
                        call.isOutgoing -> "Saliente"
                        else -> "Desconocido"
                    }
                    val cleanName = call.displayName.replace("\"", "\"\"")
                    writer.write("\"$dateStr\",\"${call.number}\",\"$cleanName\",\"$typeStr\",${call.duration}\n")
                }
            }
            shareFile(context, file, "text/csv", "Exportar Historial de Llamadas (CSV)")
        } catch (e: Exception) {
            Toast.makeText(context, "Error al exportar llamadas: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
        }
    }

    fun exportCallLogEntriesToCsv(context: Context, entries: List<com.corta.db.CallLogEntry>) {
        val models = entries.map { entry ->
            CallLogUiModel(
                id = entry.id.toString(),
                number = entry.phoneNumber,
                name = null,
                displayName = entry.phoneNumber,
                date = entry.timestamp,
                duration = entry.duration,
                type = 1,
                matchingRule = null,
                isBlocked = entry.actionTaken == "BLOCK",
                isMissed = false,
                isIncoming = true,
                isOutgoing = false
            )
        }
        exportCallsToCsv(context, models)
    }

    // ==========================================
    // 3. EXPORTACIÓN E IMPORTACIÓN DE CONTACTOS (VCF / vCard)
    // ==========================================

    fun exportContactToVcf(context: Context, contact: ContactInfo) {
        try {
            val safeName = contact.name.ifBlank { contact.phoneNumber }.replace("[^a-zA-Z0-9_-]".toRegex(), "_")
            val fileName = "contacto_${safeName}.vcf"
            val file = File(context.cacheDir, fileName)
            FileOutputStream(file).bufferedWriter().use { writer ->
                writeVCard(writer, contact)
            }
            shareFile(context, file, "text/x-vcard", "Compartir contacto ${contact.name}")
        } catch (e: Exception) {
            Toast.makeText(context, "Error al exportar contacto: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
        }
    }

    fun exportAllContactsToVcf(context: Context, contacts: List<ContactInfo>) {
        try {
            val fileName = "corta_contactos_backup_${fileTimestampFormat.format(Date())}.vcf"
            val file = File(context.cacheDir, fileName)
            FileOutputStream(file).bufferedWriter().use { writer ->
                contacts.forEach { contact ->
                    writeVCard(writer, contact)
                }
            }
            shareFile(context, file, "text/x-vcard", "Exportar todos los contactos (VCF)")
        } catch (e: Exception) {
            Toast.makeText(context, "Error al exportar contactos: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
        }
    }

    private fun writeVCard(writer: java.io.BufferedWriter, contact: ContactInfo) {
        writer.write("BEGIN:VCARD\r\n")
        writer.write("VERSION:3.0\r\n")
        writer.write("FN:${contact.name.ifBlank { contact.phoneNumber }}\r\n")
        if (contact.phoneNumbers.isNotEmpty()) {
            contact.phoneNumbers.forEach { num ->
                writer.write("TEL;TYPE=CELL:${num.trim()}\r\n")
            }
        } else if (contact.phoneNumber.isNotBlank()) {
            writer.write("TEL;TYPE=CELL:${contact.phoneNumber.trim()}\r\n")
        }
        writer.write("NOTE:Exportado por Corta App (Chile)\r\n")
        writer.write("END:VCARD\r\n\r\n")
    }

    /**
     * Parse simple de archivo VCF vCard 2.1 / 3.0 para importar contactos
     */
    fun parseVcf(content: String): List<ContactInfo> {
        val result = mutableListOf<ContactInfo>()
        val lines = content.lines()
        var currentName = ""
        val currentPhones = mutableListOf<String>()
        var inVCard = false

        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.startsWith("BEGIN:VCARD", ignoreCase = true)) {
                inVCard = true
                currentName = ""
                currentPhones.clear()
            } else if (trimmed.startsWith("END:VCARD", ignoreCase = true)) {
                if (inVCard && (currentName.isNotBlank() || currentPhones.isNotEmpty())) {
                    val phone = currentPhones.firstOrNull() ?: ""
                    result.add(
                        ContactInfo(
                            id = "vcf_${System.currentTimeMillis()}_${result.size}",
                            name = currentName.ifBlank { phone },
                            phoneNumber = phone,
                            phoneNumbers = currentPhones.toList(),
                            source = "VCF"
                        )
                    )
                }
                inVCard = false
            } else if (inVCard) {
                if (trimmed.startsWith("FN:", ignoreCase = true)) {
                    currentName = trimmed.substring(3).trim()
                } else if (trimmed.startsWith("TEL", ignoreCase = true)) {
                    val parts = trimmed.split(":")
                    if (parts.size >= 2) {
                        val num = parts[1].trim()
                        if (num.isNotEmpty()) currentPhones.add(num)
                    }
                }
            }
        }
        return result
    }

    /**
     * Importa contactos desde un URI de archivo vCard (.vcf)
     */
    fun importContactsFromVcf(context: Context, uri: Uri): List<ContactInfo> {
        return try {
            val content = context.contentResolver.openInputStream(uri)?.use { stream ->
                stream.bufferedReader().readText()
            } ?: ""
            val parsed = parseVcf(content)
            if (parsed.isNotEmpty()) {
                saveContactsToDevice(context, parsed, accountType = "com.corta.vcf")
            }
            parsed
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun exportContactsToCsv(context: Context, contacts: List<ContactInfo>) {
        try {
            val fileName = "corta_contactos_${fileTimestampFormat.format(Date())}.csv"
            val file = File(context.cacheDir, fileName)
            FileOutputStream(file).bufferedWriter().use { writer ->
                writer.write("Nombre,Telefono,Telefonos_Secundarios,Fuente,Cuenta,Favorito\n")
                contacts.forEach { c ->
                    val cleanName = c.name.replace("\"", "\"\"")
                    val cleanPhone = c.phoneNumber.replace("\"", "\"\"")
                    val cleanOtherPhones = c.phoneNumbers.joinToString(";").replace("\"", "\"\"")
                    val cleanSource = c.source.replace("\"", "\"\"")
                    val cleanAcc = (c.accountName ?: "").replace("\"", "\"\"")
                    val favStr = if (c.isFavorite) "1" else "0"
                    writer.write("\"$cleanName\",\"$cleanPhone\",\"$cleanOtherPhones\",\"$cleanSource\",\"$cleanAcc\",$favStr\n")
                }
            }
            shareFile(context, file, "text/csv", "Exportar Contactos (CSV)")
        } catch (e: Exception) {
            Toast.makeText(context, "Error al exportar contactos: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
        }
    }

    fun importContactsFromCsv(context: Context, uri: Uri): List<ContactInfo> {
        return try {
            val stream = context.contentResolver.openInputStream(uri) ?: return emptyList()
            val lines = stream.bufferedReader().readLines()
            val imported = mutableListOf<ContactInfo>()
            if (lines.size > 1) {
                lines.drop(1).forEach { line ->
                    val tokens = line.split(",(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)".toRegex())
                        .map { it.trim().removeSurrounding("\"").replace("\"\"", "\"") }
                    if (tokens.size >= 2) {
                        val name = tokens[0]
                        val phone = tokens[1]
                        if (name.isNotBlank() || phone.isNotBlank()) {
                            imported.add(
                                ContactInfo(
                                    id = "csv_${System.currentTimeMillis()}_${imported.size}",
                                    name = name.ifBlank { phone },
                                    phoneNumber = phone,
                                    phoneNumbers = listOf(phone),
                                    source = if (tokens.size > 3 && tokens[3].isNotBlank()) tokens[3] else "CSV"
                                )
                            )
                        }
                    }
                }
            }
            if (imported.isNotEmpty()) {
                saveContactsToDevice(context, imported, accountType = "com.corta.csv")
            }
            imported
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun importContactsFromSim(context: Context): List<ContactInfo> {
        val simContacts = mutableListOf<ContactInfo>()
        val urisToQuery = listOf(
            Uri.parse("content://icc/adn"),
            Uri.parse("content://icc/adn/subId/1"),
            Uri.parse("content://icc/adn/subId/2")
        )

        val seenNumbers = mutableSetOf<String>()
        urisToQuery.forEach { uri ->
            try {
                context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    val nameIdx = cursor.getColumnIndex("name").let { if (it != -1) it else cursor.getColumnIndex("tag") }
                    val numIdx = cursor.getColumnIndex("number")
                    val idIdx = cursor.getColumnIndex("_id")

                    while (cursor.moveToNext()) {
                        val name = if (nameIdx != -1) cursor.getString(nameIdx) ?: "" else ""
                        val num = if (numIdx != -1) cursor.getString(numIdx) ?: "" else ""
                        val id = if (idIdx != -1) cursor.getString(idIdx) ?: "" else ""
                        val cleanNum = num.replace(" ", "").trim()
                        if (cleanNum.isNotBlank() && seenNumbers.add(cleanNum)) {
                            simContacts.add(
                                ContactInfo(
                                    id = "sim_${id.ifBlank { simContacts.size.toString() }}",
                                    name = name.ifBlank { cleanNum },
                                    phoneNumber = cleanNum,
                                    phoneNumbers = listOf(cleanNum),
                                    source = "SIM"
                                )
                            )
                        }
                    }
                }
            } catch (e: Exception) {
                // Ignore query failure for unsupported slots
            }
        }

        if (simContacts.isNotEmpty()) {
            saveContactsToDevice(context, simContacts, accountType = "com.corta.sim")
        }
        return simContacts
    }

    fun saveContactsToDevice(context: Context, contacts: List<ContactInfo>, accountType: String? = null): Int {
        var savedCount = 0
        contacts.chunked(50).forEach { batch ->
            try {
                val ops = ArrayList<android.content.ContentProviderOperation>()
                batch.forEach { contact ->
                    val rawContactInsertIndex = ops.size
                    ops.add(
                        android.content.ContentProviderOperation.newInsert(ContactsContract.RawContacts.CONTENT_URI)
                            .withValue(ContactsContract.RawContacts.ACCOUNT_TYPE, accountType)
                            .withValue(ContactsContract.RawContacts.ACCOUNT_NAME, accountType ?: "Corta")
                            .build()
                    )
                    ops.add(
                        android.content.ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                            .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, rawContactInsertIndex)
                            .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE)
                            .withValue(ContactsContract.CommonDataKinds.StructuredName.DISPLAY_NAME, contact.name)
                            .build()
                    )
                    val phoneToInsert = contact.phoneNumber.ifBlank { contact.phoneNumbers.firstOrNull() ?: "" }
                    if (phoneToInsert.isNotBlank()) {
                        ops.add(
                            android.content.ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                                .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, rawContactInsertIndex)
                                .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE)
                                .withValue(ContactsContract.CommonDataKinds.Phone.NUMBER, phoneToInsert)
                                .withValue(ContactsContract.CommonDataKinds.Phone.TYPE, ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE)
                                .build()
                        )
                    }
                }
                if (ops.isNotEmpty()) {
                    context.contentResolver.applyBatch(ContactsContract.AUTHORITY, ops)
                    savedCount += batch.size
                }
            } catch (e: Exception) {
                // Failed to save batch
            }
        }
        return savedCount
    }

    // ==========================================
    // 4. RESPALDO Y RESTAURACIÓN COMPLETA (JSON)
    // ==========================================

    fun exportFullBackupJson(
        context: Context,
        rules: List<com.corta.db.FilterRule>,
        cortaRepository: com.corta.domain.CortaRepository,
        prefs: android.content.SharedPreferences
    ) {
        try {
            val fileName = "corta_respaldo_completo_${fileTimestampFormat.format(Date())}.json"
            val file = File(context.cacheDir, fileName)
            val json = org.json.JSONObject().apply {
                put("corta_version", "1.2")
                put("export_date", dateFormat.format(Date()))
                put("timestamp", System.currentTimeMillis())

                // Reglas
                val rulesArray = org.json.JSONArray()
                rules.forEach { rule ->
                    val r = org.json.JSONObject().apply {
                        put("pattern", rule.pattern)
                        put("action", rule.action)
                        put("isRegex", rule.isRegex == true)
                        put("alias", rule.alias ?: "")
                        put("source", rule.source)
                    }
                    rulesArray.put(r)
                }
                put("rules", rulesArray)

                // Palabras clave de spam
                val keywords = cortaRepository.getSpamKeywords()
                val kwArray = org.json.JSONArray()
                keywords.forEach { kw ->
                    val k = org.json.JSONObject().apply {
                        put("keyword", kw.keyword)
                        put("category", kw.category)
                        put("isActive", kw.isActive == true)
                    }
                    kwArray.put(k)
                }
                put("spam_keywords", kwArray)

                // Preferencias
                val prefsObj = org.json.JSONObject().apply {
                    put("only_contacts_calls", prefs.getBoolean("only_contacts_calls", false))
                    put("block_private_numbers", prefs.getBoolean("block_private_numbers", false))
                    put("block_international_calls", prefs.getBoolean("block_international_calls", false))
                    put("chao_809_aggressive", prefs.getBoolean("chao_809_aggressive", true))
                    put("silence_warn_calls", prefs.getBoolean("silence_warn_calls", false))
                    put("detect_ping_calls", prefs.getBoolean("detect_ping_calls", true))
                    put("protect_sms_links", prefs.getBoolean("protect_sms_links", true))
                    put("filter_alphanumeric_senders", prefs.getBoolean("filter_alphanumeric_senders", true))
                    put("sms_spam_notifications_enabled", prefs.getBoolean("sms_spam_notifications_enabled", true))
                    put("protect_sim_1", prefs.getBoolean("protect_sim_1", true))
                    put("protect_sim_2", prefs.getBoolean("protect_sim_2", true))
                    put("show_sim_badges", prefs.getBoolean("show_sim_badges", true))
                    put("is_donor", prefs.getBoolean("is_donor", false))
                    put("user_name", prefs.getString("user_name", "Mi Dispositivo"))
                    put("user_phone", prefs.getString("user_phone", ""))
                    put("theme_mode", prefs.getString("theme_mode", "SYSTEM"))
                    put("accent_color", prefs.getString("accent_color", "DYNAMIC"))
                    put("ui_scale", prefs.getString("ui_scale", "NORMAL"))
                    put("handedness", prefs.getString("handedness", "RIGHT"))
                    put("language", prefs.getString("language", "es"))
                }
                put("preferences", prefsObj)
            }
            file.writeText(json.toString(2))
            shareFile(context, file, "application/json", "Exportar Respaldo Corta (JSON)")
        } catch (e: Exception) {
            Toast.makeText(context, "Error al exportar respaldo: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
        }
    }

    fun importFullBackupJson(
        context: Context,
        uri: Uri,
        cortaRepository: com.corta.domain.CortaRepository,
        prefs: android.content.SharedPreferences
    ): Int {
        val jsonString = context.contentResolver.openInputStream(uri)?.use { stream ->
            stream.bufferedReader().readText()
        } ?: return 0

        val root = org.json.JSONObject(jsonString)
        var restoredCount = 0

        // 1. Restaurar reglas
        if (root.has("rules")) {
            val rulesArray = root.getJSONArray("rules")
            for (i in 0 until rulesArray.length()) {
                val obj = rulesArray.getJSONObject(i)
                val pattern = obj.getString("pattern")
                val actionStr = obj.optString("action", "BLOCK")
                val isRegex = obj.optBoolean("isRegex", false)
                val alias = obj.optString("alias", "").takeIf { it.isNotBlank() }
                val source = obj.optString("source", "USER")
                val action = try { com.corta.domain.FilterAction.valueOf(actionStr) } catch (e: Exception) { com.corta.domain.FilterAction.BLOCK }
                cortaRepository.addRule(pattern, action, isRegex, alias, source)
                restoredCount++
            }
        }

        // 2. Restaurar palabras clave
        if (root.has("spam_keywords")) {
            val kwArray = root.getJSONArray("spam_keywords")
            for (i in 0 until kwArray.length()) {
                val obj = kwArray.getJSONObject(i)
                val keyword = obj.getString("keyword")
                val category = obj.optString("category", "SMISHING")
                cortaRepository.addSpamKeyword(keyword, category)
                restoredCount++
            }
        }

        // 3. Restaurar preferencias
        if (root.has("preferences")) {
            val prefsObj = root.getJSONObject("preferences")
            val editor = prefs.edit()
            prefsObj.keys().forEach { key ->
                when (val value = prefsObj.get(key)) {
                    is Boolean -> editor.putBoolean(key, value)
                    is Int -> editor.putInt(key, value)
                    is Long -> editor.putLong(key, value)
                    is Float -> editor.putFloat(key, value)
                    is String -> editor.putString(key, value)
                }
            }
            editor.apply()
        }

        return restoredCount
    }

    private fun shareFile(context: Context, file: File, mimeType: String, chooserTitle: String) {
        val uri: Uri = try {
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        } catch (e: Exception) {
            Uri.fromFile(file)
        }
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, chooserTitle).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        })
    }
}
