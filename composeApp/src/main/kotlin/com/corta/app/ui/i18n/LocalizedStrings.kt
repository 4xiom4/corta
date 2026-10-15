package com.corta.app.ui.i18n

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.res.stringResource
import com.corta.app.R

/**
 * Plain data class that mirrors the strings declared in
 * `res/values/strings.xml`. Grouped into sub-classes to avoid
 * java.lang.VerifyError (too many argument registers).
 */
data class CortaStrings(
    val common: CommonStrings,
    val onboarding: OnboardingStrings,
    val contacts: ContactStrings,
    val sms: SmsStrings,
    val rules: RulesStrings,
    val calls: CallStrings,
    val stats: StatsStrings,
    val diagnostics: DiagnosticStrings,
    val settings: SettingsStrings,
    val login: LoginStrings,
    val dashboard: DashboardStrings,
    val setup: SetupStrings,
    val sim: SimStrings
)

data class CommonStrings(
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
    val back: String,
    val close: String,
    val save: String,
    val configure: String,
    val fix: String,
    val retry: String,
    val send: String,
    val delete: String,
    val dial: String,
    val edit: String,
    val share: String,
    val messageAction: String,
    val video: String,
    val email: String,
    val callAction: String,
    val understood: String,
    val cancel: String,
    val add: String,
    val message: String,
    val unknownNumber: String
)

data class OnboardingStrings(
    val title: String,
    val descBold: String,
    val descRest: String,
    val permissionsReasoningTitle: String,
    val permissionsReasoningDescPart1: String,
    val permissionsReasoningDescBold1: String,
    val permissionsReasoningDescPart2: String,
    val permissionsReasoningDescBold2: String,
    val privacyDetailsTitle: String,
    val privacyDetailsDesc: String,
    val enableCallScreening: String,
    val callScreeningGranted: String,
    val setDefaultDialer: String,
    val defaultDialerGranted: String,
    val setDefaultSms: String,
    val defaultSmsGranted: String,
    val corePermissions: String,
    val corePermissionsGranted: String,
    val permissionsTitle: String,
    val smartBlockingTitle: String,
    val defaultDialerTitle: String,
    val defaultSmsTitle: String,
    val overlayTitle: String,
    val startProtecting: String,
    val skipDebug: String
)

data class ContactStrings(
    val noFound: String,
    val favoritesSection: String,
    val addAction: String,
    val callLabel: String,
    val messageLabel: String,
    val videoLabel: String,
    val emailLabel: String,
    val infoTitle: String,
    val mobileLabel: String,
    val messageCd: String,
    val backCd: String,
    val editCd: String,
    val removeFavorite: String,
    val addFavorite: String,
    val blockContact: String,
    val editContact: String,
    val copyNumber: String,
    val numberBlockedToast: String,
    val numberCopiedToast: String,
    val clipLabel: String,
    val addContact: String,
    val searchContact: String
)

data class SmsStrings(
    val noFound: String,
    val inboxTab: String,
    val blockedTab: String,
    val blockedEmpty: String,
    val newMessageCd: String,
    val blockSender: String,
    val copyNumber: String,
    val blockRuleAlias: String,
    val blockedToast: String,
    val copiedToast: String,
    val shareContactCd: String,
    val sendCd: String,
    val noMessages: String,
    val selectContact: String,
    val composeTitle: String,
    val newMessageTitle: String,
    val composeAction: String,
    val searchContacts: String,
    val composeToLabel: String,
    val composeMessageLabel: String,
    val composeRequiredToast: String,
    val composeSentToast: String,
    val composeBackCd: String,
    val blockedTitle: String,
    val blockedDescTemplate: String,
    val spamWarnTitle: String,
    val spamWarnDescTemplate: String,
    val composeSendError: String,
    val composePlaceholder: String,
    val threadComposePlaceholder: String,
    val linkDialogTitle: String,
    val linkDialogBody: String,
    val linkConfirm: String,
    val linkCancel: String,
    val sendError: String,
    val composeSelected: String
)

data class RulesStrings(
    val blockedNumbers: String,
    val noDefined: String,
    val add: String,
    val enterPhoneNumber: String,
    val phoneNumber: String,
    val automaticProtection: String,
    val subtelBlockedPrefixes: String,
    val communityLocked: String,
    val sourceSubtel: String,
    val sourceCurated: String,
    val sourceCommunity: String,
    val sourceUser: String,
    val addSpamAlias: String,
    val aliasDefault: String,
    val addToContacts: String,
    val blockAndAlias: String,
    val copyNumber: String,
    val copiedPhoneClip: String,
    val copiedToast: String,
    val actionBlocked: String,
    val subtelAlias: String,
    val subtelSubtitle: String,
    val sourceLabel: String,
    val aliasLabel: String
)

