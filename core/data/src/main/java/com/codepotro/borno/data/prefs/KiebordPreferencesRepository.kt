package com.codepotro.borno.data.prefs

import android.content.Context
import android.os.Build
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

private val Context.kiebordDataStore: DataStore<Preferences> by preferencesDataStore(name = "kiebord_settings")

class KiebordPreferencesRepository private constructor(private val context: Context) {

    private val fastCache = context.getSharedPreferences("kiebord_fast_cache", Context.MODE_PRIVATE)

    fun isSetupCompletedSync(): Boolean {
        return fastCache.getBoolean("setup_completed", false)
    }

    fun getInitialPreferences(): KiebordPreferences {
        return KiebordPreferences(setupCompleted = isSetupCompletedSync())
    }

    private object PreferencesKeys {
        val THEME = stringPreferencesKey("pref_theme")
        val KEYBOARD_HEIGHT_SCALE = floatPreferencesKey("pref_keyboard_height_scale")
        val KEYBOARD_HEIGHT_SCALE_LANDSCAPE = floatPreferencesKey("pref_keyboard_height_scale_landscape")
        val CHIN_PADDING_PORTRAIT = floatPreferencesKey("pref_chin_padding_portrait")
        val CHIN_PADDING_LANDSCAPE = floatPreferencesKey("pref_chin_padding_landscape")
        val HORIZONTAL_MARGIN_PORTRAIT = floatPreferencesKey("pref_horizontal_margin_portrait")
        val HORIZONTAL_MARGIN_LANDSCAPE = floatPreferencesKey("pref_horizontal_margin_landscape")
        val SHOW_NUMBER_ROW = booleanPreferencesKey("pref_show_number_row")
        val SHOW_KEY_BORDERS = booleanPreferencesKey("pref_show_key_borders")
        val SHOW_KEY_SHADOWS = booleanPreferencesKey("pref_show_key_shadows")
        val SHOW_KEY_POPUPS = booleanPreferencesKey("pref_show_key_popups")
        val SHOW_SECONDARY_KEYS = booleanPreferencesKey("pref_show_secondary_keys")
        val SHOW_GLOBE_KEY = booleanPreferencesKey("pref_show_globe_key")
        val SHOW_EMOJI_KEY = booleanPreferencesKey("pref_show_emoji_key")
        val SHOW_EMOJI_ROW = booleanPreferencesKey("pref_show_emoji_row")
        val RECENT_EMOJIS = stringPreferencesKey("pref_recent_emojis")

        val VIBRATE_ON_KEYPRESS = booleanPreferencesKey("pref_vibrate_on_keypress")
        val VIBRATION_TYPE = stringPreferencesKey("pref_vibration_type")
        val VIBRATION_STRENGTH = intPreferencesKey("pref_vibration_strength")
        val SOUND_ON_KEYPRESS = booleanPreferencesKey("pref_sound_on_keypress")
        val SOUND_VOLUME = intPreferencesKey("pref_sound_volume")
        val LONG_PRESS_DELAY_MS = intPreferencesKey("pref_long_press_delay_ms")
        val LONG_PRESS_PRIMARY_SHORTCUTS_ENABLED = booleanPreferencesKey("pref_long_press_primary_shortcuts_enabled")
        val CURSOR_TRACKPAD_ENABLED = booleanPreferencesKey("pref_cursor_trackpad_enabled")
        val SPACEBAR_SWIPE_MODE = stringPreferencesKey("pref_spacebar_swipe_mode")
        val ONE_HANDED_MODE = stringPreferencesKey("pref_one_handed_mode")
        val LONG_PRESS_DELETE_ENABLED = booleanPreferencesKey("pref_long_press_delete_enabled")
        val SELECTIVE_DELETE_ENABLED = booleanPreferencesKey("pref_selective_delete_enabled")
        val SELECTIVE_DELETE_MODE = stringPreferencesKey("pref_selective_delete_mode")
        val FAST_DELETE_ENABLED = booleanPreferencesKey("pref_fast_delete_enabled")
        val VOLUME_KEY_GESTURES_ENABLED = booleanPreferencesKey("pref_volume_key_gestures_enabled")
        val VOLUME_KEY_GESTURE_ACTION = stringPreferencesKey("pref_volume_key_gesture_action")

        val PRIMARY_LAYOUT_ID = stringPreferencesKey("pref_primary_layout_id")
        val ENABLED_LAYOUT_IDS = stringSetPreferencesKey("pref_enabled_layout_ids")
        val ENABLED_LAYOUT_ORDER = stringPreferencesKey("pref_enabled_layout_order")

