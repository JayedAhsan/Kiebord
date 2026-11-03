package io.kiebord.app

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
import io.kiebord.theme.KeyboardFontFamily
import io.kiebord.theme.KiebordTypography
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
import io.kiebord.app.ui.onboarding.OnboardingScreen
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import io.kiebord.app.ui.settings.AboutScreen
import io.kiebord.app.ui.settings.AppearanceThemesScreen
import io.kiebord.app.ui.settings.BackupRestoreScreen
import io.kiebord.app.ui.settings.ClipboardSettingsScreen
import io.kiebord.app.ui.settings.CorrectionsSuggestionsScreen
import io.kiebord.app.ui.settings.DarkSettingsColors
import io.kiebord.app.ui.settings.KeyboardLayoutsScreen
import io.kiebord.app.ui.settings.LightSettingsColors
import io.kiebord.app.ui.settings.LocalSettingsColors
import io.kiebord.app.ui.settings.PreferencesScreen
import io.kiebord.app.ui.settings.SettingsMainScreen
import io.kiebord.app.ui.settings.SettingsScreen
import io.kiebord.app.ui.settings.SettingsUnifiedTopBar
import io.kiebord.app.ui.settings.KhiproSpecsScreen
import io.kiebord.app.ui.settings.VoiceTypingScreen
import io.kiebord.feature.ime.clipboard.SandboxedClipboardManager
import io.kiebord.data.dictionary.UserDictionaryRepository
import io.kiebord.data.layout.LayoutRepository
import io.kiebord.data.prefs.KiebordPreferences
import io.kiebord.data.prefs.KiebordPreferencesRepository
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.lifecycle.lifecycleScope

class MainActivity : ComponentActivity() {

    private var isImeEnabledState by mutableStateOf(false)
    private var isImeSelectedState by mutableStateOf(false)
    private var requestedNavigationTarget by mutableStateOf<String?>(null)
    private var pendingThemeImportUri by mutableStateOf<android.net.Uri?>(null)
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
                            onOpenSettings = {
                                startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
                            },
                            onSwitchIme = {
                                val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                                imm.showInputMethodPicker()
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
            pendingThemeImportUri = importUri
            requestedNavigationTarget = "themes"
            return
        }
        val target = intent.getStringExtra("navigate_to")
            ?: if (intent.action == "io.kiebord.action.THEMES") "themes" else null
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
        isImeSelectedState = defaultIme != null && defaultIme.contains(packageName)
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
    onOpenSettings: () -> Unit,
    onSwitchIme: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val isSetupFinished = preferences.setupCompleted || preferencesRepo.isSetupCompletedSync() || (isImeEnabled && isImeSelected)

    LaunchedEffect(isImeEnabled, isImeSelected) {
        if (isImeEnabled && isImeSelected && !preferences.setupCompleted) {
            preferencesRepo.updateSetupCompleted(true)
        }
    }

    val initialScreen = remember(isSetupFinished) {
        val target = navigationTarget
            ?: (context as? android.app.Activity)?.intent?.getStringExtra("navigate_to")
            ?: if ((context as? android.app.Activity)?.intent?.action == "io.kiebord.action.THEMES") "themes" else null
        if (target == "layouts") {
            SettingsScreen.KeyboardLayouts
        } else if (target == "themes" || target == "appearance" || pendingThemeImportUri != null) {
            SettingsScreen.AppearanceThemes
        } else if (target == "clipboard") {
            SettingsScreen.Clipboard
        } else if (target == "preferences") {
            SettingsScreen.Preferences
        } else if (!isSetupFinished) {
            SettingsScreen.Onboarding
        } else {
            SettingsScreen.Main
        }
    }
    var currentScreen by remember(initialScreen) { mutableStateOf<SettingsScreen>(initialScreen) }
    var backStack by remember { mutableStateOf<List<SettingsScreen>>(emptyList()) }

    LaunchedEffect(navigationTarget) {
        if (navigationTarget != null) {
            when (navigationTarget) {
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
                "clipboard" -> {
                    if (currentScreen != SettingsScreen.Clipboard) {
                        backStack = listOf(SettingsScreen.Main)
                        currentScreen = SettingsScreen.Clipboard
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
                        val settingsColors = io.kiebord.app.ui.settings.LocalSettingsColors.current
                        val designerFeature = io.kiebord.theme.designer.ThemeDesignerRegistry.feature
                        if (designerFeature != null) {
                            designerFeature.Screen(
                                editingThemeId = screen.editingThemeId,
                                colors = io.kiebord.theme.designer.ThemeDesignerColors(
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
                                previewConfig = io.kiebord.theme.designer.ThemeDesignerPreviewConfig(
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
                            onUpdateLongPressDelay = { delayMs ->
                                scope.launch { preferencesRepo.updateLongPressDelayMs(delayMs) }
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
                            onUpdateLearnedWordsEnabled = { enabled ->
                                scope.launch { preferencesRepo.updateLearnedWordsEnabled(enabled) }
                            },
                            onDeleteLearnedWords = {
                                UserDictionaryRepository.getInstance(context).clearAll()
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
                            onUpdateAutoSpaceAfterPunctuation = { enabled ->
                                scope.launch { preferencesRepo.updateAutoSpaceAfterPunctuation(enabled) }
                            },
                            onUpdateRemoveSpaceBeforePunctuation = { enabled ->
                                scope.launch { preferencesRepo.updateRemoveSpaceBeforePunctuation(enabled) }
                            },
                            onUpdateSuggestionBarMode = { mode ->
                                scope.launch { preferencesRepo.updateSuggestionBarMode(mode) }
                            },
                            onBack = navigateBack
                        )
                    }

                    is SettingsScreen.Clipboard -> {
                        ClipboardSettingsScreen(
                            clipboardManager = SandboxedClipboardManager.getInstance(context),
                            onBack = navigateBack
                        )
                    }

                    is SettingsScreen.KhiproSpecs -> {
                        KhiproSpecsScreen(
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

                    is SettingsScreen.BackupRestore -> {
                        BackupRestoreScreen(
                            clipboardManager = SandboxedClipboardManager.getInstance(context),
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
                            preferencesRepo = preferencesRepo,
                            isImeEnabled = isImeEnabled,
                            isImeSelected = isImeSelected,
                            onOpenSettings = onOpenSettings,
                            onSwitchIme = onSwitchIme,
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