data class CallStrings(
    val typeIncoming: String,
    val typeOutgoing: String,
    val typeMissed: String,
    val typeBlocked: String,
    val typeGeneric: String,
    val incomingCall: String,
    val dialing: String,
    val onHold: String,
    val ended: String,
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
    val recordingNotificationTitle: String,
    val recordingChannelName: String,
    val recordingFolder: String,
    val microphonePermissionRequired: String,
    val dialpadHint: String,
    val dialpadPlaceholder: String,
    val logPermissionRequired: String,
    val logEmpty: String,
    val logTypeIncoming: String,
    val logTypeOutgoing: String,
    val logTypeMissed: String,
    val logTypeBlocked: String,
    val logTypeGeneric: String,
    val aliasDialogTitle: String,
    val aliasDialogBody: String,
    val aliasDialogPlaceholder: String,
    val aliasDialogDefault: String,
    val aliasAdded: String,
    val addToContacts: String,
    val blockWithAlias: String,
    val copyNumber: String,
    val numberCopied: String,
    val clipboardLabel: String,
    val recordingNotificationText: String
)

data class StatsStrings(
    val statistics: String,
    val blockedCalls: String,
    val blockedSms: String,
    val totalSpam: String,
    val topSpammers: String,
    val noData: String,
    val importHistorical: String,
    val sectionCalls: String,
    val blockedLabel: String,
    val sectionSms: String,
    val blockedLabelPlural: String,
    val protectionLevel: String,
    val basedOnFiltered: String,
    val timeSaved: String,
    val spamByHour: String,
    val topSpammersList: String,
    val importing: String,
    val timeSavedCaption: String,
    val connectionAttempts: String
)

data class DiagnosticStrings(
    val diagnostics: String,
    val title: String,
    val backCd: String,
    val progress: String,
    val criticalRequirements: String,
    val blockedByMissing: String,
    val miscCritical: String,
    val miscCriticalDesc: String,
    val miscCriticalOk: String,
    val overlay: String,
    val overlayDesc: String,
    val unlock: String,
    val unlockDesc: String,
    val special: String,
    val specialOk: String,
    val roleDialer: String,
    val roleDialerDesc: String,
    val roleScreening: String,
    val roleScreeningDesc: String,
    val roleSms: String,
    val roleSmsDesc: String,
    val roles: String,
    val rolesOk: String,
    val enhanced: String,
    val contacts: String,
    val contactsDesc: String,
    val storage: String,
    val storageDesc: String,
    val notifications: String,
    val network: String,
    val networkOk: String,
    val networkOffline: String,
    val overall: String,
    val missingCritical: String,
    val missingDialer: String,
    val missingScreening: String,
    val missingSms: String,
    val missingOverlay: String,
    val missingNotifications: String,
    val screeningSettingsUnavailable: String,
    val dsBackCd: String,
    val dsSearchHint: String,
    val running: String,
    val continueAction: String,
    val forceContinue: String,
    val runFull: String
)

data class SettingsStrings(
    val backCd: String,
    val sectionPersonalization: String,
    val sectionSecurity: String,
    val sectionInfo: String,
    val visualTheme: String,
    val themeLight: String,
    val themeDark: String,
    val themeSystem: String,
    val themeOptionLight: String,
    val themeOptionDark: String,
    val themeOptionSystem: String,
    val accentColor: String,
    val accentStyle1Dark: String,
    val accentStyle1Light: String,
    val accentStyle2Dark: String,
    val accentStyle2Light: String,
    val accentStyle3Dark: String,
    val accentStyle3Light: String,
    val accentStyle4Dark: String,
    val accentStyle4Light: String,
    val accentStyle5Dark: String,
    val accentStyle5Light: String,
    val accentStitchAmber: String,
    val accentStitchPurple: String,
    val accentStitchCoral: String,
    val accentStitchSage: String,
    val accentDynamic: String,
    val uiScale: String,
    val uiScaleCompact: String,
    val uiScaleNormal: String,
    val uiScaleLarge: String,
    val handedness: String,
    val handednessLeft: String,
    val handednessRight: String,
    val handednessOptionLeft: String,
    val handednessOptionRight: String,
    val language: String,
    val languageEs: String,
    val languageEn: String,
    val linkProtection: String,
    val linkProtectionSubtitle: String,
    val realtimeFiltering: String,
    val realtimeFilteringSubtitle: String,
    val debugMode: String,
    val debugModeSubtitle: String,
    val permissionsManagement: String,
    val permissionsManagementSubtitle: String,
    val donateTitle: String
)