        val SUGGESTIONS_ENABLED = booleanPreferencesKey("pref_suggestions_enabled")
        val NEXT_WORD_PREDICTION = booleanPreferencesKey("pref_next_word_prediction")
        val PHONETIC_AUTO_CORRECT = booleanPreferencesKey("pref_phonetic_auto_correct")
        val BENGALI_SMART_PHONETIC = booleanPreferencesKey("pref_bengali_smart_phonetic")
        val BENGALI_SHOW_RAW_INPUT_CANDIDATE = booleanPreferencesKey("pref_bengali_show_raw_input_candidate")
        val BORNO_REMEMBER_SUGGESTION_CHOICE = booleanPreferencesKey("pref_borno_remember_suggestion_choice")
        val LEARNED_WORDS_ENABLED = booleanPreferencesKey("pref_learned_words_enabled")
        val GESTURE_TYPING_ENABLED = booleanPreferencesKey("pref_gesture_typing_enabled")
        val SHOW_GESTURE_TRAIL = booleanPreferencesKey("pref_show_gesture_trail")
        val AUTO_CAPITALIZATION = booleanPreferencesKey("pref_auto_capitalization")
        val DOUBLE_SPACE_PERIOD = booleanPreferencesKey("pref_double_space_period")
        val AUTO_SPACE_AFTER_SUGGESTION = booleanPreferencesKey("pref_auto_space_after_suggestion")
        val AUTO_SPACE_AFTER_SUGGESTION_DISABLED_LANGUAGES = stringSetPreferencesKey("pref_auto_space_after_suggestion_disabled_languages")
        val AUTO_SPACE_AFTER_PUNCTUATION = booleanPreferencesKey("pref_auto_space_after_punctuation")
        val REMOVE_SPACE_BEFORE_PUNCTUATION = booleanPreferencesKey("pref_remove_space_before_punctuation")
        val TRADITIONAL_MODE = booleanPreferencesKey("pref_traditional_mode")
        val SUGGESTION_BAR_MODE = stringPreferencesKey("pref_suggestion_bar_mode")
        val EMOJI_SUGGESTIONS = booleanPreferencesKey("pref_emoji_suggestions")

        val PINNED_TOOLS = stringPreferencesKey("pref_pinned_tools")
        val DRAWER_TOOLS = stringPreferencesKey("pref_drawer_tools")
        val VOICE_TYPING_ENABLED = booleanPreferencesKey("pref_voice_typing_enabled")
        val VOICE_TYPING_PROVIDER = stringPreferencesKey("pref_voice_typing_provider")
        val AUTO_DELETE_RECENT_CLIPS = booleanPreferencesKey("pref_auto_delete_recent_clips")
        val AUTO_DELETE_RECENT_CLIPS_DURATION_HOURS = intPreferencesKey("pref_auto_delete_recent_clips_duration_hours")
        val SETUP_COMPLETED = booleanPreferencesKey("pref_setup_completed")

        val IS_FLOATING_MODE = booleanPreferencesKey("pref_is_floating_mode")
        val FLOATING_WIDTH_PORTRAIT = floatPreferencesKey("pref_floating_width_portrait")
        val FLOATING_WIDTH_LANDSCAPE = floatPreferencesKey("pref_floating_width_landscape")
        val FLOATING_HEIGHT_SCALE_PORTRAIT = floatPreferencesKey("pref_floating_height_scale_portrait")
        val FLOATING_HEIGHT_SCALE_LANDSCAPE = floatPreferencesKey("pref_floating_height_scale_landscape")
        val FLOATING_OFFSET_X_PORTRAIT = floatPreferencesKey("pref_floating_offset_x_portrait")
        val FLOATING_OFFSET_Y_PORTRAIT = floatPreferencesKey("pref_floating_offset_y_portrait")
        val FLOATING_OFFSET_X_LANDSCAPE = floatPreferencesKey("pref_floating_offset_x_landscape")
        val FLOATING_OFFSET_Y_LANDSCAPE = floatPreferencesKey("pref_floating_offset_y_landscape")
    }

