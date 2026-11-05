package io.kiebord.data.prefs

/**
 * Immutable snapshot of all user-configurable settings and preferences for Kiebord.
 */
data class KiebordPreferences(
    // Appearance & Theming
    val theme: String = THEME_DEFAULT_DARK,
    val keyboardHeightScale: Float = 1.0f,
    val keyboardHeightScaleLandscape: Float = 1.0f,
    val chinPaddingPortraitDp: Float = 22.0f,
    val chinPaddingLandscapeDp: Float = 8.0f,
    val horizontalMarginPortraitDp: Float = 0.0f,
    val horizontalMarginLandscapeDp: Float = 0.0f,
    val showNumberRow: Boolean = false,
    val showKeyBorders: Boolean = true,
    val showKeyShadows: Boolean = true,
    val showKeyPopups: Boolean = true,
    val showSecondaryKeys: Boolean = true,
    val showEmojiKey: Boolean = false,

    // Preferences & Haptics
    val vibrateOnKeypress: Boolean = true,
    val vibrationType: String = VIBRATION_TYPE_HAPTIC,
    val vibrationStrength: Int = 50, // 0 - 100%
    val soundOnKeypress: Boolean = false,
    val longPressDelayMs: Int = 300, // 150 - 600 ms
    val cursorTrackpadEnabled: Boolean = true,
    val oneHandedMode: String = ONE_HANDED_OFF,
    val longPressDeleteEnabled: Boolean = true,
    val selectiveDeleteMode: String = DELETE_MODE_WORD,
    val fastDeleteEnabled: Boolean = false,

    // Keyboard Layouts
    val primaryLayoutId: String = LAYOUT_EN_QWERTY,
    val enabledLayoutIds: Set<String> = linkedSetOf(
        LAYOUT_EN_QWERTY,
        LAYOUT_BN_BORNO,
        LAYOUT_BN_AVRO,
        LAYOUT_BN_EASY,
        LAYOUT_BN_NATIONAL
    ),

    // Corrections & Suggestions
    val suggestionsEnabled: Boolean = true,
    val nextWordPrediction: Boolean = true,
    val phoneticAutoCorrect: Boolean = true,
    val bengaliSmartPhonetic: Boolean = true,
    val bengaliShowRawInputCandidate: Boolean = true,
    val learnedWordsEnabled: Boolean = true,
    val gestureTypingEnabled: Boolean = true,
    val showGestureTrail: Boolean = true,
    val autoCapitalization: Boolean = true,
    val doubleSpacePeriod: Boolean = true,
    val autoSpaceAfterSuggestion: Boolean = true,
    val autoSpaceAfterPunctuation: Boolean = true,
    val removeSpaceBeforePunctuation: Boolean = true,
    val traditionalMode: Boolean = false,
    val suggestionBarMode: String = SUGGESTION_BAR_MODE_NORMAL,

    // Toolbar rearrangement persistence
    val pinnedTools: List<String> = listOf("EMOJI", "TEXT_EDIT", "NUMBER_PAD", "CLIPBOARD", "THEME_TOGGLE"),
    val drawerTools: List<String> = listOf("RESIZER", "NUMBER_ROW_TOGGLE", "SECONDARY_KEYS_TOGGLE", "KEY_POPUP_TOGGLE", "ONE_HANDED", "SETTINGS", "TRADITIONAL_MODE", "UNDO", "REDO"),

    // Voice Typing
    val voiceTypingEnabled: Boolean = true,
    val voiceTypingProvider: String = VOICE_PROVIDER_GOOGLE,

    // Layout-specific dynamic extra row overrides ("${layoutId}_${rowId}" -> Boolean)
    val extraRowOverrides: Map<String, Boolean> = emptyMap(),

    // Onboarding / Setup status
    val setupCompleted: Boolean = false
) {
    fun isExtraRowEnabled(layoutId: String, rowId: String, default: Boolean = true): Boolean {
        return extraRowOverrides["${layoutId}_${rowId}"] ?: default
    }

    companion object {
        const val VOICE_PROVIDER_GOOGLE = "google"

        const val THEME_DEFAULT_DARK = "default_dark"
        const val THEME_AMOLED_BLACK = "amoled_black"
        const val THEME_TOKYO_NIGHT = "tokyo_night"
        const val THEME_NORD = "nord"
        const val THEME_DEFAULT_LIGHT = "default_light"

        const val ONE_HANDED_OFF = "off"
        const val ONE_HANDED_LEFT = "left"
        const val ONE_HANDED_RIGHT = "right"

        const val DELETE_MODE_WORD = "word"
        const val DELETE_MODE_CHARACTER = "character"

        const val VIBRATION_TYPE_HAPTIC = "haptic"
        const val VIBRATION_TYPE_VIBRATION = "vibration"

        const val SUGGESTION_BAR_MODE_NORMAL = "normal"
        const val SUGGESTION_BAR_MODE_SCROLLABLE = "scrollable"

        const val LAYOUT_EN_QWERTY = "en_qwerty"
        const val LAYOUT_BN_BORNO = "bn_borno"
        const val LAYOUT_BN_AVRO = "bn_avro"
        const val LAYOUT_BN_PROBHAT = "bn_probhat"
        const val LAYOUT_BN_NATIONAL = "bn_national"
        const val LAYOUT_BN_EASY = "bn_easy"
        const val LAYOUT_AR_ARABIC = "ar_arabic"
        const val LAYOUT_TR_Q = "tr_q"
        const val LAYOUT_TR_Q_COMPACT = "tr_q_compact"
    }
}