data class LoginStrings(
    val withGoogle: String,
    val continueWithoutAccount: String,
    val withPrefix: String,
    val donorProfileTitle: String,
    val donorProfileBackCd: String,
    val donorProfileActive: String,
    val donorProfileInactive: String,
    val donorProfileInternalTesting: String,
    val donorProfileAvailable: String,
    val donorProfileEnableDebug: String,
    val configureClientId: String,
    val donorProfileToggleLabel: String
)

data class DashboardStrings(
    val options: String,
    val systemDiagnostics: String,
    val donorProfile: String,
    val selectNumber: String,
    val searchPlaceholder: String,
    val overviewTitle: String,
    val callsBlocked: String,
    val totalRules: String,
    val welcome: String
)

data class SetupStrings(
    val title: String,
    val subtitle: String,
    val dialerTitle: String,
    val smsTitle: String,
    val screeningTitle: String,
    val ready: String,
    val start: String,
    val skip: String,
    val check: String,
    val configuredCd: String,
    val configureCd: String,
    val headerDesc: String,
    val dialerDesc: String,
    val smsDesc: String,
    val screeningDesc: String,
    val complete: String,
    val screeningError: String
)

data class SimStrings(
    val numberUnavailable: String,
    val phoneNumberLabel: String,
    val dialogTitle: String,
    val selectLabel: String,
    val manualLabel: String,
    val enterPrompt: String,
    val save: String,
    val close: String
)

/**
 * Composable factory that returns a [CortaStrings] populated from
 * `res/values/strings.xml` for the current locale. Replaces the old
 * hard-coded `EnglishStrings` / `SpanishStrings` objects so the bundle
 * can be translated through Android Studio's Translation Editor and
 * resources override (e.g. RTL, font scale, plurals) keep working.
 */
@Composable
@ReadOnlyComposable
private fun rememberCommonStrings(): CommonStrings = CommonStrings(
    appName = stringResource(R.string.app_name),
    overview = stringResource(R.string.overview),
    logs = stringResource(R.string.logs),
    rules = stringResource(R.string.rules),
    dialpad = stringResource(R.string.dialpad),
    contacts = stringResource(R.string.contacts),
    sms = stringResource(R.string.sms),
    calls = stringResource(R.string.calls),
    search = stringResource(R.string.search),
    menu = stringResource(R.string.menu),
    settings = stringResource(R.string.settings),
    back = stringResource(R.string.back),
    close = stringResource(R.string.close),
    save = stringResource(R.string.save),
    configure = stringResource(R.string.configure),
    fix = stringResource(R.string.fix),
    retry = stringResource(R.string.retry),
    send = stringResource(R.string.send),
    delete = stringResource(R.string.delete),
    dial = stringResource(R.string.dial),
    edit = stringResource(R.string.edit),
    share = stringResource(R.string.share),
    messageAction = stringResource(R.string.message_action),
    video = stringResource(R.string.video),
    email = stringResource(R.string.email),
    callAction = stringResource(R.string.call_action),
    understood = stringResource(R.string.understood),
    cancel = stringResource(R.string.cancel),
    add = stringResource(R.string.add),
    message = stringResource(R.string.message),
    unknownNumber = stringResource(R.string.unknown_number)
)

@Composable
@ReadOnlyComposable
private fun rememberOnboardingStrings(): OnboardingStrings = OnboardingStrings(
    title = stringResource(R.string.onboarding_title),
    descBold = stringResource(R.string.onboarding_desc_bold),
    descRest = stringResource(R.string.onboarding_desc_rest),
    permissionsReasoningTitle = stringResource(R.string.permissions_reasoning_title),
    permissionsReasoningDescPart1 = stringResource(R.string.permissions_reasoning_desc_part1),
    permissionsReasoningDescBold1 = stringResource(R.string.permissions_reasoning_desc_bold1),
    permissionsReasoningDescPart2 = stringResource(R.string.permissions_reasoning_desc_part2),
    permissionsReasoningDescBold2 = stringResource(R.string.permissions_reasoning_desc_bold2),
    privacyDetailsTitle = stringResource(R.string.privacy_details_title),
    privacyDetailsDesc = stringResource(R.string.privacy_details_desc),
    enableCallScreening = stringResource(R.string.enable_call_screening),
    callScreeningGranted = stringResource(R.string.call_screening_granted),
    setDefaultDialer = stringResource(R.string.set_default_dialer),
    defaultDialerGranted = stringResource(R.string.default_dialer_granted),
    setDefaultSms = stringResource(R.string.set_default_sms),
    defaultSmsGranted = stringResource(R.string.default_sms_granted),
    corePermissions = stringResource(R.string.core_permissions),
    corePermissionsGranted = stringResource(R.string.core_permissions_granted),
    permissionsTitle = stringResource(R.string.onboarding_permissions_title),
    smartBlockingTitle = stringResource(R.string.onboarding_smart_blocking_title),
    defaultDialerTitle = stringResource(R.string.onboarding_default_dialer_title),
    defaultSmsTitle = stringResource(R.string.onboarding_default_sms_title),
    overlayTitle = stringResource(R.string.onboarding_overlay_title),
    startProtecting = stringResource(R.string.onboarding_start_protecting),
    skipDebug = stringResource(R.string.onboarding_skip_debug)
)