    val preferencesFlow: Flow<KiebordPreferences> = context.kiebordDataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { prefs ->
            KiebordPreferences(
                theme = prefs[PreferencesKeys.THEME] ?: KiebordPreferences.THEME_DEFAULT_DARK,
                keyboardHeightScale = prefs[PreferencesKeys.KEYBOARD_HEIGHT_SCALE] ?: 1.0f,
                keyboardHeightScaleLandscape = prefs[PreferencesKeys.KEYBOARD_HEIGHT_SCALE_LANDSCAPE] ?: 1.0f,
                chinPaddingPortraitDp = prefs[PreferencesKeys.CHIN_PADDING_PORTRAIT] ?: 22.0f,
                chinPaddingLandscapeDp = prefs[PreferencesKeys.CHIN_PADDING_LANDSCAPE] ?: 8.0f,
                horizontalMarginPortraitDp = prefs[PreferencesKeys.HORIZONTAL_MARGIN_PORTRAIT] ?: 0.0f,
                horizontalMarginLandscapeDp = prefs[PreferencesKeys.HORIZONTAL_MARGIN_LANDSCAPE] ?: 0.0f,
                showNumberRow = prefs[PreferencesKeys.SHOW_NUMBER_ROW] ?: false,
                showKeyBorders = prefs[PreferencesKeys.SHOW_KEY_BORDERS] ?: true,
                showKeyShadows = prefs[PreferencesKeys.SHOW_KEY_SHADOWS] ?: true,
                showKeyPopups = prefs[PreferencesKeys.SHOW_KEY_POPUPS] ?: true,
                showSecondaryKeys = prefs[PreferencesKeys.SHOW_SECONDARY_KEYS] ?: true,
                showGlobeKey = prefs[PreferencesKeys.SHOW_GLOBE_KEY] ?: true,
                showEmojiKey = prefs[PreferencesKeys.SHOW_EMOJI_KEY] ?: false,
                showEmojiRow = prefs[PreferencesKeys.SHOW_EMOJI_ROW] ?: false,
                recentEmojis = prefs[PreferencesKeys.RECENT_EMOJIS]?.split(",")?.filter { it.isNotEmpty() } ?: emptyList(),

                vibrateOnKeypress = prefs[PreferencesKeys.VIBRATE_ON_KEYPRESS] ?: true,
                vibrationType = prefs[PreferencesKeys.VIBRATION_TYPE] ?: KiebordPreferences.VIBRATION_TYPE_HAPTIC,
                vibrationStrength = prefs[PreferencesKeys.VIBRATION_STRENGTH] ?: 50,
                soundOnKeypress = prefs[PreferencesKeys.SOUND_ON_KEYPRESS] ?: false,
                soundVolume = prefs[PreferencesKeys.SOUND_VOLUME] ?: 100,
                longPressDelayMs = prefs[PreferencesKeys.LONG_PRESS_DELAY_MS] ?: 300,
                longPressPrimaryShortcutsEnabled = prefs[PreferencesKeys.LONG_PRESS_PRIMARY_SHORTCUTS_ENABLED] ?: true,
                cursorTrackpadEnabled = prefs[PreferencesKeys.CURSOR_TRACKPAD_ENABLED] ?: true,
                spacebarSwipeMode = prefs[PreferencesKeys.SPACEBAR_SWIPE_MODE] ?: KiebordPreferences.SPACEBAR_MODE_SWIPE_LAYOUT_HOLD_CURSOR,
                oneHandedMode = prefs[PreferencesKeys.ONE_HANDED_MODE] ?: KiebordPreferences.ONE_HANDED_OFF,
                isFloatingMode = prefs[PreferencesKeys.IS_FLOATING_MODE] ?: false,
                floatingWidthPortraitDp = prefs[PreferencesKeys.FLOATING_WIDTH_PORTRAIT] ?: 320.0f,
                floatingWidthLandscapeDp = prefs[PreferencesKeys.FLOATING_WIDTH_LANDSCAPE] ?: 420.0f,
                floatingHeightScalePortrait = prefs[PreferencesKeys.FLOATING_HEIGHT_SCALE_PORTRAIT] ?: 1.0f,
                floatingHeightScaleLandscape = prefs[PreferencesKeys.FLOATING_HEIGHT_SCALE_LANDSCAPE] ?: 0.95f,
                floatingOffsetRatioXPortrait = prefs[PreferencesKeys.FLOATING_OFFSET_X_PORTRAIT] ?: 0.5f,
                floatingOffsetRatioYPortrait = prefs[PreferencesKeys.FLOATING_OFFSET_Y_PORTRAIT] ?: 0.7f,
                floatingOffsetRatioXLandscape = prefs[PreferencesKeys.FLOATING_OFFSET_X_LANDSCAPE] ?: 0.5f,
                floatingOffsetRatioYLandscape = prefs[PreferencesKeys.FLOATING_OFFSET_Y_LANDSCAPE] ?: 0.7f,
                longPressDeleteEnabled = prefs[PreferencesKeys.LONG_PRESS_DELETE_ENABLED] ?: true,
                selectiveDeleteEnabled = prefs[PreferencesKeys.SELECTIVE_DELETE_ENABLED] ?: true,
                selectiveDeleteMode = prefs[PreferencesKeys.SELECTIVE_DELETE_MODE] ?: KiebordPreferences.DELETE_MODE_WORD,
                fastDeleteEnabled = prefs[PreferencesKeys.FAST_DELETE_ENABLED] ?: false,
                volumeKeyGesturesEnabled = prefs[PreferencesKeys.VOLUME_KEY_GESTURES_ENABLED] ?: false,
                volumeKeyGestureAction = prefs[PreferencesKeys.VOLUME_KEY_GESTURE_ACTION] ?: KiebordPreferences.VOLUME_ACTION_MOVE_CURSOR,

                primaryLayoutId = prefs[PreferencesKeys.PRIMARY_LAYOUT_ID] ?: KiebordPreferences.LAYOUT_EN_QWERTY,
                enabledLayoutIds = run {
                    val rawSet = prefs[PreferencesKeys.ENABLED_LAYOUT_IDS] ?: linkedSetOf(
                        KiebordPreferences.LAYOUT_EN_QWERTY,
                        KiebordPreferences.LAYOUT_BN_BORNO,
                        KiebordPreferences.LAYOUT_BN_NATIONAL
                    )
                    val orderRaw = prefs[PreferencesKeys.ENABLED_LAYOUT_ORDER]
                    if (!orderRaw.isNullOrBlank()) {
                        val ordered = orderRaw.split(",").map { it.trim() }.filter { it.isNotEmpty() && it in rawSet }
                        val remaining = rawSet.filter { it !in ordered }
                        LinkedHashSet(ordered + remaining)
                    } else {
                        rawSet
                    }
                },

                suggestionsEnabled = prefs[PreferencesKeys.SUGGESTIONS_ENABLED] ?: true,
                nextWordPrediction = prefs[PreferencesKeys.NEXT_WORD_PREDICTION] ?: true,
                phoneticAutoCorrect = prefs[PreferencesKeys.PHONETIC_AUTO_CORRECT] ?: true,
                bengaliSmartPhonetic = prefs[PreferencesKeys.BENGALI_SMART_PHONETIC] ?: true,
                bengaliShowRawInputCandidate = prefs[PreferencesKeys.BENGALI_SHOW_RAW_INPUT_CANDIDATE] ?: true,
                bornoRememberSuggestionChoice = prefs[PreferencesKeys.BORNO_REMEMBER_SUGGESTION_CHOICE] ?: false,
                learnedWordsEnabled = prefs[PreferencesKeys.LEARNED_WORDS_ENABLED] ?: true,
                gestureTypingEnabled = prefs[PreferencesKeys.GESTURE_TYPING_ENABLED] ?: true,
                showGestureTrail = prefs[PreferencesKeys.SHOW_GESTURE_TRAIL] ?: true,
                autoCapitalization = prefs[PreferencesKeys.AUTO_CAPITALIZATION] ?: true,
                doubleSpacePeriod = prefs[PreferencesKeys.DOUBLE_SPACE_PERIOD] ?: true,
                autoSpaceAfterSuggestion = prefs[PreferencesKeys.AUTO_SPACE_AFTER_SUGGESTION] ?: true,
                autoSpaceAfterSuggestionDisabledLanguages = prefs[PreferencesKeys.AUTO_SPACE_AFTER_SUGGESTION_DISABLED_LANGUAGES] ?: emptySet(),
                autoSpaceAfterPunctuation = prefs[PreferencesKeys.AUTO_SPACE_AFTER_PUNCTUATION] ?: true,
                removeSpaceBeforePunctuation = prefs[PreferencesKeys.REMOVE_SPACE_BEFORE_PUNCTUATION] ?: true,
                traditionalMode = prefs[PreferencesKeys.TRADITIONAL_MODE] ?: false,
                suggestionBarMode = prefs[PreferencesKeys.SUGGESTION_BAR_MODE] ?: KiebordPreferences.SUGGESTION_BAR_MODE_SCROLLABLE,
                emojiSuggestions = prefs[PreferencesKeys.EMOJI_SUGGESTIONS] ?: true,

                pinnedTools = run {
                    val defaultPinned = listOf("EMOJI", "TEXT_EDIT", "NUMBER_PAD", "CLIPBOARD", "SETTINGS")
                    val validActionNames = listOf(
                        "EMOJI", "TEXT_EDIT", "NUMBER_PAD", "CLIPBOARD", "SETTINGS", "THEME_TOGGLE", "RESIZER", "FLOATING",
                        "NUMBER_ROW_TOGGLE", "SECONDARY_KEYS_TOGGLE", "KEY_POPUP_TOGGLE", "ONE_HANDED", "TRADITIONAL_MODE",
                        "UNDO", "REDO", "EMOJI_ROW_TOGGLE", "SHARE_BORNO"
                    )
                    prefs[PreferencesKeys.PINNED_TOOLS]?.let { raw ->
                        raw.split(",").filter { it in validActionNames }.distinct()
                    } ?: defaultPinned
                },
                drawerTools = run {
                    val defaultPinned = listOf("EMOJI", "TEXT_EDIT", "NUMBER_PAD", "CLIPBOARD", "SETTINGS")
                    val defaultDrawer = listOf("RESIZER", "FLOATING", "NUMBER_ROW_TOGGLE", "SECONDARY_KEYS_TOGGLE", "KEY_POPUP_TOGGLE", "ONE_HANDED", "THEME_TOGGLE", "TRADITIONAL_MODE", "UNDO", "REDO", "EMOJI_ROW_TOGGLE")
                    val coreActionNames = listOf(
                        "EMOJI", "TEXT_EDIT", "NUMBER_PAD", "CLIPBOARD", "SETTINGS", "THEME_TOGGLE", "RESIZER", "FLOATING",
                        "NUMBER_ROW_TOGGLE", "SECONDARY_KEYS_TOGGLE", "KEY_POPUP_TOGGLE", "ONE_HANDED", "TRADITIONAL_MODE",
                        "UNDO", "REDO", "EMOJI_ROW_TOGGLE"
                    )
                    val validActionNames = coreActionNames + "SHARE_BORNO"
                    val pinnedList = prefs[PreferencesKeys.PINNED_TOOLS]?.let { raw ->
                        raw.split(",").filter { it in validActionNames }.distinct()
                    } ?: defaultPinned
                    val pinnedSet = pinnedList.toSet()
                    val rawDrawer = prefs[PreferencesKeys.DRAWER_TOOLS]?.split(",")?.filter { it in validActionNames } ?: defaultDrawer
                    (rawDrawer.filter { it !in pinnedSet } +
                        coreActionNames.filter { it !in pinnedSet }).distinct()
                },
                voiceTypingEnabled = prefs[PreferencesKeys.VOICE_TYPING_ENABLED] ?: true,
                voiceTypingProvider = prefs[PreferencesKeys.VOICE_TYPING_PROVIDER] ?: KiebordPreferences.VOICE_PROVIDER_GOOGLE,
                autoDeleteRecentClips = prefs[PreferencesKeys.AUTO_DELETE_RECENT_CLIPS] ?: fastCache.getBoolean("auto_delete_recent_clips", false),
                autoDeleteRecentClipsDurationHours = prefs[PreferencesKeys.AUTO_DELETE_RECENT_CLIPS_DURATION_HOURS] ?: fastCache.getInt("auto_delete_recent_clips_duration_hours", 24),
                extraRowOverrides = run {
                    val overrides = mutableMapOf<String, Boolean>()
                    for ((key, value) in prefs.asMap()) {
                        if (key.name.startsWith("pref_extra_row_") && value is Boolean) {
                            overrides[key.name.removePrefix("pref_extra_row_")] = value
                        }
                    }
                    overrides
                },
                setupCompleted = (prefs[PreferencesKeys.SETUP_COMPLETED] ?: false).also { isCompleted ->
                    if (isCompleted && !fastCache.getBoolean("setup_completed", false)) {
                        fastCache.edit().putBoolean("setup_completed", true).apply()
                    }
                } || fastCache.getBoolean("setup_completed", false)
            )
        }

