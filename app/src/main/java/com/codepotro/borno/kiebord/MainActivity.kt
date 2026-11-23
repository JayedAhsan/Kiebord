package com.codepotro.borno.kiebord

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.PredictiveBackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.text.TextStyle
import com.codepotro.borno.theme.KeyboardFontFamily
import com.codepotro.borno.theme.KiebordTypography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import com.codepotro.borno.kiebord.ui.onboarding.OnboardingScreen
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.codepotro.borno.kiebord.ui.settings.AboutScreen
import com.codepotro.borno.kiebord.ui.settings.AppearanceThemesScreen
import com.codepotro.borno.kiebord.ui.settings.BackupRestoreScreen
import com.codepotro.borno.kiebord.ui.settings.ClipboardSettingsScreen
import com.codepotro.borno.kiebord.ui.settings.ClipboardManagerScreen
import com.codepotro.borno.kiebord.ui.settings.CorrectionsSuggestionsScreen
import com.codepotro.borno.kiebord.ui.settings.PersonalDictionaryScreen
import com.codepotro.borno.kiebord.ui.settings.DarkSettingsColors
import com.codepotro.borno.kiebord.ui.settings.KeyboardLayoutsScreen
import com.codepotro.borno.kiebord.ui.settings.LanguageExtensionsScreen
import com.codepotro.borno.kiebord.ui.settings.LightSettingsColors
import com.codepotro.borno.kiebord.ui.settings.LocalSettingsColors
import com.codepotro.borno.kiebord.ui.settings.PreferencesScreen
import com.codepotro.borno.kiebord.ui.settings.SettingsMainScreen
import com.codepotro.borno.kiebord.ui.settings.SettingsScreen
import com.codepotro.borno.kiebord.ui.settings.SettingsUnifiedTopBar
import com.codepotro.borno.kiebord.ui.settings.SmartGesturesScreen
import com.codepotro.borno.kiebord.ui.settings.VoiceTypingScreen
import com.codepotro.borno.feature.ime.clipboard.SandboxedClipboardManager
import com.codepotro.borno.data.dictionary.BornoSuggestionChoiceRepository
import com.codepotro.borno.data.dictionary.PersonalDictionaryRepository
import com.codepotro.borno.data.dictionary.UserDictionaryRepository
import com.codepotro.borno.data.layout.LayoutRepository
import com.codepotro.borno.data.prefs.KiebordPreferences
import com.codepotro.borno.data.prefs.KiebordPreferencesRepository
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.lifecycle.lifecycleScope

class MainActivity : ComponentActivity() {

    private var isImeEnabledState by mutableStateOf(false)
    private var isImeSelectedState by mutableStateOf(false)
    private var requestedNavigationTarget by mutableStateOf<String?>(null)
    private var pendingDictPrefillPhrase by mutableStateOf<String?>(null)
    private var pendingDictPrefillLanguage by mutableStateOf<String?>(null)
    private var pendingDictFocusShortcut by mutableStateOf(false)
    private var pendingThemeImportUri by mutableStateOf<android.net.Uri?>(null)
    private var pendingLangPackImportUri by mutableStateOf<android.net.Uri?>(null)
    private var pendingClipboardImportUri by mutableStateOf<android.net.Uri?>(null)
    private var pendingDictionaryImportUri by mutableStateOf<android.net.Uri?>(null)
    private lateinit var preferencesRepo: KiebordPreferencesRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        lifecycleScope.launch(Dispatchers.IO) {
            LayoutRepository.getInstance(applicationContext).preload()
        }
        handleIncomingIntent(intent)
        preferencesRepo = KiebordPreferencesRepository.getInstance(this)
        updateImeStatus()
        if (isImeEnabledState && isImeSelectedState && !preferencesRepo.isSetupCompletedSync()) {
            lifecycleScope.launch {
                preferencesRepo.updateSetupCompleted(true)
            }
        }