@Composable
@ReadOnlyComposable
private fun rememberContactStrings(): ContactStrings = ContactStrings(
    noFound = stringResource(R.string.no_contacts_found),
    favoritesSection = stringResource(R.string.favorites_section),
    addAction = stringResource(R.string.add_contact_action),
    callLabel = stringResource(R.string.contact_call_label),
    messageLabel = stringResource(R.string.contact_message_label),
    videoLabel = stringResource(R.string.contact_video_label),
    emailLabel = stringResource(R.string.contact_email_label),
    infoTitle = stringResource(R.string.contact_info_title),
    mobileLabel = stringResource(R.string.contact_mobile_label),
    messageCd = stringResource(R.string.contact_message_cd),
    backCd = stringResource(R.string.contact_back_cd),
    editCd = stringResource(R.string.contact_edit_cd),
    removeFavorite = stringResource(R.string.contact_remove_favorite),
    addFavorite = stringResource(R.string.contact_add_favorite),
    blockContact = stringResource(R.string.contact_block_contact),
    editContact = stringResource(R.string.contact_edit_contact),
    copyNumber = stringResource(R.string.contact_copy_number),
    numberBlockedToast = stringResource(R.string.contact_number_blocked_toast),
    numberCopiedToast = stringResource(R.string.contact_number_copied_toast),
    clipLabel = stringResource(R.string.contact_clip_label),
    addContact = stringResource(R.string.add_contact),
    searchContact = stringResource(R.string.search_contact)
)

@Composable
@ReadOnlyComposable
private fun rememberSmsStrings(): SmsStrings = SmsStrings(
    noFound = stringResource(R.string.no_sms_found),
    inboxTab = stringResource(R.string.sms_inbox_tab),
    blockedTab = stringResource(R.string.sms_blocked_tab),
    blockedEmpty = stringResource(R.string.sms_blocked_empty),
    newMessageCd = stringResource(R.string.sms_new_message_cd),
    blockSender = stringResource(R.string.sms_block_sender),
    copyNumber = stringResource(R.string.sms_copy_number),
    blockRuleAlias = stringResource(R.string.sms_block_rule_alias),
    blockedToast = stringResource(R.string.sms_blocked_toast),
    copiedToast = stringResource(R.string.sms_copied_toast),
    shareContactCd = stringResource(R.string.sms_share_contact_cd),
    sendCd = stringResource(R.string.sms_send_cd),
    noMessages = stringResource(R.string.sms_no_messages),
    selectContact = stringResource(R.string.sms_select_contact),
    composeTitle = stringResource(R.string.sms_compose_title),
    newMessageTitle = stringResource(R.string.sms_new_message_title),
    composeAction = stringResource(R.string.sms_compose_action),
    searchContacts = stringResource(R.string.sms_search_contacts),
    composeToLabel = stringResource(R.string.sms_compose_to_label),
    composeMessageLabel = stringResource(R.string.sms_compose_message_label),
    composeRequiredToast = stringResource(R.string.sms_compose_required_toast),
    composeSentToast = stringResource(R.string.sms_compose_sent_toast),
    composeBackCd = stringResource(R.string.sms_compose_back_cd),
    blockedTitle = stringResource(R.string.sms_blocked_title),
    blockedDescTemplate = stringResource(R.string.sms_blocked_desc),
    spamWarnTitle = stringResource(R.string.sms_spam_warn_title),
    spamWarnDescTemplate = stringResource(R.string.sms_spam_warn_desc),
    composeSendError = stringResource(R.string.compose_send_error),
    composePlaceholder = stringResource(R.string.compose_placeholder),
    threadComposePlaceholder = stringResource(R.string.sms_thread_compose_placeholder),
    linkDialogTitle = stringResource(R.string.sms_link_dialog_title),
    linkDialogBody = stringResource(R.string.sms_link_dialog_body),
    linkConfirm = stringResource(R.string.sms_link_confirm),
    linkCancel = stringResource(R.string.sms_link_cancel),
    sendError = stringResource(R.string.sms_send_error),
    composeSelected = stringResource(R.string.sms_compose_selected)
)