    // Appearance & Theming mutators
    suspend fun updateTheme(theme: String) {
        context.kiebordDataStore.edit { it[PreferencesKeys.THEME] = theme }
    }

    suspend fun updateKeyboardHeightScale(scale: Float) {
        context.kiebordDataStore.edit { it[PreferencesKeys.KEYBOARD_HEIGHT_SCALE] = scale }
    }

    suspend fun updateKeyboardHeightScaleLandscape(scale: Float) {
        context.kiebordDataStore.edit { it[PreferencesKeys.KEYBOARD_HEIGHT_SCALE_LANDSCAPE] = scale }
    }

    suspend fun updateKeyboardResizerValues(
        isLandscape: Boolean,
        heightScale: Float,
        chinPaddingDp: Float,
        horizontalMarginDp: Float
    ) {
        context.kiebordDataStore.edit { prefs ->
            if (isLandscape) {
                prefs[PreferencesKeys.KEYBOARD_HEIGHT_SCALE_LANDSCAPE] = heightScale
                prefs[PreferencesKeys.CHIN_PADDING_LANDSCAPE] = chinPaddingDp
                prefs[PreferencesKeys.HORIZONTAL_MARGIN_LANDSCAPE] = horizontalMarginDp
            } else {
                prefs[PreferencesKeys.KEYBOARD_HEIGHT_SCALE] = heightScale
                prefs[PreferencesKeys.CHIN_PADDING_PORTRAIT] = chinPaddingDp
                prefs[PreferencesKeys.HORIZONTAL_MARGIN_PORTRAIT] = horizontalMarginDp
            }
        }
    }