        setContent {
            val preferences by preferencesRepo.preferencesFlow.collectAsState(
                initial = preferencesRepo.getInitialPreferences()
            )

            val isDark = isSystemInDarkTheme()
            val settingsColors = if (isDark) DarkSettingsColors else LightSettingsColors

            val view = LocalView.current
            if (!view.isInEditMode) {
                SideEffect {
                    val window = (view.context as? android.app.Activity)?.window
                    if (window != null) {
                        val insetsController = WindowCompat.getInsetsController(window, view)
                        insetsController.isAppearanceLightStatusBars = !isDark
                        insetsController.isAppearanceLightNavigationBars = !isDark
                    }
                }
            }

            CompositionLocalProvider(
                LocalSettingsColors provides settingsColors,
                LocalTextStyle provides TextStyle(fontFamily = KeyboardFontFamily)
            ) {
                MaterialTheme(
                    colorScheme = if (isDark) {
                        darkColorScheme(
                            background = settingsColors.background,
                            surface = settingsColors.cardBackground,
                            primary = settingsColors.accent,
                            onPrimary = Color(0xFF0F141C),
                            onSurface = settingsColors.textPrimary
                        )
                    } else {
                        lightColorScheme(
                            background = settingsColors.background,
                            surface = settingsColors.cardBackground,
                            primary = settingsColors.accent,
                            onPrimary = Color(0xFFFFFFFF),
                            onSurface = settingsColors.textPrimary
                        )
                    },
                    typography = KiebordTypography
                ) {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background
                    ) {
                        LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
                            updateImeStatus()
                        }

                        LaunchedEffect(Unit) {
                            while (true) {
                                updateImeStatus()
                                delay(300L)
                            }
                        }

                        KiebordSettingsApp(
                            preferences = preferences,
                            preferencesRepo = preferencesRepo,
                            isImeEnabled = isImeEnabledState,
                            isImeSelected = isImeSelectedState,
                            navigationTarget = requestedNavigationTarget,
                            onNavigationTargetConsumed = { requestedNavigationTarget = null },
                            pendingThemeImportUri = pendingThemeImportUri,
                            onClearPendingThemeImportUri = { pendingThemeImportUri = null },
                            pendingLangPackImportUri = pendingLangPackImportUri,
                            onClearPendingLangPackImportUri = { pendingLangPackImportUri = null },
                            pendingClipboardImportUri = pendingClipboardImportUri,
                            onClearPendingClipboardImportUri = { pendingClipboardImportUri = null },
                            pendingDictionaryImportUri = pendingDictionaryImportUri,
                            onClearPendingDictionaryImportUri = { pendingDictionaryImportUri = null },
                            pendingDictPrefillPhrase = pendingDictPrefillPhrase,
                            pendingDictPrefillLanguage = pendingDictPrefillLanguage,
                            pendingDictFocusShortcut = pendingDictFocusShortcut,
                            onClearPendingDictPrefill = {
                                pendingDictPrefillPhrase = null
                                pendingDictPrefillLanguage = null
                                pendingDictFocusShortcut = false
                            },
                            onOpenSettings = {
                                startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
                            },
                            onSwitchIme = {
                                val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                                imm.showInputMethodPicker()
                            },
                            onSaveOnboardingLayouts = { enabledLayouts, primaryLayout ->
                                lifecycleScope.launch {
                                    preferencesRepo.updateOnboardingLayouts(enabledLayouts, primaryLayout)
                                }
                            },
                            onFinishOnboarding = { showNumRow, showSecondary, showBorders ->
                                lifecycleScope.launch {
                                    preferencesRepo.completeOnboarding(
                                        showNumberRow = showNumRow,
                                        showSecondaryKeys = showSecondary,
                                        showKeyBorders = showBorders
                                    )
                                }
                            }
                        )
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncomingIntent(intent)
    }

    private fun extractFileName(uri: android.net.Uri): String? {
        if (uri.scheme == "content") {
            try {
                contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val idx = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                        if (idx != -1) {
                            val name = cursor.getString(idx)
                            if (!name.isNullOrBlank()) return name
                        }
                    }
                }
            } catch (_: Exception) {}
        }
        return uri.lastPathSegment?.substringAfterLast('/')
    }

    private fun handleIncomingIntent(intent: Intent?) {
        if (intent == null) return
        var importUri: android.net.Uri? = null
        if (Intent.ACTION_VIEW == intent.action) {
            importUri = intent.data
        } else if (Intent.ACTION_SEND == intent.action && intent.hasExtra(Intent.EXTRA_STREAM)) {
            @Suppress("DEPRECATION")
            importUri = intent.getParcelableExtra(Intent.EXTRA_STREAM)
        }
        if (importUri != null) {
            val fileName = extractFileName(importUri)?.lowercase() ?: ""
            when {
                fileName.endsWith(".bornothemepack") || fileName.endsWith(".bornotheme") -> {
                    pendingThemeImportUri = importUri
                    requestedNavigationTarget = "themes"
                }
                fileName.endsWith(".bornolang") -> {
                    pendingLangPackImportUri = importUri
                    requestedNavigationTarget = "extensions"
                }
                fileName.endsWith(".bornoclip") || fileName.endsWith(".clip") -> {
                    pendingClipboardImportUri = importUri
                    requestedNavigationTarget = "backup_restore"
                }
                fileName.endsWith(".bornopdb") -> {
                    pendingDictionaryImportUri = importUri
                    requestedNavigationTarget = "backup_restore"
                }
                else -> {
                    android.widget.Toast.makeText(this, "Unsupported file format", android.widget.Toast.LENGTH_SHORT).show()
                }
            }
            return
        }
        val target = intent.getStringExtra("navigate_to")
            ?: if (intent.action == "com.codepotro.borno.kiebord.action.THEMES") "themes" else null
        val prefillPhrase = intent.getStringExtra("prefill_phrase")
        if (!prefillPhrase.isNullOrBlank()) {
            pendingDictPrefillPhrase = prefillPhrase
            pendingDictPrefillLanguage = intent.getStringExtra("prefill_language")
            pendingDictFocusShortcut = intent.getBooleanExtra("focus_shortcut", false)
        }
        if (target != null) {
            requestedNavigationTarget = target
        }
    }

    override fun onResume() {
        super.onResume()
        updateImeStatus()
    }

    private fun updateImeStatus() {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        val enabledMethods = imm.enabledInputMethodList
        isImeEnabledState = enabledMethods.any { it.packageName == packageName }

        val defaultIme = Settings.Secure.getString(contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD)
        val defaultPackage = defaultIme?.let { ComponentName.unflattenFromString(it)?.packageName }
        isImeSelectedState = defaultPackage == packageName ||
            (defaultIme != null && (defaultIme == packageName || defaultIme.startsWith("$packageName/")))
    }
}