@Composable
@ReadOnlyComposable
private fun rememberRulesStrings(): RulesStrings = RulesStrings(
    blockedNumbers = stringResource(R.string.blocked_numbers),
    noDefined = stringResource(R.string.no_rules_defined),
    add = stringResource(R.string.add_rule),
    enterPhoneNumber = stringResource(R.string.enter_phone_number),
    phoneNumber = stringResource(R.string.phone_number),
    automaticProtection = stringResource(R.string.rules_automatic_protection),
    subtelBlockedPrefixes = stringResource(R.string.rules_subtel_blocked_prefixes),
    communityLocked = stringResource(R.string.rules_community_locked),
    sourceSubtel = stringResource(R.string.rules_source_subtel),
    sourceCurated = stringResource(R.string.rules_source_curated),
    sourceCommunity = stringResource(R.string.rules_source_community),
    sourceUser = stringResource(R.string.rules_source_user),
    addSpamAlias = stringResource(R.string.rules_add_spam_alias),
    aliasDefault = stringResource(R.string.rules_alias_default),
    addToContacts = stringResource(R.string.rules_add_to_contacts),
    blockAndAlias = stringResource(R.string.rules_block_and_alias),
    copyNumber = stringResource(R.string.rules_copy_number),
    copiedPhoneClip = stringResource(R.string.rules_copied_phone_clip),
    copiedToast = stringResource(R.string.rules_copied_toast),
    actionBlocked = stringResource(R.string.action_blocked),
    subtelAlias = stringResource(R.string.rules_subtel_alias),
    subtelSubtitle = stringResource(R.string.rules_subtel_subtitle),
    sourceLabel = stringResource(R.string.rules_source_label),
    aliasLabel = stringResource(R.string.rules_alias_label)
)

@Composable
@ReadOnlyComposable
private fun rememberCallStrings(): CallStrings = CallStrings(
    typeIncoming = stringResource(R.string.call_type_incoming),
    typeOutgoing = stringResource(R.string.call_type_outgoing),
    typeMissed = stringResource(R.string.call_type_missed),
    typeBlocked = stringResource(R.string.call_type_blocked),
    typeGeneric = stringResource(R.string.call_type_generic),
    incomingCall = stringResource(R.string.incoming_call),
    dialing = stringResource(R.string.dialing),
    onHold = stringResource(R.string.on_hold),
    ended = stringResource(R.string.call_ended),
    connecting = stringResource(R.string.connecting),
    mute = stringResource(R.string.mute),
    keypad = stringResource(R.string.keypad),
    speaker = stringResource(R.string.speaker),
    hold = stringResource(R.string.hold),
    record = stringResource(R.string.record),
    hideKeypad = stringResource(R.string.hide_keypad),
    spamWarning = stringResource(R.string.spam_warning),
    reject = stringResource(R.string.reject),
    answer = stringResource(R.string.answer),
    endCall = stringResource(R.string.end_call),
    recordingStarted = stringResource(R.string.recording_started),
    recordingSaved = stringResource(R.string.recording_saved),
    recordingError = stringResource(R.string.recording_error),
    recordingDisabled = stringResource(R.string.recording_disabled),
    recordingNotificationTitle = stringResource(R.string.recording_notification_title),
    recordingChannelName = stringResource(R.string.recording_channel_name),
    recordingFolder = stringResource(R.string.recording_folder),
    microphonePermissionRequired = stringResource(R.string.microphone_permission_required),
    dialpadHint = stringResource(R.string.dialpad_hint),
    dialpadPlaceholder = stringResource(R.string.dialpad_placeholder),
    logPermissionRequired = stringResource(R.string.calllog_permission_required),
    logEmpty = stringResource(R.string.calllog_empty),
    logTypeIncoming = stringResource(R.string.calllog_type_incoming),
    logTypeOutgoing = stringResource(R.string.calllog_type_outgoing),
    logTypeMissed = stringResource(R.string.calllog_type_missed),
    logTypeBlocked = stringResource(R.string.calllog_type_blocked),
    logTypeGeneric = stringResource(R.string.calllog_type_generic),
    aliasDialogTitle = stringResource(R.string.calllog_alias_dialog_title),
    aliasDialogBody = stringResource(R.string.calllog_alias_dialog_body),
    aliasDialogPlaceholder = stringResource(R.string.calllog_alias_dialog_placeholder),
    aliasDialogDefault = stringResource(R.string.calllog_alias_dialog_default),
    aliasAdded = stringResource(R.string.calllog_alias_added),
    addToContacts = stringResource(R.string.calllog_add_to_contacts),
    blockWithAlias = stringResource(R.string.calllog_block_with_alias),
    copyNumber = stringResource(R.string.calllog_copy_number),
    numberCopied = stringResource(R.string.calllog_number_copied),
    clipboardLabel = stringResource(R.string.calllog_clipboard_label),
    recordingNotificationText = stringResource(R.string.recording_notification_text)
)