    suspend fun updateShowNumberRow(enabled: Boolean) {
        context.kiebordDataStore.edit { it[PreferencesKeys.SHOW_NUMBER_ROW] = enabled }
    }

    suspend fun updateShowKeyBorders(enabled: Boolean) {
        context.kiebordDataStore.edit { it[PreferencesKeys.SHOW_KEY_BORDERS] = enabled }
    }

    suspend fun updateShowKeyShadows(enabled: Boolean) {
        context.kiebordDataStore.edit { it[PreferencesKeys.SHOW_KEY_SHADOWS] = enabled }
    }

    suspend fun updateShowKeyPopups(enabled: Boolean) {
        context.kiebordDataStore.edit { it[PreferencesKeys.SHOW_KEY_POPUPS] = enabled }
    }

    suspend fun updateShowSecondaryKeys(enabled: Boolean) {
        context.kiebordDataStore.edit { it[PreferencesKeys.SHOW_SECONDARY_KEYS] = enabled }
    }

    suspend fun updateShowGlobeKey(enabled: Boolean) {
        context.kiebordDataStore.edit { it[PreferencesKeys.SHOW_GLOBE_KEY] = enabled }
    }

    suspend fun updateShowEmojiKey(enabled: Boolean) {
        context.kiebordDataStore.edit { it[PreferencesKeys.SHOW_EMOJI_KEY] = enabled }
    }

    suspend fun updateShowEmojiRow(enabled: Boolean) {
        context.kiebordDataStore.edit { it[PreferencesKeys.SHOW_EMOJI_ROW] = enabled }
    }

    suspend fun recordRecentEmoji(emoji: String) {
        if (emoji.isBlank()) return
        context.kiebordDataStore.edit { prefs ->
            val current = prefs[PreferencesKeys.RECENT_EMOJIS]?.split(",")?.filter { it.isNotEmpty() } ?: emptyList()
            val updated = (listOf(emoji) + current.filter { it != emoji }).take(30)
            prefs[PreferencesKeys.RECENT_EMOJIS] = updated.joinToString(",")
        }
    }

    // Preferences & Haptics mutators
    suspend fun updateVibrateOnKeypress(enabled: Boolean) {
        context.kiebordDataStore.edit { it[PreferencesKeys.VIBRATE_ON_KEYPRESS] = enabled }
    }

    suspend fun updateVibrationType(type: String) {
        context.kiebordDataStore.edit { it[PreferencesKeys.VIBRATION_TYPE] = type }
    }

    suspend fun updateVibrationStrength(strength: Int) {
        context.kiebordDataStore.edit { it[PreferencesKeys.VIBRATION_STRENGTH] = strength.coerceIn(0, 100) }
    }

    suspend fun updateSoundOnKeypress(enabled: Boolean) {
        context.kiebordDataStore.edit { it[PreferencesKeys.SOUND_ON_KEYPRESS] = enabled }
    }

    suspend fun updateSoundVolume(volume: Int) {
        context.kiebordDataStore.edit { it[PreferencesKeys.SOUND_VOLUME] = volume.coerceIn(1, 100) }
    }

    suspend fun updateLongPressDelayMs(delayMs: Int) {
        context.kiebordDataStore.edit { it[PreferencesKeys.LONG_PRESS_DELAY_MS] = delayMs.coerceIn(150, 600) }
    }

    suspend fun updateLongPressPrimaryShortcutsEnabled(enabled: Boolean) {
        context.kiebordDataStore.edit { it[PreferencesKeys.LONG_PRESS_PRIMARY_SHORTCUTS_ENABLED] = enabled }
    }

