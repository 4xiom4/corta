package com.corta.app.ui.i18n

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf

data class CortaStrings(
    val appName: String,
    val overview: String,
    val logs: String,
    val rules: String,
    val dialpad: String,
    val contacts: String,
    val sms: String,
    val calls: String,
    val search: String,
    val menu: String,
    val settings: String,
    val onboardingTitle: String,
    val onboardingDescBold: String,
    val onboardingDescRest: String,
    val permissionsReasoningTitle: String,
    val permissionsReasoningDescPart1: String,
    val permissionsReasoningDescBold1: String,
    val permissionsReasoningDescPart2: String,
    val permissionsReasoningDescBold2: String,
    val privacyDetailsTitle: String,
    val privacyDetailsDesc: String,
    val understood: String,
    val enableCallScreening: String,
    val callScreeningGranted: String,
    val setDefaultDialer: String,
    val defaultDialerGranted: String,
    val setDefaultSms: String,
    val defaultSmsGranted: String,
    val corePermissions: String,
    val corePermissionsGranted: String,

    // Dialpad
    val dial: String,
    val delete: String,

    // Contacts
    val noContactsFound: String,

    // SMS
    val noSmsFound: String,

    // Rules
    val blockedNumbers: String,
    val noRulesDefined: String,
    val addRule: String,
    val enterPhoneNumber: String,
    val phoneNumber: String,
    val add: String,
    val cancel: String,
    val actionBlocked: String,
    val addContact: String,
    val searchContact: String,
    val message: String,

    // In-Call
    val incomingCall: String,
    val dialing: String,
    val onHold: String,
    val callEnded: String,
    val connecting: String,
    val mute: String,
    val keypad: String,
    val speaker: String,
    val hold: String,
    val record: String,
    val hideKeypad: String,
    val spamWarning: String,
    val reject: String,
    val answer: String,
    val endCall: String,
    val recordingStarted: String,
    val recordingSaved: String,
    val recordingError: String,
    val recordingDisabled: String,

    // Stats
    val statistics: String,
    val blockedCalls: String,
    val blockedSms: String,
    val totalSpam: String,
    val topSpammers: String,

    // Notifications
    val smsBlockedTitle: String,
    val smsBlockedDesc: String,
    val smsSpamWarnTitle: String,
    val smsSpamWarnDesc: String
)

val EnglishStrings = CortaStrings(
    appName = "CORTA!",
    overview = "Overview",
    logs = "Logs",
    rules = "Rules",
    dialpad = "Dialpad",
    contacts = "Contacts",
    sms = "SMS",
    calls = "Calls",
    search = "Search",
    menu = "Menu",
    settings = "Settings",
    onboardingTitle = "CORTA!",
    onboardingDescBold = "To protect you from Spam",
    onboardingDescRest = " we need the following system permissions.",
    permissionsReasoningTitle = "Why CORTA needs permissions",
    permissionsReasoningDescPart1 = "CORTA requires full access to your ",
    permissionsReasoningDescBold1 = "Phone Calls, Call Logs, Contacts, and SMS messages",
    permissionsReasoningDescPart2 = ". These core permissions are strictly used locally to block SUBTEL-identified spam numbers and manage your communications intuitively. ",
    permissionsReasoningDescBold2 = "Your privacy is totally secure.",
    privacyDetailsTitle = "Privacy & Security",
    privacyDetailsDesc = "CORTA processes all data locally on your device. We do not transmit your calls, messages, or contacts to any external servers. The requested permissions are strictly required by the Android OS to block spam and manage your call log according to SUBTEL regulations.",
    understood = "Understood",
    enableCallScreening = "Enable Spam Blocking",
    callScreeningGranted = "Call Screening Granted",
    setDefaultDialer = "Set as Default Dialer",
    defaultDialerGranted = "Default Dialer Granted",
    setDefaultSms = "Set as Default SMS App",
    defaultSmsGranted = "Default SMS App Granted",
    corePermissions = "Grant Core Permissions",
    corePermissionsGranted = "Core Permissions Granted",

    dial = "Dial",
    delete = "Delete",
    noContactsFound = "No contacts found.",
    noSmsFound = "No messages found.",
    
    blockedNumbers = "Blocked Numbers",
    noRulesDefined = "No rules defined.",
    addRule = "Add Rule",
    enterPhoneNumber = "Enter the phone number to block:",
    phoneNumber = "Phone Number",
    add = "Add",
    cancel = "Cancel",
    actionBlocked = "Action: BLOCK",
    addContact = "Add Contact",
    searchContact = "Search contact",
    message = "Message",

    incomingCall = "Incoming call...",
    dialing = "Dialing...",
    onHold = "On Hold",
    callEnded = "Call Ended",
    connecting = "Connecting...",
    mute = "Mute",
    keypad = "Keypad",
    speaker = "Speaker",
    hold = "Hold",
    record = "Record",
    hideKeypad = "Hide Keypad",
    spamWarning = "Attention: potential spam call",
    reject = "Reject",
    answer = "Answer",
    endCall = "End Call",
    recordingStarted = "Recording...",
    recordingSaved = "Recording saved",
    recordingError = "Error: device not compatible with recording",
    recordingDisabled = "Recording disabled in settings",

    statistics = "Statistics",
    blockedCalls = "Blocked Calls",
    blockedSms = "Blocked SMS",
    totalSpam = "Total Spam",
    topSpammers = "Top Spammers",

    smsBlockedTitle = "SMS Blocked",
    smsBlockedDesc = "A message from %s was blocked",
    smsSpamWarnTitle = "Potential SMS Spam",
    smsSpamWarnDesc = "A suspicious message from %s was detected"
)