@Composable
@ReadOnlyComposable
private fun rememberStatsStrings(): StatsStrings = StatsStrings(
    statistics = stringResource(R.string.statistics),
    blockedCalls = stringResource(R.string.blocked_calls),
    blockedSms = stringResource(R.string.blocked_sms),
    totalSpam = stringResource(R.string.total_spam),
    topSpammers = stringResource(R.string.top_spammers),
    noData = stringResource(R.string.stats_no_data),
    importHistorical = stringResource(R.string.stats_import_historical),
    sectionCalls = stringResource(R.string.stats_section_calls),
    blockedLabel = stringResource(R.string.stats_blocked_label),
    sectionSms = stringResource(R.string.stats_section_sms),
    blockedLabelPlural = stringResource(R.string.stats_blocked_label_plural),
    protectionLevel = stringResource(R.string.stats_protection_level),
    basedOnFiltered = stringResource(R.string.stats_based_on_filtered),
    timeSaved = stringResource(R.string.stats_time_saved),
    spamByHour = stringResource(R.string.stats_spam_by_hour),
    topSpammersList = stringResource(R.string.stats_top_spammers),
    importing = stringResource(R.string.stats_importing),
    timeSavedCaption = stringResource(R.string.stats_time_saved_caption),
    connectionAttempts = stringResource(R.string.stats_connection_attempts)
)

@Composable
@ReadOnlyComposable
private fun rememberDiagnosticStrings(): DiagnosticStrings = DiagnosticStrings(
    diagnostics = stringResource(R.string.diagnostics),
    title = stringResource(R.string.diagnostics_title),
    backCd = stringResource(R.string.diagnostics_back_cd),
    progress = stringResource(R.string.diagnostics_progress),
    criticalRequirements = stringResource(R.string.diagnostics_critical_requirements),
    blockedByMissing = stringResource(R.string.diagnostics_blocked_by_missing),
    miscCritical = stringResource(R.string.diagnostics_misc_critical),
    miscCriticalDesc = stringResource(R.string.diagnostics_misc_critical_desc),
    miscCriticalOk = stringResource(R.string.diagnostics_misc_critical_ok),
    overlay = stringResource(R.string.diagnostics_overlay),
    overlayDesc = stringResource(R.string.diagnostics_overlay_desc),
    unlock = stringResource(R.string.diagnostics_unlock),
    unlockDesc = stringResource(R.string.diagnostics_unlock_desc),
    special = stringResource(R.string.diagnostics_special),
    specialOk = stringResource(R.string.diagnostics_special_ok),
    roleDialer = stringResource(R.string.diagnostics_role_dialer),
    roleDialerDesc = stringResource(R.string.diagnostics_role_dialer_desc),
    roleScreening = stringResource(R.string.diagnostics_role_screening),
    roleScreeningDesc = stringResource(R.string.diagnostics_role_screening_desc),
    roleSms = stringResource(R.string.diagnostics_role_sms),
    roleSmsDesc = stringResource(R.string.diagnostics_role_sms_desc),
    roles = stringResource(R.string.diagnostics_roles),
    rolesOk = stringResource(R.string.diagnostics_roles_ok),
    enhanced = stringResource(R.string.diagnostics_enhanced),
    contacts = stringResource(R.string.diagnostics_contacts),
    contactsDesc = stringResource(R.string.diagnostics_contacts_desc),
    storage = stringResource(R.string.diagnostics_storage),
    storageDesc = stringResource(R.string.diagnostics_storage_desc),
    notifications = stringResource(R.string.diagnostics_notifications),
    network = stringResource(R.string.diagnostics_network),
    networkOk = stringResource(R.string.diagnostics_network_ok),
    networkOffline = stringResource(R.string.diagnostics_network_offline),
    overall = stringResource(R.string.diagnostics_overall),
    missingCritical = stringResource(R.string.diagnostics_missing_critical),
    missingDialer = stringResource(R.string.diagnostics_missing_dialer),
    missingScreening = stringResource(R.string.diagnostics_missing_screening),
    missingSms = stringResource(R.string.diagnostics_missing_sms),
    missingOverlay = stringResource(R.string.diagnostics_missing_overlay),
    missingNotifications = stringResource(R.string.diagnostics_missing_notifications),
    screeningSettingsUnavailable = stringResource(R.string.diagnostics_screening_settings_unavailable),
    dsBackCd = stringResource(R.string.ds_back_cd),
    dsSearchHint = stringResource(R.string.ds_search_hint),
    running = stringResource(R.string.diagnostics_running),
    continueAction = stringResource(R.string.diagnostics_continue),
    forceContinue = stringResource(R.string.diagnostics_force_continue),
    runFull = stringResource(R.string.diagnostics_run_full)
)