    suspend fun updateCursorTrackpadEnabled(enabled: Boolean) {
        context.kiebordDataStore.edit { it[PreferencesKeys.CURSOR_TRACKPAD_ENABLED] = enabled }
    }

    suspend fun updateOneHandedMode(mode: String) {
        context.kiebordDataStore.edit { it[PreferencesKeys.ONE_HANDED_MODE] = mode }
    }

    suspend fun updateFloatingMode(enabled: Boolean) {
        context.kiebordDataStore.edit { it[PreferencesKeys.IS_FLOATING_MODE] = enabled }
    }

    suspend fun updateFloatingSize(isLandscape: Boolean, widthDp: Float, heightScale: Float) {
        context.kiebordDataStore.edit { prefs ->
            if (isLandscape) {
                prefs[PreferencesKeys.FLOATING_WIDTH_LANDSCAPE] = widthDp
                prefs[PreferencesKeys.FLOATING_HEIGHT_SCALE_LANDSCAPE] = heightScale
            } else {
                prefs[PreferencesKeys.FLOATING_WIDTH_PORTRAIT] = widthDp
                prefs[PreferencesKeys.FLOATING_HEIGHT_SCALE_PORTRAIT] = heightScale
            }
        }
    }

    suspend fun updateFloatingPosition(isLandscape: Boolean, ratioX: Float, ratioY: Float) {
        context.kiebordDataStore.edit { prefs ->
            if (isLandscape) {
                prefs[PreferencesKeys.FLOATING_OFFSET_X_LANDSCAPE] = ratioX
                prefs[PreferencesKeys.FLOATING_OFFSET_Y_LANDSCAPE] = ratioY
            } else {
                prefs[PreferencesKeys.FLOATING_OFFSET_X_PORTRAIT] = ratioX
                prefs[PreferencesKeys.FLOATING_OFFSET_Y_PORTRAIT] = ratioY
            }
        }
    }

    suspend fun updateLongPressDeleteEnabled(enabled: Boolean) {
        context.kiebordDataStore.edit { it[PreferencesKeys.LONG_PRESS_DELETE_ENABLED] = enabled }
    }

    suspend fun updateSelectiveDeleteEnabled(enabled: Boolean) {
        context.kiebordDataStore.edit { it[PreferencesKeys.SELECTIVE_DELETE_ENABLED] = enabled }
    }

    suspend fun updateSelectiveDeleteMode(mode: String) {
        context.kiebordDataStore.edit { it[PreferencesKeys.SELECTIVE_DELETE_MODE] = mode }
    }

    suspend fun updateFastDeleteEnabled(enabled: Boolean) {
        context.kiebordDataStore.edit { it[PreferencesKeys.FAST_DELETE_ENABLED] = enabled }
    }

    suspend fun updateSpacebarSwipeMode(mode: String) {
        context.kiebordDataStore.edit { it[PreferencesKeys.SPACEBAR_SWIPE_MODE] = mode }
    }

    suspend fun updateVolumeKeyGesturesEnabled(enabled: Boolean) {
        context.kiebordDataStore.edit { it[PreferencesKeys.VOLUME_KEY_GESTURES_ENABLED] = enabled }
    }

    suspend fun updateVolumeKeyGestureAction(action: String) {
        context.kiebordDataStore.edit { it[PreferencesKeys.VOLUME_KEY_GESTURE_ACTION] = action }
    }

    // Layout mutators
    suspend fun updatePrimaryLayoutId(layoutId: String) {
        context.kiebordDataStore.edit { prefs ->
            prefs[PreferencesKeys.PRIMARY_LAYOUT_ID] = layoutId
            val currentEnabled = prefs[PreferencesKeys.ENABLED_LAYOUT_IDS] ?: linkedSetOf(
                KiebordPreferences.LAYOUT_EN_QWERTY,
                KiebordPreferences.LAYOUT_BN_BORNO,
                KiebordPreferences.LAYOUT_BN_NATIONAL
            )
            if (!currentEnabled.contains(layoutId)) {
                prefs[PreferencesKeys.ENABLED_LAYOUT_IDS] = currentEnabled + layoutId
            }
        }
    }

    suspend fun toggleLayoutEnabled(layoutId: String, enabled: Boolean) {
        context.kiebordDataStore.edit { prefs ->
            val currentEnabled = (prefs[PreferencesKeys.ENABLED_LAYOUT_IDS] ?: linkedSetOf(
                KiebordPreferences.LAYOUT_EN_QWERTY,
                KiebordPreferences.LAYOUT_BN_BORNO,
                KiebordPreferences.LAYOUT_BN_NATIONAL
            )).toMutableSet()

            if (enabled) {
                currentEnabled.add(layoutId)
            } else {
                // Ensure at least one layout is enabled
                if (currentEnabled.size > 1) {
                    currentEnabled.remove(layoutId)
                    // If primary was removed, point primary to the first available layout
                    val primary = prefs[PreferencesKeys.PRIMARY_LAYOUT_ID]
                    if (primary == layoutId) {
                        prefs[PreferencesKeys.PRIMARY_LAYOUT_ID] = currentEnabled.first()
                    }
                }
            }
            prefs[PreferencesKeys.ENABLED_LAYOUT_IDS] = currentEnabled
            val existingOrder = prefs[PreferencesKeys.ENABLED_LAYOUT_ORDER]
                ?.split(",")
                ?.map { it.trim() }
                ?.filter { it.isNotEmpty() && it in currentEnabled }
                ?.toMutableList()
                ?: mutableListOf()
            for (id in currentEnabled) {
                if (id !in existingOrder) existingOrder.add(id)
            }
            prefs[PreferencesKeys.ENABLED_LAYOUT_ORDER] = existingOrder.joinToString(",")
        }
    }