@Composable
fun KiebordSettingsApp(
    preferences: KiebordPreferences,
    preferencesRepo: KiebordPreferencesRepository,
    isImeEnabled: Boolean,
    isImeSelected: Boolean,
    navigationTarget: String? = null,
    onNavigationTargetConsumed: () -> Unit = {},
    pendingThemeImportUri: android.net.Uri? = null,
    onClearPendingThemeImportUri: () -> Unit = {},
    pendingLangPackImportUri: android.net.Uri? = null,
    onClearPendingLangPackImportUri: () -> Unit = {},
    pendingClipboardImportUri: android.net.Uri? = null,
    onClearPendingClipboardImportUri: () -> Unit = {},
    pendingDictionaryImportUri: android.net.Uri? = null,
    onClearPendingDictionaryImportUri: () -> Unit = {},
    pendingDictPrefillPhrase: String? = null,
    pendingDictPrefillLanguage: String? = null,
    pendingDictFocusShortcut: Boolean = false,
    onClearPendingDictPrefill: () -> Unit = {},
    onOpenSettings: () -> Unit,
    onSwitchIme: () -> Unit,
    onSaveOnboardingLayouts: (enabledLayouts: Set<String>, primaryLayout: String) -> Unit,
    onFinishOnboarding: (showNumberRow: Boolean, showSecondaryKeys: Boolean, showKeyBorders: Boolean) -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val isSetupFinished = preferences.setupCompleted ||
        preferencesRepo.isSetupCompletedSync() ||
        (isImeEnabled && isImeSelected)

    var currentScreen by remember {
        val target = navigationTarget
            ?: (context as? android.app.Activity)?.intent?.getStringExtra("navigate_to")
            ?: if ((context as? android.app.Activity)?.intent?.action == "com.codepotro.borno.kiebord.action.THEMES") "themes" else null
        val initial = when {
            target == "main" -> SettingsScreen.Main
            target == "layouts" -> SettingsScreen.KeyboardLayouts
            target == "themes" || target == "appearance" || pendingThemeImportUri != null -> SettingsScreen.AppearanceThemes
            target == "extensions" || target == "language_extensions" || target == "languages" || pendingLangPackImportUri != null -> SettingsScreen.LanguageExtensions
            target == "backup" || target == "backup_restore" || target == "restore" || pendingClipboardImportUri != null || pendingDictionaryImportUri != null -> SettingsScreen.BackupRestore
            target == "clipboard" -> SettingsScreen.Clipboard
            target == "dictionary" || target == "personal_dictionary" -> SettingsScreen.PersonalDictionary
            target == "preferences" -> SettingsScreen.Preferences
            !isSetupFinished -> SettingsScreen.Onboarding
            else -> SettingsScreen.Main
        }
        mutableStateOf<SettingsScreen>(initial)
    }
    var backStack by remember { mutableStateOf<List<SettingsScreen>>(emptyList()) }

    LaunchedEffect(navigationTarget) {
        if (navigationTarget != null) {
            when (navigationTarget) {
                "main" -> {
                    backStack = emptyList()
                    currentScreen = SettingsScreen.Main
                }
                "themes", "appearance" -> {
                    if (currentScreen != SettingsScreen.AppearanceThemes) {
                        backStack = listOf(SettingsScreen.Main)
                        currentScreen = SettingsScreen.AppearanceThemes
                    }
                }
                "layouts" -> {
                    if (currentScreen != SettingsScreen.KeyboardLayouts) {
                        backStack = listOf(SettingsScreen.Main)
                        currentScreen = SettingsScreen.KeyboardLayouts
                    }
                }
                "preferences" -> {
                    if (currentScreen != SettingsScreen.Preferences) {
                        backStack = listOf(SettingsScreen.Main)
                        currentScreen = SettingsScreen.Preferences
                    }
                }
                "gestures", "smart_gestures" -> {
                    if (currentScreen != SettingsScreen.SmartGestures) {
                        backStack = listOf(SettingsScreen.Main)
                        currentScreen = SettingsScreen.SmartGestures
                    }
                }
                "clipboard" -> {
                    if (currentScreen != SettingsScreen.Clipboard) {
                        backStack = listOf(SettingsScreen.Main)
                        currentScreen = SettingsScreen.Clipboard
                    }
                }
                "clipboard_manager", "manage_clips" -> {
                    if (currentScreen != SettingsScreen.ClipboardManager) {
                        backStack = listOf(SettingsScreen.Main, SettingsScreen.Clipboard)
                        currentScreen = SettingsScreen.ClipboardManager
                    }
                }
                "dictionary", "personal_dictionary" -> {
                    if (currentScreen != SettingsScreen.PersonalDictionary) {
                        backStack = listOf(SettingsScreen.Main)
                        currentScreen = SettingsScreen.PersonalDictionary
                    }
                }
                "extensions", "language_extensions", "languages" -> {
                    if (currentScreen != SettingsScreen.LanguageExtensions) {
                        backStack = listOf(SettingsScreen.Main)
                        currentScreen = SettingsScreen.LanguageExtensions
                    }
                }
                "backup", "backup_restore", "restore" -> {
                    if (currentScreen != SettingsScreen.BackupRestore) {
                        backStack = listOf(SettingsScreen.Main)
                        currentScreen = SettingsScreen.BackupRestore
                    }
                }
            }
            onNavigationTargetConsumed()
        }
    }
    var predictiveProgress by remember { mutableFloatStateOf(0f) }
    var isPredictiveBackActive by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    val navigateTo: (SettingsScreen) -> Unit = { screen ->
        backStack = backStack + currentScreen
        currentScreen = screen
    }

    val navigateBack: () -> Unit = {
        if (backStack.isNotEmpty()) {
            currentScreen = backStack.last()
            backStack = backStack.dropLast(1)
        } else {
            currentScreen = SettingsScreen.Main
        }
    }

    PredictiveBackHandler(enabled = currentScreen != SettingsScreen.Main) { progress ->
        try {
            isPredictiveBackActive = true
            progress.collect { backEvent ->
                predictiveProgress = backEvent.progress
            }
            navigateBack()
        } catch (e: CancellationException) {
            // Cancelled
        } finally {
            predictiveProgress = 0f
            isPredictiveBackActive = false
        }
    }

    val predictiveModifier = if (isPredictiveBackActive) {
        Modifier.graphicsLayer {
            val scale = 1f - (predictiveProgress * 0.08f)
            scaleX = scale
            scaleY = scale
            translationX = predictiveProgress * 48.dp.toPx()
            alpha = 1f - (predictiveProgress * 0.2f)
            shape = RoundedCornerShape((predictiveProgress * 20f).dp)
            clip = true
        }
    } else {
        Modifier
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        // Persistent Top Bar that stays anchored, only transitioning title/back-icon smoothly
        if (currentScreen != SettingsScreen.Onboarding) {
            SettingsUnifiedTopBar(
                screen = currentScreen,
                onBack = navigateBack
            )
        }

        // Inner Content changes smoothly without reloading the entire page
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .then(predictiveModifier)
        ) {
            AnimatedContent(
                targetState = currentScreen,
                transitionSpec = {
                    if (targetState == SettingsScreen.Main) {
                        (slideInHorizontally { -it / 3 } + fadeIn(tween(220))) togetherWith
                                (slideOutHorizontally { it } + fadeOut(tween(180)))
                    } else {
                        (slideInHorizontally { it } + fadeIn(tween(220))) togetherWith
                                (slideOutHorizontally { -it / 3 } + fadeOut(tween(180)))
                    }
                },
                label = "SettingsInnerContentTransition"
            ) { screen ->
                when (screen) {
                    is SettingsScreen.Main -> {
                        SettingsMainScreen(
                            preferences = preferences,
                            isImeEnabled = isImeEnabled,
                            isImeSelected = isImeSelected,
                            onOpenSettings = onOpenSettings,
                            onSwitchIme = onSwitchIme,
                            onNavigate = navigateTo
                        )
                    }

                    is SettingsScreen.AppearanceThemes -> {
                        AppearanceThemesScreen(
                            preferences = preferences,
                            onUpdateTheme = { theme ->
                                scope.launch { preferencesRepo.updateTheme(theme) }
                            },
                            onUpdateHeightScale = { scale ->
                                scope.launch { preferencesRepo.updateKeyboardHeightScale(scale) }
                            },
                            onUpdateShowKeyBorders = { enabled ->
                                scope.launch { preferencesRepo.updateShowKeyBorders(enabled) }
                            },
                            onUpdateShowKeyShadows = { enabled ->
                                scope.launch { preferencesRepo.updateShowKeyShadows(enabled) }
                            },
                            pendingImportUri = pendingThemeImportUri,
                            onClearPendingImportUri = onClearPendingThemeImportUri,
                            onOpenThemeDesigner = { editingThemeId ->
                                navigateTo(SettingsScreen.ThemeDesigner(editingThemeId))
                            },
                            onBack = navigateBack
                        )
                    }

                    is SettingsScreen.ThemeDesigner -> {
                        val settingsColors = com.codepotro.borno.kiebord.ui.settings.LocalSettingsColors.current
                        val designerFeature = com.codepotro.borno.theme.designer.ThemeDesignerRegistry.feature
                        if (designerFeature != null) {
                            designerFeature.Screen(
                                editingThemeId = screen.editingThemeId,
                                colors = com.codepotro.borno.theme.designer.ThemeDesignerColors(
                                    background = settingsColors.background,
                                    cardBackground = settingsColors.cardBackground,
                                    cardBorder = settingsColors.cardBorder,
                                    textPrimary = settingsColors.textPrimary,
                                    textSecondary = settingsColors.textSecondary,
                                    accent = settingsColors.accent,
                                    divider = settingsColors.divider,
                                    inputBackground = settingsColors.inputBackground,
                                    inputBorder = settingsColors.inputBorder,
                                    isDark = settingsColors.isDark
                                ),
                                previewConfig = com.codepotro.borno.theme.designer.ThemeDesignerPreviewConfig(
                                    showNumberRow = preferences.showNumberRow,
                                    keyboardHeightScale = preferences.keyboardHeightScale,
                                    showKeyBorders = preferences.showKeyBorders,
                                    showKeyShadows = preferences.showKeyShadows
                                ),
                                onBack = navigateBack,
                                onThemeSaved = { savedId ->
                                    scope.launch { preferencesRepo.updateTheme(savedId) }
                                    navigateBack()
                                }
                            )
                        } else {
                            androidx.compose.runtime.LaunchedEffect(Unit) { navigateBack() }
                        }
                    }

                    is SettingsScreen.KeyboardLayouts -> {
                        KeyboardLayoutsScreen(
                            preferences = preferences,
                            onUpdatePrimaryLayout = { layoutId ->
                                scope.launch { preferencesRepo.updatePrimaryLayoutId(layoutId) }
                            },
                            onToggleLayoutEnabled = { layoutId, enabled ->
                                scope.launch { preferencesRepo.toggleLayoutEnabled(layoutId, enabled) }
                            },
                            onReorderEnabledLayouts = { orderedIds ->
                                scope.launch { preferencesRepo.updateEnabledLayoutsOrder(orderedIds) }
                            },
                            onToggleExtraRow = { layoutId, rowId, enabled ->
                                scope.launch { preferencesRepo.setLayoutExtraRowEnabled(layoutId, rowId, enabled) }
                            },
                            onOpenLanguageExtensions = {
                                navigateTo(SettingsScreen.LanguageExtensions)
                            },
                            onBack = navigateBack
                        )
                    }

                    is SettingsScreen.Preferences -> {
                        PreferencesScreen(
                            preferences = preferences,
                            onUpdateVibrate = { enabled ->
                                scope.launch { preferencesRepo.updateVibrateOnKeypress(enabled) }
                            },
                            onUpdateVibrationType = { type ->
                                scope.launch { preferencesRepo.updateVibrationType(type) }
                            },
                            onUpdateVibrationStrength = { strength ->
                                scope.launch { preferencesRepo.updateVibrationStrength(strength) }
                            },
                            onUpdateSound = { enabled ->
                                scope.launch { preferencesRepo.updateSoundOnKeypress(enabled) }
                            },
                            onUpdateSoundVolume = { volume ->
                                scope.launch { preferencesRepo.updateSoundVolume(volume) }
                            },
                            onUpdateLongPressDelay = { delayMs ->
                                scope.launch { preferencesRepo.updateLongPressDelayMs(delayMs) }
                            },
                            onUpdateLongPressPrimaryShortcuts = { enabled ->
                                scope.launch { preferencesRepo.updateLongPressPrimaryShortcutsEnabled(enabled) }
                            },
                            onUpdateShowNumberRow = { enabled ->
                                scope.launch { preferencesRepo.updateShowNumberRow(enabled) }
                            },
                            onUpdateShowKeyPopups = { enabled ->
                                scope.launch { preferencesRepo.updateShowKeyPopups(enabled) }
                            },
                            onUpdateShowSecondaryKeys = { enabled ->
                                scope.launch { preferencesRepo.updateShowSecondaryKeys(enabled) }
                            },
                            onUpdateShowGlobeKey = { enabled ->
                                scope.launch { preferencesRepo.updateShowGlobeKey(enabled) }
                            },
                            onUpdateShowEmojiKey = { enabled ->
                                scope.launch { preferencesRepo.updateShowEmojiKey(enabled) }
                            },
                            onUpdateCursorTrackpad = { enabled ->
                                scope.launch { preferencesRepo.updateCursorTrackpadEnabled(enabled) }
                            },
                            onUpdateOneHandedMode = { mode ->
                                scope.launch { preferencesRepo.updateOneHandedMode(mode) }
                            },
                            onUpdateLongPressDelete = { enabled ->
                                scope.launch { preferencesRepo.updateLongPressDeleteEnabled(enabled) }
                            },
                            onUpdateSelectiveDeleteMode = { mode ->
                                scope.launch { preferencesRepo.updateSelectiveDeleteMode(mode) }
                            },
                            onBack = navigateBack
                        )
                    }

                    is SettingsScreen.SmartGestures -> {
                        SmartGesturesScreen(
                            preferences = preferences,
                            onUpdateFastDeleteEnabled = { enabled ->
                                scope.launch { preferencesRepo.updateFastDeleteEnabled(enabled) }
                            },
                            onUpdateSelectiveDeleteEnabled = { enabled ->
                                scope.launch { preferencesRepo.updateSelectiveDeleteEnabled(enabled) }
                            },
                            onUpdateSelectiveDeleteMode = { mode ->
                                scope.launch { preferencesRepo.updateSelectiveDeleteMode(mode) }
                            },
                            onUpdateCursorTrackpad = { enabled ->
                                scope.launch { preferencesRepo.updateCursorTrackpadEnabled(enabled) }
                            },
                            onUpdateSpacebarSwipeMode = { mode ->
                                scope.launch { preferencesRepo.updateSpacebarSwipeMode(mode) }
                            },
                            onUpdateVolumeKeyGesturesEnabled = { enabled ->
                                scope.launch { preferencesRepo.updateVolumeKeyGesturesEnabled(enabled) }
                            },
                            onUpdateVolumeKeyGestureAction = { action ->
                                scope.launch { preferencesRepo.updateVolumeKeyGestureAction(action) }
                            },
                            onBack = navigateBack
                        )
                    }

                    is SettingsScreen.CorrectionsSuggestions -> {
                        CorrectionsSuggestionsScreen(
                            preferences = preferences,
                            onUpdateSuggestionsEnabled = { enabled ->
                                scope.launch { preferencesRepo.updateSuggestionsEnabled(enabled) }
                            },
                            onUpdateNextWordPrediction = { enabled ->
                                scope.launch { preferencesRepo.updateNextWordPrediction(enabled) }
                            },
                            onUpdatePhoneticAutoCorrect = { enabled ->
                                scope.launch { preferencesRepo.updatePhoneticAutoCorrect(enabled) }
                            },
                            onUpdateBengaliSmartPhonetic = { enabled ->
                                scope.launch { preferencesRepo.updateBengaliSmartPhonetic(enabled) }
                            },
                            onUpdateBengaliShowRawInputCandidate = { enabled ->
                                scope.launch { preferencesRepo.updateBengaliShowRawInputCandidate(enabled) }
                            },
                            onUpdateBornoRememberSuggestionChoice = { enabled ->
                                scope.launch { preferencesRepo.updateBornoRememberSuggestionChoice(enabled) }
                            },
                            onUpdateLearnedWordsEnabled = { enabled ->
                                scope.launch { preferencesRepo.updateLearnedWordsEnabled(enabled) }
                            },
                            onDeleteLearnedWords = {
                                UserDictionaryRepository.getInstance(context).clearAll()
                                BornoSuggestionChoiceRepository.getInstance(context).clearAll()
                            },
                            onUpdateGestureTyping = { enabled ->
                                scope.launch { preferencesRepo.updateGestureTypingEnabled(enabled) }
                            },
                            onUpdateShowGestureTrail = { enabled ->
                                scope.launch { preferencesRepo.updateShowGestureTrail(enabled) }
                            },
                            onUpdateAutoCapitalization = { enabled ->
                                scope.launch { preferencesRepo.updateAutoCapitalization(enabled) }
                            },
                            onUpdateDoubleSpacePeriod = { enabled ->
                                scope.launch { preferencesRepo.updateDoubleSpacePeriod(enabled) }
                            },
                            onUpdateAutoSpaceAfterSuggestion = { enabled ->
                                scope.launch { preferencesRepo.updateAutoSpaceAfterSuggestion(enabled) }
                            },
                            onToggleAutoSpaceAfterSuggestionLanguage = { langCode, enabled ->
                                scope.launch { preferencesRepo.setAutoSpaceAfterSuggestionLanguageEnabled(langCode, enabled) }
                            },
                            onUpdateAutoSpaceAfterPunctuation = { enabled ->
                                scope.launch { preferencesRepo.updateAutoSpaceAfterPunctuation(enabled) }
                            },
                            onUpdateRemoveSpaceBeforePunctuation = { enabled ->
                                scope.launch { preferencesRepo.updateRemoveSpaceBeforePunctuation(enabled) }
                            },
                            onUpdateSuggestionBarMode = { mode ->
                                scope.launch { preferencesRepo.updateSuggestionBarMode(mode) }
                            },
                            onUpdateEmojiSuggestions = { enabled ->
                                scope.launch { preferencesRepo.updateEmojiSuggestions(enabled) }
                            },
                            onBack = navigateBack
                        )
                    }

                    is SettingsScreen.PersonalDictionary -> {
                        PersonalDictionaryScreen(
                            repository = PersonalDictionaryRepository.getInstance(context),
                            initialPhrase = pendingDictPrefillPhrase,
                            initialLanguage = pendingDictPrefillLanguage,
                            focusShortcut = pendingDictFocusShortcut,
                            onPrefillConsumed = onClearPendingDictPrefill,
                            onBack = navigateBack
                        )
                    }

                    is SettingsScreen.Clipboard -> {
                        ClipboardSettingsScreen(
                            clipboardManager = SandboxedClipboardManager.getInstance(context),
                            preferences = preferences,
                            onUpdateAutoDeleteRecentClips = { enabled ->
                                scope.launch { preferencesRepo.updateAutoDeleteRecentClips(enabled) }
                            },
                            onUpdateAutoDeleteRecentClipsDurationHours = { hours ->
                                scope.launch { preferencesRepo.updateAutoDeleteRecentClipsDurationHours(hours) }
                            },
                            onNavigateToManageClips = {
                                navigateTo(SettingsScreen.ClipboardManager)
                            },
                            onBack = navigateBack
                        )
                    }

                    is SettingsScreen.ClipboardManager -> {
                        ClipboardManagerScreen(
                            clipboardManager = SandboxedClipboardManager.getInstance(context),
                            onBack = navigateBack
                        )
                    }

                    is SettingsScreen.VoiceTyping -> {
                        VoiceTypingScreen(
                            preferences = preferences,
                            onUpdateVoiceTypingEnabled = { enabled ->
                                scope.launch { preferencesRepo.updateVoiceTypingEnabled(enabled) }
                            },
                            onUpdateVoiceTypingProvider = { provider ->
                                scope.launch { preferencesRepo.updateVoiceTypingProvider(provider) }
                            }
                        )
                    }

                    is SettingsScreen.LanguageExtensions -> {
                        LanguageExtensionsScreen(
                            pendingImportUri = pendingLangPackImportUri,
                            onClearPendingImportUri = onClearPendingLangPackImportUri,
                            onBack = navigateBack
                        )
                    }

                    is SettingsScreen.BackupRestore -> {
                        BackupRestoreScreen(
                            clipboardManager = SandboxedClipboardManager.getInstance(context),
                            pendingClipboardUri = pendingClipboardImportUri,
                            onClearPendingClipboardUri = onClearPendingClipboardImportUri,
                            pendingDictionaryUri = pendingDictionaryImportUri,
                            onClearPendingDictionaryUri = onClearPendingDictionaryImportUri,
                            onBack = navigateBack
                        )
                    }

                    is SettingsScreen.About -> {
                        AboutScreen(
                            onResetDefaults = {
                                scope.launch { preferencesRepo.resetToDefaults() }
                            },
                            onBack = navigateBack
                        )
                    }

                    is SettingsScreen.Onboarding -> {
                        OnboardingScreen(
                            preferences = preferences,
                            isImeEnabled = isImeEnabled,
                            isImeSelected = isImeSelected,
                            onOpenSettings = onOpenSettings,
                            onSwitchIme = onSwitchIme,
                            onSaveLayouts = onSaveOnboardingLayouts,
                            onFinishOnboarding = onFinishOnboarding,
                            onComplete = {
                                currentScreen = SettingsScreen.Main
                                backStack = emptyList()
                            },
                            onOpenKeyboardLayouts = {
                                navigateTo(SettingsScreen.KeyboardLayouts)
                            }
                        )
                    }
                }
            }
        }
    }
}