@Composable
@ReadOnlyComposable
private fun rememberSettingsStrings(): SettingsStrings = SettingsStrings(
    backCd = stringResource(R.string.settings_back_cd),
    sectionPersonalization = stringResource(R.string.settings_section_personalization),
    sectionSecurity = stringResource(R.string.settings_section_security),
    sectionInfo = stringResource(R.string.settings_section_info),
    visualTheme = stringResource(R.string.settings_visual_theme),
    themeLight = stringResource(R.string.settings_theme_light),
    themeDark = stringResource(R.string.settings_theme_dark),
    themeSystem = stringResource(R.string.settings_theme_system),
    themeOptionLight = stringResource(R.string.settings_theme_option_light),
    themeOptionDark = stringResource(R.string.settings_theme_option_dark),
    themeOptionSystem = stringResource(R.string.settings_theme_option_system),
    accentColor = stringResource(R.string.settings_accent_color),
    accentStyle1Dark = stringResource(R.string.settings_accent_style1_dark),
    accentStyle1Light = stringResource(R.string.settings_accent_style1_light),
    accentStyle2Dark = stringResource(R.string.settings_accent_style2_dark),
    accentStyle2Light = stringResource(R.string.settings_accent_style2_light),
    accentStyle3Dark = stringResource(R.string.settings_accent_style3_dark),
    accentStyle3Light = stringResource(R.string.settings_accent_style3_light),
    accentStyle4Dark = stringResource(R.string.settings_accent_style4_dark),
    accentStyle4Light = stringResource(R.string.settings_accent_style4_light),
    accentStyle5Dark = stringResource(R.string.settings_accent_style5_dark),
    accentStyle5Light = stringResource(R.string.settings_accent_style5_light),
    accentStitchAmber = stringResource(R.string.settings_accent_stitch_amber),
    accentStitchPurple = stringResource(R.string.settings_accent_stitch_purple),
    accentStitchCoral = stringResource(R.string.settings_accent_stitch_coral),
    accentStitchSage = stringResource(R.string.settings_accent_stitch_sage),
    accentDynamic = stringResource(R.string.settings_accent_dynamic),
    uiScale = stringResource(R.string.settings_ui_scale),
    uiScaleCompact = stringResource(R.string.settings_ui_scale_compact),
    uiScaleNormal = stringResource(R.string.settings_ui_scale_normal),
    uiScaleLarge = stringResource(R.string.settings_ui_scale_large),
    handedness = stringResource(R.string.settings_handedness),
    handednessLeft = stringResource(R.string.settings_handedness_left),
    handednessRight = stringResource(R.string.settings_handedness_right),
    handednessOptionLeft = stringResource(R.string.settings_handedness_option_left),
    handednessOptionRight = stringResource(R.string.settings_handedness_option_right),
    language = stringResource(R.string.settings_language),
    languageEs = stringResource(R.string.settings_language_es),
    languageEn = stringResource(R.string.settings_language_en),
    linkProtection = stringResource(R.string.settings_link_protection),
    linkProtectionSubtitle = stringResource(R.string.settings_link_protection_subtitle),
    realtimeFiltering = stringResource(R.string.settings_realtime_filtering),
    realtimeFilteringSubtitle = stringResource(R.string.settings_realtime_filtering_subtitle),
    debugMode = stringResource(R.string.settings_debug_mode),
    debugModeSubtitle = stringResource(R.string.settings_debug_mode_subtitle),
    permissionsManagement = stringResource(R.string.settings_permissions_management),
    permissionsManagementSubtitle = stringResource(R.string.settings_permissions_management_subtitle),
    donateTitle = stringResource(R.string.settings_donate_title)
)

@Composable
@ReadOnlyComposable
private fun rememberLoginStrings(): LoginStrings = LoginStrings(
    withGoogle = stringResource(R.string.login_with_google),
    continueWithoutAccount = stringResource(R.string.login_continue_without_account),
    withPrefix = stringResource(R.string.login_with_prefix),
    donorProfileTitle = stringResource(R.string.donor_profile_title),
    donorProfileBackCd = stringResource(R.string.donor_profile_back_cd),
    donorProfileActive = stringResource(R.string.donor_profile_active),
    donorProfileInactive = stringResource(R.string.donor_profile_inactive),
    donorProfileInternalTesting = stringResource(R.string.donor_profile_internal_testing),
    donorProfileAvailable = stringResource(R.string.donor_profile_available),
    donorProfileEnableDebug = stringResource(R.string.donor_profile_enable_debug),
    configureClientId = stringResource(R.string.login_configure_client_id),
    donorProfileToggleLabel = stringResource(R.string.donor_profile_toggle_label)
)