    suspend fun updateEnabledLayoutsOrder(orderedIds: List<String>) {
        if (orderedIds.isEmpty()) return
        context.kiebordDataStore.edit { prefs ->
            val distinctIds = orderedIds.distinct()
            prefs[PreferencesKeys.ENABLED_LAYOUT_IDS] = distinctIds.toSet()
            prefs[PreferencesKeys.ENABLED_LAYOUT_ORDER] = distinctIds.joinToString(",")
            prefs[PreferencesKeys.PRIMARY_LAYOUT_ID] = distinctIds.first()
        }
    }

    // Suggestions & Corrections mutators
    suspend fun updateSuggestionsEnabled(enabled: Boolean) {
        context.kiebordDataStore.edit { it[PreferencesKeys.SUGGESTIONS_ENABLED] = enabled }
    }

    suspend fun updateNextWordPrediction(enabled: Boolean) {
        context.kiebordDataStore.edit { it[PreferencesKeys.NEXT_WORD_PREDICTION] = enabled }
    }

    suspend fun updatePhoneticAutoCorrect(enabled: Boolean) {
        context.kiebordDataStore.edit { it[PreferencesKeys.PHONETIC_AUTO_CORRECT] = enabled }
    }

    suspend fun updateBengaliSmartPhonetic(enabled: Boolean) {
        context.kiebordDataStore.edit { it[PreferencesKeys.BENGALI_SMART_PHONETIC] = enabled }
    }

    suspend fun updateBengaliShowRawInputCandidate(enabled: Boolean) {
        context.kiebordDataStore.edit { it[PreferencesKeys.BENGALI_SHOW_RAW_INPUT_CANDIDATE] = enabled }
    }

    suspend fun updateBornoRememberSuggestionChoice(enabled: Boolean) {
        context.kiebordDataStore.edit { it[PreferencesKeys.BORNO_REMEMBER_SUGGESTION_CHOICE] = enabled }
    }

    suspend fun updateLearnedWordsEnabled(enabled: Boolean) {
        context.kiebordDataStore.edit { it[PreferencesKeys.LEARNED_WORDS_ENABLED] = enabled }
    }

    suspend fun updateGestureTypingEnabled(enabled: Boolean) {
        context.kiebordDataStore.edit { it[PreferencesKeys.GESTURE_TYPING_ENABLED] = enabled }
    }

    suspend fun updateShowGestureTrail(enabled: Boolean) {
        context.kiebordDataStore.edit { it[PreferencesKeys.SHOW_GESTURE_TRAIL] = enabled }
    }

    suspend fun updateAutoCapitalization(enabled: Boolean) {
        context.kiebordDataStore.edit { it[PreferencesKeys.AUTO_CAPITALIZATION] = enabled }
    }

    suspend fun updateDoubleSpacePeriod(enabled: Boolean) {
        context.kiebordDataStore.edit { it[PreferencesKeys.DOUBLE_SPACE_PERIOD] = enabled }
    }

    suspend fun updateAutoSpaceAfterSuggestion(enabled: Boolean) {
        context.kiebordDataStore.edit { it[PreferencesKeys.AUTO_SPACE_AFTER_SUGGESTION] = enabled }
    }

    suspend fun setAutoSpaceAfterSuggestionLanguageEnabled(languageCode: String, enabled: Boolean) {
        val lang = when {
            languageCode.startsWith("bn") -> "bn"
            languageCode.startsWith("en") -> "en"
            else -> languageCode.lowercase().trim()
        }
        context.kiebordDataStore.edit { prefs ->
            val current = prefs[PreferencesKeys.AUTO_SPACE_AFTER_SUGGESTION_DISABLED_LANGUAGES]?.toMutableSet() ?: mutableSetOf()
            if (enabled) {
                current.remove(lang)
            } else {
                current.add(lang)
            }
            prefs[PreferencesKeys.AUTO_SPACE_AFTER_SUGGESTION_DISABLED_LANGUAGES] = current
        }
    }

    suspend fun updateAutoSpaceAfterPunctuation(enabled: Boolean) {
        context.kiebordDataStore.edit { it[PreferencesKeys.AUTO_SPACE_AFTER_PUNCTUATION] = enabled }
    }

    suspend fun updateRemoveSpaceBeforePunctuation(enabled: Boolean) {
        context.kiebordDataStore.edit { it[PreferencesKeys.REMOVE_SPACE_BEFORE_PUNCTUATION] = enabled }
    }

    suspend fun updateTraditionalMode(enabled: Boolean) {
        context.kiebordDataStore.edit { it[PreferencesKeys.TRADITIONAL_MODE] = enabled }
    }

    suspend fun updateSuggestionBarMode(mode: String) {
        context.kiebordDataStore.edit { it[PreferencesKeys.SUGGESTION_BAR_MODE] = mode }
    }

    suspend fun updateEmojiSuggestions(enabled: Boolean) {
        context.kiebordDataStore.edit { it[PreferencesKeys.EMOJI_SUGGESTIONS] = enabled }
    }

