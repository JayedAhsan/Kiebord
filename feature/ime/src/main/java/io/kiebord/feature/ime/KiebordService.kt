package io.kiebord.feature.ime

import android.content.ClipData
import android.content.ClipboardManager
import android.content.ComponentCallbacks2
import android.content.Context
import android.content.Intent
import android.inputmethodservice.InputMethodService
import android.os.Build
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.HapticFeedbackConstants
import android.view.KeyEvent
import android.view.View
import android.view.Window
import android.text.InputType
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.ExtractedTextRequest
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputMethodManager
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import io.kiebord.common.model.KeyDef
import io.kiebord.common.model.KeyType
import io.kiebord.common.model.KeyboardLayoutDef
import io.kiebord.common.model.ShiftState
import io.kiebord.data.dictionary.UserDictionaryRepository
import io.kiebord.data.layout.LayoutRepository
import io.kiebord.data.layout.PredefinedLayouts
import io.kiebord.feature.ime.clipboard.SandboxedClipboardManager
import io.kiebord.feature.ime.gesture.CrossLanguageGestureDecoder
import io.kiebord.feature.ime.gesture.KeyCentroid
import io.kiebord.feature.ime.lifecycle.KeyboardViewLifecycleOwner
import io.kiebord.feature.ime.translit.BengaliBstiEngine
import io.kiebord.feature.ime.translit.BornoTransliterationManager
import io.kiebord.feature.ime.translit.GenericTransliterationManager
import io.kiebord.feature.ime.translit.TransliterationManager
import io.kiebord.feature.ime.ui.emoji.EmojiFontManager
import androidx.compose.ui.unit.dp
import io.kiebord.data.prefs.KiebordPreferences
import io.kiebord.data.prefs.KiebordPreferencesRepository
import io.kiebord.feature.ime.translit.TransliterationManagerFactory
import io.kiebord.feature.ime.action.ActionKeyResolver
import io.kiebord.feature.ime.action.ActionKeyVisual
import io.kiebord.feature.ime.ui.KeyboardRootView
import io.kiebord.feature.ime.ui.suggestion.SuggestionCandidate
import io.kiebord.feature.ime.ui.textedit.TextAction
import io.kiebord.feature.ime.ui.toolbar.ToolbarAction
import io.kiebord.feature.ime.voice.VoiceInputManager
import io.kiebord.theme.KeyboardColorScheme
import io.kiebord.theme.KiebordTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class KiebordService : InputMethodService() {

    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private lateinit var preferencesRepo: KiebordPreferencesRepository
    private var preferencesState by mutableStateOf(KiebordPreferences())
    private var currentEditorInfoState by mutableStateOf<EditorInfo?>(null)
    private lateinit var voiceInputManager: VoiceInputManager

    private val lifecycleOwner = KeyboardViewLifecycleOwner()
    private var transliterationManager: TransliterationManager =
        TransliterationManagerFactory.create("bn_borno", preferencesState.phoneticAutoCorrect) { currentInputConnection }
    private val composingBuffer = StringBuilder()
    private val gestureDecoder = CrossLanguageGestureDecoder()
    private var lastGestureCommittedWord: String = ""
    private var isGestureAlternativeActive: Boolean = false
    private var lastSpaceTapTime: Long = 0L
    private var lastAutoSpacePunctuationTime: Long = 0L

    private fun isM17nLayout(): Boolean {
        return activeLayout.processor == "m17n" ||
                !activeLayout.rules.isNullOrBlank() ||
                activeLayout.id == "khipro" ||
                activeLayout.id == "bn_khipro" ||
                NativeEngine.isM17nLayoutReady(activeLayout.id)
    }

    private fun isTransliterationLayout(): Boolean {
        if (PredefinedLayouts.isNumberLayout(activeLayout.id)) return false
        if (isM17nLayout()) return true
        return TransliterationManagerFactory.isTransliterationLayout(activeLayout.id)
    }

    private fun isPhoneticLayout(): Boolean {
        return activeLayout.id == KiebordPreferences.LAYOUT_BN_BORNO ||
                activeLayout.id == KiebordPreferences.LAYOUT_BN_AVRO ||
                primaryAlphaLayout.id == KiebordPreferences.LAYOUT_BN_BORNO ||
                primaryAlphaLayout.id == KiebordPreferences.LAYOUT_BN_AVRO
    }

    private fun shouldAutoCapitalize(): Boolean {
        if (!preferencesState.autoCapitalization) return false
        if (isPhoneticLayout()) return false
        if (activeLayout.id.startsWith("bn_") || primaryAlphaLayout.id.startsWith("bn_")) return false
        if (isUrlOrCodeField()) return false
        return true
    }

    private fun ensureLayoutProcessorInitialized(layout: KeyboardLayoutDef) {
        if (layout.processor == "m17n" && !layout.rules.isNullOrBlank()) {
            NativeEngine.ensureM17nInitialized(this@KiebordService, layout.id, layout.rules!!)
        } else if (layout.id == "khipro" || layout.id == "bn_khipro") {
            NativeEngine.ensureM17nInitialized(this@KiebordService, layout.id, "bn-khipro.classic.mim")
        }
    }

    private fun getTransliterationManager(): TransliterationManager {
        if (transliterationManager.layoutId != activeLayout.id) {
            transliterationManager = TransliterationManagerFactory.create(
                activeLayout.id,
                preferencesState.phoneticAutoCorrect
            ) { currentInputConnection }
        } else {
            (transliterationManager as? GenericTransliterationManager)?.isAutoCorrectEnabled = preferencesState.phoneticAutoCorrect
        }
        return transliterationManager
    }

    // Configurable state
    private var primaryAlphaLayout by mutableStateOf(PredefinedLayouts.EnglishQwerty)
    private var activeLayout by mutableStateOf(PredefinedLayouts.EnglishQwerty)
    private var shiftState by mutableStateOf(ShiftState.UNSHIFTED)
    private var lastShiftTapTime: Long = 0L
    private var isSymbolsMode by mutableStateOf(false)
    private var colorScheme by mutableStateOf(KeyboardColorScheme.DefaultDark)
    private var navBarInsetBottomPx by mutableIntStateOf(0)
    private var activeSuggestions by mutableStateOf<List<SuggestionCandidate>>(emptyList())
    private lateinit var clipboardManager: SandboxedClipboardManager
    private val clipboardListener = ClipboardManager.OnPrimaryClipChangedListener {
        clipboardManager.syncFromSystemClipboard(this, forceNotify = true)
    }
    private lateinit var userDictionaryRepo: UserDictionaryRepository
    private lateinit var layoutRepo: LayoutRepository
    private lateinit var khiproSpecs: io.kiebord.feature.ime.khipro.KhiproSpecRepository
    private var selectionAnchor: Int? = null
    private var selectionCursor: Int? = null
    private var inputSessionId by mutableIntStateOf(0)
    private var lastDeletedBatchState by mutableStateOf<String?>(null)
    private var isBornoEasyKarMode by mutableStateOf(false)
    private var bornoEasyTypedCluster by mutableStateOf("")

    data class TrailingClusterInfo(
        val cluster: String,
        val endsWithConsonant: Boolean,
        val endsWithHasanta: Boolean
    )

    private fun isBengaliConsonantChar(c: Char): Boolean {
        return (c in '\u0995'..'\u09B9') || (c in '\u09DC'..'\u09DF') || c == '\u09CE'
    }

    private fun extractTrailingBengaliCluster(text: String): TrailingClusterInfo {
        if (text.isEmpty()) return TrailingClusterInfo("", endsWithConsonant = false, endsWithHasanta = false)

        val lastChar = text.last()
        if (isBengaliConsonantChar(lastChar)) {
            var i = text.length - 1
            while (i >= 2) {
                val prevHasantaIdx = if (i >= 2 && text[i - 1] == '\u09BC') i - 2 else i - 1
                if (prevHasantaIdx >= 1 && text[prevHasantaIdx] == '\u09CD') {
                    val prevConsonantIdx = if (prevHasantaIdx >= 2 && text[prevHasantaIdx - 1] == '\u09BC') prevHasantaIdx - 2 else prevHasantaIdx - 1
                    if (isBengaliConsonantChar(text[prevConsonantIdx])) {
                        i = prevConsonantIdx
                    } else {
                        break
                    }
                } else {
                    break
                }
            }
            val cluster = text.substring(i)
            return TrailingClusterInfo(cluster, endsWithConsonant = true, endsWithHasanta = false)
        }

        if (lastChar == '\u09CD') {
            return TrailingClusterInfo("", endsWithConsonant = false, endsWithHasanta = true)
        }

        return TrailingClusterInfo("", endsWithConsonant = false, endsWithHasanta = false)
    }

    private fun updateBornoEasyStateFromText(text: String) {
        if (!PredefinedLayouts.isBornoEasy(activeLayout.id)) return
        val info = extractTrailingBengaliCluster(text)
        if (info.endsWithConsonant) {
            isBornoEasyKarMode = true
            bornoEasyTypedCluster = info.cluster
        } else {
            isBornoEasyKarMode = false
            bornoEasyTypedCluster = ""
        }
    }

    override fun onCreate() {
        super.onCreate()
        preferencesRepo = KiebordPreferencesRepository.getInstance(this)
        layoutRepo = LayoutRepository.getInstance(this)
        clipboardManager = SandboxedClipboardManager.getInstance(this)
        userDictionaryRepo = UserDictionaryRepository.getInstance(this)
        khiproSpecs = io.kiebord.feature.ime.khipro.KhiproSpecRepository(
            assetReader = ::readKhiproAsset,
            filesDir = filesDir,
            loadPref = { key, def ->
                try {
                    getSharedPreferences("kiebord_runtime", Context.MODE_PRIVATE)
                        .getString(key, def)
                } catch (_: Exception) { def }
            },
            storePref = { key, value ->
                try {
                    getSharedPreferences("kiebord_runtime", Context.MODE_PRIVATE)
                        .edit().putString(key, value).apply()
                } catch (_: Exception) {}
            },
            removePref = { key ->
                try {
                    getSharedPreferences("kiebord_runtime", Context.MODE_PRIVATE)
                        .edit().remove(key).apply()
                } catch (_: Exception) {}
            },
            fetcher = { url -> io.kiebord.feature.ime.khipro.KhiproSpecRepository.defaultFetcher(url) },
        )
        voiceInputManager = VoiceInputManager(this).apply {
            onPartialResult = { text ->
                currentInputConnection?.setComposingText(text, 1)
            }
            onFinalResult = { text ->
                currentInputConnection?.let { ic ->
                    ic.commitText("$text ", 1)
                }
                updateSuggestions()
            }
            onSpeechDismissed = {
                currentInputConnection?.finishComposingText()
            }
        }
        lifecycleOwner.onCreate()
        EmojiFontManager.initEmojiCompat(this)

        val cachedLayoutId = try {
            getSharedPreferences("kiebord_runtime", Context.MODE_PRIVATE)
                .getString("last_active_layout_id", null)
        } catch (_: Exception) { null }
        if (cachedLayoutId != null) {
            primaryAlphaLayout = layoutRepo.getLayout(cachedLayoutId)
            activeLayout = primaryAlphaLayout
        }

        val initialLang = if (activeLayout.id.startsWith("bn")) "bn" else "en"
        val secondaryLang = if (initialLang == "bn") "en" else "bn"
        serviceScope.launch(Dispatchers.IO) {
            NativeEngine.ensureInitialized(this@KiebordService, initialLang)
            ensureLayoutProcessorInitialized(primaryAlphaLayout)
            kotlinx.coroutines.withContext(Dispatchers.Main) {
                updateSuggestions()
            }
            NativeEngine.ensureInitialized(this@KiebordService, secondaryLang)
        }

        serviceScope.launch {
            preferencesRepo.preferencesFlow.collect { prefs ->
                preferencesState = prefs
                (transliterationManager as? GenericTransliterationManager)?.isAutoCorrectEnabled = prefs.phoneticAutoCorrect
                colorScheme = io.kiebord.theme.ThemeRepository.getInstance(this@KiebordService).getColorScheme(prefs.theme)
                val availableMeta = layoutRepo.getAvailableLayouts().map { it.id }.toSet()
                val enabledIds = prefs.enabledLayoutIds.toList().filter { id ->
                    id in availableMeta || id == KiebordPreferences.LAYOUT_AR_ARABIC
                }.ifEmpty { listOf(KiebordPreferences.LAYOUT_EN_QWERTY) }

                val targetPrimaryId = if (enabledIds.contains(prefs.primaryLayoutId)) {
                    prefs.primaryLayoutId
                } else {
                    enabledIds.first()
                }

                val targetPrimary = layoutRepo.getLayout(targetPrimaryId)
                if (primaryAlphaLayout.id != targetPrimary.id || !enabledIds.contains(primaryAlphaLayout.id)) {
                    primaryAlphaLayout = targetPrimary
                    if (!isSymbolsMode) {
                        activeLayout = targetPrimary
                    }
                }
                try {
                    getSharedPreferences("kiebord_runtime", Context.MODE_PRIVATE)
                        .edit()
                        .putString("last_active_layout_id", primaryAlphaLayout.id)
                        .apply()
                } catch (_: Exception) {}
                updateSuggestions()
            }
        }
    }

    override fun onEvaluateFullscreenMode(): Boolean = false

    override fun onConfigureWindow(win: Window, isFullscreen: Boolean, isCandidatesOnly: Boolean) {
        super.onConfigureWindow(win, false, isCandidatesOnly)
        lifecycleOwner.attachToView(win.decorView)
    }

    override fun onCreateInputView(): View {
        window?.window?.decorView?.let { decorView ->
            lifecycleOwner.attachToView(decorView)
            ViewCompat.getRootWindowInsets(decorView)?.let { insets ->
                val nav = insets.getInsets(WindowInsetsCompat.Type.navigationBars())
                val gestures = insets.getInsets(WindowInsetsCompat.Type.systemGestures())
                val mandatory = insets.getInsets(WindowInsetsCompat.Type.mandatorySystemGestures())
                val bottom = maxOf(nav.bottom, gestures.bottom, mandatory.bottom)
                if (bottom > 0) {
                    navBarInsetBottomPx = bottom
                }
            }
        }

        val composeView = ComposeView(this).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnLifecycleDestroyed(lifecycleOwner))
            lifecycleOwner.attachToView(this)
        }

        ViewCompat.setOnApplyWindowInsetsListener(composeView) { _, insets ->
            val nav = insets.getInsets(WindowInsetsCompat.Type.navigationBars())
            val gestures = insets.getInsets(WindowInsetsCompat.Type.systemGestures())
            val mandatory = insets.getInsets(WindowInsetsCompat.Type.mandatorySystemGestures())
            val bottom = maxOf(nav.bottom, gestures.bottom, mandatory.bottom)
            navBarInsetBottomPx = bottom
            insets
        }

        composeView.setContent {
            val density = LocalDensity.current
            val navBarBottomDp = with(density) { navBarInsetBottomPx.toDp() }

            val defaultPinnedActions = listOf(ToolbarAction.EMOJI, ToolbarAction.TEXT_EDIT, ToolbarAction.NUMBER_PAD, ToolbarAction.CLIPBOARD, ToolbarAction.THEME_TOGGLE)
            val pinnedToolbarActions = preferencesState.pinnedTools.mapNotNull { name ->
                try { ToolbarAction.valueOf(name) } catch (_: Exception) { null }
            }.distinct()
            val pinnedSet = pinnedToolbarActions.toSet()
            val drawerToolbarActions = (preferencesState.drawerTools.mapNotNull { name ->
                try { ToolbarAction.valueOf(name) } catch (_: Exception) { null }
            }.filter { it !in pinnedSet } + ToolbarAction.values().filter { it !in pinnedSet }).distinct()

            val voiceInputState by voiceInputManager.state.collectAsState()

            val currentActionVisual = remember(currentEditorInfoState, shiftState) {
                ActionKeyResolver.resolve(currentEditorInfoState, shiftState)
            }
            val availableLayoutIds = remember {
                layoutRepo.getAvailableLayouts().map { it.id }.toSet()
            }

            KiebordTheme(colorScheme = colorScheme) {
                KeyboardRootView(
                    layout = activeLayout,
                    primaryAlphaLayout = primaryAlphaLayout,
                    isNumberPadActive = activeLayout.id == PredefinedLayouts.NumberPadExtended.id,
                    onToggleNumberPad = {
                        finishComposing()
                        if (activeLayout.id == PredefinedLayouts.NumberPadExtended.id) {
                            activeLayout = primaryAlphaLayout
                        } else {
                            activeLayout = PredefinedLayouts.NumberPadExtended
                        }
                    },
                    actionKeyVisual = currentActionVisual,
                    shiftState = shiftState,
                    isBornoEasyKarMode = isBornoEasyKarMode,
                    bornoEasyTypedCluster = bornoEasyTypedCluster,
                    inputSessionId = inputSessionId,
                    navBarBottomInset = navBarBottomDp,
                    vibrationEnabled = preferencesState.vibrateOnKeypress,
                    voiceInputState = voiceInputState,
                    onStartVoiceTyping = {
                        currentInputConnection?.finishComposingText()
                        voiceInputManager.start(activeLayout.language)
                    },
                    onDismissVoiceTyping = { voiceInputManager.stop() },
                    voiceTypingEnabled = preferencesState.voiceTypingEnabled,
                    onKeyPress = { performKeyFeedback() },
                    onKeyTap = ::handleKeyTap,
                    onKeyLongPress = ::handleKeyLongPress,
                    onTextAction = ::handleTextAction,
                    suggestions = activeSuggestions,
                    suggestionBarMode = preferencesState.suggestionBarMode,
                    onCandidateSelect = ::handleCandidateSelect,
                    onUnlearnWord = { word ->
                        userDictionaryRepo.unlearnWord(word)
                        updateSuggestions()
                    },
                    clipboardManager = clipboardManager,
                    onPasteClip = { clipText ->
                        val ic = currentInputConnection
                        finishComposing()
                        lastGestureCommittedWord = ""
                        isGestureAlternativeActive = false
                        ic?.commitText(clipText, 1)
                        updateSuggestions()
                    },
                    onOpenClipboardSettings = ::openClipboardSettings,
                    lastDeletedBatch = lastDeletedBatchState,
                    onGestureCommit = ::handleGestureCommit,
                    onGestureLivePredict = ::predictLiveGestureCandidate,
                    gestureTypingEnabled = preferencesState.gestureTypingEnabled && activeLayout.isQwertyBased(),
                    showGestureTrail = preferencesState.showGestureTrail,
                    longPressDelayMs = preferencesState.longPressDelayMs.toLong(),
                    cursorTrackpadEnabled = preferencesState.cursorTrackpadEnabled,
                    heightScalePortrait = preferencesState.keyboardHeightScale,
                    heightScaleLandscape = preferencesState.keyboardHeightScaleLandscape,
                    chinPaddingPortraitDp = preferencesState.chinPaddingPortraitDp,
                    chinPaddingLandscapeDp = preferencesState.chinPaddingLandscapeDp,
                    horizontalMarginPortraitDp = preferencesState.horizontalMarginPortraitDp,
                    horizontalMarginLandscapeDp = preferencesState.horizontalMarginLandscapeDp,
                    onResizerValuesChange = { isLandscape, scale, chinDp, marginDp ->
                        serviceScope.launch {
                            preferencesRepo.updateKeyboardResizerValues(isLandscape, scale, chinDp, marginDp)
                        }
                    },
                    defaultShowNumberRow = preferencesState.showNumberRow,
                    extraRowOverrides = preferencesState.extraRowOverrides,
                    onNumberRowToggle = { enabled ->
                        serviceScope.launch { preferencesRepo.updateShowNumberRow(enabled) }
                    },
                    showKeyBorders = preferencesState.showKeyBorders,
                    showKeyShadows = preferencesState.showKeyShadows,
                    defaultShowSecondaryKeys = preferencesState.showSecondaryKeys,
                    onSecondaryKeysToggle = { enabled ->
                        serviceScope.launch { preferencesRepo.updateShowSecondaryKeys(enabled) }
                    },
                    defaultShowKeyPopups = preferencesState.showKeyPopups,
                    onKeyPopupsToggle = { enabled ->
                        serviceScope.launch { preferencesRepo.updateShowKeyPopups(enabled) }
                    },
                    defaultTraditionalMode = preferencesState.traditionalMode,
                    onTraditionalModeToggle = { enabled ->
                        serviceScope.launch { preferencesRepo.updateTraditionalMode(enabled) }
                    },
                    initialPinnedTools = pinnedToolbarActions,
                    initialDrawerTools = drawerToolbarActions,
                    onToolbarToolsChange = { newPinned, newDrawer ->
                        serviceScope.launch {
                            preferencesRepo.updateToolbarTools(
                                newPinned.map { it.name },
                                newDrawer.map { it.name }
                            )
                        }
                    },
                    onHeightScaleChange = { scale ->
                        serviceScope.launch {
                            preferencesRepo.updateKeyboardHeightScale(scale)
                        }
                    },
                    onDeleteSwipeMove = ::handleDeleteSwipeMove,
                    onDeleteSwipeEnd = ::handleDeleteSwipeEnd,
                    onDeleteSwipeCancel = ::handleDeleteSwipeCancel,
                    enabledLayoutIds = preferencesState.enabledLayoutIds.toList().filter { id ->
                        id in availableLayoutIds || id == KiebordPreferences.LAYOUT_AR_ARABIC
                    }.ifEmpty { listOf(KiebordPreferences.LAYOUT_EN_QWERTY) },
                    onSwitchToLayout = ::switchToLayout,
                    onOpenLanguagesSettings = ::openLanguagesSettings,
                    onOpenInputMethodPicker = ::openInputMethodPicker,
                    onOpenThemeSettings = ::openThemeSettings,
                    defaultOneHandedMode = when (preferencesState.oneHandedMode) {
                        KiebordPreferences.ONE_HANDED_LEFT -> "LEFT"
                        KiebordPreferences.ONE_HANDED_RIGHT -> "RIGHT"
                        else -> "OFF"
                    },
                    onOneHandedModeChange = { mode ->
                        serviceScope.launch {
                            val prefMode = when (mode) {
                                "LEFT" -> KiebordPreferences.ONE_HANDED_LEFT
                                "RIGHT" -> KiebordPreferences.ONE_HANDED_RIGHT
                                else -> KiebordPreferences.ONE_HANDED_OFF
                            }
                            preferencesRepo.updateOneHandedMode(prefMode)
                        }
                    },
                    onThemeToggle = ::openThemeSettings
                )
            }
        }
        return composeView
    }

    override fun onWindowShown() {
        super.onWindowShown()
        try {
            (getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager)
                ?.addPrimaryClipChangedListener(clipboardListener)
        } catch (_: Exception) {}
        clipboardManager.syncFromSystemClipboard(this)
        serviceScope.launch {
            delay(150)
            clipboardManager.syncFromSystemClipboard(this@KiebordService)
        }
    }

    override fun onWindowHidden() {
        super.onWindowHidden()
        try {
            (getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager)
                ?.removePrimaryClipChangedListener(clipboardListener)
        } catch (_: Exception) {}
    }

    override fun onStartInput(attribute: EditorInfo?, restarting: Boolean) {
        super.onStartInput(attribute, restarting)
        currentEditorInfoState = attribute
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        currentEditorInfoState = info
        clipboardManager.syncFromSystemClipboard(this)
        if (PredefinedLayouts.isBornoEasy(activeLayout.id)) {
            val before = currentInputConnection?.getTextBeforeCursor(10, 0)?.toString() ?: ""
            updateBornoEasyStateFromText(before)
        }
        window?.window?.decorView?.let { decorView ->
            lifecycleOwner.attachToView(decorView)
            ViewCompat.getRootWindowInsets(decorView)?.let { insets ->
                val nav = insets.getInsets(WindowInsetsCompat.Type.navigationBars())
                val gestures = insets.getInsets(WindowInsetsCompat.Type.systemGestures())
                val mandatory = insets.getInsets(WindowInsetsCompat.Type.mandatorySystemGestures())
                val bottom = maxOf(nav.bottom, gestures.bottom, mandatory.bottom)
                if (bottom > 0) {
                    navBarInsetBottomPx = bottom
                }
            }
        }
        lifecycleOwner.onStart()
        inputSessionId++
        isSymbolsMode = false
        val cachedLayoutId = try {
            getSharedPreferences("kiebord_runtime", Context.MODE_PRIVATE)
                .getString("last_active_layout_id", null)
        } catch (_: Exception) { null }
        if (cachedLayoutId != null && cachedLayoutId != primaryAlphaLayout.id) {
            primaryAlphaLayout = layoutRepo.getLayout(cachedLayoutId)
        }
        val inputType = info?.inputType ?: 0
        val inputClass = inputType and InputType.TYPE_MASK_CLASS
        val variation = inputType and InputType.TYPE_MASK_VARIATION
        val numberLayout = PredefinedLayouts.resolveNumberLayout(inputClass, variation)
        activeLayout = numberLayout ?: primaryAlphaLayout
        val initialCaps = if (shouldAutoCapitalize() && info != null) {
            info.initialCapsMode
        } else 0
        shiftState = if (initialCaps != 0) ShiftState.ONE_SHOT else ShiftState.UNSHIFTED
        getTransliterationManager().reset()
        composingBuffer.clear()
        lastGestureCommittedWord = ""
        isGestureAlternativeActive = false
        lastSpaceTapTime = 0L
        lastAutoSpacePunctuationTime = 0L
        selectionAnchor = null
        selectionCursor = null
        updateSuggestions()
    }

    override fun onUpdateSelection(
        oldSelStart: Int,
        oldSelEnd: Int,
        newSelStart: Int,
        newSelEnd: Int,
        candidatesStart: Int,
        candidatesEnd: Int
    ) {
        super.onUpdateSelection(oldSelStart, oldSelEnd, newSelStart, newSelEnd, candidatesStart, candidatesEnd)
        if (PredefinedLayouts.isBornoEasy(activeLayout.id) && composingBuffer.isEmpty()) {
            val before = currentInputConnection?.getTextBeforeCursor(10, 0)?.toString() ?: ""
            updateBornoEasyStateFromText(before)
        }
        val translitMgr = getTransliterationManager()
        val isComposing = translitMgr.isComposing || composingBuffer.isNotEmpty()

        if (isComposing) {
            // When typing normally, the cursor stays collapsed at the end of the candidate region.
            // If the user touched another position, the cursor or selection moved away from candidatesEnd,
            // or the composing span was cleared/invalidated by the host view.
            val cursorMovedAway = candidatesStart < 0 ||
                    candidatesEnd < 0 ||
                    newSelStart != candidatesEnd ||
                    newSelEnd != candidatesEnd ||
                    newSelStart < candidatesStart ||
                    newSelEnd > candidatesEnd

            if (cursorMovedAway) {
                val ic = currentInputConnection
                val wordToLearn = when {
                    translitMgr.isComposing -> translitMgr.currentTransliteration.trim()
                    composingBuffer.isNotEmpty() -> BengaliBstiEngine.cleanPending(composingBuffer.toString()).trim()
                    else -> ""
                }
                if (preferencesState.learnedWordsEnabled && wordToLearn.length >= UserDictionaryRepository.MIN_LEARN_LENGTH) {
                    val prevWord = getPrecedingWord(excludeComposing = true).takeIf { it.isNotBlank() }
                    userDictionaryRepo.recordWord(wordToLearn, prevWord)
                }

                translitMgr.reset()
                composingBuffer.clear()
                lastGestureCommittedWord = ""
                isGestureAlternativeActive = false

                ic?.finishComposingText()
                updateSuggestions()
                return
            }
        }

        if (isGestureAlternativeActive && lastGestureCommittedWord.isNotEmpty()) {
            val ic = currentInputConnection
            val textBefore = ic?.getTextBeforeCursor(lastGestureCommittedWord.length + 1, 0)?.toString() ?: ""
            if (textBefore.endsWith(lastGestureCommittedWord)) {
                return
            } else {
                isGestureAlternativeActive = false
                lastGestureCommittedWord = ""
            }
        }
        if (!translitMgr.isComposing && composingBuffer.isEmpty()) {
            updateSuggestions()
        }
    }

    override fun onFinishInputView(finishingInput: Boolean) {
        super.onFinishInputView(finishingInput)
        if (::voiceInputManager.isInitialized) {
            voiceInputManager.stop()
        }
        try {
            (getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager)
                ?.removePrimaryClipChangedListener(clipboardListener)
        } catch (_: Exception) {}
        finishComposing()
        lastGestureCommittedWord = ""
        isGestureAlternativeActive = false
        lastSpaceTapTime = 0L
        lastAutoSpacePunctuationTime = 0L
        selectionAnchor = null
        selectionCursor = null
        activeSuggestions = emptyList()
        lifecycleOwner.onStop()
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::voiceInputManager.isInitialized) {
            voiceInputManager.destroy()
        }
        try {
            (getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager)
                ?.removePrimaryClipChangedListener(clipboardListener)
        } catch (_: Exception) {}
        serviceScope.cancel()
        lifecycleOwner.onDestroy()
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        userDictionaryRepo.trimMemory(level)
    }

    override fun onLowMemory() {
        super.onLowMemory()
        userDictionaryRepo.trimMemory(ComponentCallbacks2.TRIM_MEMORY_COMPLETE)
    }

    fun switchToLayout(layoutId: String) {
        finishComposing()
        isBornoEasyKarMode = false
        bornoEasyTypedCluster = ""
        primaryAlphaLayout = layoutRepo.getLayout(layoutId)
        ensureLayoutProcessorInitialized(primaryAlphaLayout)
        activeLayout = if (!isSymbolsMode) {
            primaryAlphaLayout
        } else {
            primaryAlphaLayout.effectiveSymbolsLayout() ?: PredefinedLayouts.Symbols
        }
        if (!shouldAutoCapitalize()) {
            shiftState = ShiftState.UNSHIFTED
        }
        try {
            getSharedPreferences("kiebord_runtime", Context.MODE_PRIVATE)
                .edit()
                .putString("last_active_layout_id", layoutId)
                .apply()
        } catch (_: Exception) {}
        serviceScope.launch {
            preferencesRepo.updatePrimaryLayoutId(layoutId)
        }
        updateSuggestions()
    }

    fun openLanguagesSettings() {
        try {
            val intent = Intent(this, Class.forName("io.kiebord.app.MainActivity")).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra("navigate_to", "layouts")
            }
            startActivity(intent)
        } catch (_: Exception) {}
    }

    fun openThemeSettings() {
        try {
            val intent = Intent(this, Class.forName("io.kiebord.app.MainActivity")).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra("navigate_to", "themes")
            }
            startActivity(intent)
        } catch (_: Exception) {}
    }

    fun openClipboardSettings() {
        try {
            val intent = Intent(this, Class.forName("io.kiebord.app.MainActivity")).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra("navigate_to", "clipboard")
            }
            startActivity(intent)
        } catch (_: Exception) {}
    }

    fun openInputMethodPicker() {
        try {
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
            imm?.showInputMethodPicker()
        } catch (_: Exception) {}
    }

    private fun finishComposing() {
        val ic = currentInputConnection
        val translitMgr = getTransliterationManager()
        var wasComposing = false
        if (translitMgr.isComposing) {
            val word = translitMgr.currentTransliteration.trim()
            if (preferencesState.learnedWordsEnabled && word.length >= UserDictionaryRepository.MIN_LEARN_LENGTH) {
                val prevWord = getPrecedingWord(excludeComposing = true).takeIf { it.isNotBlank() }
                userDictionaryRepo.recordWord(word, prevWord)
            }
            ic?.commitText(translitMgr.currentTransliteration, 1)
            translitMgr.reset()
            wasComposing = true
        }
        if (composingBuffer.isNotEmpty()) {
            val clean = BengaliBstiEngine.cleanPending(composingBuffer.toString()).trim()
            if (preferencesState.learnedWordsEnabled && clean.length >= UserDictionaryRepository.MIN_LEARN_LENGTH) {
                val prevWord = getPrecedingWord(excludeComposing = true).takeIf { it.isNotBlank() }
                userDictionaryRepo.recordWord(clean, prevWord)
            }
            ic?.commitText(clean, 1)
            composingBuffer.clear()
            wasComposing = true
        }
        if (wasComposing) {
            ic?.finishComposingText()
        }
    }

    private val audioManager: android.media.AudioManager? by lazy(LazyThreadSafetyMode.NONE) {
        getSystemService(Context.AUDIO_SERVICE) as? android.media.AudioManager
    }

    private val vibratorService: Vibrator? by lazy(LazyThreadSafetyMode.NONE) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    }

    private val clickVibrationEffect: VibrationEffect? by lazy(LazyThreadSafetyMode.NONE) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK)
        } else null
    }

    private var cachedOneShotStrength: Int = -1
    private var cachedOneShotEffect: VibrationEffect? = null

    private fun performKeyFeedback() {
        if (preferencesState.soundOnKeypress) {
            try {
                audioManager?.playSoundEffect(android.media.AudioManager.FX_KEYPRESS_STANDARD)
            } catch (_: Exception) {}
        }
        if (!preferencesState.vibrateOnKeypress) return
        try {
            if (preferencesState.vibrationType == KiebordPreferences.VIBRATION_TYPE_HAPTIC) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    clickVibrationEffect?.let { vibratorService?.vibrate(it) }
                } else {
                    window?.window?.decorView?.performHapticFeedback(
                        HapticFeedbackConstants.KEYBOARD_TAP,
                        HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING
                    )
                }
            } else {
                // Vibration mode: duration and amplitude scaled by vibrationStrength
                val strength = preferencesState.vibrationStrength
                val durationMs = (10L + (strength * 30L / 100L)).coerceIn(5L, 60L)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    val effect = if (cachedOneShotStrength == strength && cachedOneShotEffect != null) {
                        cachedOneShotEffect!!
                    } else {
                        val amplitude = (strength * 255 / 100).coerceIn(1, 255)
                        VibrationEffect.createOneShot(durationMs, amplitude).also {
                            cachedOneShotStrength = strength
                            cachedOneShotEffect = it
                        }
                    }
                    vibratorService?.vibrate(effect)
                } else {
                    @Suppress("DEPRECATION")
                    vibratorService?.vibrate(durationMs)
                }
            }
        } catch (_: Exception) {
            try {
                window?.window?.decorView?.performHapticFeedback(
                    HapticFeedbackConstants.KEYBOARD_TAP,
                    HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING
                )
            } catch (_: Exception) {}
        }
    }

    private fun handleKeyTap(key: KeyDef) {
        if (::voiceInputManager.isInitialized && voiceInputManager.isListening) {
            voiceInputManager.stop()
        }
        val ic = currentInputConnection ?: return

        if (key.type != KeyType.DELETE) {
            lastGestureCommittedWord = ""
            isGestureAlternativeActive = false
        }
        if (key.type != KeyType.SPACE) {
            lastSpaceTapTime = 0L
            lastAutoSpacePunctuationTime = 0L
        }
        if (key.type != KeyType.SHIFT) {
            lastShiftTapTime = 0L
        }

        when (key.type) {
            KeyType.CHARACTER -> {
                val initialShiftState = shiftState
                val text = key.textToCommit(shiftState)

                when {
                    isTransliterationLayout() -> {
                        if (activeLayout.id in io.kiebord.feature.ime.khipro.KHIPRO_LAYOUT_IDS) {
                            khiproSpecs.ensureLoaded(khiproVariantFor(activeLayout.id))
                        }
                        if (
                            isTransliterableChar(text) || isBengaliOrModifier(text) ||
                            (activeLayout.id in io.kiebord.feature.ime.khipro.KHIPRO_LAYOUT_IDS &&
                                text.length == 1 && text[0] in io.kiebord.feature.ime.khipro.KHIPRO_BUFFER_KEYS)
                        ) {
                            getTransliterationManager().handleCharacter(text)
                        } else {
                            val translitMgr = getTransliterationManager()
                            val word = translitMgr.currentTransliteration.trim()
                            if (preferencesState.learnedWordsEnabled && translitMgr.isComposing && word.length >= UserDictionaryRepository.MIN_LEARN_LENGTH) {
                                val prevWord = getPrecedingWord(excludeComposing = true)
                                userDictionaryRepo.recordWord(word, prevWord)
                            }
                            finishComposing()
                            commitCharacterWithPunctuationHandling(ic, text)
                        }
                    }
                    // English mode
                    isEnglishLayout() -> {
                        if (isEnglishLetter(text)) {
                            composingBuffer.append(text)
                            ic.setComposingText(composingBuffer.toString(), 1)
                        } else {
                            val word = composingBuffer.toString().trim()
                            if (preferencesState.learnedWordsEnabled && word.length >= UserDictionaryRepository.MIN_LEARN_LENGTH) {
                                val prevWord = getPrecedingWord(excludeComposing = true)
                                userDictionaryRepo.recordWord(word, prevWord)
                            }
                            finishComposing()
                            commitCharacterWithPunctuationHandling(ic, text)
                        }
                    }
                    // Bengali Probhat layout
                    activeLayout.id == "bn_probhat" -> {
                        if (isBengaliChar(text)) {
                            composingBuffer.append(text)
                            ic.setComposingText(composingBuffer.toString(), 1)
                        } else {
                            val word = composingBuffer.toString().trim()
                            if (preferencesState.learnedWordsEnabled && word.length >= UserDictionaryRepository.MIN_LEARN_LENGTH) {
                                val prevWord = getPrecedingWord(excludeComposing = true)
                                userDictionaryRepo.recordWord(word, prevWord)
                            }
                            finishComposing()
                            commitCharacterWithPunctuationHandling(ic, text)
                        }
                    }
                    // Bengali Borno Easy layout
                    activeLayout.id == "bn_easy" -> {
                        if (isBornoEasyKarMode && key.commitText == "") {
                            // Key 0 in Kar mode tapped -> inherent vowel confirmed, no kar added
                            isBornoEasyKarMode = false
                            bornoEasyTypedCluster = ""
                        } else if (isBengaliChar(text) || text == "\u200C" || text == "\u200D") {
                            composingBuffer.append(text)
                            ic.setComposingText(composingBuffer.toString(), 1)
                            updateBornoEasyStateFromText(composingBuffer.toString())
                        } else {
                            val word = composingBuffer.toString().trim()
                            if (preferencesState.learnedWordsEnabled && word.length >= UserDictionaryRepository.MIN_LEARN_LENGTH) {
                                val prevWord = getPrecedingWord(excludeComposing = true)
                                userDictionaryRepo.recordWord(word, prevWord)
                            }
                            finishComposing()
                            commitCharacterWithPunctuationHandling(ic, text)
                            isBornoEasyKarMode = false
                            bornoEasyTypedCluster = ""
                        }
                    }
                    // Bengali National (BSTI) layout
                    activeLayout.id == "bn_national" -> {
                        if (isBengaliChar(text)) {
                            composingBuffer.append(text)
                            val isTraditional = preferencesState.traditionalMode
                            val transformed = if (text == "র্" && isTraditional) {
                                BengaliBstiEngine.applyRephReordering(composingBuffer.toString())
                            } else {
                                var res = composingBuffer.toString()
                                var idx = 0
                                val isMacro = text.length > 1
                                while (idx < text.length) {
                                    val cp = text.codePointAt(idx)
                                    res = BengaliBstiEngine.applyBstiRules(res, cp, isTraditional, isMacro)
                                    idx += Character.charCount(cp)
                                }
                                res
                            }
                            composingBuffer.clear()
                            composingBuffer.append(transformed)
                            ic.setComposingText(composingBuffer.toString(), 1)
                        } else {
                            val cleanWord = BengaliBstiEngine.cleanPending(composingBuffer.toString().trim())
                            if (preferencesState.learnedWordsEnabled && cleanWord.length >= UserDictionaryRepository.MIN_LEARN_LENGTH) {
                                val prevWord = getPrecedingWord(excludeComposing = true)
                                userDictionaryRepo.recordWord(cleanWord, prevWord)
                            }
                            finishComposing()
                            commitCharacterWithPunctuationHandling(ic, text)
                        }
                    }
                    // Other layouts (Symbols, etc.)
                    else -> {
                        val word = composingBuffer.toString().trim()
                        if (preferencesState.learnedWordsEnabled && word.length >= UserDictionaryRepository.MIN_LEARN_LENGTH) {
                            val prevWord = getPrecedingWord(excludeComposing = true)
                            userDictionaryRepo.recordWord(word, prevWord)
                        }
                        finishComposing()
                        commitCharacterWithPunctuationHandling(ic, text)
                    }
                }

                updateSuggestions()

                // If in one-shot shift mode, revert to unshifted after one character (unless a sentence terminator just set ONE_SHOT)
                if (initialShiftState == ShiftState.ONE_SHOT && shiftState == ShiftState.ONE_SHOT) {
                    if (!shouldAutoCapitalize() || !isSentenceTerminator(text)) {
                        shiftState = ShiftState.UNSHIFTED
                    }
                }
            }
            KeyType.SHIFT -> {
                val now = SystemClock.uptimeMillis()
                val isDoubleTap = (now - lastShiftTapTime) <= 350L
                shiftState = when {
                    shiftState == ShiftState.CAPS_LOCK -> {
                        lastShiftTapTime = 0L
                        ShiftState.UNSHIFTED
                    }
                    shiftState == ShiftState.ONE_SHOT && isDoubleTap -> {
                        lastShiftTapTime = 0L
                        ShiftState.CAPS_LOCK
                    }
                    shiftState == ShiftState.ONE_SHOT -> {
                        lastShiftTapTime = now
                        ShiftState.UNSHIFTED
                    }
                    else -> {
                        lastShiftTapTime = now
                        ShiftState.ONE_SHOT
                    }
                }
            }
            KeyType.DELETE -> {
                if (shiftState == ShiftState.ONE_SHOT) {
                    shiftState = ShiftState.UNSHIFTED
                }
                val selectedText = ic.getSelectedText(0)
                if (!selectedText.isNullOrEmpty()) {
                    ic.commitText("", 1)
                    updateSuggestions()
                    return
                }
                if (lastGestureCommittedWord.isNotEmpty()) {
                    ic.deleteSurroundingText(lastGestureCommittedWord.length, 0)
                    lastGestureCommittedWord = ""
                    isGestureAlternativeActive = false
                    updateSuggestions()
                    return
                }
                if (isTransliterationLayout() && getTransliterationManager().handleDelete()) {
                    updateSuggestions()
                    return
                }
                // If composing in English, Bengali Probhat or National
                if (composingBuffer.isNotEmpty()) {
                    composingBuffer.deleteCharAt(composingBuffer.length - 1)
                    if (composingBuffer.endsWith("\u200B")) {
                        composingBuffer.deleteCharAt(composingBuffer.length - 1)
                    }
                    if (composingBuffer.isEmpty()) {
                        ic.setComposingText("", 1)
                        ic.finishComposingText()
                    } else {
                        ic.setComposingText(composingBuffer.toString(), 1)
                    }
                    if (PredefinedLayouts.isBornoEasy(activeLayout.id)) {
                        updateBornoEasyStateFromText(composingBuffer.toString())
                    }
                    updateSuggestions()
                    return
                }

                ic.deleteSurroundingText(1, 0)
                if (PredefinedLayouts.isBornoEasy(activeLayout.id)) {
                    val before = ic.getTextBeforeCursor(10, 0)?.toString() ?: ""
                    updateBornoEasyStateFromText(before)
                }
                updateSuggestions()
            }
            KeyType.ACTION -> {
                finishComposing()
                if (PredefinedLayouts.isBornoEasy(activeLayout.id)) {
                    isBornoEasyKarMode = false
                    bornoEasyTypedCluster = ""
                }
                val visual = ActionKeyResolver.resolve(currentInputEditorInfo, shiftState)
                if (visual.isAction) {
                    val actionIdToPerform = if (currentInputEditorInfo?.actionId != 0 && currentInputEditorInfo?.actionId != null) {
                        currentInputEditorInfo!!.actionId
                    } else {
                        visual.actionId
                    }
                    val handled = ic.performEditorAction(actionIdToPerform)
                    if (!handled && actionIdToPerform == EditorInfo.IME_ACTION_UNSPECIFIED) {
                        sendKeyChar('\n')
                    }
                } else {
                    sendKeyChar('\n')
                    if (shouldAutoCapitalize() && shiftState == ShiftState.UNSHIFTED) {
                        shiftState = ShiftState.ONE_SHOT
                    }
                }
                updateSuggestions()
            }
            KeyType.SPACE -> {
                if (PredefinedLayouts.isBornoEasy(activeLayout.id)) {
                    isBornoEasyKarMode = false
                    bornoEasyTypedCluster = ""
                }
                val translitMgr = getTransliterationManager()
                if (translitMgr.isComposing) {
                    val committed = translitMgr.currentTransliteration
                    val cleanCommitted = committed.trim()
                    if (preferencesState.learnedWordsEnabled && cleanCommitted.length >= UserDictionaryRepository.MIN_LEARN_LENGTH) {
                        val prevWord = getPrecedingWord(excludeComposing = true).takeIf { it.isNotBlank() }
                        userDictionaryRepo.recordWord(cleanCommitted, prevWord)
                    }
                    ic.commitText(committed + " ", 1)
                    translitMgr.reset()
                    lastSpaceTapTime = SystemClock.uptimeMillis()
                } else if (composingBuffer.isNotEmpty()) {
                    val committed = BengaliBstiEngine.cleanPending(composingBuffer.toString())
                    val cleanCommitted = committed.trim()
                    if (preferencesState.learnedWordsEnabled && cleanCommitted.length >= UserDictionaryRepository.MIN_LEARN_LENGTH) {
                        val prevWord = getPrecedingWord(excludeComposing = true).takeIf { it.isNotBlank() }
                        userDictionaryRepo.recordWord(cleanCommitted, prevWord)
                    }
                    ic.commitText(committed + " ", 1)
                    composingBuffer.clear()
                    lastSpaceTapTime = SystemClock.uptimeMillis()
                } else {
                    val now = SystemClock.uptimeMillis()

                    // If user reflexively presses space right after auto-spaced punctuation, consume redundant space tap
                    if (lastAutoSpacePunctuationTime > 0L && (now - lastAutoSpacePunctuationTime) < 1500L) {
                        val textBefore = ic.getTextBeforeCursor(2, 0)
                        if (textBefore != null && textBefore.endsWith(" ")) {
                            lastAutoSpacePunctuationTime = 0L
                            lastSpaceTapTime = now
                            updateSuggestions()
                            return
                        }
                    }
                    lastAutoSpacePunctuationTime = 0L

                    val isDoubleSpace = preferencesState.doubleSpacePeriod &&
                            !isUrlOrCodeField() &&
                            (now - lastSpaceTapTime <= 500L)

                    if (isDoubleSpace) {
                        val textBefore = ic.getTextBeforeCursor(2, 0)
                        if (textBefore != null && textBefore.endsWith(" ") && textBefore.length >= 2 &&
                            !textBefore[textBefore.length - 2].isWhitespace() &&
                            !isPunctuationChar(textBefore[textBefore.length - 2])
                        ) {
                            ic.deleteSurroundingText(1, 0)
                            val puncToInsert = if (activeLayout.id.startsWith("bn_")) "। " else ". "
                            ic.commitText(puncToInsert, 1)
                            if (shouldAutoCapitalize() && shiftState == ShiftState.UNSHIFTED) {
                                shiftState = ShiftState.ONE_SHOT
                            }
                            lastSpaceTapTime = 0L
                            updateSuggestions()
                            return
                        }
                    }

                    // Auto-capitalization if previous character was sentence-ending punctuation
                    val textBefore = ic.getTextBeforeCursor(1, 0)
                    ic.commitText(" ", 1)
                    lastSpaceTapTime = now
                    if (shouldAutoCapitalize() && shiftState == ShiftState.UNSHIFTED && textBefore != null) {
                        val lastChar = textBefore.lastOrNull()
                        if (lastChar != null && isSentenceTerminatorChar(lastChar)) {
                            shiftState = ShiftState.ONE_SHOT
                        }
                    }
                }
                updateSuggestions()
            }
            KeyType.SYMBOLS -> {
                finishComposing()
                when (key.label) {
                    "?123", "?১২৩" -> {
                        if (!isSymbolsMode) {
                            primaryAlphaLayout = activeLayout
                            isSymbolsMode = true
                        }
                        activeLayout = primaryAlphaLayout.effectiveSymbolsLayout() ?: PredefinedLayouts.Symbols
                    }
                    "=\\<", "=<" -> {
                        isSymbolsMode = true
                        activeLayout = primaryAlphaLayout.effectiveMoreSymbolsLayout() ?: PredefinedLayouts.SymbolsMore
                    }
                    "ABC" -> {
                        isSymbolsMode = false
                        activeLayout = primaryAlphaLayout
                        if (!shouldAutoCapitalize()) {
                            shiftState = ShiftState.UNSHIFTED
                        }
                    }
                    "*#" -> {
                        if (!isSymbolsMode) {
                            primaryAlphaLayout = activeLayout
                            isSymbolsMode = true
                        }
                        activeLayout = primaryAlphaLayout.effectiveSymbolsLayout() ?: PredefinedLayouts.Symbols
                    }
                    else -> {
                        if (isSymbolsMode || PredefinedLayouts.isNumberLayout(activeLayout.id)) {
                            isSymbolsMode = false
                            activeLayout = primaryAlphaLayout
                            if (!shouldAutoCapitalize()) {
                                shiftState = ShiftState.UNSHIFTED
                            }
                        } else {
                            primaryAlphaLayout = activeLayout
                            isSymbolsMode = true
                            activeLayout = primaryAlphaLayout.effectiveSymbolsLayout() ?: PredefinedLayouts.Symbols
                        }
                    }
                }
                if (isSymbolsMode) {
                    activeSuggestions = emptyList()
                } else {
                    serviceScope.launch {
                        updateSuggestions()
                    }
                }
            }
            KeyType.SWITCH_LANGUAGE -> {
                val availableMeta = layoutRepo.getAvailableLayouts().map { it.id }.toSet()
                val enabledIds = preferencesState.enabledLayoutIds.toList().filter { id ->
                    id in availableMeta || id == KiebordPreferences.LAYOUT_AR_ARABIC
                }.ifEmpty { listOf(KiebordPreferences.LAYOUT_EN_QWERTY) }

                if (enabledIds.size > 1) {
                    val currentIndex = enabledIds.indexOf(primaryAlphaLayout.id)
                    val nextIndex = if (currentIndex >= 0) (currentIndex + 1) % enabledIds.size else 0
                    val nextId = enabledIds[nextIndex]
                    switchToLayout(nextId)
                }
            }
            KeyType.MORE_KEYS -> {
                // Secondary popups
            }
        }
    }

    private fun handleKeyLongPress(key: KeyDef) {
        if (key.type == KeyType.SWITCH_LANGUAGE) {
            return
        }
        if (key.type == KeyType.SPACE) {
            return
        }
        if (key.type == KeyType.DELETE) {
            handleLongPressDelete()
            return
        }
        val ic = currentInputConnection ?: return
        val currentShiftState = shiftState
        val currentMoreKeys = key.moreKeys(currentShiftState)
        val currentFlickUp = key.flickUp(currentShiftState)
        val charToInsert = when {
            currentMoreKeys.isNotEmpty() -> currentMoreKeys.first()
            !currentFlickUp.isNullOrEmpty() -> currentFlickUp
            else -> ""
        }
        if (charToInsert.isEmpty()) return

        if (isTransliterationLayout() && (isTransliterableChar(charToInsert) || isBengaliOrModifier(charToInsert))) {
            getTransliterationManager().handleCharacter(charToInsert)
        } else {
            finishComposing()
            commitCharacterWithPunctuationHandling(ic, charToInsert)
        }
        updateSuggestions()
    }

    private var isSwipeDeleting = false
    private var swipeDeleteInitialCursor = -1
    private var swipeDeleteInitialText = ""

    private fun handleDeleteSwipeMove(deltaX: Float) {
        val ic = currentInputConnection ?: return
        if (!isSwipeDeleting) {
            isSwipeDeleting = true
            finishComposing()
            val req = ExtractedTextRequest().apply { token = 0; flags = 0 }
            val ext = ic.getExtractedText(req, 0)
            swipeDeleteInitialCursor = ext?.let { it.startOffset + it.selectionEnd } ?: -1
            swipeDeleteInitialText = ic.getTextBeforeCursor(1024, 0)?.toString() ?: ""
        }

        val absDistance = (-deltaX).coerceAtLeast(0f)
        val density = resources.displayMetrics.density
        val textLen = swipeDeleteInitialText.length

        val charsToSelect = if (preferencesState.selectiveDeleteMode == KiebordPreferences.DELETE_MODE_WORD) {
            val stepPx = 28f * density
            val wordsToSelect = (absDistance / stepPx).toInt().coerceAtLeast(0)
            var idx = textLen
            var wordsFound = 0
            while (idx > 0 && wordsFound < wordsToSelect) {
                while (idx > 0 && swipeDeleteInitialText[idx - 1].isWhitespace()) {
                    idx--
                }
                while (idx > 0 && !swipeDeleteInitialText[idx - 1].isWhitespace()) {
                    idx--
                }
                wordsFound++
            }
            textLen - idx
        } else {
            val stepPx = 11f * density
            (absDistance / stepPx).toInt().coerceIn(0, textLen)
        }

        if (swipeDeleteInitialCursor != -1) {
            val cur = swipeDeleteInitialCursor.coerceIn(0, textLen)
            val start = (cur - charsToSelect).coerceIn(0, textLen)
            ic.setSelection(start, cur)
        } else {
            val start = (textLen - charsToSelect).coerceIn(0, textLen)
            ic.setSelection(start, textLen)
        }
    }

    private fun handleDeleteSwipeEnd() {
        val ic = currentInputConnection ?: return
        if (isSwipeDeleting) {
            isSwipeDeleting = false
            val selectedText = ic.getSelectedText(0)?.toString()
            if (!selectedText.isNullOrEmpty()) {
                ic.commitText("", 1)
                lastDeletedBatchState = selectedText
            }
            updateSuggestions()
        }
    }

    private fun handleDeleteSwipeCancel() {
        val ic = currentInputConnection ?: return
        if (isSwipeDeleting) {
            isSwipeDeleting = false
            val textLen = swipeDeleteInitialText.length
            if (swipeDeleteInitialCursor != -1) {
                val cur = swipeDeleteInitialCursor.coerceIn(0, textLen)
                ic.setSelection(cur, cur)
            } else {
                ic.setSelection(textLen, textLen)
            }
        }
    }

    private fun handleLongPressDelete() {
        val ic = currentInputConnection ?: return

        // 1. If text is highlighted / selected, delete the whole selection first
        val selectedText = ic.getSelectedText(0)
        if (!selectedText.isNullOrEmpty()) {
            ic.commitText("", 1)
            updateSuggestions()
            return
        }

        // 2. Gesture-committed word
        if (lastGestureCommittedWord.isNotEmpty()) {
            ic.deleteSurroundingText(lastGestureCommittedWord.length, 0)
            lastGestureCommittedWord = ""
            updateSuggestions()
            return
        }

        // 3. Active transliteration composing
        if (isTransliterationLayout()) {
            val translitMgr = getTransliterationManager()
            if (translitMgr.isComposing) {
                translitMgr.handleDelete()
                updateSuggestions()
                return
            }
        }

        // 4. Standard composing buffer
        if (composingBuffer.isNotEmpty()) {
            composingBuffer.deleteCharAt(composingBuffer.length - 1)
            if (composingBuffer.isEmpty()) {
                ic.setComposingText("", 1)
                ic.finishComposingText()
            } else {
                ic.setComposingText(composingBuffer.toString(), 1)
            }
            updateSuggestions()
            return
        }

        // 5. Normal continuous deletion
        ic.deleteSurroundingText(1, 0)
        updateSuggestions()
    }

    private fun khiproVariantFor(layoutId: String): io.kiebord.feature.ime.khipro.KhiproVariant =
        if (layoutId == "bn_khipro_dark") io.kiebord.feature.ime.khipro.KhiproVariant.DARK
        else io.kiebord.feature.ime.khipro.KhiproVariant.CLASSIC

    private fun readKhiproAsset(path: String): String? {
        return try {
            assets.open(path).bufferedReader().use { it.readText() }
        } catch (_: Exception) {
            null
        }
    }

    private fun isTransliterableChar(text: String): Boolean {
        if (text.length != 1) return false
        val c = text[0]
        if (isM17nLayout()) {
            return (c in 'a'..'z') || (c in 'A'..'Z') || (c in '0'..'9') ||
                    c == '/' || c == '\\' || c == '.' || c == ':' || c == '$' ||
                    c == '&' || c == '*' || c == '~' || c == '^' || c == '+' ||
                    c == '-' || c == '_' || c == '\'' || c == '"' || c == '`' ||
                    c == ';' || c == '?' || c == '!'
        }
        return (c in 'a'..'z') || (c in 'A'..'Z')
    }

    private fun isEnglishLetter(text: String): Boolean {
        if (text.length != 1) return false
        val c = text[0]
        return (c in 'a'..'z') || (c in 'A'..'Z') || c == '\''
    }

    private fun isUrlOrCodeField(): Boolean {
        val info = currentInputEditorInfo ?: return false
        val inputType = info.inputType
        val inputClass = inputType and InputType.TYPE_MASK_CLASS
        val variation = inputType and InputType.TYPE_MASK_VARIATION
        val flags = inputType and InputType.TYPE_MASK_FLAGS

        // Non-text fields (Number, Phone, DateTime)
        if (inputClass != InputType.TYPE_CLASS_TEXT) {
            return true
        }

        // URI / URL fields
        if (variation == InputType.TYPE_TEXT_VARIATION_URI) {
            return true
        }

        // Email address fields
        if (variation == InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS ||
            variation == InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS
        ) {
            return true
        }

        // Password fields
        if (variation == InputType.TYPE_TEXT_VARIATION_PASSWORD ||
            variation == InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD ||
            variation == InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD
        ) {
            return true
        }

        // Code / Terminal fields: typically have TYPE_TEXT_FLAG_NO_SUGGESTIONS
        if ((flags and InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS) != 0) {
            return true
        }

        return false
    }

    private fun isDigitChar(c: Char): Boolean {
        return (c in '0'..'9') || (c in '\u09E6'..'\u09EF')
    }

    private fun isPunctuationChar(c: Char): Boolean {
        return c == '.' || c == ',' || c == '!' || c == '?' ||
               c == ':' || c == ';' || c == '।' || c == '॥' ||
               c == '-' || c == '/' || c == '(' || c == ')' ||
               c == '[' || c == ']' || c == '{' || c == '}' ||
               c == '"' || c == '\'' || c == '`' || c == '~' ||
               c == '@' || c == '#' || c == '$' || c == '%' ||
               c == '^' || c == '&' || c == '*' || c == '_' ||
               c == '+' || c == '=' || c == '<' || c == '>' ||
               c == '|' || c == '\\'
    }

    private fun isRemoveSpaceBeforePunctuation(str: String): Boolean {
        if (str.length != 1) return false
        val c = str[0]
        return c == '.' || c == ',' || c == '!' || c == '?' ||
               c == ':' || c == ';' || c == '।' || c == '॥' ||
               c == ')' || c == ']' || c == '}' || c == '—' || c == '%'
    }

    private fun isAutoSpacePunctuation(str: String): Boolean {
        if (str.length != 1) return false
        val c = str[0]
        return c == '.' || c == ',' || c == '!' || c == '?' ||
               c == ':' || c == ';' || c == '।' || c == '॥'
    }

    private fun isSentenceTerminator(str: String): Boolean {
        return str == "." || str == "!" || str == "?" || str == "।" || str == "॥"
    }

    private fun isSentenceTerminatorChar(c: Char): Boolean {
        return c == '.' || c == '!' || c == '?' || c == '।' || c == '॥'
    }

    private fun shouldAutoSpaceAfterPunctuation(precedingChar: Char, puncText: String): Boolean {
        // Do not auto-space if preceded by whitespace
        if (precedingChar.isWhitespace()) return false

        // Do not auto-space for decimal numbers, formatted thousands, or timestamps: e.g. 3.14, 1,000, 10:30, ৩.১৪
        if (isDigitChar(precedingChar) && (puncText == "." || puncText == "," || puncText == ":")) {
            return false
        }

        // Do not auto-space for repeated punctuation: e.g. .., ,, --
        if (puncText.isNotEmpty() && precedingChar == puncText[0]) {
            return false
        }

        // Do not auto-space between consecutive sentence terminators: e.g. ?!, !?, .!
        if (isSentenceTerminatorChar(precedingChar) && isSentenceTerminator(puncText)) {
            return false
        }

        // Do not auto-space for path/url-like prefixes: e.g. ./, ../
        if (precedingChar == '/' || precedingChar == '\\' || precedingChar == '~') {
            return false
        }

        return true
    }

    private fun commitCharacterWithPunctuationHandling(ic: InputConnection, text: String) {
        val inUrlOrCode = isUrlOrCodeField()
        val isRemovePunc = isRemoveSpaceBeforePunctuation(text)
        val isAutoPunc = isAutoSpacePunctuation(text)

        val canRemoveSpace = preferencesState.removeSpaceBeforePunctuation && !inUrlOrCode && isRemovePunc
        val canAutoSpace = preferencesState.autoSpaceAfterPunctuation && !inUrlOrCode && isAutoPunc

        val textBefore = ic.getTextBeforeCursor(4, 0)?.toString() ?: ""

        if (textBefore.endsWith(" ")) {
            val nonSpacePrefix = textBefore.trimEnd(' ')
            val precedingChar = nonSpacePrefix.lastOrNull()

            if (canRemoveSpace && precedingChar != null && !precedingChar.isWhitespace()) {
                // Remove the space before the punctuation
                ic.deleteSurroundingText(1, 0)

                if (canAutoSpace && shouldAutoSpaceAfterPunctuation(precedingChar, text)) {
                    ic.commitText(text + " ", 1)
                    val now = SystemClock.uptimeMillis()
                    lastSpaceTapTime = now
                    lastAutoSpacePunctuationTime = now
                    if (isSentenceTerminator(text) && shouldAutoCapitalize() && shiftState == ShiftState.UNSHIFTED) {
                        shiftState = ShiftState.ONE_SHOT
                    }
                    return
                } else {
                    ic.commitText(text, 1)
                    lastAutoSpacePunctuationTime = 0L
                    return
                }
            } else if (canAutoSpace && precedingChar != null && shouldAutoSpaceAfterPunctuation(precedingChar, text)) {
                // Space before punctuation was not removed, but auto-space is enabled
                ic.commitText(text + " ", 1)
                val now = SystemClock.uptimeMillis()
                lastSpaceTapTime = now
                lastAutoSpacePunctuationTime = now
                if (isSentenceTerminator(text) && shouldAutoCapitalize() && shiftState == ShiftState.UNSHIFTED) {
                    shiftState = ShiftState.ONE_SHOT
                }
                return
            }
        } else {
            // Not preceded by a space (e.g. typing a punctuation immediately after a word)
            if (canAutoSpace && textBefore.isNotEmpty()) {
                val precedingChar = textBefore.last()
                if (shouldAutoSpaceAfterPunctuation(precedingChar, text)) {
                    ic.commitText(text + " ", 1)
                    val now = SystemClock.uptimeMillis()
                    lastSpaceTapTime = now
                    lastAutoSpacePunctuationTime = now
                    if (isSentenceTerminator(text) && shouldAutoCapitalize() && shiftState == ShiftState.UNSHIFTED) {
                        shiftState = ShiftState.ONE_SHOT
                    }
                    return
                }
            }
        }

        lastAutoSpacePunctuationTime = 0L
        ic.commitText(text, 1)
    }

    private fun isBengaliChar(text: String): Boolean {
        if (text.isEmpty()) return false
        return text.all { c ->
            (c in '\u0980'..'\u09FF') || c == '\u200B' || c == '\u200C' || c == '\u200D'
        }
    }

    private fun isWordSeparator(c: Char): Boolean {
        return c.isWhitespace() || c == '.' || c == ',' || c == '!' || c == '?' ||
               c == ':' || c == ';' || c == '।' || c == '॥' || c == '/' || c == '\\' ||
               c == '(' || c == ')' || c == '[' || c == ']' || c == '{' || c == '}' ||
               c == '<' || c == '>' || c == '"' || c == '\'' || c == '`' || c == '@' ||
               c == '#' || c == '$' || c == '%' || c == '^' || c == '&' || c == '*' ||
               c == '-' || c == '+' || c == '=' || c == '|' || c == '~'
    }

    private fun handleTextAction(action: TextAction, isSelecting: Boolean): Boolean {
        val ic = currentInputConnection ?: return false
        finishComposing()

        val moved: Boolean = when (action) {
            TextAction.MOVE_LEFT -> {
                val extracted = ic.getExtractedText(ExtractedTextRequest(), 0)
                if (extracted != null && extracted.text != null) {
                    val textLen = extracted.text.length
                    val start = extracted.selectionStart.coerceIn(0, textLen)
                    val end = extracted.selectionEnd.coerceIn(0, textLen)
                    if (isSelecting) {
                        val anchor = selectionAnchor ?: start
                        val cur = selectionCursor ?: if (start == end) start else if (anchor == start) end else start
                        if (cur <= 0) {
                            false
                        } else {
                            val newCur = (cur - 1).coerceIn(0, textLen)
                            selectionAnchor = anchor
                            selectionCursor = newCur
                            ic.setSelection(minOf(anchor, newCur), maxOf(anchor, newCur))
                            true
                        }
                    } else {
                        selectionAnchor = null
                        selectionCursor = null
                        if (start <= 0 && end <= 0) {
                            false
                        } else {
                            val newPos = if (start != end) start else (start - 1).coerceIn(0, textLen)
                            ic.setSelection(newPos, newPos)
                            true
                        }
                    }
                } else {
                    val before = ic.getTextBeforeCursor(1, 0)
                    if (before.isNullOrEmpty()) {
                        false
                    } else {
                        sendNavKeyEvent(ic, KeyEvent.KEYCODE_DPAD_LEFT, isSelecting)
                        true
                    }
                }
            }
            TextAction.MOVE_RIGHT -> {
                val extracted = ic.getExtractedText(ExtractedTextRequest(), 0)
                if (extracted != null && extracted.text != null) {
                    val textLen = extracted.text.length
                    val start = extracted.selectionStart.coerceIn(0, textLen)
                    val end = extracted.selectionEnd.coerceIn(0, textLen)
                    if (isSelecting) {
                        val anchor = selectionAnchor ?: start
                        val cur = selectionCursor ?: if (start == end) end else if (anchor == end) start else end
                        if (cur >= textLen) {
                            false
                        } else {
                            val newCur = (cur + 1).coerceIn(0, textLen)
                            selectionAnchor = anchor
                            selectionCursor = newCur
                            ic.setSelection(minOf(anchor, newCur), maxOf(anchor, newCur))
                            true
                        }
                    } else {
                        selectionAnchor = null
                        selectionCursor = null
                        if (start >= textLen && end >= textLen) {
                            false
                        } else {
                            val newPos = if (start != end) end else (end + 1).coerceIn(0, textLen)
                            ic.setSelection(newPos, newPos)
                            true
                        }
                    }
                } else {
                    val after = ic.getTextAfterCursor(1, 0)
                    if (after.isNullOrEmpty()) {
                        false
                    } else {
                        sendNavKeyEvent(ic, KeyEvent.KEYCODE_DPAD_RIGHT, isSelecting)
                        true
                    }
                }
            }
            TextAction.MOVE_UP -> {
                selectionAnchor = null
                selectionCursor = null
                val extracted = ic.getExtractedText(ExtractedTextRequest(), 0)
                if (extracted != null && extracted.text != null) {
                    val text = extracted.text.toString()
                    val start = extracted.selectionStart.coerceIn(0, text.length)
                    val firstLine = !text.substring(0, start).contains('\n')
                    if (firstLine && start <= 0) {
                        false
                    } else if (firstLine && !isSelecting) {
                        ic.setSelection(0, 0)
                        true
                    } else {
                        sendNavKeyEvent(ic, KeyEvent.KEYCODE_DPAD_UP, isSelecting)
                        true
                    }
                } else {
                    val before = ic.getTextBeforeCursor(200, 0)?.toString() ?: ""
                    if (!before.contains('\n') && before.isEmpty()) {
                        false
                    } else {
                        sendNavKeyEvent(ic, KeyEvent.KEYCODE_DPAD_UP, isSelecting)
                        true
                    }
                }
            }
            TextAction.MOVE_DOWN -> {
                selectionAnchor = null
                selectionCursor = null
                val extracted = ic.getExtractedText(ExtractedTextRequest(), 0)
                if (extracted != null && extracted.text != null) {
                    val text = extracted.text.toString()
                    val end = extracted.selectionEnd.coerceIn(0, text.length)
                    val lastLine = !text.substring(end).contains('\n')
                    if (lastLine && end >= text.length) {
                        false
                    } else if (lastLine && !isSelecting) {
                        ic.setSelection(text.length, text.length)
                        true
                    } else {
                        sendNavKeyEvent(ic, KeyEvent.KEYCODE_DPAD_DOWN, isSelecting)
                        true
                    }
                } else {
                    val after = ic.getTextAfterCursor(200, 0)?.toString() ?: ""
                    if (!after.contains('\n') && after.isEmpty()) {
                        false
                    } else {
                        sendNavKeyEvent(ic, KeyEvent.KEYCODE_DPAD_DOWN, isSelecting)
                        true
                    }
                }
            }
            TextAction.WORD_LEFT -> {
                val extracted = ic.getExtractedText(ExtractedTextRequest(), 0)
                if (extracted != null && extracted.text != null) {
                    val text = extracted.text.toString()
                    val textLen = text.length
                    val start = extracted.selectionStart.coerceIn(0, textLen)
                    val end = extracted.selectionEnd.coerceIn(0, textLen)
                    val cur = if (isSelecting) (selectionCursor ?: start) else start
                    if (cur <= 0) {
                        false
                    } else {
                        var idx = cur - 1
                        while (idx > 0 && text[idx].isWhitespace()) {
                            idx--
                        }
                        while (idx > 0 && !text[idx - 1].isWhitespace() && !isWordSeparator(text[idx - 1])) {
                            idx--
                        }
                        val target = idx.coerceIn(0, textLen)
                        if (target == cur) {
                            false
                        } else {
                            if (isSelecting) {
                                val anchor = selectionAnchor ?: start
                                selectionAnchor = anchor
                                selectionCursor = target
                                ic.setSelection(minOf(anchor, target), maxOf(anchor, target))
                            } else {
                                selectionAnchor = null
                                selectionCursor = null
                                ic.setSelection(target, target)
                            }
                            true
                        }
                    }
                } else {
                    val before = ic.getTextBeforeCursor(1, 0)
                    if (before.isNullOrEmpty()) {
                        false
                    } else {
                        sendNavKeyEvent(ic, KeyEvent.KEYCODE_DPAD_LEFT, isSelecting, isWord = true)
                        true
                    }
                }
            }
            TextAction.WORD_RIGHT -> {
                val extracted = ic.getExtractedText(ExtractedTextRequest(), 0)
                if (extracted != null && extracted.text != null) {
                    val text = extracted.text.toString()
                    val textLen = text.length
                    val start = extracted.selectionStart.coerceIn(0, textLen)
                    val end = extracted.selectionEnd.coerceIn(0, textLen)
                    val cur = if (isSelecting) (selectionCursor ?: end) else end
                    if (cur >= textLen) {
                        false
                    } else {
                        var idx = cur
                        while (idx < textLen && text[idx].isWhitespace()) {
                            idx++
                        }
                        while (idx < textLen && !text[idx].isWhitespace() && !isWordSeparator(text[idx])) {
                            idx++
                        }
                        val target = idx.coerceIn(0, textLen)
                        if (target == cur) {
                            false
                        } else {
                            if (isSelecting) {
                                val anchor = selectionAnchor ?: start
                                selectionAnchor = anchor
                                selectionCursor = target
                                ic.setSelection(minOf(anchor, target), maxOf(anchor, target))
                            } else {
                                selectionAnchor = null
                                selectionCursor = null
                                ic.setSelection(target, target)
                            }
                            true
                        }
                    }
                } else {
                    val after = ic.getTextAfterCursor(1, 0)
                    if (after.isNullOrEmpty()) {
                        false
                    } else {
                        sendNavKeyEvent(ic, KeyEvent.KEYCODE_DPAD_RIGHT, isSelecting, isWord = true)
                        true
                    }
                }
            }
            TextAction.LINE_START -> {
                val extracted = ic.getExtractedText(ExtractedTextRequest(), 0)
                if (extracted != null && extracted.text != null) {
                    val text = extracted.text.toString()
                    val textLen = text.length
                    val start = extracted.selectionStart.coerceIn(0, textLen)
                    val end = extracted.selectionEnd.coerceIn(0, textLen)
                    val cur = if (isSelecting) (selectionCursor ?: start) else start
                    if (cur <= 0) {
                        false
                    } else {
                        val prevNewline = text.lastIndexOf('\n', (cur - 1).coerceAtLeast(0))
                        val target = if (prevNewline == -1) 0 else (prevNewline + 1).coerceIn(0, textLen)
                        if (target == cur) {
                            false
                        } else {
                            if (isSelecting) {
                                val anchor = selectionAnchor ?: start
                                selectionAnchor = anchor
                                selectionCursor = target
                                ic.setSelection(minOf(anchor, target), maxOf(anchor, target))
                            } else {
                                selectionAnchor = null
                                selectionCursor = null
                                ic.setSelection(target, target)
                            }
                            true
                        }
                    }
                } else {
                    val before = ic.getTextBeforeCursor(1, 0)
                    if (before.isNullOrEmpty()) {
                        false
                    } else {
                        sendNavKeyEvent(ic, KeyEvent.KEYCODE_MOVE_HOME, isSelecting)
                        true
                    }
                }
            }
            TextAction.LINE_END -> {
                val extracted = ic.getExtractedText(ExtractedTextRequest(), 0)
                if (extracted != null && extracted.text != null) {
                    val text = extracted.text.toString()
                    val textLen = text.length
                    val start = extracted.selectionStart.coerceIn(0, textLen)
                    val end = extracted.selectionEnd.coerceIn(0, textLen)
                    val cur = if (isSelecting) (selectionCursor ?: end) else end
                    if (cur >= textLen) {
                        false
                    } else {
                        val nextNewline = text.indexOf('\n', cur)
                        val target = if (nextNewline == -1) textLen else nextNewline.coerceIn(0, textLen)
                        if (target == cur) {
                            false
                        } else {
                            if (isSelecting) {
                                val anchor = selectionAnchor ?: start
                                selectionAnchor = anchor
                                selectionCursor = target
                                ic.setSelection(minOf(anchor, target), maxOf(anchor, target))
                            } else {
                                selectionAnchor = null
                                selectionCursor = null
                                ic.setSelection(target, target)
                            }
                            true
                        }
                    }
                } else {
                    val after = ic.getTextAfterCursor(1, 0)
                    if (after.isNullOrEmpty()) {
                        false
                    } else {
                        sendNavKeyEvent(ic, KeyEvent.KEYCODE_MOVE_END, isSelecting)
                        true
                    }
                }
            }
            TextAction.SELECT -> {
                val extracted = ic.getExtractedText(ExtractedTextRequest(), 0)
                if (isSelecting) {
                    // Only enable select mode without selecting any text to the left.
                    if (extracted != null && extracted.text != null) {
                        val textLen = extracted.text.length
                        val start = extracted.selectionStart.coerceIn(0, textLen)
                        val end = extracted.selectionEnd.coerceIn(0, textLen)
                        selectionAnchor = start
                        selectionCursor = end
                    } else {
                        selectionAnchor = null
                        selectionCursor = null
                    }
                } else {
                    selectionAnchor = null
                    selectionCursor = null
                    if (extracted != null && extracted.text != null) {
                        val end = extracted.selectionEnd.coerceIn(0, extracted.text.length)
                        ic.setSelection(end, end)
                    } else {
                        sendNavKeyEvent(ic, KeyEvent.KEYCODE_DPAD_RIGHT, false)
                    }
                }
                true
            }
            TextAction.SELECT_ALL -> {
                selectionAnchor = null
                selectionCursor = null
                ic.performContextMenuAction(android.R.id.selectAll)
                true
            }
            TextAction.CUT -> {
                selectionAnchor = null
                selectionCursor = null
                finishComposing()
                val selected = ic.getSelectedText(0)?.toString()
                if (!selected.isNullOrEmpty()) {
                    clipboardManager.addClip(selected)
                    try {
                        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                        cm?.setPrimaryClip(ClipData.newPlainText("text", selected))
                    } catch (_: Exception) {}
                    ic.performContextMenuAction(android.R.id.cut)
                } else {
                    // If not selected explicitly, select all text then cut
                    val before = ic.getTextBeforeCursor(100000, 0)?.toString() ?: ""
                    val after = ic.getTextAfterCursor(100000, 0)?.toString() ?: ""
                    val fullText = before + after
                    if (fullText.isNotEmpty()) {
                        clipboardManager.addClip(fullText)
                        try {
                            val cm = getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                            cm?.setPrimaryClip(ClipData.newPlainText("text", fullText))
                        } catch (_: Exception) {}
                        ic.performContextMenuAction(android.R.id.selectAll)
                        ic.performContextMenuAction(android.R.id.cut)
                    }
                }
                serviceScope.launch {
                    delay(80)
                    clipboardManager.syncFromSystemClipboard(this@KiebordService, forceNotify = true)
                }
                true
            }
            TextAction.COPY -> {
                selectionAnchor = null
                selectionCursor = null
                finishComposing()
                val selected = ic.getSelectedText(0)?.toString()
                if (!selected.isNullOrEmpty()) {
                    clipboardManager.addClip(selected)
                    try {
                        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                        cm?.setPrimaryClip(ClipData.newPlainText("text", selected))
                    } catch (_: Exception) {}
                    ic.performContextMenuAction(android.R.id.copy)
                } else {
                    // If not selected explicitly, select all text then copy
                    val before = ic.getTextBeforeCursor(100000, 0)?.toString() ?: ""
                    val after = ic.getTextAfterCursor(100000, 0)?.toString() ?: ""
                    val fullText = before + after
                    if (fullText.isNotEmpty()) {
                        val originalCursor = before.length
                        clipboardManager.addClip(fullText)
                        try {
                            val cm = getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                            cm?.setPrimaryClip(ClipData.newPlainText("text", fullText))
                        } catch (_: Exception) {}
                        ic.performContextMenuAction(android.R.id.selectAll)
                        ic.performContextMenuAction(android.R.id.copy)
                        // Unselect text after copying by restoring cursor position
                        ic.setSelection(originalCursor, originalCursor)
                        serviceScope.launch {
                            delay(40)
                            ic.setSelection(originalCursor, originalCursor)
                        }
                    }
                }
                serviceScope.launch {
                    delay(80)
                    clipboardManager.syncFromSystemClipboard(this@KiebordService, forceNotify = true)
                }
                true
            }
            TextAction.PASTE -> {
                selectionAnchor = null
                selectionCursor = null
                finishComposing()
                lastGestureCommittedWord = ""
                val sysClip = (getSystemService(Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager)
                    ?.primaryClip?.getItemAt(0)?.coerceToText(this)?.toString()
                val textToPaste = sysClip ?: clipboardManager.clips.firstOrNull()?.text
                if (!textToPaste.isNullOrEmpty()) {
                    ic.commitText(textToPaste, 1)
                    finishComposing()
                } else {
                    ic.performContextMenuAction(android.R.id.paste)
                    finishComposing()
                }
                updateSuggestions()
                true
            }
            TextAction.UNDO -> {
                selectionAnchor = null
                selectionCursor = null
                ic.performContextMenuAction(android.R.id.undo)
                true
            }
            TextAction.REDO -> {
                selectionAnchor = null
                selectionCursor = null
                ic.performContextMenuAction(android.R.id.redo)
                true
            }
            TextAction.DELETE_BACKWARD -> {
                selectionAnchor = null
                selectionCursor = null
                val selected = ic.getSelectedText(0)
                if (!selected.isNullOrEmpty()) {
                    ic.commitText("", 1)
                } else {
                    ic.deleteSurroundingText(1, 0)
                }
                true
            }
            TextAction.DELETE_FORWARD -> {
                selectionAnchor = null
                selectionCursor = null
                val selected = ic.getSelectedText(0)
                if (!selected.isNullOrEmpty()) {
                    ic.commitText("", 1)
                } else {
                    ic.deleteSurroundingText(0, 1)
                }
                true
            }
            TextAction.ENTER -> {
                selectionAnchor = null
                selectionCursor = null
                val visual = ActionKeyResolver.resolve(currentInputEditorInfo, shiftState)
                if (visual.isAction) {
                    val actionIdToPerform = if (currentInputEditorInfo?.actionId != 0 && currentInputEditorInfo?.actionId != null) {
                        currentInputEditorInfo!!.actionId
                    } else {
                        visual.actionId
                    }
                    val handled = ic.performEditorAction(actionIdToPerform)
                    if (!handled && actionIdToPerform == EditorInfo.IME_ACTION_UNSPECIFIED) {
                        sendKeyChar('\n')
                    }
                } else {
                    sendKeyChar('\n')
                    if (shouldAutoCapitalize() && shiftState == ShiftState.UNSHIFTED) {
                        shiftState = ShiftState.ONE_SHOT
                    }
                }
                true
            }
        }
        updateSuggestions()
        return moved
    }

    private fun isPrecedingWordDelimiter(c: Char): Boolean {
        return c.isWhitespace() || when (c) {
            ',', '.', ';', ':', '!', '?', '"', '\'', '(', ')', '[', ']', '{', '}' -> true
            else -> false
        }
    }

    private fun lastToken(s: String): String {
        var end = s.length
        while (end > 0 && isPrecedingWordDelimiter(s[end - 1])) {
            end--
        }
        if (end == 0) return ""
        var start = end
        while (start > 0 && !isPrecedingWordDelimiter(s[start - 1])) {
            start--
        }
        return s.substring(start, end)
    }

    private fun secondToLastToken(s: String): String {
        var end = s.length
        while (end > 0 && isPrecedingWordDelimiter(s[end - 1])) {
            end--
        }
        while (end > 0 && !isPrecedingWordDelimiter(s[end - 1])) {
            end--
        }
        while (end > 0 && isPrecedingWordDelimiter(s[end - 1])) {
            end--
        }
        if (end == 0) return ""
        var start = end
        while (start > 0 && !isPrecedingWordDelimiter(s[start - 1])) {
            start--
        }
        return s.substring(start, end)
    }

    private fun getPrecedingWord(excludeComposing: Boolean = false): String {
        val ic = currentInputConnection ?: return ""
        val text = ic.getTextBeforeCursor(100, 0)?.toString() ?: return ""
        if (text.isEmpty()) return ""

        val translitMgr = getTransliterationManager()
        val isCurrentlyComposing = translitMgr.isComposing || composingBuffer.isNotEmpty()

        val textToProcess = if (excludeComposing && isCurrentlyComposing) {
            val composing = when {
                translitMgr.isComposing -> translitMgr.currentTransliteration
                composingBuffer.isNotEmpty() -> composingBuffer.toString()
                else -> ""
            }
            val raw = if (translitMgr.isComposing) translitMgr.currentRawInput else ""

            when {
                composing.isNotEmpty() && text.endsWith(composing) -> text.dropLast(composing.length)
                composing.isNotEmpty() && text.trimEnd().endsWith(composing) -> text.trimEnd().dropLast(composing.length)
                raw.isNotEmpty() && text.endsWith(raw) -> text.dropLast(raw.length)
                raw.isNotEmpty() && text.trimEnd().endsWith(raw) -> text.trimEnd().dropLast(raw.length)
                else -> return secondToLastToken(text)
            }
        } else {
            text
        }

        return lastToken(textToProcess)
    }

    private fun handleCandidateSelect(candidate: SuggestionCandidate) {
        performKeyFeedback()
        if (PredefinedLayouts.isBornoEasy(activeLayout.id)) {
            isBornoEasyKarMode = false
            bornoEasyTypedCluster = ""
        }
        val ic = currentInputConnection ?: return
        val candidateText = candidate.text.trim()
        val shouldAutoSpace = preferencesState.autoSpaceAfterSuggestion
        val textToCommit = if (shouldAutoSpace) "$candidateText " else candidateText

        if (isGestureAlternativeActive && lastGestureCommittedWord.isNotEmpty()) {
            val prevWord = getPrecedingWord(excludeComposing = true)
            if (preferencesState.learnedWordsEnabled && candidateText.length >= UserDictionaryRepository.MIN_LEARN_LENGTH) {
                repeat(3) {
                    userDictionaryRepo.recordWord(candidateText, prevWord, isGesture = true)
                }
            }
            ic.deleteSurroundingText(lastGestureCommittedWord.length, 0)
            lastGestureCommittedWord = ""
            isGestureAlternativeActive = false
            ic.commitText(textToCommit, 1)
            if (shouldAutoSpace) lastSpaceTapTime = SystemClock.uptimeMillis()
            updateSuggestions()
            return
        }

        if (preferencesState.learnedWordsEnabled && candidateText.length >= UserDictionaryRepository.MIN_LEARN_LENGTH) {
            val prevWord = getPrecedingWord(excludeComposing = true)
            userDictionaryRepo.recordWord(candidateText, prevWord)
        }
        val translitMgr = getTransliterationManager()
        if (translitMgr.isComposing) {
            translitMgr.commitCustom(textToCommit)
        } else if (composingBuffer.isNotEmpty()) {
            ic.commitText(textToCommit, 1)
            composingBuffer.clear()
        } else {
            ic.commitText(textToCommit, 1)
        }
        if (!shouldAutoCapitalize() && shiftState == ShiftState.ONE_SHOT) {
            shiftState = ShiftState.UNSHIFTED
        }
        if (shouldAutoSpace) {
            lastSpaceTapTime = SystemClock.uptimeMillis()
        }
        updateSuggestions()
    }

    private fun isBengaliChar(c: Char): Boolean = c in '\u0980'..'\u09FF'
    private fun isBengaliOrModifier(text: String): Boolean =
        text.isNotEmpty() && text.all { c -> isBengaliChar(c) || c == '\u200C' || c == '\u200D' }
    private fun isLatinChar(c: Char): Boolean = (c in 'a'..'z') || (c in 'A'..'Z')

    private fun containsBengaliText(text: String): Boolean = text.any { isBengaliChar(it) }
    private fun containsLatinText(text: String): Boolean = text.any { isLatinChar(it) }

    private fun isBanglaLayout(layout: io.kiebord.common.model.KeyboardLayoutDef = activeLayout): Boolean {
        return layout.language == "bn" || layout.id.startsWith("bn")
    }

    private fun isEnglishLayout(layout: io.kiebord.common.model.KeyboardLayoutDef = activeLayout): Boolean {
        return layout.language == "en" || layout.id.startsWith("en") || layout.gestureMode.equals("qwerty", ignoreCase = true)
    }

    private fun isQwertyBasedLayout(layoutId: String): Boolean {
        val layout = if (layoutId == activeLayout.id) activeLayout else layoutRepo.getCachedLayout(layoutId)
        return layout?.isQwertyBased()
            ?: (layoutId == "en_qwerty" || layoutId == "bn_borno" || layoutId == "bn_avro")
    }

    private fun filterCandidatesForLayout(candidates: List<SuggestionCandidate>): List<SuggestionCandidate> {
        if (candidates.isEmpty()) return emptyList()
        val isBangla = isBanglaLayout()
        val isEnglish = isEnglishLayout()
        return candidates.filter { cand ->
            val text = cand.text
            if (cand.rawInput != null && cand.text == cand.rawInput) {
                true
            } else {
                when {
                    isBangla -> !containsLatinText(text)
                    isEnglish -> !containsBengaliText(text)
                    else -> true
                }
            }
        }
    }

    private fun filterAndMarkCandidates(candidates: List<SuggestionCandidate>): List<SuggestionCandidate> {
        val filtered = filterCandidatesForLayout(candidates)
        if (filtered.isEmpty()) return emptyList()
        val marked = markLearnedCandidates(filtered)
        val hasExplicitPrimary = marked.any { it.isPrimary }
        return if (hasExplicitPrimary) {
            marked
        } else {
            marked.mapIndexed { idx, cand ->
                if (cand.isPrimary != (idx == 0)) cand.copy(isPrimary = (idx == 0)) else cand
            }
        }
    }

    private var gestureCommitJob: kotlinx.coroutines.Job? = null

    private fun handleGestureCommit(
        trail: List<androidx.compose.ui.geometry.Offset>,
        centroids: List<KeyCentroid>,
        keyWidthPx: Float
    ) {
        if (!preferencesState.gestureTypingEnabled || !activeLayout.isQwertyBased()) return
        if (trail.size < 2 || centroids.isEmpty()) return

        gestureCommitJob?.cancel()
        gestureCommitJob = serviceScope.launch(kotlinx.coroutines.Dispatchers.Default) {
            val lang = if (isBanglaLayout()) activeLayout.id else "en"
            val learnedWords = if (preferencesState.learnedWordsEnabled) {
                val all = userDictionaryRepo.getLearnedGestureWords(20)
                if (isBanglaLayout()) {
                    all.filter { !containsLatinText(it) }
                } else {
                    all.filter { !containsBengaliText(it) }
                }
            } else emptyList()

            val rawCandidates = gestureDecoder.decodeGesture(trail, centroids, keyWidthPx, lang, learnedWords)
            val candidates = filterCandidatesForLayout(rawCandidates)

            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                if (candidates.isNotEmpty()) {
                    val primary = candidates.first()
                    val primaryText = primary.text.trim()
                    val shouldAutoSpace = preferencesState.autoSpaceAfterSuggestion
                    val textToCommit = if (shouldAutoSpace) "$primaryText " else primaryText
                    val ic = currentInputConnection
                    if (preferencesState.learnedWordsEnabled && primaryText.length >= UserDictionaryRepository.MIN_LEARN_LENGTH) {
                        val prevWord = getPrecedingWord()
                        userDictionaryRepo.recordWord(primaryText, prevWord, isGesture = false)
                    }
                    finishComposing()
                    lastGestureCommittedWord = textToCommit
                    isGestureAlternativeActive = true
                    ic?.commitText(textToCommit, 1)
                    if (shouldAutoSpace) {
                        lastSpaceTapTime = SystemClock.uptimeMillis()
                    }

                    activeSuggestions = filterAndMarkCandidates(candidates.take(12))
                }
            }
        }
    }

    private fun predictLiveGestureCandidate(
        trail: List<androidx.compose.ui.geometry.Offset>,
        centroids: List<KeyCentroid>,
        keyWidthPx: Float
    ): String? {
        if (!preferencesState.gestureTypingEnabled || !activeLayout.isQwertyBased()) return null
        if (trail.size < 4 || centroids.isEmpty()) return null
        val lang = if (isBanglaLayout()) activeLayout.id else "en"
        val rawCandidates = gestureDecoder.decodeGesture(trail, centroids, keyWidthPx, lang, emptyList())
        val candidates = filterCandidatesForLayout(rawCandidates)
        return candidates.firstOrNull()?.text?.trim()
    }

    private fun markLearnedCandidates(candidates: List<SuggestionCandidate>): List<SuggestionCandidate> {
        if (!preferencesState.learnedWordsEnabled) return candidates
        return candidates.map { cand ->
            if (cand.isLearned) cand else cand.copy(isLearned = userDictionaryRepo.isLearned(cand.text))
        }
    }

    private fun hasDoubleOrGreaterSpacing(): Boolean {
        val ic = currentInputConnection ?: return false
        val textBefore = ic.getTextBeforeCursor(40, 0)?.toString() ?: return false
        val trailingWhitespace = textBefore.takeLastWhile { it.isWhitespace() }
        if (trailingWhitespace.length >= 2 || trailingWhitespace.contains('\n')) return true
        val nonWhitespace = textBefore.dropLast(trailingWhitespace.length)
        if (nonWhitespace.isNotEmpty() && isSentenceTerminatorChar(nonWhitespace.last())) {
            return true
        }
        return false
    }

    private fun updateSuggestions() {
        if (!preferencesState.suggestionsEnabled || !NativeEngine.isAvailable() || isSymbolsMode || PredefinedLayouts.isNumberLayout(activeLayout.id)) {
            activeSuggestions = emptyList()
            return
        }

        if (isGestureAlternativeActive && lastGestureCommittedWord.isNotEmpty()) {
            return
        }

        if (isTransliterationLayout()) {
            val translitMgr = getTransliterationManager()
            if (translitMgr.isComposing) {
                val raw = translitMgr.currentRawInput
                val translit = translitMgr.currentTransliteration
                val primaryWord = if (translit.isNotEmpty() && !containsLatinText(translit)) {
                    translit
                } else ""

                val list = mutableListOf<SuggestionCandidate>()

                // Khipro layouts: roman-first deterministic candidates.
                if (activeLayout.id in io.kiebord.feature.ime.khipro.KHIPRO_LAYOUT_IDS) {
                    val completions = if (primaryWord.isNotEmpty()) {
                        NativeEngine.querySuggestions(primaryWord, "bn_exact", 8)
                            .filter { it != primaryWord && !containsLatinText(it) }
                    } else emptyList()
                    val learned = if (preferencesState.learnedWordsEnabled && primaryWord.isNotEmpty()) {
                        userDictionaryRepo.getCompletions(primaryWord, 5) { w -> !containsLatinText(w) }
                    } else emptyList()
                    val candidates = io.kiebord.feature.ime.khipro.buildKhiproCandidates(
                        raw, primaryWord, completions, learned,
                    )
                    activeSuggestions = filterAndMarkCandidates(
                        candidates.map { c ->
                            SuggestionCandidate(text = c, isPrimary = c == primaryWord, rawInput = raw)
                        },
                    )
                    return
                }

                // 1. Raw typed English word as first candidate if toggle is enabled
                if (preferencesState.bengaliShowRawInputCandidate && raw.isNotBlank()) {
                    list.add(SuggestionCandidate(text = raw, isPrimary = false, rawInput = raw))
                }

                // 2. Primary transliterated word (strictly non-Latin/Bengali)
                if (primaryWord.isNotEmpty()) {
                    list.add(SuggestionCandidate(text = primaryWord, isPrimary = true, rawInput = raw))
                }

                // 2b. If phonetic auto-correct modified the word, offer literal uncorrected transliteration as alternative candidate
                if (preferencesState.phoneticAutoCorrect && (activeLayout.id == KiebordPreferences.LAYOUT_BN_BORNO || activeLayout.id == KiebordPreferences.LAYOUT_BN_AVRO)) {
                    val literalTranslit = if (NativeEngine.isAvailable()) {
                        NativeEngine.transliterate(activeLayout.id, raw)
                    } else raw
                    if (literalTranslit.isNotEmpty() && literalTranslit != primaryWord && !containsLatinText(literalTranslit)) {
                        list.add(SuggestionCandidate(text = literalTranslit, isPrimary = false, rawInput = raw))
                    }
                }

                val phoneticAlts = if (raw.isNotBlank() && activeLayout.id == KiebordPreferences.LAYOUT_BN_BORNO) {
                    NativeEngine.queryTransliterationAlternatives(activeLayout.id, raw)
                        .filter { it.isNotEmpty() && it != primaryWord && !containsLatinText(it) }
                } else emptyList()

                // 3. User learned words matching primary transliteration prefix or phonetic alternatives
                // Prioritize personalized learned vocabulary immediately after primary transliteration (like in English)
                if (preferencesState.learnedWordsEnabled) {
                    val learnedCandidates = mutableListOf<String>()

                    // Match completions for primary transliteration prefix (e.g. "আম" -> "আমাদের", "আমার")
                    if (primaryWord.isNotEmpty()) {
                        learnedCandidates.addAll(
                            userDictionaryRepo.getCompletions(primaryWord, 5) { !containsLatinText(it) }
                        )
                    }

                    // Check exact learned matches and prefix completions for phonetic alternatives
                    for (alt in phoneticAlts) {
                        if (userDictionaryRepo.isLearned(alt)) {
                            learnedCandidates.add(alt)
                        }
                        learnedCandidates.addAll(
                            userDictionaryRepo.getCompletions(alt, 2) { !containsLatinText(it) }
                        )
                    }

                    for (w in learnedCandidates.distinct()) {
                        if (w != primaryWord && list.size < 12 && list.none { it.text == w }) {
                            list.add(SuggestionCandidate(text = w, isPrimary = false))
                        }
                    }
                }

                // 4. Phonetic transliteration alternatives for raw Latin input
                val hasComplexKarLower = raw.contains("ou", ignoreCase = true) ||
                        raw.contains("oi", ignoreCase = true) ||
                        (raw.contains("ri") && !raw.contains("rri"))
                for ((idx, alt) in phoneticAlts.withIndex()) {
                    if (list.size < 12 && list.none { it.text == alt }) {
                        val isLearned = userDictionaryRepo.isLearned(alt)
                        val isDirectComplexKarAlt = hasComplexKarLower && idx == 0
                        val isDictWord = isLearned || isDirectComplexKarAlt || NativeEngine.isDictionaryWord(alt, "bn")
                        if (isDictWord) {
                            list.add(SuggestionCandidate(text = alt, isPrimary = false))
                        }
                    }
                }

                // 5. Dictionary completions from bn.dict matching primary transliteration prefix
                if (primaryWord.isNotEmpty() && list.size < 12) {
                    val dictMatches = if (preferencesState.bengaliSmartPhonetic && activeLayout.id == KiebordPreferences.LAYOUT_BN_BORNO) {
                        NativeEngine.querySuggestions(primaryWord, activeLayout.id, 12)
                    } else {
                        NativeEngine.querySuggestions(primaryWord, "bn_exact", 12)
                    }.filter { it != primaryWord && !containsLatinText(it) }

                    for (match in dictMatches) {
                        if (list.size < 12 && list.none { it.text == match }) {
                            list.add(SuggestionCandidate(text = match, isPrimary = false))
                        }
                    }
                }

                activeSuggestions = filterAndMarkCandidates(list.take(12))
            } else {
                if (!preferencesState.nextWordPrediction || hasDoubleOrGreaterSpacing()) {
                    activeSuggestions = emptyList()
                    return
                }
                val prev = getPrecedingWord()
                val learnedNext = if (preferencesState.learnedWordsEnabled && prev.isNotEmpty()) {
                    userDictionaryRepo.getNextWords(prev, 5) { !containsLatinText(it) }
                } else emptyList()
                val predictions = if (prev.isNotEmpty()) {
                    NativeEngine.queryNextWords(prev, activeLayout.id, 10).filter { !containsLatinText(it) }
                } else emptyList()
                val merged = (learnedNext + predictions).distinct().take(12)
                activeSuggestions = filterAndMarkCandidates(merged.mapIndexed { idx, word ->
                    SuggestionCandidate(text = word, isPrimary = idx == 0)
                })
            }
            return
        }

        val layoutMode = if (isEnglishLayout()) "en_qwerty" else activeLayout.id
        when (layoutMode) {
            "bn_probhat", "bn_national", "bn_easy" -> {
                val prefix = BengaliBstiEngine.cleanPending(composingBuffer.toString())
                if (prefix.isNotEmpty()) {
                    val learned = if (preferencesState.learnedWordsEnabled) {
                        userDictionaryRepo.getCompletions(prefix, 4) { !containsLatinText(it) }
                    } else emptyList()
                    val matches = NativeEngine.querySuggestions(prefix, activeLayout.id, 12)
                        .filter { !containsLatinText(it) }
                    val merged = (learned + matches).distinct().take(12)
                    activeSuggestions = filterAndMarkCandidates(if (merged.isNotEmpty()) {
                        merged.mapIndexed { i, w ->
                            SuggestionCandidate(text = w, isPrimary = i == 0)
                        }
                    } else {
                        if (!containsLatinText(prefix)) {
                            listOf(SuggestionCandidate(text = prefix, isPrimary = true))
                        } else emptyList()
                    })
                } else {
                    if (!preferencesState.nextWordPrediction || hasDoubleOrGreaterSpacing()) {
                        activeSuggestions = emptyList()
                        return
                    }
                    val prev = getPrecedingWord()
                    val learnedNext = if (preferencesState.learnedWordsEnabled && prev.isNotEmpty()) {
                        userDictionaryRepo.getNextWords(prev, 5) { !containsLatinText(it) }
                    } else emptyList()
                    val predictions = if (prev.isNotEmpty()) {
                        NativeEngine.queryNextWords(prev, activeLayout.id, 10).filter { !containsLatinText(it) }
                    } else emptyList()
                    val merged = (learnedNext + predictions).distinct().take(12)
                    activeSuggestions = filterAndMarkCandidates(merged.mapIndexed { idx, word ->
                        SuggestionCandidate(text = word, isPrimary = idx == 0)
                    })
                }
            }
            "en_qwerty" -> {
                val prefix = composingBuffer.toString()
                if (prefix.isNotEmpty()) {
                    val learned = if (preferencesState.learnedWordsEnabled) {
                        userDictionaryRepo.getCompletions(prefix, 4) { !containsBengaliText(it) }
                    } else emptyList()
                    val matches = NativeEngine.querySuggestions(prefix, activeLayout.id, 12)
                        .filter { !containsBengaliText(it) }
                    val merged = (learned + matches).distinct().take(12)
                    activeSuggestions = filterAndMarkCandidates(if (merged.isNotEmpty()) {
                        merged.mapIndexed { i, w ->
                            SuggestionCandidate(text = w, isPrimary = i == 0)
                        }
                    } else {
                        if (!containsBengaliText(prefix)) {
                            listOf(SuggestionCandidate(text = prefix, isPrimary = true))
                        } else emptyList()
                    })
                } else {
                    if (!preferencesState.nextWordPrediction || hasDoubleOrGreaterSpacing()) {
                        activeSuggestions = emptyList()
                        return
                    }
                    val prev = getPrecedingWord()
                    val learnedNext = if (preferencesState.learnedWordsEnabled && prev.isNotEmpty()) {
                        userDictionaryRepo.getNextWords(prev, 5) { !containsBengaliText(it) }
                    } else emptyList()
                    val predictions = if (prev.isNotEmpty()) {
                        NativeEngine.queryNextWords(prev, activeLayout.id, 10).filter { !containsBengaliText(it) }
                    } else emptyList()
                    val merged = (learnedNext + predictions).distinct().take(12)
                    activeSuggestions = filterAndMarkCandidates(merged.mapIndexed { idx, word ->
                        SuggestionCandidate(text = word, isPrimary = idx == 0)
                    })
                }
            }
            else -> {
                activeSuggestions = emptyList()
            }
        }
    }

    private fun sendNavKeyEvent(
        ic: InputConnection,
        keyCode: Int,
        isSelecting: Boolean,
        isWord: Boolean = false
    ) {
        val now = SystemClock.uptimeMillis()
        var meta = 0
        if (isSelecting) {
            meta = meta or KeyEvent.META_SHIFT_ON or KeyEvent.META_SHIFT_LEFT_ON
        }
        if (isWord) {
            meta = meta or KeyEvent.META_CTRL_ON or KeyEvent.META_CTRL_LEFT_ON
        }
        ic.sendKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_DOWN, keyCode, 0, meta))
        ic.sendKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_UP, keyCode, 0, meta))
    }
}