@Composable
@ReadOnlyComposable
private fun rememberDashboardStrings(): DashboardStrings = DashboardStrings(
    options = stringResource(R.string.menu_options),
    systemDiagnostics = stringResource(R.string.menu_system_diagnostics),
    donorProfile = stringResource(R.string.menu_donor_profile),
    selectNumber = stringResource(R.string.dashboard_select_number),
    searchPlaceholder = stringResource(R.string.dashboard_search_placeholder),
    overviewTitle = stringResource(R.string.overview_title),
    callsBlocked = stringResource(R.string.overview_calls_blocked),
    totalRules = stringResource(R.string.overview_total_rules),
    welcome = stringResource(R.string.overview_welcome)
)

@Composable
@ReadOnlyComposable
private fun rememberSetupStrings(): SetupStrings = SetupStrings(
    title = stringResource(R.string.default_setup_title),
    subtitle = stringResource(R.string.default_setup_subtitle),
    dialerTitle = stringResource(R.string.default_setup_dialer_title),
    smsTitle = stringResource(R.string.default_setup_sms_title),
    screeningTitle = stringResource(R.string.default_setup_screening_title),
    ready = stringResource(R.string.default_setup_ready),
    start = stringResource(R.string.default_setup_start),
    skip = stringResource(R.string.default_setup_skip),
    check = stringResource(R.string.default_setup_check),
    configuredCd = stringResource(R.string.default_setup_configured_cd),
    configureCd = stringResource(R.string.default_setup_configure_cd),
    headerDesc = stringResource(R.string.default_setup_header_desc),
    dialerDesc = stringResource(R.string.default_setup_dialer_desc),
    smsDesc = stringResource(R.string.default_setup_sms_desc),
    screeningDesc = stringResource(R.string.default_setup_screening_desc),
    complete = stringResource(R.string.default_setup_complete),
    screeningError = stringResource(R.string.default_setup_screening_error)
)

@Composable
@ReadOnlyComposable
private fun rememberSimStrings(): SimStrings = SimStrings(
    numberUnavailable = stringResource(R.string.sim_number_unavailable),
    phoneNumberLabel = stringResource(R.string.sim_phone_number_label),
    dialogTitle = stringResource(R.string.sim_dialog_title),
    selectLabel = stringResource(R.string.sim_select_label),
    manualLabel = stringResource(R.string.sim_manual_label),
    enterPrompt = stringResource(R.string.sim_enter_prompt),
    save = stringResource(R.string.sim_save),
    close = stringResource(R.string.sim_close)
)

@Composable
@ReadOnlyComposable
fun rememberCortaStrings(): CortaStrings {
    return CortaStrings(
        common = rememberCommonStrings(),
        onboarding = rememberOnboardingStrings(),
        contacts = rememberContactStrings(),
        sms = rememberSmsStrings(),
        rules = rememberRulesStrings(),
        calls = rememberCallStrings(),
        stats = rememberStatsStrings(),
        diagnostics = rememberDiagnosticStrings(),
        settings = rememberSettingsStrings(),
        login = rememberLoginStrings(),
        dashboard = rememberDashboardStrings(),
        setup = rememberSetupStrings(),
        sim = rememberSimStrings()
    )
}

/**
 * Backward-compatibility accessor. Note: Since CortaStrings was refactored
 * into nested groups to avoid VerifyError, existing flat access will break.
 * The caller must update usages to Strings.common.appName, etc.
 */
val LocalLocalizedResources = compositionLocalOf<android.content.res.Resources?> { null }

@Composable
@ReadOnlyComposable
private fun stringResource(@StringRes id: Int): String {
    val localizedRes = LocalLocalizedResources.current
    return localizedRes?.getString(id) ?: androidx.compose.ui.res.stringResource(id)
}

val Strings: CortaStrings
    @Composable
    @ReadOnlyComposable
    get() = rememberCortaStrings()

@Composable
fun CortaTheme(
    language: String = "es",
    content: @Composable () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val localizedResources = androidx.compose.runtime.remember(context, language) {
        val locale = java.util.Locale(language)
        val config = android.content.res.Configuration(context.resources.configuration).apply {
            setLocale(locale)
        }
        context.createConfigurationContext(config).resources
    }
    androidx.compose.runtime.CompositionLocalProvider(
        androidx.compose.ui.platform.LocalConfiguration provides localizedResources.configuration,
        LocalLocalizedResources provides localizedResources
    ) {
        content()
    }
}