    suspend fun updateToolbarTools(pinned: List<String>, drawer: List<String>) {
        val coreActionNames = listOf(
            "EMOJI", "TEXT_EDIT", "NUMBER_PAD", "CLIPBOARD", "SETTINGS", "THEME_TOGGLE", "RESIZER", "FLOATING",
            "NUMBER_ROW_TOGGLE", "SECONDARY_KEYS_TOGGLE", "KEY_POPUP_TOGGLE", "ONE_HANDED", "TRADITIONAL_MODE",
            "UNDO", "REDO", "EMOJI_ROW_TOGGLE"
        )
        val validActionNames = coreActionNames + "SHARE_BORNO"
        val sanitizedPinned = pinned.filter { it in validActionNames }.distinct()
        val pinnedSet = sanitizedPinned.toSet()
        val sanitizedDrawer = (drawer.filter { it in validActionNames && it !in pinnedSet } +
            coreActionNames.filter { it !in pinnedSet }).distinct()

        context.kiebordDataStore.edit {
            it[PreferencesKeys.PINNED_TOOLS] = sanitizedPinned.joinToString(",")
            it[PreferencesKeys.DRAWER_TOOLS] = sanitizedDrawer.joinToString(",")
        }
    }

    suspend fun updateVoiceTypingEnabled(enabled: Boolean) {
        context.kiebordDataStore.edit { it[PreferencesKeys.VOICE_TYPING_ENABLED] = enabled }
    }

    suspend fun updateVoiceTypingProvider(provider: String) {
        context.kiebordDataStore.edit { it[PreferencesKeys.VOICE_TYPING_PROVIDER] = provider }
    }

    suspend fun updateAutoDeleteRecentClips(enabled: Boolean) {
        fastCache.edit().putBoolean("auto_delete_recent_clips", enabled).apply()
        context.kiebordDataStore.edit { it[PreferencesKeys.AUTO_DELETE_RECENT_CLIPS] = enabled }
    }

    suspend fun updateAutoDeleteRecentClipsDurationHours(hours: Int) {
        val clamped = hours.coerceIn(1, 48)
        fastCache.edit().putInt("auto_delete_recent_clips_duration_hours", clamped).apply()
        context.kiebordDataStore.edit { it[PreferencesKeys.AUTO_DELETE_RECENT_CLIPS_DURATION_HOURS] = clamped }
    }

    suspend fun updateOnboardingLayouts(enabledLayouts: Set<String>, primaryLayoutId: String) {
        if (enabledLayouts.isEmpty()) return
        context.kiebordDataStore.edit { prefs ->
            val sanitizedEnabled = enabledLayouts.toMutableSet()
            val resolvedPrimary = if (primaryLayoutId in sanitizedEnabled) {
                primaryLayoutId
            } else {
                sanitizedEnabled.first()
            }
            prefs[PreferencesKeys.ENABLED_LAYOUT_IDS] = sanitizedEnabled
            prefs[PreferencesKeys.PRIMARY_LAYOUT_ID] = resolvedPrimary

            val existingOrder = prefs[PreferencesKeys.ENABLED_LAYOUT_ORDER]
                ?.split(",")
                ?.map { it.trim() }
                ?.filter { it.isNotEmpty() && it in sanitizedEnabled }
                ?.toMutableList()
                ?: mutableListOf()
            for (id in sanitizedEnabled) {
                if (id !in existingOrder) existingOrder.add(id)
            }
            prefs[PreferencesKeys.ENABLED_LAYOUT_ORDER] = existingOrder.joinToString(",")
        }
    }

    suspend fun completeOnboarding(
        showNumberRow: Boolean,
        showSecondaryKeys: Boolean,
        showKeyBorders: Boolean
    ) {
        fastCache.edit().putBoolean("setup_completed", true).apply()
        context.kiebordDataStore.edit { prefs ->
            prefs[PreferencesKeys.SHOW_NUMBER_ROW] = showNumberRow
            prefs[PreferencesKeys.SHOW_SECONDARY_KEYS] = showSecondaryKeys
            prefs[PreferencesKeys.SHOW_KEY_BORDERS] = showKeyBorders
            prefs[PreferencesKeys.SETUP_COMPLETED] = true
        }
    }

    suspend fun updateSetupCompleted(completed: Boolean) {
        fastCache.edit().putBoolean("setup_completed", completed).apply()
        context.kiebordDataStore.edit { it[PreferencesKeys.SETUP_COMPLETED] = completed }
    }

    suspend fun setLayoutExtraRowEnabled(layoutId: String, rowId: String, enabled: Boolean) {
        val key = booleanPreferencesKey("pref_extra_row_${layoutId}_${rowId}")
        context.kiebordDataStore.edit { it[key] = enabled }
    }

    // Reset
    suspend fun resetToDefaults() {
        fastCache.edit().clear().apply()
        context.kiebordDataStore.edit { it.clear() }
    }

    companion object {
        @Volatile
        private var INSTANCE: KiebordPreferencesRepository? = null

        fun getInstance(context: Context): KiebordPreferencesRepository {
            val safeContext = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                val app = context.applicationContext ?: context
                if (app.isDeviceProtectedStorage) app else app.createDeviceProtectedStorageContext()
            } else {
                context.applicationContext ?: context
            }
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: KiebordPreferencesRepository(safeContext).also { INSTANCE = it }
            }
        }
    }
}