val SpanishStrings = CortaStrings(
    appName = "CORTA!",
    overview = "Resumen",
    logs = "Registros",
    rules = "Reglas",
    dialpad = "Teclado",
    contacts = "Contactos",
    sms = "Mensajes",
    calls = "Llamadas",
    search = "Buscar",
    menu = "Menú",
    settings = "Ajustes",
    onboardingTitle = "¡CORTA!",
    onboardingDescBold = "Para protegerte del Spam",
    onboardingDescRest = " necesitamos los siguientes permisos del sistema.",
    permissionsReasoningTitle = "Por qué CORTA necesita estos permisos",
    permissionsReasoningDescPart1 = "CORTA requiere acceso total a tus ",
    permissionsReasoningDescBold1 = "Llamadas, Registro de Llamadas, Contactos y mensajes SMS",
    permissionsReasoningDescPart2 = ". Estos permisos fundamentales se utilizan de forma estrictamente local para bloquear números spam de la lista SUBTEL y manejar tus comunicaciones con facilidad. ",
    permissionsReasoningDescBold2 = "Tu privacidad está completamente a salvo.",
    privacyDetailsTitle = "Privacidad y Seguridad",
    privacyDetailsDesc = "CORTA procesa todos los datos localmente en tu dispositivo. No transmitimos tus llamadas, mensajes ni contactos a servidores externos. Los permisos solicitados son estrictamente necesarios por el sistema operativo Android para bloquear el spam y gestionar tu registro de llamadas según las normativas de la SUBTEL.",
    understood = "Entendido",
    enableCallScreening = "Activar Bloqueo de Spam",
    callScreeningGranted = "Bloqueo Concedido",
    setDefaultDialer = "Predeterminado Llamadas",
    defaultDialerGranted = "Llamadas Concedido",
    setDefaultSms = "Predeterminado SMS",
    defaultSmsGranted = "SMS Concedido",
    corePermissions = "Otorgar Permisos Básicos",
    corePermissionsGranted = "Permisos Básicos Concedidos",

    dial = "Llamar",
    delete = "Borrar",
    noContactsFound = "No se encontraron contactos.",
    noSmsFound = "No se encontraron mensajes.",
    
    blockedNumbers = "Números Bloqueados",
    noRulesDefined = "No hay reglas definidas.",
    addRule = "Agregar Regla",
    enterPhoneNumber = "Ingresa el número a bloquear:",
    phoneNumber = "Número de Teléfono",
    add = "Agregar",
    cancel = "Cancelar",
    actionBlocked = "Acción: BLOQUEAR",
    addContact = "Agregar Contacto",
    searchContact = "Buscar contacto",
    message = "Message",

    incomingCall = "Llamada entrante...",
    dialing = "Llamando...",
    onHold = "En espera",
    callEnded = "Llamada finalizada",
    connecting = "Conectando...",
    mute = "Silenciar",
    keypad = "Teclado",
    speaker = "Altavoz",
    hold = "Retener",
    record = "Grabar",
    hideKeypad = "Ocultar teclado",
    spamWarning = "Atención: posible llamada spam",
    reject = "Rechazar",
    answer = "Contestar",
    endCall = "Finalizar",
    recordingStarted = "Grabando...",
    recordingSaved = "Grabación guardada",
    recordingError = "Dispositivo no compatible con grabación",
    recordingDisabled = "Grabación desactivada en ajustes",

    statistics = "Estadísticas",
    blockedCalls = "Llamadas Bloqueadas",
    blockedSms = "SMS Bloqueados",
    totalSpam = "Total Spam",
    topSpammers = "Top Spammers",

    smsBlockedTitle = "SMS bloqueado",
    smsBlockedDesc = "Se bloqueó un mensaje de %s",
    smsSpamWarnTitle = "Posible spam por SMS",
    smsSpamWarnDesc = "Se detectó un mensaje sospecho de %s"
)

val LocalCortaStrings = staticCompositionLocalOf { EnglishStrings }

@Composable
fun CortaTheme(
    language: String = "en", // "en" or "es"
    content: @Composable () -> Unit
) {
    val strings = if (language == "es") SpanishStrings else EnglishStrings
    
    CompositionLocalProvider(
        LocalCortaStrings provides strings
    ) {
        content()
    }
}

val Strings: CortaStrings
    @Composable
    get() = LocalCortaStrings.current
