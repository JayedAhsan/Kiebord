package com.codepotro.borno.feature.ime.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import com.codepotro.borno.data.prefs.KiebordPreferences
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.foundation.BorderStroke
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Assignment
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.ui.text.style.TextOverflow
import com.codepotro.borno.feature.ime.ui.onehanded.OneHandedSideBar
import com.codepotro.borno.feature.ime.ui.floating.FloatingCornerResizers
import com.codepotro.borno.feature.ime.ui.floating.FloatingLandingHintRect
import com.codepotro.borno.feature.ime.ui.floating.FloatingMoveHandleBar
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.ui.res.painterResource
import com.codepotro.borno.feature.ime.R
import androidx.compose.material3.Icon
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import com.codepotro.borno.theme.ResolvedKeyVisual
import androidx.compose.ui.zIndex
import kotlin.math.roundToInt
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import com.codepotro.borno.feature.ime.voice.VoiceInputState
import android.os.SystemClock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import android.content.res.Configuration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.codepotro.borno.common.model.KeyDef
import com.codepotro.borno.common.model.KeyRow
import com.codepotro.borno.common.model.KeyType
import com.codepotro.borno.common.model.KeyboardLayoutDef
import com.codepotro.borno.common.model.ShiftState
import com.codepotro.borno.data.layout.PredefinedLayouts
import com.codepotro.borno.feature.ime.clipboard.SandboxedClipboardManager
import com.codepotro.borno.feature.ime.gesture.KeyCentroid
import com.codepotro.borno.feature.ime.ui.clipboard.ClipboardPanelView
import com.codepotro.borno.feature.ime.ui.emoji.EmojiPanelView
import com.codepotro.borno.feature.ime.ui.emoji.EmojiRowBar
import com.codepotro.borno.feature.ime.ui.emoji.EmojiSearchCardView
import com.codepotro.borno.feature.ime.ui.emoji.EmojiSearchBarRow
import com.codepotro.borno.feature.ime.ui.emoji.EmojiSearchResultsGrid
import com.codepotro.borno.feature.ime.ui.emoji.EmojiSearchResultsStrip
import com.codepotro.borno.feature.ime.ui.emoji.EmojiSearchCatalog
import com.codepotro.borno.feature.ime.ui.resizer.LiveResizerOverlay
import com.codepotro.borno.feature.ime.action.ActionKeyVisual
import com.codepotro.borno.feature.ime.ui.textedit.TextAction
import com.codepotro.borno.feature.ime.ui.textedit.TextEditPanelView
import com.codepotro.borno.feature.ime.ui.suggestion.ExpandedSuggestionsPanelView
import com.codepotro.borno.feature.ime.ui.suggestion.SuggestionCandidate
import com.codepotro.borno.feature.ime.ui.suggestion.SuggestionStripView
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import com.codepotro.borno.feature.ime.ui.toolbar.ReorderableToolbar
import com.codepotro.borno.feature.ime.ui.toolbar.ToolbarAction
import com.codepotro.borno.feature.ime.ui.toolbar.ToolsDrawerView
import com.codepotro.borno.feature.ime.ui.dialog.LanguageSelectionDialog
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.codepotro.borno.theme.KiebordTheme
import com.codepotro.borno.theme.KeyboardColorScheme
import com.codepotro.borno.theme.ThemeColorUtils
import com.codepotro.borno.theme.effectiveToolbarBackground
import com.codepotro.borno.theme.effectiveToolbarForeground

sealed class UndoRedoPillState {
    data class CanUndo(val text: String, val isPaste: Boolean = false, val isBatchDelete: Boolean = false) : UndoRedoPillState()
    data class CanRedo(val text: String, val isPaste: Boolean = false, val isBatchDelete: Boolean = false) : UndoRedoPillState()
}

@Composable
fun KeyboardRootView(
    layout: KeyboardLayoutDef,
    primaryAlphaLayout: KeyboardLayoutDef = layout,
    isEmailField: Boolean = false,
    isIncognitoMode: Boolean = false,
    shiftState: ShiftState,
    isBornoEasyKarMode: Boolean = false,
    bornoEasyTypedCluster: String = "",
    onKeyTap: (KeyDef) -> Unit,
    onKeyLongPress: (KeyDef) -> Unit,
    onTextAction: (TextAction, Boolean) -> Boolean = { _, _ -> false },
    onSpacebarTrackpadStart: () -> Unit = {},
    onSpacebarTrackpadMove: ((Int) -> Boolean)? = null,
    onSpacebarTrackpadEnd: () -> Unit = {},
    onThemeToggle: () -> Unit = {},
    onOpenThemeSettings: () -> Unit = onThemeToggle,
    defaultOneHandedMode: String = "OFF",
    onOneHandedModeChange: ((String) -> Unit)? = null,
    suggestions: List<SuggestionCandidate> = emptyList(),
    suggestionBarMode: String = KiebordPreferences.SUGGESTION_BAR_MODE_SCROLLABLE,
    onCandidateSelect: (SuggestionCandidate) -> Unit = {},
    onUnlearnWord: (String) -> Unit = {},
    clipboardManager: SandboxedClipboardManager? = null,
    onPasteClip: (String) -> Unit = {},
    onOpenClipboardSettings: () -> Unit = {},
    lastDeletedBatch: String? = null,
    onGestureCommit: (List<Offset>, List<KeyCentroid>, Float) -> Unit = { _, _, _ -> },
    onGestureLivePredict: ((List<Offset>, List<KeyCentroid>, Float) -> String?)? = null,
    initialRowHeight: Dp? = null,
    heightScalePortrait: Float = 1.0f,
    heightScaleLandscape: Float = 1.0f,
    chinPaddingPortraitDp: Float = 22.0f,
    chinPaddingLandscapeDp: Float = 8.0f,
    horizontalMarginPortraitDp: Float = 0.0f,
    horizontalMarginLandscapeDp: Float = 0.0f,
    onResizerValuesChange: ((isLandscape: Boolean, heightScale: Float, chinPaddingDp: Float, horizontalMarginDp: Float) -> Unit)? = null,
    defaultShowNumberRow: Boolean = false,
    extraRowOverrides: Map<String, Boolean> = emptyMap(),
    onNumberRowToggle: ((Boolean) -> Unit)? = null,
    showKeyBorders: Boolean = true,
    showKeyShadows: Boolean = true,
    defaultShowSecondaryKeys: Boolean = true,
    onSecondaryKeysToggle: ((Boolean) -> Unit)? = null,
    defaultShowKeyPopups: Boolean = true,
    onKeyPopupsToggle: ((Boolean) -> Unit)? = null,
    showGlobeKey: Boolean = true,
    showEmojiKey: Boolean = false,
    defaultShowEmojiRow: Boolean = false,
    recentEmojis: List<String> = emptyList(),
    onEmojiRowToggle: ((Boolean) -> Unit)? = null,
    onRecordRecentEmoji: ((String) -> Unit)? = null,
    defaultTraditionalMode: Boolean = false,
    onTraditionalModeToggle: ((Boolean) -> Unit)? = null,
    onKeyPress: (KeyDef) -> Unit = {},
    onDeleteSwipeMove: (Float) -> Unit = {},
    onDeleteSwipeEnd: () -> Unit = {},
    onDeleteSwipeCancel: () -> Unit = {},
    onDeleteLongPressEnd: () -> Unit = {},
    fastDeleteEnabled: Boolean = false,
    selectiveDeleteEnabled: Boolean = true,
    spacebarSwipeMode: String = KiebordPreferences.SPACEBAR_MODE_SWIPE_LAYOUT_HOLD_CURSOR,
    onFastDelete: () -> Unit = {},
    navBarBottomInset: Dp = 0.dp,
    applySystemInsets: Boolean = true,
    isInteractive: Boolean = true,
    initialPinnedTools: List<ToolbarAction>? = null,
    initialDrawerTools: List<ToolbarAction>? = null,
    onToolbarToolsChange: ((List<ToolbarAction>, List<ToolbarAction>) -> Unit)? = null,
    onHeightScaleChange: ((Float) -> Unit)? = null,
    enabledLayoutIds: List<String> = emptyList(),
    onSwitchToLayout: (String) -> Unit = {},
    onOpenLanguagesSettings: () -> Unit = {},
    onOpenInputMethodPicker: () -> Unit = {},
    gestureTypingEnabled: Boolean = true,
    showGestureTrail: Boolean = true,
    longPressDelayMs: Long = 300L,
    longPressPrimaryShortcutsEnabled: Boolean = true,
    cursorTrackpadEnabled: Boolean = true,
    inputSessionId: Int = 0,
    actionKeyVisual: ActionKeyVisual = ActionKeyVisual.DefaultEnter,
    vibrationEnabled: Boolean = true,
    voiceInputState: VoiceInputState = VoiceInputState.Idle,
    onStartVoiceTyping: () -> Unit = {},
    onDismissVoiceTyping: () -> Unit = {},
    voiceTypingEnabled: Boolean = true,
    isNumberPadActive: Boolean = false,
    onToggleNumberPad: () -> Unit = {},
    enablePopupHeadroom: Boolean = isInteractive,
    isFloatingMode: Boolean = false,
    floatingWidthPortraitDp: Float = 320.0f,
    floatingWidthLandscapeDp: Float = 420.0f,
    floatingHeightScalePortrait: Float = 1.0f,
    floatingHeightScaleLandscape: Float = 0.95f,
    floatingOffsetRatioXPortrait: Float = 0.5f,
    floatingOffsetRatioYPortrait: Float = 0.7f,
    floatingOffsetRatioXLandscape: Float = 0.5f,
    floatingOffsetRatioYLandscape: Float = 0.7f,
    onFloatingModeChange: ((Boolean) -> Unit)? = null,
    onFloatingSizeChange: ((isLandscape: Boolean, widthDp: Float, heightScale: Float) -> Unit)? = null,
    onFloatingPositionChange: ((isLandscape: Boolean, ratioX: Float, ratioY: Float) -> Unit)? = null,
    onFloatingTouchableRegionChanged: ((android.graphics.Region?) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val colorScheme = KiebordTheme.colorScheme
    val density = LocalDensity.current
    val haptic = LocalHapticFeedback.current
    val performHaptic: (HapticFeedbackType) -> Unit = { type ->
        if (vibrationEnabled) {
            try {
                haptic.performHapticFeedback(type)
            } catch (_: Exception) {}
        }
    }

    var rootCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    var rootWidthPx by remember { mutableFloatStateOf(0f) }

    val cellCoordinatesMap = remember { android.util.SparseArray<LayoutCoordinates>(64) }

    var moreKeysState by remember { mutableStateOf<MoreKeysPopupState?>(null) }
    var selectedMoreKeyIndex by remember { mutableIntStateOf(0) }
    var quickDragState by remember { mutableStateOf<QuickSymbolDragState?>(null) }
    var isSlidingSymbolMode by remember { mutableStateOf(false) }
    var isSlidingShiftMode by remember { mutableStateOf(false) }
    var slidingInitiatorKey by remember { mutableStateOf<KeyDef?>(null) }
    var dragStartPosInRoot by remember { mutableStateOf<Offset?>(null) }
    val coroutineScope = rememberCoroutineScope()
    var slidingHoveredKey by remember { mutableStateOf<KeyDef?>(null) }
    var slidingHoveredBounds by remember { mutableStateOf<Rect?>(null) }
    var slidingDwellJob by remember { mutableStateOf<Job?>(null) }
    var gestureTrail by remember { mutableStateOf<List<Offset>>(emptyList()) }
    var fadingTrail by remember { mutableStateOf<List<Offset>>(emptyList()) }
    val trailFadeAlpha = remember { Animatable(1f) }
    var fadingTrailJob by remember { mutableStateOf<Job?>(null) }
    var cachedCentroids by remember { mutableStateOf<List<KeyCentroid>>(emptyList()) }
    var cachedKeyWidthPx by remember { mutableFloatStateOf(60f) }
    var liveGestureCandidate by remember { mutableStateOf<String?>(null) }
    var livePredictJob by remember { mutableStateOf<Job?>(null) }
    var lastPredictTimeMs by remember { mutableStateOf(0L) }

    var isLanguagePickerOpen by remember { mutableStateOf(false) }
    var unlearnCandidateTarget by remember { mutableStateOf<SuggestionCandidate?>(null) }
    var isSpacebarSwiping by remember { mutableStateOf(false) }
    var spacebarSwipeDeltaX by remember { mutableFloatStateOf(0f) }

    val effectiveLayoutList = remember(enabledLayoutIds, layout.id) {
        val list = enabledLayoutIds.ifEmpty { listOf(layout.id) }
        if (list.contains(layout.id)) list else list + layout.id
    }
    val currentLayoutIndex = remember(effectiveLayoutList, layout.id) {
        effectiveLayoutList.indexOf(layout.id).coerceAtLeast(0)
    }
    val commitThresholdPx = with(density) { 56.dp.toPx() }
    val n = effectiveLayoutList.size
    val targetSwipeIndex = if (isSpacebarSwiping && n > 1) {
        if (spacebarSwipeDeltaX >= commitThresholdPx) {
            (currentLayoutIndex + 1) % n
        } else if (spacebarSwipeDeltaX <= -commitThresholdPx) {
            (currentLayoutIndex - 1 + n) % n
        } else {
            currentLayoutIndex
        }
    } else currentLayoutIndex

    // Phase 5, 6 & 8: Toolbar, Emoji, Resizer, Number row, TextEdit, Clipboard, and Tools Drawer states
    val defaultPinned = listOf(
        ToolbarAction.EMOJI,
        ToolbarAction.TEXT_EDIT,
        ToolbarAction.NUMBER_PAD,
        ToolbarAction.CLIPBOARD,
        ToolbarAction.SETTINGS
    )
    val defaultDrawer = listOf(
        ToolbarAction.RESIZER,
        ToolbarAction.FLOATING,
        ToolbarAction.NUMBER_ROW_TOGGLE,
        ToolbarAction.SECONDARY_KEYS_TOGGLE,
        ToolbarAction.KEY_POPUP_TOGGLE,
        ToolbarAction.ONE_HANDED,
        ToolbarAction.THEME_TOGGLE,
        ToolbarAction.TRADITIONAL_MODE,
        ToolbarAction.UNDO,
        ToolbarAction.REDO,
        ToolbarAction.EMOJI_ROW_TOGGLE
    )
    var pinnedTools by remember(initialPinnedTools) {
        val pinned = (initialPinnedTools ?: defaultPinned).filter { it != ToolbarAction.SHARE_BORNO }.distinct()
        mutableStateOf(pinned)
    }
    var drawerTools by remember(initialDrawerTools, initialPinnedTools) {
        val pSet = (initialPinnedTools ?: defaultPinned).filter { it != ToolbarAction.SHARE_BORNO }.toSet()
        val dList = ((initialDrawerTools ?: defaultDrawer).filter { it != ToolbarAction.SHARE_BORNO && it !in pSet } +
            ToolbarAction.values().filter { it != ToolbarAction.SHARE_BORNO && it !in pSet }).distinct()
        mutableStateOf(dList)
    }

    // Hit-testing bounding boxes in root coordinates
    val toolbarItemBounds = remember { mutableMapOf<ToolbarAction, Rect>() }
    val drawerItemBounds = remember { mutableMapOf<ToolbarAction, Rect>() }
    var drawerContainerBounds by remember { mutableStateOf<Rect?>(null) }

    val updateTools: (List<ToolbarAction>, List<ToolbarAction>) -> Unit = { newPinned, newDrawer ->
        val sanitizedPinned = newPinned.filter { it != ToolbarAction.SHARE_BORNO }.distinct()
        val pinnedSet = sanitizedPinned.toSet()
        val allActions = ToolbarAction.values().filter { it != ToolbarAction.SHARE_BORNO }
        val sanitizedDrawer = (newDrawer.filter { it != ToolbarAction.SHARE_BORNO && it !in pinnedSet } +
            allActions.filter { it !in pinnedSet }).distinct()

        toolbarItemBounds.keys.retainAll(sanitizedPinned.toSet())
        drawerItemBounds.keys.retainAll(sanitizedDrawer.toSet())

        pinnedTools = sanitizedPinned
        drawerTools = sanitizedDrawer
        onToolbarToolsChange?.invoke(sanitizedPinned, sanitizedDrawer)
    }

    val interchangeTools: (ToolbarAction, ToolbarAction) -> Unit = { act1, act2 ->
        if (act1 != act2) {
            val isAct1Pinned = pinnedTools.contains(act1)
            val isAct2Pinned = pinnedTools.contains(act2)
            if (isAct1Pinned && !isAct2Pinned) {
                val pIdx = pinnedTools.indexOf(act1)
                val dIdx = drawerTools.indexOf(act2)
                val newPinned = pinnedTools.toMutableList()
                val newDrawer = drawerTools.toMutableList()
                if (pIdx != -1) newPinned[pIdx] = act2
                if (dIdx != -1) newDrawer[dIdx] = act1
                updateTools(newPinned, newDrawer)
            } else if (!isAct1Pinned && isAct2Pinned) {
                val dIdx = drawerTools.indexOf(act1)
                val pIdx = pinnedTools.indexOf(act2)
                val newPinned = pinnedTools.toMutableList()
                val newDrawer = drawerTools.toMutableList()
                if (pIdx != -1) newPinned[pIdx] = act1
                if (dIdx != -1) newDrawer[dIdx] = act2
                updateTools(newPinned, newDrawer)
            }
        }
    }

    // Active drag session across toolbar and drawer
    var activeDraggingFromToolbar by remember { mutableStateOf<ToolbarAction?>(null) }
    var activeDraggingFromDrawer by remember { mutableStateOf<ToolbarAction?>(null) }
    var currentFingerInRoot by remember { mutableStateOf<Offset?>(null) }

    val moveToolbarToDrawer: (ToolbarAction) -> Unit = { toolbarAct ->
        if (pinnedTools.contains(toolbarAct)) {
            val newPinned = (pinnedTools - toolbarAct).distinct()
            val newDrawer = (drawerTools + toolbarAct).distinct()
            updateTools(newPinned, newDrawer)
        }
    }

    val moveDrawerToToolbar: (ToolbarAction) -> Unit = { drawerAct ->
        if (drawerTools.contains(drawerAct)) {
            if (pinnedTools.size >= 5) {
                // Toolbar is already at capacity (5 tools max): swap with the slot at finger position
                val finger = currentFingerInRoot
                val targetIndex = if (finger != null && rootWidthPx > 0f) {
                    val slotWidth = rootWidthPx / pinnedTools.size
                    (finger.x / slotWidth).toInt().coerceIn(0, pinnedTools.lastIndex)
                } else {
                    pinnedTools.lastIndex
                }
                val targetToolbarAct = pinnedTools[targetIndex]
                interchangeTools(drawerAct, targetToolbarAct)
            } else {
                // Toolbar has space (< 5 items): ADD TO TOOLBAR at finger slot position!
                val finger = currentFingerInRoot
                val newPinned = pinnedTools.toMutableList()
                if (finger != null && rootWidthPx > 0f && pinnedTools.isNotEmpty()) {
                    val slotWidth = rootWidthPx / (pinnedTools.size + 1)
                    val targetIndex = (finger.x / slotWidth).toInt().coerceIn(0, newPinned.size)
                    newPinned.add(targetIndex, drawerAct)
                } else {
                    newPinned.add(drawerAct)
                }
                val newDrawer = (drawerTools - drawerAct).distinct()
                updateTools(newPinned.distinct(), newDrawer)
            }
        }
    }

    var isToolbarOptionsVisible by remember { mutableStateOf(false) }
    var isFloating by remember(isFloatingMode) { mutableStateOf(isFloatingMode) }
    var oneHandedSide by remember(defaultOneHandedMode, isFloating) { mutableStateOf(if (isFloating) "OFF" else defaultOneHandedMode) } // "OFF", "RIGHT", "LEFT"
    var oneHandedSideWidth by remember { mutableFloatStateOf(64f) }
    var isEmojiOpen by remember { mutableStateOf(false) }
    var isEmojiSearchOpen by remember { mutableStateOf(false) }
    var emojiSearchQuery by remember { mutableStateOf("") }

    val handleKeyTapUnified: (KeyDef) -> Unit = { key ->
        if (gestureTrail.isNotEmpty() || fadingTrail.isNotEmpty() || liveGestureCandidate != null) {
            livePredictJob?.cancel()
            livePredictJob = null
            fadingTrailJob?.cancel()
            fadingTrailJob = null
            gestureTrail = emptyList()
            fadingTrail = emptyList()
            liveGestureCandidate = null
        }
        if (isToolbarOptionsVisible && (key.type == KeyType.CHARACTER || key.type == KeyType.DELETE || key.type == KeyType.SPACE)) {
            isToolbarOptionsVisible = false
        }
        if (voiceInputState is VoiceInputState.Listening) {
            onDismissVoiceTyping()
        }
        if (key.type == KeyType.EMOJI) {
            isEmojiOpen = true
            isEmojiSearchOpen = false
            emojiSearchQuery = ""
        } else if (isEmojiSearchOpen) {
            when (key.type) {
                KeyType.CHARACTER -> {
                    val text = key.commitText ?: key.label
                    emojiSearchQuery += text
                }
                KeyType.DELETE -> {
                    if (emojiSearchQuery.isNotEmpty()) {
                        emojiSearchQuery = emojiSearchQuery.dropLast(1)
                    } else {
                        onKeyTap(key)
                    }
                }
                KeyType.SPACE -> {
                    emojiSearchQuery += " "
                }
                KeyType.ACTION -> {
                    val topEmoji = EmojiSearchCatalog.search(emojiSearchQuery).firstOrNull()
                    if (topEmoji != null) {
                        val keyDef = KeyDef(label = topEmoji, type = KeyType.CHARACTER)
                        onKeyPress(keyDef)
                        onKeyTap(keyDef)
                        onRecordRecentEmoji?.invoke(topEmoji)
                    }
                    isEmojiSearchOpen = false
                    emojiSearchQuery = ""
                }
                else -> onKeyTap(key)
            }
        } else {
            onKeyTap(key)
        }
    }

    val hoveredDrawerAction: ToolbarAction? = if (currentFingerInRoot != null) {
        val finger = currentFingerInRoot!!
        val activeDrag = activeDraggingFromToolbar ?: activeDraggingFromDrawer
        if (activeDrag != null) {
            val validDrawerBounds = drawerItemBounds.filterKeys { it in drawerTools && it != activeDrag }
            validDrawerBounds.entries.firstOrNull { (_, rect) ->
                rect.inflate(14f).contains(finger)
            }?.key
        } else null
    } else null

    val isOverDrawerBlankSpace: Boolean = if (activeDraggingFromToolbar != null && currentFingerInRoot != null && hoveredDrawerAction == null) {
        drawerContainerBounds?.contains(currentFingerInRoot!!) ?: false
    } else false

    var isResizerOpen by remember { mutableStateOf(false) }
    var isTextEditOpen by remember { mutableStateOf(false) }
    var isToolsDrawerOpen by remember { mutableStateOf(false) }
    var isSuggestionsExpanded by remember { mutableStateOf(false) }
    var showNumberRow by remember(defaultShowNumberRow) { mutableStateOf(defaultShowNumberRow) }
    var showEmojiRow by remember(defaultShowEmojiRow) { mutableStateOf(defaultShowEmojiRow) }
    var showSecondaryKeys by remember(defaultShowSecondaryKeys) { mutableStateOf(defaultShowSecondaryKeys) }
    var showKeyPopups by remember(defaultShowKeyPopups) { mutableStateOf(defaultShowKeyPopups) }
    var traditionalMode by remember(defaultTraditionalMode) { mutableStateOf(defaultTraditionalMode) }

    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val screenHeightDp = configuration.screenHeightDp.dp
    val popupHeadroomDp = if (!enablePopupHeadroom || isFloating) 0.dp else if (isLandscape) 28.dp else 56.dp

    val activeInitialFloatingWidth = if (isLandscape) floatingWidthLandscapeDp.dp else floatingWidthPortraitDp.dp
    val activeInitialFloatingHeightScale = if (isLandscape) floatingHeightScaleLandscape else floatingHeightScalePortrait
    val activeInitialFloatingRatioX = if (isLandscape) floatingOffsetRatioXLandscape else floatingOffsetRatioXPortrait
    val activeInitialFloatingRatioY = if (isLandscape) floatingOffsetRatioYLandscape else floatingOffsetRatioYPortrait

    var currentFloatingWidth by remember(isLandscape, activeInitialFloatingWidth) {
        mutableStateOf(activeInitialFloatingWidth)
    }
    var currentFloatingHeightScale by remember(isLandscape, activeInitialFloatingHeightScale) {
        mutableFloatStateOf(activeInitialFloatingHeightScale)
    }
    var currentFloatingRatioX by remember(isLandscape, activeInitialFloatingRatioX) {
        mutableFloatStateOf(activeInitialFloatingRatioX)
    }
    var currentFloatingRatioY by remember(isLandscape, activeInitialFloatingRatioY) {
        mutableFloatStateOf(activeInitialFloatingRatioY)
    }

    var isDraggingFloating by remember { mutableStateOf(false) }
    var floatingDragOffset by remember { mutableStateOf(Offset.Zero) }
    var landingHintBoundsInRoot by remember { mutableStateOf<Rect?>(null) }
    var floatingKeyboardBoundsInRoot by remember { mutableStateOf<Rect?>(null) }
    var isOverLandingZone by remember { mutableStateOf(false) }

    // When in sliding symbol mode from ?123, seamlessly switch display to Symbols layout
    val displayLayout = if (isSlidingSymbolMode) PredefinedLayouts.resolveSymbolsLayout(layout) else layout

    val isDisplayBornoEasy = PredefinedLayouts.isBornoEasy(displayLayout.id)
    val isPrimaryBornoEasy = PredefinedLayouts.isBornoEasy(primaryAlphaLayout.id)
    val isBornoEasyLayout = isDisplayBornoEasy || isPrimaryBornoEasy
    val isNumberLayout = PredefinedLayouts.isNumberLayout(displayLayout.id)

    // Baseline row heights for portrait
    val baseStandardPortraitRowHeight = if (showNumberRow) 54.dp else 61.dp
    val baseBornoEasyPortraitRowHeight = if (showNumberRow) 43.dp else 48.dp
    val basePortraitRowHeight = when {
        isDisplayBornoEasy -> baseBornoEasyPortraitRowHeight
        showNumberRow -> 54.dp
        else -> 61.dp
    }

    // Baseline row heights for landscape
    val baseLandscapeBottomRowHeight = remember(screenHeightDp, showNumberRow) {
        val totalStandardLandscapeRows = if (showNumberRow) 5f else 4f
        ((screenHeightDp * 0.60f - 38.dp - 4.dp).coerceAtLeast(110.dp) / totalStandardLandscapeRows)
    }
    val baseLandscapeBornoEasyRowHeight = remember(screenHeightDp, showNumberRow) {
        val totalBornoEasyLandscapeRows = if (showNumberRow) 7f else 6f
        ((screenHeightDp * 0.62f - 38.dp - 4.dp).coerceAtLeast(110.dp) / totalBornoEasyLandscapeRows)
    }
    val baseLandscapeRowHeight = if (isDisplayBornoEasy) baseLandscapeBornoEasyRowHeight else baseLandscapeBottomRowHeight

    val activeBaseRowHeight = if (isLandscape) baseLandscapeRowHeight else basePortraitRowHeight
    val activeHeightScale = if (isFloating) {
        currentFloatingHeightScale
    } else if (isLandscape) {
        heightScaleLandscape
    } else {
        heightScalePortrait
    }
    val activeTargetRowHeight = if (initialRowHeight != null && !isLandscape && !isFloating) {
        initialRowHeight
    } else {
        activeBaseRowHeight * activeHeightScale
    }

    val activeChinPadding = if (isLandscape) chinPaddingLandscapeDp.dp else chinPaddingPortraitDp.dp
    val activeHorizontalMargin = if (isLandscape) horizontalMarginLandscapeDp.dp else horizontalMarginPortraitDp.dp

    var keyRowHeight by remember(isLandscape, isFloating, activeTargetRowHeight) { mutableStateOf(activeTargetRowHeight) }
    val scaleFactor = (keyRowHeight.value / (if (activeBaseRowHeight.value > 0f) activeBaseRowHeight.value else 61f)).coerceIn(0.4f, 1.6f)
    val toolbarHeight = if (isLandscape) (38 * scaleFactor).coerceIn(32f, 44f).dp else (44 * scaleFactor).dp

    // Unified bottom row height across ALL layouts using scaleFactor
    val bottomRowHeight = (if (isLandscape) baseLandscapeBottomRowHeight else baseStandardPortraitRowHeight) * scaleFactor
    val standardRowHeight = (if (isLandscape) baseLandscapeBottomRowHeight else baseStandardPortraitRowHeight) * scaleFactor
    val bornoEasyRowHeight = (if (isLandscape) baseLandscapeBornoEasyRowHeight else baseBornoEasyPortraitRowHeight) * scaleFactor

    // Target keyboard height to match primary layout height
    val targetKeyboardHeight = remember(
        isPrimaryBornoEasy,
        showNumberRow,
        standardRowHeight,
        bornoEasyRowHeight,
        bottomRowHeight,
        scaleFactor
    ) {
        val topNumberHeight = if (showNumberRow) (50 * scaleFactor).dp else 0.dp
        if (isPrimaryBornoEasy) {
            topNumberHeight + (bornoEasyRowHeight * 5) + bottomRowHeight
        } else {
            topNumberHeight + (standardRowHeight * 3) + bottomRowHeight
        }
    }

    val numberUpperRowHeight = remember(targetKeyboardHeight, bottomRowHeight) {
        ((targetKeyboardHeight - bottomRowHeight) / 3).coerceAtLeast(40.dp)
    }

    var chinPadding by remember(isLandscape, activeChinPadding) {
        mutableStateOf(activeChinPadding)
    }
    var horizontalMargin by remember(isLandscape, activeHorizontalMargin) {
        mutableStateOf(activeHorizontalMargin)
    }

    // Snapshot state for Resizer Cancel button
    var savedRowHeight by remember { mutableStateOf(keyRowHeight) }
    var savedChinPadding by remember { mutableStateOf(chinPadding) }
    var savedHorizontalMargin by remember { mutableStateOf(horizontalMargin) }

    var dragRowHeight by remember(isLandscape, keyRowHeight) { mutableStateOf(keyRowHeight) }
    var dragChinPadding by remember(isLandscape, chinPadding) { mutableStateOf(chinPadding) }
    var dragHorizontalMargin by remember(isLandscape, horizontalMargin) { mutableStateOf(horizontalMargin) }

    val hoveredToolbarAction: ToolbarAction? = if (activeDraggingFromDrawer != null && currentFingerInRoot != null) {
        val finger = currentFingerInRoot!!
        val toolbarHeightPx = with(density) { toolbarHeight.toPx() }
        if (finger.y <= toolbarHeightPx * 2.2f && pinnedTools.isNotEmpty()) {
            val validToolbarBounds = toolbarItemBounds.filterKeys { it in pinnedTools && it != activeDraggingFromDrawer }
            validToolbarBounds.entries.firstOrNull { (_, rect) ->
                rect.inflate(14f).contains(finger)
            }?.key
        } else null
    } else null
    val context = LocalContext.current
    var isClipboardOpen by remember { mutableStateOf(false) }
    var undoActionPillState by remember { mutableStateOf<UndoRedoPillState?>(null) }
    val quickPasteClip = clipboardManager?.quickPasteClip

    LaunchedEffect(quickPasteClip) {
        if (quickPasteClip != null) {
            delay(8000)
            clipboardManager?.clearQuickPaste()
        }
    }

    LaunchedEffect(undoActionPillState) {
        if (undoActionPillState != null) {
            delay(8000)
            undoActionPillState = null
        }
    }

    LaunchedEffect(isClipboardOpen) {
        if (isClipboardOpen) {
            clipboardManager?.syncFromSystemClipboard(context)
        }
    }

    LaunchedEffect(lastDeletedBatch) {
        if (!lastDeletedBatch.isNullOrEmpty()) {
            undoActionPillState = UndoRedoPillState.CanUndo(text = lastDeletedBatch, isBatchDelete = true)
            clipboardManager?.clearQuickPaste()
        }
    }

    // Obey navigation bar insets across all Android versions
    val composeNavBottom = if (applySystemInsets) {
        WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    } else {
        0.dp
    }
    val effectiveNavBarBottom = if (applySystemInsets) maxOf(navBarBottomInset, composeNavBottom) else 0.dp
    // Total bottom padding combines navigation bar clearance with user chin preference,
    // allowing chinPadding to reduce down to -effectiveNavBarBottom so users can reach 0.dp total padding.
    val totalBottomPadding = if (isFloating) 2.dp else (effectiveNavBarBottom + chinPadding).coerceAtLeast(0.dp)

    LaunchedEffect(isFloating, floatingKeyboardBoundsInRoot, isDraggingFloating, landingHintBoundsInRoot) {
        if (isFloating) {
            val kb = floatingKeyboardBoundsInRoot
            if (kb != null && !kb.isEmpty) {
                val headroomPx = with(density) { 48.dp.toPx() }
                val resizerMarginPx = with(density) { 24.dp.toPx() }
                val region = android.graphics.Region(
                    (kb.left - resizerMarginPx).roundToInt().coerceAtLeast(0),
                    (kb.top - maxOf(headroomPx, resizerMarginPx)).roundToInt().coerceAtLeast(0),
                    (kb.right + resizerMarginPx).roundToInt(),
                    (kb.bottom + resizerMarginPx).roundToInt()
                )
                if (isDraggingFloating) {
                    landingHintBoundsInRoot?.let { dock ->
                        if (!dock.isEmpty) {
                            region.op(
                                dock.left.roundToInt(),
                                dock.top.roundToInt(),
                                dock.right.roundToInt(),
                                dock.bottom.roundToInt(),
                                android.graphics.Region.Op.UNION
                            )
                        }
                    }
                }
                onFloatingTouchableRegionChanged?.invoke(region)
            } else {
                onFloatingTouchableRegionChanged?.invoke(null)
            }
        } else {
            onFloatingTouchableRegionChanged?.invoke(null)
        }
    }

    // Reset toolbar options & expanded suggestions visibility when suggestions clear
    LaunchedEffect(suggestions.isEmpty()) {
        if (suggestions.isEmpty()) {
            isToolbarOptionsVisible = false
            isSuggestionsExpanded = false
        }
    }


    val layoutSupportsShift = remember(displayLayout) { displayLayout.hasShiftKey() }
    val effectiveShiftState = if (!layoutSupportsShift) {
        ShiftState.UNSHIFTED
    } else if (isSlidingShiftMode) {
        ShiftState.ONE_SHOT
    } else {
        shiftState
    }
    val isShiftActive = effectiveShiftState != ShiftState.UNSHIFTED
    val isSymbolLayout = displayLayout.isSymbolsLayout()
    val isMoreSymbolsLayout = displayLayout.isMoreSymbolsLayout()
    val baseAlphaLayout = if (isSymbolLayout) (if (isSlidingSymbolMode) layout else primaryAlphaLayout) else displayLayout

    val keyboardRows: List<KeyRow> = remember(displayLayout, baseAlphaLayout, showNumberRow, extraRowOverrides, isShiftActive, isBornoEasyKarMode, bornoEasyTypedCluster, showGlobeKey) {
        val numberRow: List<KeyRow> = if (showNumberRow && !isNumberLayout && !isMoreSymbolsLayout) {
            val baseRow = displayLayout.numberRow
                ?: (if (isSymbolLayout) baseAlphaLayout.numberRow else null)
                ?: PredefinedLayouts.createNumberRow(if (isSymbolLayout) baseAlphaLayout.language else displayLayout.language)
            listOf(baseRow)
        } else {
            emptyList()
        }
        val extraRowsToRender: List<KeyRow> = if (isSymbolLayout) {
            displayLayout.resolveExtraRowsForSymbols(baseAlphaLayout, extraRowOverrides)
        } else {
            displayLayout.extraRows.filter { extraRow ->
                val primaryId = displayLayout.id.removeSuffix("_symbols").removeSuffix("_symbols_more").removeSuffix("_1").removeSuffix("_2")
                extraRowOverrides["${displayLayout.id}_${extraRow.id}"]
                    ?: extraRowOverrides["${primaryId}_${extraRow.id}"]
                    ?: extraRowOverrides[extraRow.id]
                    ?: extraRow.defaultEnabled
            }.map { KeyRow(it.keys) }
        }
        val coreRowsToRender: List<KeyRow> = if (isSymbolLayout) {
            displayLayout.resolveCoreRowsForSymbols(showNumberRow)
        } else if (PredefinedLayouts.isBornoEasy(displayLayout.id)) {
            val firstRow = PredefinedLayouts.getBornoEasyFirstRow(isBornoEasyKarMode, bornoEasyTypedCluster)
            listOf(firstRow) + displayLayout.rows.drop(1)
        } else {
            displayLayout.rows
        }
        val allRows = numberRow + extraRowsToRender + coreRowsToRender
        if (!showGlobeKey) {
            allRows.map { row ->
                if (row.keys.any { it.type == KeyType.SWITCH_LANGUAGE }) {
                    val removedWeight = row.keys.filter { it.type == KeyType.SWITCH_LANGUAGE }.sumOf { it.widthWeight.toDouble() }.toFloat()
                    val hasSpace = row.keys.any { it.type == KeyType.SPACE }
                    row.copy(
                        keys = row.keys.filter { it.type != KeyType.SWITCH_LANGUAGE }.map { key ->
                            if (hasSpace && key.type == KeyType.SPACE) {
                                key.copy(widthWeight = key.widthWeight + removedWeight)
                            } else {
                                key
                            }
                        }
                    )
                } else {
                    row
                }
            }
        } else {
            allRows
        }
    }

    val isQwertyBasedLayout = displayLayout.isQwertyBased()
    val isEffectiveGestureTypingEnabled = gestureTypingEnabled && isQwertyBasedLayout

    val extractCentroids: (LayoutCoordinates) -> Pair<List<KeyCentroid>, Float> = { rCoords ->
        val centroids = mutableListOf<KeyCentroid>()
        var totalWidth = 0f
        var count = 0
        for (r in keyboardRows.indices) {
            val row = keyboardRows[r]
            for (c in row.keys.indices) {
                val k = row.keys[c]
                val coords = cellCoordinatesMap.get((r shl 16) or (c and 0xFFFF))
                if (k.type == KeyType.CHARACTER && coords != null && coords.isAttached) {
                    val topLeft = rCoords.localPositionOf(coords, Offset.Zero)
                    val width = coords.size.width.toFloat()
                    val height = coords.size.height.toFloat()
                    val cx = topLeft.x + width / 2f
                    val cy = topLeft.y + height / 2f
                    val ch = if (k.label.isNotEmpty()) k.label.lowercase() else ""
                    if (ch.isNotEmpty()) {
                        centroids.add(KeyCentroid(ch, cx, cy))
                        totalWidth += width
                        count++
                    }
                }
            }
        }
        val keyWidthPx = if (count > 0) totalWidth / count else 60f
        Pair(centroids, keyWidthPx)
    }

    // Clear key coordinate cache whenever primary active layout changes (not during sliding symbol mode)
    LaunchedEffect(layout.id) {
        cellCoordinatesMap.clear()
        gestureTrail = emptyList()
    }

    LaunchedEffect(isSlidingSymbolMode, isSlidingShiftMode) {
        gestureTrail = emptyList()
        cellCoordinatesMap.clear()
        if (!isSlidingSymbolMode && !isSlidingShiftMode) {
            slidingDwellJob?.cancel()
            slidingDwellJob = null
            slidingHoveredKey = null
            slidingHoveredBounds = null
        }
    }

    LaunchedEffect(inputSessionId) {
        if (inputSessionId > 0) {
            isSlidingSymbolMode = false
            isSlidingShiftMode = false
            slidingInitiatorKey = null
            quickDragState = null
            isToolsDrawerOpen = false
            isToolbarOptionsVisible = false
            isEmojiOpen = false
            isTextEditOpen = false
            isClipboardOpen = false
            undoActionPillState = null
            isResizerOpen = false
            isSuggestionsExpanded = false
            isLanguagePickerOpen = false
            livePredictJob?.cancel()
            livePredictJob = null
            fadingTrailJob?.cancel()
            fadingTrailJob = null
            gestureTrail = emptyList()
            fadingTrail = emptyList()
            liveGestureCandidate = null
            clipboardManager?.syncFromSystemClipboard(context)
        }
    }

    LaunchedEffect(suggestionBarMode) {
        if (suggestionBarMode == KiebordPreferences.SUGGESTION_BAR_MODE_SCROLLABLE) {
            isSuggestionsExpanded = false
        }
    }

    val isKeysInteractive = isInteractive && !isResizerOpen
    val hasTopNumberRow = showNumberRow && !isNumberLayout && !isMoreSymbolsLayout
    val keyboardContentHeight = remember(
        isNumberLayout,
        targetKeyboardHeight,
        keyboardRows,
        bottomRowHeight,
        bornoEasyRowHeight,
        standardRowHeight,
        scaleFactor,
        hasTopNumberRow,
        isDisplayBornoEasy,
        isMoreSymbolsLayout,
        showNumberRow
    ) {
        if (isNumberLayout) {
            targetKeyboardHeight
        } else {
            var total = 0.dp
            keyboardRows.forEachIndexed { index, _ ->
                val isBottom = index == keyboardRows.lastIndex
                val isTopNumber = hasTopNumberRow && index == 0
                val isMoreSymbolsTopRow = isMoreSymbolsLayout && showNumberRow && index == 0
                total += when {
                    isBottom -> bottomRowHeight
                    isTopNumber || isMoreSymbolsTopRow -> (50 * scaleFactor).dp
                    isDisplayBornoEasy -> bornoEasyRowHeight
                    else -> standardRowHeight
                }
            }
            total
        }
    }

    val emojiRowHeight = if (isLandscape) (37 * scaleFactor).coerceIn(30f, 44f).dp else (43 * scaleFactor).coerceIn(36f, 50f).dp
    val isEmojiRowVisible = showEmojiRow && !isEmojiSearchOpen && !isToolsDrawerOpen &&
        !isSuggestionsExpanded && !isTextEditOpen && !isClipboardOpen && !isEmojiOpen
    val effectiveEmojiRowHeight = if (isEmojiRowVisible) emojiRowHeight else 0.dp

    fun calculateTotalKeyboardHeight(rowHeightDp: Dp): Dp {
        val s = (rowHeightDp.value / (if (activeBaseRowHeight.value > 0f) activeBaseRowHeight.value else 61f)).coerceIn(0.4f, 1.6f)
        val tbHeight = if (isLandscape) (38 * s).coerceIn(32f, 44f).dp else (44 * s).dp
        val eRowHeight = if (showEmojiRow) (if (isLandscape) (37 * s).coerceIn(30f, 44f).dp else (43 * s).dp) else 0.dp
        val bRow = (if (isLandscape) baseLandscapeBottomRowHeight else baseStandardPortraitRowHeight) * s
        val content = if (isNumberLayout) {
            val topNum = if (showNumberRow) (50 * s).dp else 0.dp
            val stdRow = (if (isLandscape) baseLandscapeBottomRowHeight else baseStandardPortraitRowHeight) * s
            val bEasyRow = (if (isLandscape) baseLandscapeBornoEasyRowHeight else baseBornoEasyPortraitRowHeight) * s
            if (isPrimaryBornoEasy) {
                topNum + (bEasyRow * 5) + bRow
            } else {
                topNum + (stdRow * 3) + bRow
            }
        } else {
            var total = 0.dp
            val stdRow = (if (isLandscape) baseLandscapeBottomRowHeight else baseStandardPortraitRowHeight) * s
            val bEasyRow = (if (isLandscape) baseLandscapeBornoEasyRowHeight else baseBornoEasyPortraitRowHeight) * s
            keyboardRows.forEachIndexed { index, _ ->
                val isBottom = index == keyboardRows.lastIndex
                val isTopNumber = hasTopNumberRow && index == 0
                val isMoreSymbolsTopRow = isMoreSymbolsLayout && showNumberRow && index == 0
                total += when {
                    isBottom -> bRow
                    isTopNumber || isMoreSymbolsTopRow -> (50 * s).dp
                    isDisplayBornoEasy -> bEasyRow
                    else -> stdRow
                }
            }
            total
        }
        return tbHeight + content + eRowHeight
    }

    val committedKeyboardHeight = toolbarHeight + keyboardContentHeight + effectiveEmojiRowHeight
    val committedTotalHeight = popupHeadroomDp + committedKeyboardHeight + totalBottomPadding
    val dragKeyboardHeight = calculateTotalKeyboardHeight(dragRowHeight)
    val dragBottomPadding = (effectiveNavBarBottom + dragChinPadding).coerceAtLeast(0.dp)
    val dragTotalHeight = popupHeadroomDp + dragKeyboardHeight + dragBottomPadding

    // Stable envelope height for the resizer window to eliminate GPU buffer recreation & relayout flicker on lower Android versions
    val resizerEnvelopeHeight = remember(isLandscape, activeBaseRowHeight, effectiveNavBarBottom) {
        val maxAllowedRowHeight = (activeBaseRowHeight * 1.45f).coerceAtMost(90.dp)
        val maxKbHeight = calculateTotalKeyboardHeight(maxAllowedRowHeight)
        val maxBottom = (effectiveNavBarBottom + 64.dp).coerceAtLeast(0.dp)
        popupHeadroomDp + maxKbHeight + maxBottom
    }
    val activeRootHeight = if (isResizerOpen) maxOf(committedTotalHeight, resizerEnvelopeHeight) else committedTotalHeight

    val handleToolbarAction: (ToolbarAction) -> Unit = { action ->
        when (action) {
            ToolbarAction.TEXT_EDIT -> {
                isTextEditOpen = true
                isToolsDrawerOpen = false
                isEmojiOpen = false
                isResizerOpen = false
                isSuggestionsExpanded = false
            }
            ToolbarAction.EMOJI -> {
                if (isEmojiSearchOpen) {
                    isEmojiSearchOpen = false
                    emojiSearchQuery = ""
                }
                isEmojiOpen = !isEmojiOpen
                if (isEmojiOpen) {
                    isToolsDrawerOpen = false
                    isClipboardOpen = false
                    isTextEditOpen = false
                    isResizerOpen = false
                    isSuggestionsExpanded = false
                }
            }
            ToolbarAction.RESIZER -> {
                if (isFloating) {
                    performHaptic(HapticFeedbackType.LongPress)
                } else {
                    if (!isResizerOpen) {
                        savedRowHeight = keyRowHeight
                        savedChinPadding = chinPadding
                        savedHorizontalMargin = horizontalMargin
                        dragRowHeight = keyRowHeight
                        dragChinPadding = chinPadding
                        dragHorizontalMargin = horizontalMargin
                        isEmojiOpen = false
                        isToolsDrawerOpen = false
                        isSuggestionsExpanded = false
                    }
                    isResizerOpen = !isResizerOpen
                }
            }
            ToolbarAction.NUMBER_ROW_TOGGLE -> {
                showNumberRow = !showNumberRow
                onNumberRowToggle?.invoke(showNumberRow)
            }
            ToolbarAction.SECONDARY_KEYS_TOGGLE -> {
                showSecondaryKeys = !showSecondaryKeys
                onSecondaryKeysToggle?.invoke(showSecondaryKeys)
            }
            ToolbarAction.KEY_POPUP_TOGGLE -> {
                showKeyPopups = !showKeyPopups
                onKeyPopupsToggle?.invoke(showKeyPopups)
            }
            ToolbarAction.THEME_TOGGLE -> {
                isToolsDrawerOpen = false
                isToolbarOptionsVisible = false
                isEmojiOpen = false
                isTextEditOpen = false
                isClipboardOpen = false
                isResizerOpen = false
                isSuggestionsExpanded = false
                isLanguagePickerOpen = false
                onOpenThemeSettings()
            }
            ToolbarAction.CLIPBOARD -> {
                isToolsDrawerOpen = false
                isEmojiOpen = false
                isResizerOpen = false
                isSuggestionsExpanded = false
                isClipboardOpen = !isClipboardOpen
                if (isClipboardOpen) {
                    clipboardManager?.syncFromSystemClipboard(context)
                }
            }
            ToolbarAction.FLOATING -> {
                isToolsDrawerOpen = false
                isToolbarOptionsVisible = false
                isEmojiOpen = false
                isTextEditOpen = false
                isClipboardOpen = false
                isResizerOpen = false
                isSuggestionsExpanded = false
                isLanguagePickerOpen = false
                val nextFloating = !isFloating
                isFloating = nextFloating
                if (nextFloating) {
                    oneHandedSide = "OFF"
                    onOneHandedModeChange?.invoke("OFF")
                }
                onFloatingModeChange?.invoke(nextFloating)
            }
            ToolbarAction.ONE_HANDED -> {
                if (isFloating) {
                    performHaptic(HapticFeedbackType.LongPress)
                } else {
                    oneHandedSide = when (oneHandedSide) {
                        "OFF" -> "RIGHT"
                        "RIGHT" -> "LEFT"
                        else -> "OFF"
                    }
                    onOneHandedModeChange?.invoke(oneHandedSide)
                }
            }
            ToolbarAction.SETTINGS -> {
                isToolsDrawerOpen = false
                isToolbarOptionsVisible = false
                isEmojiOpen = false
                isTextEditOpen = false
                isClipboardOpen = false
                isResizerOpen = false
                isSuggestionsExpanded = false
                isLanguagePickerOpen = false
                try {
                    val activityClass = runCatching {
                        Class.forName("${context.packageName}.MainActivity")
                    }.getOrNull() ?: Class.forName("com.codepotro.borno.kiebord.MainActivity")
                    val intent = android.content.Intent(context, activityClass).apply {
                        flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK or
                            android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP or
                            android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP
                        putExtra("navigate_to", "main")
                    }
                    context.startActivity(intent)
                } catch (_: Exception) {}
            }
            ToolbarAction.TRADITIONAL_MODE -> {
                traditionalMode = !traditionalMode
                onTraditionalModeToggle?.invoke(traditionalMode)
            }
            ToolbarAction.UNDO -> {
                onTextAction(TextAction.UNDO, false)
            }
            ToolbarAction.REDO -> {
                onTextAction(TextAction.REDO, false)
            }
            ToolbarAction.NUMBER_PAD -> {
                isToolsDrawerOpen = false
                isEmojiOpen = false
                isTextEditOpen = false
                isClipboardOpen = false
                isResizerOpen = false
                isSuggestionsExpanded = false
                onToggleNumberPad()
            }
            ToolbarAction.EMOJI_ROW_TOGGLE -> {
                showEmojiRow = !showEmojiRow
                onEmojiRowToggle?.invoke(showEmojiRow)
            }
            ToolbarAction.SHARE_BORNO -> Unit
        }
    }

    val isActionActive: (ToolbarAction) -> Boolean = { action ->
        when (action) {
            ToolbarAction.NUMBER_ROW_TOGGLE -> showNumberRow
            ToolbarAction.EMOJI_ROW_TOGGLE -> showEmojiRow
            ToolbarAction.SECONDARY_KEYS_TOGGLE -> showSecondaryKeys
            ToolbarAction.KEY_POPUP_TOGGLE -> showKeyPopups
            ToolbarAction.TRADITIONAL_MODE -> traditionalMode
            ToolbarAction.FLOATING -> isFloating
            ToolbarAction.ONE_HANDED -> !isFloating && oneHandedSide != "OFF"
            ToolbarAction.EMOJI -> isEmojiOpen
            ToolbarAction.RESIZER -> !isFloating && isResizerOpen
            ToolbarAction.CLIPBOARD -> isClipboardOpen
            ToolbarAction.TEXT_EDIT -> isTextEditOpen
            ToolbarAction.NUMBER_PAD -> isNumberPadActive
            else -> false
        }
    }

    val isToolEnabled: (ToolbarAction) -> Boolean = { action ->
        if (isFloating) {
            action != ToolbarAction.RESIZER && action != ToolbarAction.ONE_HANDED
        } else {
            true
        }
    }

    val oneHandedStart = if (!isFloating && oneHandedSide == "RIGHT") oneHandedSideWidth.dp else 0.dp
    val oneHandedEnd = if (!isFloating && oneHandedSide == "LEFT") oneHandedSideWidth.dp else 0.dp

    val keyboardStartInset = if (isFloating) 8.dp else (6.dp + horizontalMargin + oneHandedStart)
    val keyboardEndInset = if (isFloating) 8.dp else (6.dp + horizontalMargin + oneHandedEnd)

    val floatingWidthPx = with(density) { currentFloatingWidth.toPx() }
    val rootHeightPx = rootCoordinates?.size?.height?.toFloat() ?: with(density) { screenHeightDp.toPx() }
    val keyboardSurfaceHeightPx = with(density) { (toolbarHeight + keyboardContentHeight + totalBottomPadding + 26.dp).toPx() }

    val floatingScreenMarginPx = with(density) { 16.dp.toPx() }
    val maxFloatingX = (rootWidthPx - floatingWidthPx - floatingScreenMarginPx * 2).coerceAtLeast(0f)
    val maxFloatingY = (rootHeightPx - keyboardSurfaceHeightPx - floatingScreenMarginPx * 2).coerceAtLeast(0f)

    val baseFloatingX = floatingScreenMarginPx + maxFloatingX * currentFloatingRatioX.coerceIn(0f, 1f)
    val baseFloatingY = floatingScreenMarginPx + maxFloatingY * currentFloatingRatioY.coerceIn(0f, 1f)

    val activeFloatingX = (baseFloatingX + floatingDragOffset.x).coerceIn(
        floatingScreenMarginPx,
        floatingScreenMarginPx + maxFloatingX
    )
    val activeFloatingY = (baseFloatingY + floatingDragOffset.y).coerceIn(
        floatingScreenMarginPx,
        floatingScreenMarginPx + maxFloatingY
    )

    val keyboardLeftPx = if (isFloating) {
        activeFloatingX + with(density) { keyboardStartInset.toPx() }
    } else {
        with(density) { keyboardStartInset.toPx() }
    }
    val keyboardRightPx = if (isFloating) {
        activeFloatingX + with(density) { (currentFloatingWidth - keyboardEndInset).toPx() }
    } else if (rootWidthPx > 0f) {
        (rootWidthPx - with(density) { keyboardEndInset.toPx() }).coerceAtLeast(keyboardLeftPx + 100f)
    } else {
        1080f
    }
    val effectiveKeyboardWidthPx = (keyboardRightPx - keyboardLeftPx).coerceAtLeast(100f)

    val containerBgBrush = remember(colorScheme, rootWidthPx) {
        colorScheme.themeSpec?.background?.toBrush(Size(if (rootWidthPx > 0f) rootWidthPx else 1080f, 1000f))
    }
    val bgImageSpec = colorScheme.themeSpec?.background?.image

    val moreKeysOverlayAlpha by animateFloatAsState(
        targetValue = if (moreKeysState != null) 1f else 0f,
        animationSpec = tween(durationMillis = 150),
        label = "moreKeysOverlayAlpha"
    )

    val KeyboardInnerContent: @Composable () -> Unit = {
        val topBarStartInset = if (isFloating) 0.dp else oneHandedStart
        val topBarEndInset = if (isFloating) 0.dp else oneHandedEnd

        if (isTextEditOpen) {
            TextEditPanelView(
                onTextAction = { action, isSelecting -> onTextAction(action, isSelecting) },
                onClose = { isTextEditOpen = false },
                height = toolbarHeight + keyboardContentHeight
            )
        } else if (isClipboardOpen) {
            ClipboardPanelView(
                clips = clipboardManager?.clips ?: emptyList(),
                onClipClick = { clip ->
                    onPasteClip(clip.text)
                    clipboardManager?.clearQuickPaste()
                    undoActionPillState = UndoRedoPillState.CanUndo(text = clip.text, isPaste = true)
                },
                onTogglePin = { clip -> clipboardManager?.togglePin(clip) },
                onDeleteClip = { clip -> clipboardManager?.deleteClip(clip) },
                onClearAll = { clipboardManager?.clearAllUnpinned() },
                onOpenSettings = {
                    isClipboardOpen = false
                    onOpenClipboardSettings()
                },
                onClose = { isClipboardOpen = false },
                height = toolbarHeight + keyboardContentHeight,
                onSyncRequest = { clipboardManager?.syncFromSystemClipboard(context) }
            )
        } else if (isEmojiOpen) {
            EmojiPanelView(
                onEmojiSelected = { emoji ->
                    val keyDef = KeyDef(label = emoji, type = KeyType.CHARACTER)
                    onKeyPress(keyDef)
                    onKeyTap(keyDef)
                    onRecordRecentEmoji?.invoke(emoji)
                },
                onDelete = {
                    val keyDef = KeyDef(type = KeyType.DELETE)
                    onKeyPress(keyDef)
                    onKeyTap(keyDef)
                },
                onSpace = {
                    val keyDef = KeyDef(label = " ", type = KeyType.CHARACTER)
                    onKeyPress(keyDef)
                    onKeyTap(keyDef)
                },
                onClose = {
                    performHaptic(HapticFeedbackType.TextHandleMove)
                    isEmojiOpen = false
                },
                onOpenSearch = {
                    performHaptic(HapticFeedbackType.TextHandleMove)
                    isEmojiOpen = false
                    isEmojiSearchOpen = true
                    emojiSearchQuery = ""
                },
                height = toolbarHeight + keyboardContentHeight,
                showKeyBorders = showKeyBorders
            )
        } else {
            if (isEmojiSearchOpen) {
                EmojiSearchCardView(
                    query = emojiSearchQuery,
                    onQueryChange = { emojiSearchQuery = it },
                    onEmojiSelected = { emoji ->
                        val keyDef = KeyDef(label = emoji, type = KeyType.CHARACTER)
                        onKeyPress(keyDef)
                        onKeyTap(keyDef)
                        onRecordRecentEmoji?.invoke(emoji)
                    },
                    onClose = {
                        performHaptic(HapticFeedbackType.TextHandleMove)
                        isEmojiSearchOpen = false
                        emojiSearchQuery = ""
                        isEmojiOpen = true
                    },
                    colorScheme = colorScheme,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = topBarStartInset, end = topBarEndInset)
                )
            } else if (showEmojiRow) {
                EmojiRowBar(
                    recentEmojis = recentEmojis,
                    onEmojiClick = { emoji ->
                        onKeyTap(KeyDef(label = emoji, commitText = emoji, type = KeyType.CHARACTER))
                    },
                    onRecordRecentEmoji = onRecordRecentEmoji,
                    onHideClick = {
                        showEmojiRow = false
                        onEmojiRowToggle?.invoke(false)
                    },
                    height = emojiRowHeight,
                    scaleFactor = scaleFactor,
                    colorScheme = colorScheme,
                    vibrationEnabled = vibrationEnabled,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = topBarStartInset, end = topBarEndInset)
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(toolbarHeight)
                    .padding(start = topBarStartInset, end = topBarEndInset)
                    .background(
                        if (isToolsDrawerOpen) colorScheme.containerBackground.copy(alpha = 1f)
                        else colorScheme.effectiveToolbarBackground
                    )
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                    val isToolsExpanded = isToolsDrawerOpen || isToolbarOptionsVisible || isSuggestionsExpanded
                    val leadingButtonSize = (34 * scaleFactor).coerceAtLeast(25f).dp
                    val micButtonSize = (34 * scaleFactor).coerceAtLeast(25f).dp
                    val trailingBalanceWidth = if (voiceTypingEnabled) {
                        (leadingButtonSize - micButtonSize).coerceAtLeast(0.dp)
                    } else {
                        leadingButtonSize
                    }

                    // 1. Expand / Back / Toggle button on far left
                    Box(
                        modifier = Modifier
                            .size(leadingButtonSize)
                            .clip(CircleShape)
                            .background(
                                if (isToolsExpanded) Color(0x22FFFFFF) else Color.Transparent
                            )
                            .clickable {
                                when {
                                    isSuggestionsExpanded -> {
                                        performHaptic(HapticFeedbackType.TextHandleMove)
                                        isSuggestionsExpanded = false
                                    }
                                    isToolsDrawerOpen || isToolbarOptionsVisible -> {
                                        isToolsDrawerOpen = false
                                        isToolbarOptionsVisible = false
                                    }
                                    else -> {
                                        isToolsDrawerOpen = true
                                        isToolbarOptionsVisible = true
                                    }
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        if (isToolsExpanded) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                                contentDescription = when {
                                    isSuggestionsExpanded -> "Collapse Suggestions"
                                    isToolsDrawerOpen -> "Close Tools"
                                    isToolbarOptionsVisible -> "Show Suggestions"
                                    else -> "All Tools"
                                },
                                tint = colorScheme.effectiveToolbarForeground,
                                modifier = Modifier.size((20 * scaleFactor).coerceAtLeast(16f).dp)
                            )
                        } else if (isIncognitoMode) {
                            Icon(
                                painter = painterResource(R.drawable.ic_incognito),
                                contentDescription = "Incognito Mode",
                                tint = colorScheme.effectiveToolbarForeground,
                                modifier = Modifier.size((24 * scaleFactor).coerceAtLeast(18f).dp)
                            )
                        } else {
                            Icon(
                                painter = painterResource(R.drawable.ic_kiebord_top),
                                contentDescription = "All Tools",
                                tint = colorScheme.effectiveToolbarForeground,
                                modifier = Modifier.size((26 * scaleFactor).coerceAtLeast(18f).dp)
                            )
                        }
                    }

                    val isGesturing = gestureTrail.isNotEmpty() || (fadingTrail.isNotEmpty() && suggestions.isEmpty())
                    val displayedSuggestions = when {
                        isGesturing -> {
                            if (liveGestureCandidate != null) {
                                listOf(SuggestionCandidate(text = liveGestureCandidate!!, isPrimary = true))
                            } else {
                                emptyList()
                            }
                        }
                        else -> suggestions
                    }

                    // 2. Main content: Voice Typing Status OR Suggestion Strip OR Quick Paste/Undo Pill OR Toolbar Options
                    if (voiceInputState is VoiceInputState.Listening) {
                        VoiceTypingCenterBar(
                            statusText = voiceInputState.statusText,
                            scaleFactor = scaleFactor,
                            colorScheme = colorScheme,
                            onClick = onDismissVoiceTyping,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                        )
                    } else if (displayedSuggestions.isNotEmpty() && !isToolbarOptionsVisible && !isToolsDrawerOpen) {
                        SuggestionStripView(
                            candidates = displayedSuggestions,
                            onCandidateClick = { candidate ->
                                isSuggestionsExpanded = false
                                undoActionPillState = null
                                clipboardManager?.clearQuickPaste()
                                onCandidateSelect(candidate)
                            },
                            onCandidateLongClick = { candidate ->
                                unlearnCandidateTarget = candidate
                            },
                            mode = suggestionBarMode,
                            isExpanded = isSuggestionsExpanded,
                            scaleFactor = scaleFactor,
                            isFloating = isFloating,
                            onExpandToggle = if (suggestionBarMode == KiebordPreferences.SUGGESTION_BAR_MODE_SCROLLABLE) null else {
                                {
                                    performHaptic(HapticFeedbackType.TextHandleMove)
                                    isSuggestionsExpanded = !isSuggestionsExpanded
                                    if (isSuggestionsExpanded) {
                                        isToolsDrawerOpen = false
                                        isClipboardOpen = false
                                        isEmojiOpen = false
                                        isResizerOpen = false
                                    }
                                }
                            },
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                        )
                    } else if (!isGesturing && !isToolbarOptionsVisible && !isToolsDrawerOpen && (quickPasteClip != null || undoActionPillState != null)) {
                        val pillHeight = (35 * scaleFactor).coerceIn(24f, 40f).dp
                        val pillFontSize = (14 * scaleFactor).coerceIn(10f, 15f).sp
                        val pillIconSize = (18 * scaleFactor).coerceIn(12f, 20f).dp
                        val undoIconSize = (24 * scaleFactor).coerceIn(16f, 26f).dp
                        val pillPaddingH = (14 * scaleFactor).coerceIn(8f, 16f).dp
                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .padding(horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy((10 * scaleFactor).coerceIn(6f, 12f).dp, Alignment.CenterHorizontally)
                        ) {
                            // SHOW ONLY ONE PILL AT A TIME (never beside each other)
                            if (quickPasteClip != null) {
                                val textToPaste = quickPasteClip.text
                                val ellipsized = if (textToPaste.length > 20) textToPaste.take(20) + "…" else textToPaste
                                Row(
                                    modifier = Modifier
                                        .height(pillHeight)
                                        .clip(CircleShape)
                                        .background(colorScheme.keyBackground)
                                        .clickable {
                                            onPasteClip(textToPaste)
                                            clipboardManager?.clearQuickPaste()
                                            undoActionPillState = UndoRedoPillState.CanUndo(text = textToPaste, isPaste = true)
                                        }
                                        .padding(horizontal = pillPaddingH),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(7.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Outlined.Assignment,
                                        contentDescription = "Quick paste",
                                        tint = colorScheme.effectiveToolbarForeground,
                                        modifier = Modifier.size(pillIconSize)
                                    )
                                    Text(
                                        text = ellipsized,
                                        color = colorScheme.effectiveToolbarForeground,
                                        fontSize = pillFontSize,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            } else if (undoActionPillState != null) {
                                when (val state = undoActionPillState!!) {
                                    is UndoRedoPillState.CanUndo -> {
                                        Row(
                                            modifier = Modifier
                                                .height(pillHeight)
                                                .clip(CircleShape)
                                                .background(colorScheme.keyBackground)
                                                .clickable {
                                                    if (state.isBatchDelete) {
                                                        onPasteClip(state.text)
                                                    } else {
                                                        onTextAction(TextAction.UNDO, false)
                                                    }
                                                    undoActionPillState = UndoRedoPillState.CanRedo(
                                                        text = state.text,
                                                        isPaste = state.isPaste,
                                                        isBatchDelete = state.isBatchDelete
                                                    )
                                                }
                                                .padding(horizontal = pillPaddingH),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(7.dp)
                                        ) {
                                            Icon(
                                                painter = painterResource(id = R.drawable.ic_undo),
                                                contentDescription = "Undo",
                                                tint = colorScheme.keyForeground,
                                                modifier = Modifier.size(undoIconSize)
                                            )
                                            Text(
                                                text = "Undo",
                                                color = colorScheme.keyForeground,
                                                fontSize = pillFontSize,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                        }
                                    }
                                    is UndoRedoPillState.CanRedo -> {
                                        val redoFg = ThemeColorUtils.ensureContrast(
                                            foreground = colorScheme.actionKeyForeground,
                                            background = colorScheme.actionKeyBackground,
                                            minContrast = 4.0f
                                        )
                                        Row(
                                            modifier = Modifier
                                                .height(pillHeight)
                                                .clip(CircleShape)
                                                .background(colorScheme.actionKeyBackground)
                                                .clickable {
                                                    if (state.isBatchDelete) {
                                                        onTextAction(TextAction.UNDO, false)
                                                    } else {
                                                        onTextAction(TextAction.REDO, false)
                                                    }
                                                    undoActionPillState = UndoRedoPillState.CanUndo(
                                                        text = state.text,
                                                        isPaste = state.isPaste,
                                                        isBatchDelete = state.isBatchDelete
                                                    )
                                                }
                                                .padding(horizontal = pillPaddingH),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(7.dp)
                                        ) {
                                            Icon(
                                                painter = painterResource(id = R.drawable.ic_redo),
                                                contentDescription = "Redo",
                                                tint = redoFg,
                                                modifier = Modifier.size(undoIconSize)
                                            )
                                            Text(
                                                text = "Redo",
                                                color = redoFg,
                                                fontSize = pillFontSize,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        if (trailingBalanceWidth > 0.dp) {
                            Spacer(modifier = Modifier.width(trailingBalanceWidth))
                        }
                    } else {
                        ReorderableToolbar(
                            pinnedTools = pinnedTools,
                            onPinnedToolsChange = { updateTools(it, drawerTools) },
                            onInterchangeWithDrawer = { toolbarAct, drawerAct ->
                                interchangeTools(toolbarAct, drawerAct)
                            },
                            onMoveToDrawer = { toolbarAct ->
                                moveToolbarToDrawer(toolbarAct)
                            },
                            onActionClick = handleToolbarAction,
                            rootCoordinates = rootCoordinates,
                            onReportItemBounds = { act, bounds -> toolbarItemBounds[act] = bounds },
                            hoveredDrawerAction = hoveredDrawerAction,
                            isOverDrawerBlankSpace = isOverDrawerBlankSpace,
                            hoveredByDrawerAction = hoveredToolbarAction,
                            isActionActive = isActionActive,
                            isToolEnabled = isToolEnabled,
                            isEmojiActive = isEmojiOpen,
                            isResizerActive = isResizerOpen,
                            isNumberRowActive = showNumberRow,
                            isSecondaryKeysActive = showSecondaryKeys,
                            isOneHandedActive = oneHandedSide != "OFF",
                            isTextEditActive = isTextEditOpen,
                            isClipboardActive = isClipboardOpen,
                            isTraditionalModeActive = traditionalMode,
                            isNumberPadActive = isNumberPadActive,
                            isEmojiRowActive = showEmojiRow,
                            isCustomizeMode = isToolsDrawerOpen,
                            scaleFactor = scaleFactor,
                            onDraggingStateChange = { action, fingerPos ->
                                activeDraggingFromToolbar = action
                                currentFingerInRoot = fingerPos
                            },
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                        )
                    }

                    // 3. Right edge key: Voice key with filled mic icon
                    if (voiceTypingEnabled) {
                        val isVoiceActive = voiceInputState is VoiceInputState.Listening
                        val micIconSize = (22.5f * scaleFactor).coerceAtLeast(17f).dp

                        val activeAlpha = if (isVoiceActive) {
                            val infiniteTransition = rememberInfiniteTransition(label = "VoicePulse")
                            val pulseAlpha by infiniteTransition.animateFloat(
                                initialValue = 0.55f,
                                targetValue = 1.0f,
                                animationSpec = infiniteRepeatable(
                                    animation = tween(650, easing = LinearEasing),
                                    repeatMode = RepeatMode.Reverse
                                ),
                                label = "VoicePulseAlpha"
                            )
                            pulseAlpha
                        } else {
                            1.0f
                        }

                        val micBgColor = when {
                            isVoiceActive -> colorScheme.accentColor.copy(alpha = activeAlpha)
                            colorScheme.keyBackground.alpha < 0.85f -> colorScheme.containerBackground
                            else -> colorScheme.keyBackground
                        }
                        Box(
                            modifier = Modifier
                                .size(micButtonSize)
                                .clip(CircleShape)
                                .background(micBgColor)
                                .clickable {
                                    if (isVoiceActive) {
                                        onDismissVoiceTyping()
                                    } else {
                                        onStartVoiceTyping()
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.ic_mic),
                                contentDescription = if (isVoiceActive) "Stop Voice Typing" else "Start Voice Typing",
                                tint = if (isVoiceActive) Color.White else ThemeColorUtils.ensureContrast(colorScheme.keyForeground, micBgColor),
                                modifier = Modifier.size(micIconSize)
                            )
                        }
                    }
                }

                // Main content area below the top bar (height = keyboardContentHeight)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(keyboardContentHeight)
                        .padding(start = keyboardStartInset, end = keyboardEndInset)
                ) {
                    val isAnyPanelOpen = (isSuggestionsExpanded && suggestions.isNotEmpty()) || isToolsDrawerOpen || isClipboardOpen || isEmojiOpen
                    val areKeysInteractive = isKeysInteractive && !isAnyPanelOpen
                    var keyboardColumnCoords by remember { mutableStateOf<LayoutCoordinates?>(null) }

                    val handleKeyPress: (KeyDef) -> Unit = { keyDef ->
                        if (voiceInputState is VoiceInputState.Listening) {
                            onDismissVoiceTyping()
                        }
                        if (moreKeysState != null) {
                            moreKeysState = null
                        }
                        undoActionPillState = null
                        clipboardManager?.clearQuickPaste()
                        onKeyPress(keyDef)
                    }

                    // 1. Keyboard rows (ALWAYS retained in the composition tree for instant zero-lag collapse!)
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .onGloballyPositioned { keyboardColumnCoords = it }
                            .pointerInput(areKeysInteractive) {
                                if (!areKeysInteractive) return@pointerInput
                                awaitEachGesture {
                                    val down = awaitFirstDown(requireUnconsumed = true)
                                    if (moreKeysState != null) return@awaitEachGesture
                                    val up = waitForUpOrCancellation()
                                    if (up != null && !up.isConsumed && moreKeysState == null) {
                                        up.consume()
                                        val rCoords = rootCoordinates ?: return@awaitEachGesture
                                        val cCoords = keyboardColumnCoords ?: return@awaitEachGesture
                                        if (!rCoords.isAttached || !cCoords.isAttached) return@awaitEachGesture
                                        val rootTapPos = rCoords.localPositionOf(cCoords, up.position)
                                        var closestKey: KeyDef? = null
                                        var minDistance = Float.MAX_VALUE

                                        for (r in keyboardRows.indices) {
                                            val row = keyboardRows[r]
                                            for (c in row.keys.indices) {
                                                val coords = cellCoordinatesMap.get((r shl 16) or (c and 0xFFFF))
                                                if (coords != null && coords.isAttached) {
                                                    val keyTopLeft = rCoords.localPositionOf(coords, Offset.Zero)
                                                    val keyWidth = coords.size.width.toFloat()
                                                    val keyHeight = coords.size.height.toFloat()
                                                    val keyRect = Rect(keyTopLeft, Size(keyWidth, keyHeight))
                                                    val dx = when {
                                                        rootTapPos.x < keyRect.left -> keyRect.left - rootTapPos.x
                                                        rootTapPos.x > keyRect.right -> rootTapPos.x - keyRect.right
                                                        else -> 0f
                                                    }
                                                    val dy = when {
                                                        rootTapPos.y < keyRect.top -> keyRect.top - rootTapPos.y
                                                        rootTapPos.y > keyRect.bottom -> rootTapPos.y - keyRect.bottom
                                                        else -> 0f
                                                    }
                                                    val dist = Math.hypot(dx.toDouble(), dy.toDouble()).toFloat()
                                                    if (dist < minDistance) {
                                                        minDistance = dist
                                                        val rawKey = row.keys[c]
                                                        closestKey = if (rawKey.type == KeyType.SPACE && rawKey.label.isBlank()) {
                                                            val spaceLabel = if (displayLayout.isSymbolsLayout()) {
                                                                "Space"
                                                            } else {
                                                                displayLayout.displayName ?: PredefinedLayouts.displayName(displayLayout.id)
                                                            }
                                                            rawKey.copy(label = spaceLabel)
                                                        } else if (rawKey.type == KeyType.SWITCH_LANGUAGE && showEmojiKey) {
                                                            rawKey.copy(type = KeyType.EMOJI, icon = "emoji")
                                                        } else if (isEmailField && r == keyboardRows.lastIndex && rawKey.label == "," && !displayLayout.isSymbolsLayout()) {
                                                            rawKey.copy(label = "@", commitText = "@", shiftedCommitText = "@", shiftedLabel = "@")
                                                        } else {
                                                            rawKey
                                                        }
                                                    }
                                                }
                                            }
                                        }

                                        val maxProximityDistPx = with(density) { 68.dp.toPx() }
                                        if (closestKey != null && minDistance <= maxProximityDistPx) {
                                            handleKeyPress(closestKey)
                                            handleKeyTapUnified(closestKey)
                                            performHaptic(HapticFeedbackType.TextHandleMove)
                                        }
                                    }
                                }
                            }
                    ) {
                        // Shared MoreKeys & key press handlers for NumberRowBar and keyboard rows

                    val openMoreKeysForAnchor: (KeyDef, Rect) -> Unit = openMoreKeys@{ keyDef, keyRect ->
                        val rawItems = if (keyDef.isEnterKey()) keyDef.resolveActionMoreKeys(keyDef.moreKeys) else keyDef.moreKeys
                        val effectiveItems = if (longPressPrimaryShortcutsEnabled) {
                            rawItems
                        } else {
                            rawItems.filterNot { KeyDef.isPrimaryShortcutToken(it) }
                        }
                        if (effectiveItems.isEmpty()) return@openMoreKeys
                        val dims = MoreKeysLayoutHelper.calculateDimensions(
                            density = density.density,
                            scaleFactor = scaleFactor,
                            keyboardWidthPx = effectiveKeyboardWidthPx,
                            maxColumns = keyDef.moreKeysColumns ?: 5
                        )
                        val anchorXInKeyboard = (keyRect.center.x - keyboardLeftPx).coerceAtLeast(0f)
                        val layoutResult = MoreKeysLayoutHelper.computeLayout(
                            itemCount = effectiveItems.size,
                            columnsHint = keyDef.moreKeysColumns,
                            anchorCenterX = anchorXInKeyboard,
                            keyboardWidthPx = effectiveKeyboardWidthPx,
                            columnWidthPx = dims.itemWidthPx + dims.colGapPx
                        )
                        val totalWidthPx = dims.totalWidthPx(layoutResult.numColumns)
                        val totalHeightPx = dims.totalHeightPx(layoutResult.numRows)

                        val marginPx = with(density) { 4.dp.toPx() }
                        val minLeftPx = keyboardLeftPx + marginPx
                        val maxLeftPx = (keyboardRightPx - totalWidthPx - marginPx).coerceAtLeast(minLeftPx)
                        val idealLeft = keyRect.center.x - (layoutResult.leftKeys * (dims.itemWidthPx + dims.colGapPx) + dims.itemWidthPx / 2f + dims.horizontalPaddingPx)
                        val clampedLeft = idealLeft.coerceIn(minLeftPx, maxLeftPx)

                        val gapDp = (10 * scaleFactor).coerceIn(6f, 14f).dp
                        val gapPx = with(density) { gapDp.toPx() }
                        val idealTop = keyRect.top - totalHeightPx - gapPx
                        val minTopDp = 4.dp
                        val minTopPx = with(density) { minTopDp.toPx() }
                        val popupTop = if (idealTop < minTopPx) minTopPx else idealTop

                        val pivotFractionX = if (totalWidthPx > 0f) {
                            ((keyRect.center.x - clampedLeft) / totalWidthPx).coerceIn(0.06f, 0.94f)
                        } else {
                            0.5f
                        }

                        // Generous hit-test bounding box with slop so user doesn't accidentally cancel
                        val slopHorizontalPx = with(density) { (48 * scaleFactor).dp.toPx() }
                        val slopTopPx = with(density) { (48 * scaleFactor).dp.toPx() }
                        val slopBottomPx = with(density) { (64 * scaleFactor).dp.toPx() }

                        val allowedLeft = minOf(clampedLeft, keyRect.left) - slopHorizontalPx
                        val allowedRight = maxOf(clampedLeft + totalWidthPx, keyRect.right) + slopHorizontalPx
                        val allowedTop = popupTop - slopTopPx
                        val allowedBottom = keyRect.bottom + slopBottomPx

                        selectedMoreKeyIndex = layoutResult.defaultItemIndex
                        moreKeysState = MoreKeysPopupState(
                            anchorKey = keyDef,
                            anchorRect = keyRect,
                            items = effectiveItems,
                            dims = dims,
                            layoutResult = layoutResult,
                            clampedLeft = clampedLeft,
                            popupTop = popupTop,
                            pivotFractionX = pivotFractionX,
                            allowedLeft = allowedLeft,
                            allowedRight = allowedRight,
                            allowedTop = allowedTop,
                            allowedBottom = allowedBottom,
                            selectedIndex = layoutResult.defaultItemIndex
                        )
                    }

                    val updateMoreKeysHoverFromRoot: (Offset) -> Boolean = { fingerPosInRoot ->
                        val state = moreKeysState
                        if (state != null) {
                            val isWithinBounds = fingerPosInRoot.x in state.allowedLeft..state.allowedRight &&
                                fingerPosInRoot.y in state.allowedTop..state.allowedBottom

                            if (!isWithinBounds) {
                                false
                            } else {
                                // While finger is resting on or touching the anchor key, lock hover to default item
                                val isTouchingAnchorKey = fingerPosInRoot.y >= state.anchorRect.top - 8f &&
                                    kotlin.math.abs(fingerPosInRoot.x - state.anchorRect.center.x) <= state.anchorRect.width / 2f + 16f

                                val targetItemIndex = if (isTouchingAnchorKey) {
                                    state.layoutResult.defaultItemIndex
                                } else {
                                    val relX = fingerPosInRoot.x - (state.clampedLeft + state.dims.horizontalPaddingPx)
                                    val relY = fingerPosInRoot.y - (state.popupTop + state.dims.verticalPaddingPx)

                                    val rowStep = state.dims.itemHeightPx + state.dims.rowGapPx
                                    val colStep = state.dims.itemWidthPx + state.dims.colGapPx

                                    val hoveredRow = if (state.layoutResult.numRows > 1 && rowStep > 0f) {
                                        (relY / rowStep).toInt().coerceIn(0, state.layoutResult.numRows - 1)
                                    } else {
                                        0
                                    }

                                    val hoveredCol = if (colStep > 0f) {
                                        (relX / colStep).toInt().coerceIn(0, state.layoutResult.numColumns - 1)
                                    } else {
                                        0
                                    }

                                    state.layoutResult.grid.getOrNull(hoveredRow)?.getOrNull(hoveredCol)
                                        ?: run {
                                            val rowList = state.layoutResult.grid.getOrNull(hoveredRow) ?: emptyList()
                                            rowList.filterNotNull().minByOrNull { itemIdx ->
                                                val coords = state.layoutResult.itemPositions[itemIdx] ?: Pair(0, 0)
                                                kotlin.math.abs(coords.second - hoveredCol)
                                            }
                                        }
                                }

                                if (targetItemIndex != null && targetItemIndex != selectedMoreKeyIndex) {
                                    selectedMoreKeyIndex = targetItemIndex
                                }
                                true
                            }
                        } else {
                            false
                        }
                    }

                    val handleShowMoreKeys: (KeyDef, LayoutCoordinates) -> Unit = { keyDef, keyCoords ->
                        val rCoords = rootCoordinates
                        if (rCoords != null && rCoords.isAttached && keyCoords.isAttached) {
                            val localTopLeft = rCoords.localPositionOf(keyCoords, Offset.Zero)
                            val size = keyCoords.size
                            val keyRect = Rect(
                                localTopLeft,
                                Size(size.width.toFloat(), size.height.toFloat())
                            )
                            openMoreKeysForAnchor(keyDef, keyRect)
                        }
                    }

                    val handleMoreKeysHover: (LayoutCoordinates, Offset) -> Unit = { keyCoords, touchPosInKey ->
                        val rCoords = rootCoordinates
                        if (rCoords != null && rCoords.isAttached && keyCoords.isAttached) {
                            val fingerPosInRoot = rCoords.localPositionOf(keyCoords, touchPosInKey)
                            val isInside = updateMoreKeysHoverFromRoot(fingerPosInRoot)
                            if (!isInside) {
                                moreKeysState = null
                            }
                        }
                    }

                    val handleMoreKeysCommit: () -> Unit = {
                        val state = moreKeysState
                        if (state != null) {
                            val selectedChar = state.items.getOrNull(selectedMoreKeyIndex)
                                ?: state.items.firstOrNull()
                            if (!selectedChar.isNullOrEmpty()) {
                                when (selectedChar) {
                                    "ACTION_EMOJI", "action:emoji", "EMOJI" -> {
                                        isEmojiOpen = true
                                        isToolsDrawerOpen = false
                                        isClipboardOpen = false
                                        isResizerOpen = false
                                        isTextEditOpen = false
                                        isSuggestionsExpanded = false
                                    }
                                    "ACTION_NEW_LINE", "ACTION_NEWLINE", "action:newline", "NEWLINE" -> {
                                        onTextAction(TextAction.NEW_LINE, false)
                                    }
                                    "ACTION_SELECT_ALL", "action:select_all", "SELECT_ALL" -> {
                                        onTextAction(TextAction.SELECT_ALL, true)
                                    }
                                    "ACTION_CUT", "action:cut", "CUT" -> {
                                        onTextAction(TextAction.CUT, false)
                                    }
                                    "ACTION_COPY", "action:copy", "COPY" -> {
                                        onTextAction(TextAction.COPY, false)
                                    }
                                    "ACTION_PASTE", "action:paste", "PASTE" -> {
                                        onTextAction(TextAction.PASTE, false)
                                    }
                                    "ACTION_UNDO", "action:undo", "UNDO" -> {
                                        onTextAction(TextAction.UNDO, false)
                                    }
                                    "ACTION_REDO", "action:redo", "REDO" -> {
                                        onTextAction(TextAction.REDO, false)
                                    }
                                    "ACTION_SETTINGS", "action:settings", "SETTINGS" -> {
                                        handleToolbarAction(ToolbarAction.SETTINGS)
                                    }
                                    "ACTION_ONE_HANDED", "action:one_handed", "ONE_HANDED" -> {
                                        handleToolbarAction(ToolbarAction.ONE_HANDED)
                                    }
                                    else -> {
                                        val charToCommit = when (selectedChar) {
                                            "ZWNJ", "ZW\nNJ" -> "\u200C"
                                            "ZWJ" -> "\u200D"
                                            else -> selectedChar
                                        }
                                        handleKeyTapUnified(KeyDef(label = selectedChar, commitText = charToCommit, type = KeyType.CHARACTER))
                                    }
                                }
                            }
                        }
                        moreKeysState = null
                    }


                    // Keyboard rows (stable layout without recomposition shifts)
                    if (displayLayout.id == "num_extended" && keyboardRows.size >= 4) {
                        NumberPadExtendedLayout(
                            keyboardRows = keyboardRows,
                            numberUpperRowHeight = numberUpperRowHeight,
                            bottomRowHeight = bottomRowHeight,
                            scaleFactor = scaleFactor,
                            areKeysInteractive = areKeysInteractive,
                            colorScheme = colorScheme,
                            showKeyBorders = showKeyBorders,
                            showKeyShadows = showKeyShadows,
                            showSecondaryKeys = showSecondaryKeys,
                            showKeyPopups = showKeyPopups,
                            actionKeyVisual = actionKeyVisual,
                            longPressDelayMs = longPressDelayMs,
                            handleKeyPress = handleKeyPress,
                            handleKeyTapUnified = handleKeyTapUnified,
                            onKeyLongPress = onKeyLongPress,
                            onDeleteSwipeMove = onDeleteSwipeMove,
                            onDeleteSwipeEnd = onDeleteSwipeEnd,
                            onDeleteSwipeCancel = onDeleteSwipeCancel,
                            onDeleteLongPressEnd = onDeleteLongPressEnd,
                            fastDeleteEnabled = fastDeleteEnabled,
                            selectiveDeleteEnabled = selectiveDeleteEnabled,
                            onFastDelete = onFastDelete,
                            handleShowMoreKeys = handleShowMoreKeys,
                            handleMoreKeysHover = handleMoreKeysHover,
                            handleMoreKeysCommit = handleMoreKeysCommit
                        )
                    } else {
                        keyboardRows.forEachIndexed { rowIndex, row ->
                        val isBottomRow = rowIndex == keyboardRows.lastIndex
                        val isTopNumberRow = hasTopNumberRow && rowIndex == 0
                        val isMoreSymbolsTopRow = isMoreSymbolsLayout && showNumberRow && rowIndex == 0
                        val currentRowHeight = when {
                            isBottomRow -> bottomRowHeight
                            isTopNumberRow || isMoreSymbolsTopRow -> (50 * scaleFactor).dp
                            isNumberLayout -> numberUpperRowHeight
                            isDisplayBornoEasy -> bornoEasyRowHeight
                            else -> standardRowHeight
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(currentRowHeight)
                                .then(if (isTopNumberRow || isMoreSymbolsTopRow) Modifier.padding(bottom = 1.dp) else Modifier)
                        ) {
                        if (row.startPaddingDp > 0f) {
                            Spacer(
                                modifier = Modifier
                                    .width((row.startPaddingDp * scaleFactor).dp)
                                    .fillMaxHeight()
                            )
                        }
                        if (row.startPaddingWeight > 0f) {
                            Spacer(
                                modifier = Modifier
                                    .weight(row.startPaddingWeight)
                                    .fillMaxHeight()
                            )
                        }

                        row.keys.forEachIndexed { keyIndex, key ->
                            val effectiveKey = if (key.type == KeyType.SPACE && key.label.isBlank()) {
                                val spaceLabel = if (displayLayout.isSymbolsLayout()) {
                                    "Space"
                                } else {
                                    displayLayout.displayName ?: PredefinedLayouts.displayName(displayLayout.id)
                                }
                                key.copy(label = spaceLabel)
                            } else if (key.type == KeyType.SWITCH_LANGUAGE && showEmojiKey) {
                                key.copy(type = KeyType.EMOJI, icon = "emoji")
                            } else if (isEmailField && isBottomRow && key.label == "," && !displayLayout.isSymbolsLayout()) {
                                key.copy(label = "@", commitText = "@", shiftedCommitText = "@", shiftedLabel = "@")
                            } else {
                                key
                            }

                            val cellShiftState = if (isTopNumberRow) ShiftState.UNSHIFTED else effectiveShiftState

                            key("cell_${rowIndex}_$keyIndex") {
                                KeycapView(
                                key = effectiveKey,
                                shiftState = cellShiftState,
                                actionKeyVisual = actionKeyVisual,
                                rowIndex = rowIndex,
                                keyIndex = keyIndex,
                                isBottomRow = isBottomRow,
                                isSlidingSymbolMode = isSlidingSymbolMode,
                                isSlidingShiftMode = isSlidingShiftMode,
                                showKeyBorders = showKeyBorders,
                                showKeyShadows = showKeyShadows,
                                showSecondaryKeys = showSecondaryKeys && !isTopNumberRow,
                                showKeyPopups = showKeyPopups,
                                compactGaps = isDisplayBornoEasy && !isBottomRow,
                                isInteractive = areKeysInteractive,
                                rowHeight = currentRowHeight,
                                scaleFactor = scaleFactor,
                                gestureTypingEnabled = isEffectiveGestureTypingEnabled,
                                longPressDelayMs = longPressDelayMs,
                                longPressPrimaryShortcutsEnabled = longPressPrimaryShortcutsEnabled,
                                modifier = Modifier
                                    .weight(effectiveKey.widthWeight)
                                    .fillMaxHeight(),
                                onKeyPress = handleKeyPress,
                                onKeyTap = handleKeyTapUnified,
                                onKeyLongPress = { keyDef ->
                                    if (keyDef.type == KeyType.SWITCH_LANGUAGE || keyDef.type == KeyType.EMOJI) {
                                        isLanguagePickerOpen = true
                                        performHaptic(HapticFeedbackType.LongPress)
                                    } else {
                                        onKeyLongPress(keyDef)
                                    }
                                },
                                onDeleteSwipeMove = onDeleteSwipeMove,
                                onDeleteSwipeEnd = onDeleteSwipeEnd,
                                onDeleteSwipeCancel = onDeleteSwipeCancel,
                                onDeleteLongPressEnd = onDeleteLongPressEnd,
                                fastDeleteEnabled = fastDeleteEnabled,
                                selectiveDeleteEnabled = selectiveDeleteEnabled,
                                spacebarSwipeMode = spacebarSwipeMode,
                                cursorTrackpadEnabled = cursorTrackpadEnabled,
                                onFastDelete = onFastDelete,
                                onSpacebarSwipeMove = { deltaX ->
                                    isSpacebarSwiping = true
                                    spacebarSwipeDeltaX = deltaX
                                },
                                onSpacebarSwipeEnd = { finalDeltaX ->
                                    if (isSpacebarSwiping && n > 1) {
                                        val finalTarget = if (finalDeltaX >= commitThresholdPx) {
                                            (currentLayoutIndex + 1) % n
                                        } else if (finalDeltaX <= -commitThresholdPx) {
                                            (currentLayoutIndex - 1 + n) % n
                                        } else {
                                            currentLayoutIndex
                                        }
                                        val newLayoutId = effectiveLayoutList.getOrNull(finalTarget)
                                        if (newLayoutId != null && newLayoutId != layout.id) {
                                            onSwitchToLayout(newLayoutId)
                                        }
                                        isSpacebarSwiping = false
                                        spacebarSwipeDeltaX = 0f
                                    }
                                },
                                onSpacebarDwellReady = {
                                    performHaptic(HapticFeedbackType.TextHandleMove)
                                },
                                onKeyPositioned = { _, coords ->
                                    cellCoordinatesMap.put((rowIndex shl 16) or (keyIndex and 0xFFFF), coords)
                                },
                                onSpacebarTrackpad = { steps ->
                                    if (cursorTrackpadEnabled && steps != 0) {
                                        onSpacebarTrackpadStart()
                                        val didMove = if (onSpacebarTrackpadMove != null) {
                                            onSpacebarTrackpadMove(steps)
                                        } else {
                                            val action = if (steps > 0) TextAction.MOVE_RIGHT else TextAction.MOVE_LEFT
                                            var movedAny = false
                                            repeat(Math.abs(steps)) {
                                                if (onTextAction(action, false)) movedAny = true
                                            }
                                            movedAny
                                        }
                                        if (didMove) {
                                            performHaptic(HapticFeedbackType.TextHandleMove)
                                        }
                                    }
                                },
                                onSpacebarTrackpadEnd = {
                                    if (cursorTrackpadEnabled) {
                                        onSpacebarTrackpadEnd()
                                    }
                                },
                                onShowMoreKeys = handleShowMoreKeys,
                                onMoreKeysHover = handleMoreKeysHover,
                                onMoreKeysCommit = handleMoreKeysCommit,
                                onSlidingDragStart = { initiatorKey: KeyDef, keyCoords: LayoutCoordinates, initialDownPos: Offset ->
                                    val rCoords = rootCoordinates
                                    if (rCoords != null && rCoords.isAttached && keyCoords.isAttached) {
                                        slidingDwellJob?.cancel()
                                        slidingDwellJob = null
                                        slidingHoveredKey = null
                                        slidingHoveredBounds = null
                                        moreKeysState = null

                                        slidingInitiatorKey = initiatorKey
                                        if (initiatorKey.type == KeyType.SHIFT) {
                                            isSlidingShiftMode = true
                                            isSlidingSymbolMode = false
                                        } else {
                                            isSlidingSymbolMode = true
                                            isSlidingShiftMode = false
                                            cellCoordinatesMap.clear()
                                        }

                                        val downInRoot = rCoords.localPositionOf(keyCoords, initialDownPos)
                                        val keyCenterInRoot = rCoords.localPositionOf(
                                            keyCoords,
                                            Offset(keyCoords.size.width.toFloat() / 2f, keyCoords.size.height.toFloat() / 2f)
                                        )
                                        dragStartPosInRoot = downInRoot
                                        quickDragState = QuickSymbolDragState(
                                            startOffset = keyCenterInRoot,
                                            currentOffset = keyCenterInRoot
                                        )
                                    }
                                },
                                onSlidingDragMove = { deltaFromDown: Offset ->
                                    val rCoords = rootCoordinates
                                    val startPos = dragStartPosInRoot
                                    if (rCoords != null && startPos != null) {
                                        val currentFingerInRoot = startPos + deltaFromDown

                                        val currentMoreKeys = moreKeysState
                                        if (currentMoreKeys != null) {
                                            val isInside = updateMoreKeysHoverFromRoot(currentFingerInRoot)
                                            if (isInside) {
                                                quickDragState = quickDragState?.copy(
                                                    currentOffset = currentFingerInRoot
                                                )
                                            } else {
                                                moreKeysState = null
                                            }
                                        } else {
                                            // Find hovered key in root by inspecting the currently displayed keyboard rows
                                    var hoveredKey: KeyDef? = null
                                    var hoveredBounds: Rect? = null
                                    for (r in keyboardRows.indices) {
                                        val row = keyboardRows[r]
                                        for (c in row.keys.indices) {
                                            val coords = cellCoordinatesMap.get((r shl 16) or (c and 0xFFFF))
                                            if (coords != null && coords.isAttached) {
                                                val topLeft = rCoords.localPositionOf(coords, Offset.Zero)
                                                val rect = Rect(
                                                    topLeft,
                                                    Size(coords.size.width.toFloat(), coords.size.height.toFloat())
                                                )
                                                if (rect.contains(currentFingerInRoot)) {
                                                    val rawKey = row.keys[c]
                                                    val effectiveKey = if (rawKey.type == KeyType.SPACE && rawKey.label.isBlank()) {
                                                        val spaceLabel = if (displayLayout.isSymbolsLayout()) {
                                                            "Space"
                                                        } else {
                                                            displayLayout.displayName ?: PredefinedLayouts.displayName(displayLayout.id)
                                                        }
                                                        rawKey.copy(label = spaceLabel)
                                                    } else if (rawKey.type == KeyType.SWITCH_LANGUAGE && showEmojiKey) {
                                                        rawKey.copy(type = KeyType.EMOJI, icon = "emoji")
                                                    } else if (isEmailField && r == keyboardRows.lastIndex && rawKey.label == "," && !displayLayout.isSymbolsLayout()) {
                                                        rawKey.copy(label = "@", commitText = "@", shiftedCommitText = "@", shiftedLabel = "@")
                                                    } else {
                                                        rawKey
                                                    }
                                                    hoveredKey = effectiveKey
                                                    hoveredBounds = rect
                                                    break
                                                }
                                            }
                                        }
                                        if (hoveredKey != null) break
                                    }

                                    if (hoveredKey != slidingHoveredKey) {
                                        slidingDwellJob?.cancel()
                                        slidingDwellJob = null
                                        slidingHoveredKey = hoveredKey
                                        slidingHoveredBounds = hoveredBounds

                                        if (hoveredKey != null && hoveredKey.moreKeys(effectiveShiftState, includePrimaryShortcuts = longPressPrimaryShortcutsEnabled).isNotEmpty()) {
                                            val targetKey = hoveredKey
                                            val targetBounds = hoveredBounds!!
                                            slidingDwellJob = coroutineScope.launch {
                                                delay(260L)
                                                openMoreKeysForAnchor(targetKey, targetBounds)
                                                performHaptic(HapticFeedbackType.LongPress)
                                            }
                                        }
                                    }

                                    val isShiftDrag = isSlidingShiftMode || slidingInitiatorKey?.type == KeyType.SHIFT
                                    val isSpecialNoPopupKey = hoveredKey != null && (
                                        hoveredKey.type == KeyType.SPACE ||
                                        hoveredKey.type == KeyType.SYMBOLS ||
                                        hoveredKey.type == KeyType.SHIFT ||
                                        hoveredKey.commitText == "\u200C" ||
                                        hoveredKey.label.contains("ZWNJ") ||
                                        hoveredKey.label.replace("\n", "") == "ZWNJ"
                                    )
                                    val symbol = if (hoveredKey != null && !isSpecialNoPopupKey) {
                                        if (isShiftDrag) {
                                            if (hoveredKey.type == KeyType.CHARACTER) {
                                                hoveredKey.displayLabel(ShiftState.ONE_SHOT)
                                            } else null
                                        } else {
                                            if (hoveredKey.label.isNotEmpty()) hoveredKey.label else null
                                        }
                                    } else {
                                        null
                                    }

                                            quickDragState = quickDragState?.copy(
                                                currentOffset = currentFingerInRoot,
                                                hoveredKey = hoveredKey,
                                                hoveredKeyBounds = hoveredBounds,
                                                hoveredSymbol = symbol
                                            )
                                        }
                                    }
                                },
                                onSlidingDragEnd = { deltaFromDown: Offset ->
                                    slidingDwellJob?.cancel()
                                    slidingDwellJob = null

                                    val hasMoved = deltaFromDown.getDistance() > 24f
                                    val keyToCommit = slidingHoveredKey
                                    val initiator = slidingInitiatorKey
                                    val isShiftDrag = isSlidingShiftMode || initiator?.type == KeyType.SHIFT

                                    if (moreKeysState != null) {
                                        handleMoreKeysCommit()
                                    } else if (hasMoved && keyToCommit != null && (keyToCommit.label.isNotEmpty() || !keyToCommit.commitText.isNullOrEmpty())) {
                                        if (isShiftDrag) {
                                            if (keyToCommit.type == KeyType.CHARACTER) {
                                                val shiftedKey = keyToCommit.copy(
                                                    commitText = keyToCommit.textToCommit(ShiftState.ONE_SHOT),
                                                    label = keyToCommit.displayLabel(ShiftState.ONE_SHOT)
                                                )
                                                handleKeyTapUnified(shiftedKey)
                                            }
                                        } else {
                                            if (keyToCommit.type != KeyType.SYMBOLS) {
                                                handleKeyTapUnified(keyToCommit)
                                            }
                                        }
                                    } else if (!hasMoved) {
                                        if (isShiftDrag) {
                                            handleKeyTapUnified(initiator ?: KeyDef(type = KeyType.SHIFT))
                                        } else {
                                            handleKeyTapUnified(initiator ?: KeyDef("?123", type = KeyType.SYMBOLS))
                                        }
                                    }

                                    slidingHoveredKey = null
                                    slidingHoveredBounds = null
                                    slidingInitiatorKey = null
                                    val wasSymbolSliding = isSlidingSymbolMode
                                    isSlidingSymbolMode = false
                                    isSlidingShiftMode = false
                                    quickDragState = null
                                    dragStartPosInRoot = null
                                    if (wasSymbolSliding) {
                                        cellCoordinatesMap.clear()
                                    }
                                },
                                onGestureStart = { keyCoords: LayoutCoordinates, posInKey: Offset ->
                                    if (isEffectiveGestureTypingEnabled) {
                                        val rCoords = rootCoordinates
                                        if (rCoords != null && rCoords.isAttached && keyCoords.isAttached) {
                                            val rootPos = rCoords.localPositionOf(keyCoords, posInKey)
                                            fadingTrailJob?.cancel()
                                            fadingTrailJob = null
                                            fadingTrail = emptyList()
                                            coroutineScope.launch { trailFadeAlpha.snapTo(1f) }
                                            livePredictJob?.cancel()
                                            livePredictJob = null
                                            liveGestureCandidate = null
                                            val (c, kw) = extractCentroids(rCoords)
                                            cachedCentroids = c
                                            cachedKeyWidthPx = kw
                                            gestureTrail = listOf(rootPos)
                                        }
                                    }
                                },
                                onGestureMove = { keyCoords: LayoutCoordinates, posInKey: Offset ->
                                    if (isEffectiveGestureTypingEnabled) {
                                        val rCoords = rootCoordinates
                                        if (rCoords != null && rCoords.isAttached && keyCoords.isAttached) {
                                            val rootPos = rCoords.localPositionOf(keyCoords, posInKey)
                                            val lastPos = gestureTrail.lastOrNull()
                                            if (lastPos == null || (rootPos - lastPos).getDistance() >= 8f) {
                                                val updatedTrail = gestureTrail + rootPos
                                                gestureTrail = updatedTrail

                                                if (onGestureLivePredict != null && updatedTrail.size >= 4) {
                                                    val now = SystemClock.uptimeMillis()
                                                    if (now - lastPredictTimeMs >= 50L && (livePredictJob == null || livePredictJob?.isCompleted == true)) {
                                                        lastPredictTimeMs = now
                                                        val centroids = cachedCentroids
                                                        val kw = cachedKeyWidthPx
                                                        if (centroids.isNotEmpty()) {
                                                            livePredictJob = coroutineScope.launch(Dispatchers.Default) {
                                                                if (gestureTrail.isNotEmpty() && updatedTrail.size >= 4) {
                                                                    val predicted = onGestureLivePredict(updatedTrail, centroids, kw)
                                                                    if (predicted != null && gestureTrail.isNotEmpty()) {
                                                                        withContext(Dispatchers.Main) {
                                                                            if (gestureTrail.isNotEmpty()) {
                                                                                liveGestureCandidate = predicted
                                                                            }
                                                                        }
                                                                    }
                                                                }
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                },
                                onGestureEnd = {
                                    livePredictJob?.cancel()
                                    livePredictJob = null
                                    val trail = gestureTrail
                                    gestureTrail = emptyList()

                                    if (trail.size >= 2) {
                                        fadingTrail = trail
                                        fadingTrailJob?.cancel()
                                        fadingTrailJob = coroutineScope.launch {
                                            trailFadeAlpha.snapTo(1f)
                                            trailFadeAlpha.animateTo(
                                                targetValue = 0f,
                                                animationSpec = tween(durationMillis = 200, easing = LinearOutSlowInEasing)
                                            )
                                            fadingTrail = emptyList()
                                            liveGestureCandidate = null
                                        }
                                    } else {
                                        liveGestureCandidate = null
                                    }

                                    if (isEffectiveGestureTypingEnabled) {
                                        var totalDist = 0f
                                        for (i in 1 until trail.size) {
                                            totalDist += (trail[i] - trail[i - 1]).getDistance()
                                        }
                                        val minGestureDistPx = with(density) { 48.dp.toPx() }
                                        if (trail.size >= 4 && totalDist >= minGestureDistPx) {
                                            val centroids = cachedCentroids
                                            val keyWidthPx = cachedKeyWidthPx
                                            if (centroids.isNotEmpty()) {
                                                onGestureCommit(trail, centroids, keyWidthPx)
                                            } else {
                                                val rCoords = rootCoordinates
                                                if (rCoords != null && rCoords.isAttached) {
                                                    val (c, kw) = extractCentroids(rCoords)
                                                    onGestureCommit(trail, c, kw)
                                                }
                                            }
                                        }
                                    }
                                }
                            )
                        }
                    }

                        if (row.endPaddingWeight > 0f) {
                            Spacer(
                                modifier = Modifier
                                    .weight(row.endPaddingWeight)
                                    .fillMaxHeight()
                            )
                        }
                        if (row.endPaddingDp > 0f) {
                            Spacer(
                                modifier = Modifier
                                    .width((row.endPaddingDp * scaleFactor).dp)
                                    .fillMaxHeight()
                            )
                        }
                    }
                }
            }
            }

            // 2. Full-screen panels (Tools Drawer, Clipboard, Emoji)
            if (isToolsDrawerOpen) {
                ToolsDrawerView(
                    drawerTools = drawerTools,
                    onDrawerToolsChange = { updateTools(pinnedTools, it) },
                    onInterchangeWithToolbar = { drawerAct, toolbarAct ->
                        interchangeTools(drawerAct, toolbarAct)
                    },
                    onMoveToToolbar = { drawerAct ->
                        moveDrawerToToolbar(drawerAct)
                    },
                    onToolClick = handleToolbarAction,
                    height = keyboardContentHeight,
                    rootCoordinates = rootCoordinates,
                    onReportItemBounds = { act, bounds -> drawerItemBounds[act] = bounds },
                    onReportContainerBounds = { bounds -> drawerContainerBounds = bounds },
                    hoveredDrawerAction = hoveredDrawerAction,
                    isOverDrawerBlankSpace = isOverDrawerBlankSpace,
                    hoveredToolbarAction = hoveredToolbarAction,
                    isActionActive = isActionActive,
                    scaleFactor = scaleFactor,
                    isLandscape = isLandscape,
                    isFloating = isFloating,
                    isToolEnabled = isToolEnabled,
                    onDraggingStateChange = { action, fingerPos ->
                        activeDraggingFromDrawer = action
                        currentFingerInRoot = fingerPos
                    }
                )
            }

            // 3. Expanded Suggestions Panel: overlay on top of keyboard keys
            if (suggestionBarMode != KiebordPreferences.SUGGESTION_BAR_MODE_SCROLLABLE && isSuggestionsExpanded && suggestions.isNotEmpty()) {
                ExpandedSuggestionsPanelView(
                    candidates = suggestions,
                    onCandidateClick = { candidate ->
                        isSuggestionsExpanded = false
                        onCandidateSelect(candidate)
                    },
                    onCandidateLongClick = { candidate ->
                        unlearnCandidateTarget = candidate
                    },
                    onDelete = {
                        onKeyTap(KeyDef(type = KeyType.DELETE))
                    },
                    onEnter = {
                        isSuggestionsExpanded = false
                        onKeyTap(KeyDef(type = KeyType.ACTION))
                    },
                    height = keyboardContentHeight,
                    scaleFactor = scaleFactor,
                    isFloating = isFloating,
                    modifier = Modifier
                        .fillMaxSize()
                        .zIndex(10f)
                )
            }
        }
    }
    }

    Box(
        modifier = modifier
            .then(if (isFloating) Modifier.fillMaxSize() else Modifier.fillMaxWidth())
            .onGloballyPositioned { coords ->
                rootCoordinates = coords
                rootWidthPx = coords.size.width.toFloat()
            }
    ) {
        if (!isFloating && popupHeadroomDp > 0.dp) {
            Spacer(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(popupHeadroomDp)
            )
        }
        if (!isFloating && isResizerOpen) {
            Spacer(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(activeRootHeight)
            )
        }

        if (!isFloating) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(top = popupHeadroomDp)
                    .then(
                        if (containerBgBrush != null) {
                            Modifier.background(containerBgBrush)
                        } else {
                            Modifier.background(colorScheme.containerBackground)
                        }
                    )
            ) {
                if (bgImageSpec != null) {
                    com.codepotro.borno.theme.ThemeImageBackgroundView(
                        spec = bgImageSpec,
                        modifier = Modifier.matchParentSize()
                    )
                }
                val isSolidOverlayActive = isToolsDrawerOpen || (isSuggestionsExpanded && suggestions.isNotEmpty())
                if (isSolidOverlayActive) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = if (isToolsDrawerOpen) 0.dp else (toolbarHeight + effectiveEmojiRowHeight))
                            .matchParentSize()
                            .background(colorScheme.containerBackground.copy(alpha = 1f))
                    )
                }
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(bottom = totalBottomPadding)
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        KeyboardInnerContent()
                    }
                }

                if (moreKeysOverlayAlpha > 0.005f) {
                    val isLight = colorScheme == KeyboardColorScheme.DefaultLight
                    val overlayColor = if (isLight) {
                        Color.White.copy(alpha = 0.38f)
                    } else {
                        colorScheme.containerBackground.copy(alpha = 0.45f)
                    }
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .zIndex(85f)
                            .graphicsLayer { alpha = moreKeysOverlayAlpha }
                            .background(overlayColor)
                    )
                }
            }
        } else {
            Box(
                modifier = Modifier
                    .offset { IntOffset(activeFloatingX.roundToInt(), activeFloatingY.roundToInt()) }
                    .width(currentFloatingWidth)
                    .onGloballyPositioned { coords ->
                        if (coords.isAttached) {
                            floatingKeyboardBoundsInRoot = coords.boundsInRoot()
                        }
                    }
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .shadow(elevation = 12.dp, shape = RoundedCornerShape(18.dp))
                        .border(1.2.dp, colorScheme.accentColor.copy(alpha = 0.40f), RoundedCornerShape(18.dp))
                        .clip(RoundedCornerShape(18.dp))
                        .then(
                            if (containerBgBrush != null) {
                                Modifier.background(containerBgBrush)
                            } else {
                                Modifier.background(colorScheme.containerBackground)
                            }
                        )
                ) {
                    if (bgImageSpec != null) {
                        com.codepotro.borno.theme.ThemeImageBackgroundView(
                            spec = bgImageSpec,
                            modifier = Modifier.matchParentSize()
                        )
                    }
                    val isSolidOverlayActive = isToolsDrawerOpen || (isSuggestionsExpanded && suggestions.isNotEmpty())
                    if (isSolidOverlayActive) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = if (isToolsDrawerOpen) 0.dp else (toolbarHeight + effectiveEmojiRowHeight))
                                .matchParentSize()
                                .background(colorScheme.containerBackground.copy(alpha = 1f))
                        )
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(
                                bottom = totalBottomPadding
                            )
                    ) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            KeyboardInnerContent()

                            FloatingMoveHandleBar(
                                isDragging = isDraggingFloating,
                                handleColor = colorScheme.keyForeground,
                                accentColor = colorScheme.accentColor,
                                onDragStart = {
                                    isDraggingFloating = true
                                },
                                onDrag = { dx, dy ->
                                    floatingDragOffset += Offset(dx, dy)
                                    val curKbRect = Rect(
                                        activeFloatingX,
                                        activeFloatingY,
                                        activeFloatingX + floatingWidthPx,
                                        activeFloatingY + keyboardSurfaceHeightPx
                                    )
                                    val hovering = landingHintBoundsInRoot?.let { dock ->
                                        dock.overlaps(curKbRect) || (curKbRect.center.y > dock.top && curKbRect.center.x in dock.left..dock.right)
                                    } ?: false
                                    if (hovering != isOverLandingZone) {
                                        if (hovering) {
                                            performHaptic(HapticFeedbackType.LongPress)
                                        }
                                        isOverLandingZone = hovering
                                    }
                                },
                                onDragEnd = {
                                    if (isOverLandingZone) {
                                        isOverLandingZone = false
                                        isDraggingFloating = false
                                        floatingDragOffset = Offset.Zero
                                        isFloating = false
                                        onFloatingModeChange?.invoke(false)
                                    } else {
                                        isDraggingFloating = false
                                        val newRatioX = if (maxFloatingX > 0f) ((activeFloatingX - floatingScreenMarginPx) / maxFloatingX).coerceIn(0f, 1f) else 0.5f
                                        val newRatioY = if (maxFloatingY > 0f) ((activeFloatingY - floatingScreenMarginPx) / maxFloatingY).coerceIn(0f, 1f) else 0.7f
                                        currentFloatingRatioX = newRatioX
                                        currentFloatingRatioY = newRatioY
                                        floatingDragOffset = Offset.Zero
                                        onFloatingPositionChange?.invoke(isLandscape, newRatioX, newRatioY)
                                    }
                                },
                                onDragCancel = {
                                    isDraggingFloating = false
                                    floatingDragOffset = Offset.Zero
                                    isOverLandingZone = false
                                }
                            )
                        }
                    }

                    if (moreKeysOverlayAlpha > 0.005f) {
                        val isLight = colorScheme == KeyboardColorScheme.DefaultLight
                        val overlayColor = if (isLight) {
                            Color.White.copy(alpha = 0.38f)
                        } else {
                            colorScheme.containerBackground.copy(alpha = 0.45f)
                        }
                        Box(
                            modifier = Modifier
                                .matchParentSize()
                                .zIndex(85f)
                                .graphicsLayer { alpha = moreKeysOverlayAlpha }
                                .background(overlayColor)
                        )
                    }
                }

                FloatingCornerResizers(
                    currentWidth = currentFloatingWidth,
                    currentHeightScale = currentFloatingHeightScale,
                    minWidth = 240.dp,
                    maxWidth = with(density) { (rootWidthPx - (floatingScreenMarginPx * 2)).coerceAtLeast(240f).toDp() },
                    accentColor = colorScheme.accentColor,
                    onSizeChange = { newW, newHScale ->
                        currentFloatingWidth = newW
                        currentFloatingHeightScale = newHScale
                    },
                    onCommitSize = { newW, newHScale ->
                        currentFloatingWidth = newW
                        currentFloatingHeightScale = newHScale
                        onFloatingSizeChange?.invoke(isLandscape, newW.value, newHScale)
                    },
                    modifier = Modifier.matchParentSize()
                )
            }

            FloatingLandingHintRect(
                isVisible = isFloating && isDraggingFloating,
                isHovered = isOverLandingZone,
                accentColor = colorScheme.accentColor,
                onBoundsChanged = { landingHintBoundsInRoot = it },
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }

        // 1. Guideline overlay for sliding symbol input (in outer Box matching rootCoordinates)
        QuickSymbolDragOverlay(
            state = quickDragState,
            isMoreKeysOpen = moreKeysState != null,
            scaleFactor = scaleFactor,
            keyboardWidthPx = if (rootWidthPx > 0f) rootWidthPx else 1080f,
            modifier = Modifier
                .matchParentSize()
                .zIndex(95f)
        )

        // 2. Borno Keyboard rounded pill MoreKeys floating popup
        MoreKeysPopup(
            state = moreKeysState,
            selectedIndexProvider = { selectedMoreKeyIndex },
            keyboardWidthPx = effectiveKeyboardWidthPx,
            keyboardLeftPx = keyboardLeftPx,
            keyboardRightPx = keyboardRightPx,
            scaleFactor = scaleFactor,
            modifier = Modifier.zIndex(100f)
        )

        // 3. Live Keyboard Resizer Overlay (Borno Keyboard 4-way drag handles)
        if (!isFloating && isResizerOpen) {
            LiveResizerOverlay(
                dragRowHeight = dragRowHeight,
                dragChinPadding = dragChinPadding,
                dragHorizontalMargin = dragHorizontalMargin,
                rectHeight = dragKeyboardHeight,
                rectBottomPadding = dragBottomPadding,
                oneHandedStart = oneHandedStart,
                oneHandedEnd = oneHandedEnd,
                baseRowHeight = activeBaseRowHeight,
                effectiveNavBarBottom = effectiveNavBarBottom,
                onDragRowHeightChange = { dragRowHeight = it },
                onDragChinPaddingChange = { dragChinPadding = it },
                onDragHorizontalMarginChange = { dragHorizontalMargin = it },
                onCommitDrag = {
                    keyRowHeight = dragRowHeight
                    chinPadding = dragChinPadding
                    horizontalMargin = dragHorizontalMargin
                },
                onReset = {
                    val defaultChin = if (isLandscape) 8.dp else 22.dp
                    dragRowHeight = activeBaseRowHeight
                    dragChinPadding = defaultChin
                    dragHorizontalMargin = 0.dp
                    keyRowHeight = activeBaseRowHeight
                    chinPadding = defaultChin
                    horizontalMargin = 0.dp
                    onResizerValuesChange?.invoke(
                        isLandscape,
                        1.0f,
                        defaultChin.value,
                        0f
                    )
                },
                onDone = {
                    keyRowHeight = dragRowHeight
                    chinPadding = dragChinPadding
                    horizontalMargin = dragHorizontalMargin
                    val scale = (dragRowHeight.value / (if (activeBaseRowHeight.value > 0f) activeBaseRowHeight.value else 61f)).coerceIn(0.6f, 1.5f)
                    onResizerValuesChange?.invoke(
                        isLandscape,
                        scale,
                        dragChinPadding.value,
                        dragHorizontalMargin.value
                    )
                    isResizerOpen = false
                },
                onCancel = {
                    dragRowHeight = savedRowHeight
                    dragChinPadding = savedChinPadding
                    dragHorizontalMargin = savedHorizontalMargin
                    keyRowHeight = savedRowHeight
                    chinPadding = savedChinPadding
                    horizontalMargin = savedHorizontalMargin
                    isResizerOpen = false
                },
                modifier = Modifier
                    .matchParentSize()
                    .zIndex(80f)
            )
        }

        // 4. Single-colored Gesture Trail Overlay with smooth fade-out
        if (isEffectiveGestureTypingEnabled && showGestureTrail) {
            val active = gestureTrail
            val fading = fadingTrail
            val currentAlpha = if (active.isNotEmpty()) 1f else trailFadeAlpha.value
            val currentTrail = if (active.isNotEmpty()) active else fading
            if (currentTrail.size >= 2 && currentAlpha > 0f) {
                GestureTrailOverlay(
                    trail = currentTrail,
                    accentColor = colorScheme.accentColor,
                    fadeAlpha = currentAlpha,
                    modifier = Modifier.matchParentSize()
                )
            }
        }

        // 5. Spacebar Layout Slider Floating Popup Overlay
        if (isSpacebarSwiping && effectiveLayoutList.size > 1) {
            val spaceKeyCoords = run {
                for (r in keyboardRows.indices) {
                    val row = keyboardRows[r]
                    for (c in row.keys.indices) {
                        if (row.keys[c].type == KeyType.SPACE) {
                            return@run cellCoordinatesMap.get((r shl 16) or (c and 0xFFFF))
                        }
                    }
                }
                null
            }
            SpacebarLayoutSliderOverlay(
                activeLayouts = effectiveLayoutList,
                currentIndex = currentLayoutIndex,
                swipeDeltaX = spacebarSwipeDeltaX,
                commitThresholdPx = commitThresholdPx,
                spaceKeyCoords = spaceKeyCoords,
                rootCoordinates = rootCoordinates,
                scaleFactor = scaleFactor,
                totalBottomPadding = totalBottomPadding,
                keyRowHeight = bottomRowHeight,
                keyboardLeftPx = keyboardLeftPx,
                keyboardRightPx = keyboardRightPx,
                modifier = Modifier.zIndex(90f)
            )
        }

        // 6. Language Selection Modal Dialog (Long-press on globe key)
        if (isLanguagePickerOpen) {
            val globeKeyCoords = run {
                for (r in keyboardRows.indices) {
                    val row = keyboardRows[r]
                    for (c in row.keys.indices) {
                        val k = row.keys[c]
                        if (k.type == KeyType.SWITCH_LANGUAGE || k.type == KeyType.EMOJI) {
                            return@run cellCoordinatesMap.get((r shl 16) or (c and 0xFFFF))
                        }
                    }
                }
                null
            }

            val keyboardSurfaceHeightDp = remember(committedKeyboardHeight, totalBottomPadding) {
                committedKeyboardHeight + totalBottomPadding
            }

            val (globeBottomOffsetDp, horizontalOffsetDp, maxDialogHeightDp) = remember(
                globeKeyCoords,
                rootCoordinates,
                popupHeadroomDp,
                bottomRowHeight,
                totalBottomPadding,
                keyboardSurfaceHeightDp,
                keyboardLeftPx,
                keyboardRightPx,
                rootWidthPx,
                oneHandedSide,
                isLandscape
            ) {
                val rCoords = rootCoordinates
                val globeBounds = if (globeKeyCoords != null && globeKeyCoords.isAttached &&
                    rCoords != null && rCoords.isAttached) {
                    try {
                        rCoords.localBoundingBoxOf(globeKeyCoords)
                    } catch (_: Exception) {
                        null
                    }
                } else null

                val bottomOffset = if (globeBounds != null) {
                    val rootHeightPx = rCoords?.size?.height?.toFloat() ?: 0f
                    val spaceFromBottomPx = (rootHeightPx - globeBounds.top).coerceAtLeast(0f)
                    with(density) { spaceFromBottomPx.toDp() } + 4.dp
                } else {
                    bottomRowHeight + totalBottomPadding + 4.dp
                }

                val hOffset = if (oneHandedSide != "OFF" && rootWidthPx > 0f) {
                    val activeCenterX = keyboardLeftPx + (keyboardRightPx - keyboardLeftPx) / 2f
                    val rootCenterX = rootWidthPx / 2f
                    with(density) { (activeCenterX - rootCenterX).toDp() }
                } else {
                    0.dp
                }

                val maxAllowedHeight = (keyboardSurfaceHeightDp - bottomOffset - 4.dp).coerceAtLeast(160.dp)
                Triple(bottomOffset, hOffset, maxAllowedHeight)
            }

            LanguageSelectionDialog(
                currentLayoutId = layout.id,
                enabledLayoutIds = effectiveLayoutList,
                onSelectLayout = { selectedId ->
                    isLanguagePickerOpen = false
                    onSwitchToLayout(selectedId)
                },
                onMoreLayouts = {
                    isLanguagePickerOpen = false
                    isToolsDrawerOpen = false
                    isToolbarOptionsVisible = false
                    onOpenLanguagesSettings()
                },
                onOtherKeyboards = {
                    isLanguagePickerOpen = false
                    onOpenInputMethodPicker()
                },
                onDismiss = { isLanguagePickerOpen = false },
                bottomOffsetDp = globeBottomOffsetDp,
                horizontalOffsetDp = horizontalOffsetDp,
                maxAvailableHeightDp = maxDialogHeightDp,
                modifier = Modifier
                    .padding(top = popupHeadroomDp)
                    .matchParentSize()
            )
        }

        // 7. Unlearn Learned Word Confirmation Dialog
        if (unlearnCandidateTarget != null) {
            val candidateToUnlearn = unlearnCandidateTarget!!
            UnlearnWordConfirmationDialog(
                word = candidateToUnlearn.text,
                onConfirm = {
                    onUnlearnWord(candidateToUnlearn.text)
                    unlearnCandidateTarget = null
                    performHaptic(HapticFeedbackType.LongPress)
                },
                onDismiss = { unlearnCandidateTarget = null },
                modifier = Modifier
                    .padding(top = popupHeadroomDp)
                    .matchParentSize()
            )
        }

        // 8. One-Handed Mode Side Control Bar (Padding Adjuster '=', Expand, and Direction '<' / '>')
        if (!isFloating && oneHandedSide != "OFF") {
            OneHandedSideBar(
                oneHandedSide = oneHandedSide,
                sideWidth = oneHandedSideWidth.dp,
                vibrationEnabled = vibrationEnabled,
                onAdjustPaddingDelta = { deltaPx ->
                    val deltaDp = deltaPx / density.density
                    if (oneHandedSide == "LEFT") {
                        oneHandedSideWidth = (oneHandedSideWidth - deltaDp).coerceIn(48f, 130f)
                    } else {
                        oneHandedSideWidth = (oneHandedSideWidth + deltaDp).coerceIn(48f, 130f)
                    }
                },
                onCyclePaddingPreset = {
                    oneHandedSideWidth = when {
                        oneHandedSideWidth < 62f -> 76f
                        oneHandedSideWidth < 86f -> 100f
                        else -> 56f
                    }
                },
                onExpand = {
                    oneHandedSide = "OFF"
                    onOneHandedModeChange?.invoke("OFF")
                },
                onSwitchDirection = {
                    val nextSide = if (oneHandedSide == "LEFT") "RIGHT" else "LEFT"
                    oneHandedSide = nextSide
                    onOneHandedModeChange?.invoke(nextSide)
                },
                modifier = Modifier
                    .padding(top = popupHeadroomDp)
                    .height(committedKeyboardHeight)
                    .align(if (oneHandedSide == "LEFT") Alignment.TopEnd else Alignment.TopStart)
            )
        }
    }
}

/**
 * Floating rounded pill overlay displayed when swiping horizontally on the spacebar
 * to slide through active keyboard layouts. Displays a single layout name at a time,
 * smoothly translating in 1:1 direct tracking with the thumb swipe, with clear commitment
 * threshold and gentle rubber-band clamping.
 */
@Composable
fun SpacebarLayoutSliderOverlay(
    activeLayouts: List<String>,
    currentIndex: Int,
    swipeDeltaX: Float,
    commitThresholdPx: Float,
    spaceKeyCoords: LayoutCoordinates? = null,
    rootCoordinates: LayoutCoordinates? = null,
    scaleFactor: Float = 1.0f,
    totalBottomPadding: Dp = 8.dp,
    keyRowHeight: Dp = 61.dp,
    keyboardLeftPx: Float = 0f,
    keyboardRightPx: Float = 0f,
    modifier: Modifier = Modifier
) {
    val colorScheme = KiebordTheme.colorScheme
    val density = LocalDensity.current
    val n = activeLayouts.size
    if (n <= 1) return

    // Exact spacebar bounds in root coordinates
    val (anchorCenterX, anchorTopY, spaceWidthPx) = if (spaceKeyCoords != null && spaceKeyCoords.isAttached &&
        rootCoordinates != null && rootCoordinates.isAttached) {
        try {
            val spaceBounds = rootCoordinates.localBoundingBoxOf(spaceKeyCoords)
            Triple(spaceBounds.center.x, spaceBounds.top, spaceBounds.width)
        } catch (_: Exception) {
            val fallbackRootHeight = rootCoordinates?.size?.height?.toFloat() ?: 0f
            val spaceTop = fallbackRootHeight - with(density) { (totalBottomPadding + keyRowHeight).toPx() }
            val fallbackWidth = (keyboardRightPx - keyboardLeftPx) * 0.55f
            Triple(keyboardLeftPx + (keyboardRightPx - keyboardLeftPx) / 2f, spaceTop, fallbackWidth)
        }
    } else {
        val fallbackRootHeight = rootCoordinates?.size?.height?.toFloat() ?: 0f
        val spaceTop = fallbackRootHeight - with(density) { (totalBottomPadding + keyRowHeight).toPx() }
        val fallbackWidth = (keyboardRightPx - keyboardLeftPx) * 0.55f
        Triple(keyboardLeftPx + (keyboardRightPx - keyboardLeftPx) / 2f, spaceTop, fallbackWidth)
    }

    // Balanced pill dimensions: at least the width of the spacebar itself
    val spaceWidthDp = with(density) { spaceWidthPx.toDp() }
    val maxAllowedWidthDp = with(density) { (keyboardRightPx - keyboardLeftPx - 16f).toDp() }
    val popupWidth = maxOf(spaceWidthDp, (180 * scaleFactor).dp).coerceAtMost(maxAllowedWidthDp)
    val popupHeight = (46 * scaleFactor).coerceIn(42f, 52f).dp
    val defaultGapDp = (12 * scaleFactor).coerceIn(8f, 16f).dp

    val popupWidthPx = with(density) { popupWidth.toPx() }
    val popupHeightPx = with(density) { popupHeight.toPx() }
    val defaultGapPx = with(density) { defaultGapDp.toPx() }

    val slotPx = with(density) { (popupWidth * 0.5f).toPx() }

    val absDelta = Math.abs(swipeDeltaX)
    val clampedAbs = if (absDelta <= slotPx) {
        absDelta
    } else {
        slotPx + (absDelta - slotPx) * 0.22f
    }
    val signedOffset = if (swipeDeltaX >= 0f) clampedAbs else -clampedAbs
    val isCommitted = absDelta >= commitThresholdPx

    val leftIndex = (currentIndex + 1) % n
    val centerIndex = currentIndex
    val rightIndex = (currentIndex - 1 + n) % n

    val leftTranslationX = -slotPx + signedOffset
    val centerTranslationX = signedOffset
    val rightTranslationX = slotPx + signedOffset

    val targetIndex = when {
        swipeDeltaX >= commitThresholdPx -> leftIndex
        swipeDeltaX <= -commitThresholdPx -> rightIndex
        else -> centerIndex
    }

    // Position popup so its bottom edge is exactly `defaultGapPx` (12dp) above the spacebar's top edge
    // This gives the exact same offset from the touch point as standard key popups
    val minTopPx = with(density) { 6.dp.toPx() }
    val idealTopPx = anchorTopY - defaultGapPx - popupHeightPx
    val clampedTopPx = if (idealTopPx < minTopPx) minTopPx else idealTopPx

    // Horizontally center above spacebar, clamped within keyboard bounds
    val marginPx = with(density) { 6.dp.toPx() }
    val minLeftPx = keyboardLeftPx + marginPx
    val maxLeftPx = (keyboardRightPx - popupWidthPx - marginPx).coerceAtLeast(minLeftPx)
    val idealLeftPx = anchorCenterX - (popupWidthPx / 2f)
    val clampedLeftPx = idealLeftPx.coerceIn(minLeftPx, maxLeftPx)

    val popupBg = colorScheme.moreKeysBackground
    val isDarkPopup = ThemeColorUtils.isColorDark(popupBg)
    val candidateColor = when {
        colorScheme.accentColor != Color.Unspecified -> colorScheme.accentColor
        colorScheme.shiftActiveColor != Color.Unspecified -> colorScheme.shiftActiveColor
        else -> colorScheme.keyForeground
    }
    val activeLabelColor = ThemeColorUtils.adjustContrast(
        color = candidateColor,
        background = popupBg,
        minContrast = 2.8f
    )
    val pillShape = RoundedCornerShape(percent = 50)

    Box(
        modifier = modifier
            .offset { IntOffset(clampedLeftPx.roundToInt(), clampedTopPx.roundToInt()) }
            .wrapContentSize()
    ) {
        Row(
            modifier = Modifier
                .requiredSize(width = popupWidth, height = popupHeight)
                .shadow(
                    elevation = (4 * scaleFactor).dp,
                    shape = pillShape,
                    clip = false,
                    ambientColor = Color(0x38000000),
                    spotColor = Color(0x30000000)
                )
                .clip(pillShape)
                .background(popupBg)
                .border(
                    width = 1.dp,
                    color = if (isCommitted) activeLabelColor else (if (isDarkPopup) Color(0x33FFFFFF) else Color(0x1F000000)),
                    shape = pillShape
                )
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowLeft,
                contentDescription = null,
                tint = if (swipeDeltaX <= -commitThresholdPx) {
                    activeLabelColor
                } else {
                    colorScheme.keyForeground.copy(alpha = 0.35f)
                },
                modifier = Modifier.size(18.dp)
            )

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clipToBounds(),
                contentAlignment = Alignment.Center
            ) {
                // Item incoming from left (selected when swiping right)
                val leftName = PredefinedLayouts.displayName(activeLayouts[leftIndex])
                val leftDist = Math.abs(leftTranslationX)
                val leftProgress = (leftDist / slotPx).coerceIn(0f, 1f)
                val leftAlpha = (1f - leftProgress * 0.75f).coerceIn(0.15f, 1f)
                val leftScale = (1f - leftProgress * 0.12f).coerceIn(0.88f, 1f)
                val isLeftTarget = targetIndex == leftIndex
                Text(
                    text = leftName,
                    color = if (isLeftTarget) activeLabelColor else colorScheme.keyForeground.copy(alpha = 0.75f),
                    fontSize = (15.5f * scaleFactor).coerceIn(14f, 18f).sp,
                    fontWeight = if (isLeftTarget) FontWeight.SemiBold else FontWeight.Normal,
                    fontFamily = KiebordTheme.fontFamily,
                    maxLines = 1,
                    modifier = Modifier
                        .graphicsLayer {
                            translationX = leftTranslationX
                            alpha = leftAlpha
                            scaleX = leftScale
                            scaleY = leftScale
                        }
                )

                // Current center item
                val centerName = PredefinedLayouts.displayName(activeLayouts[centerIndex])
                val centerDist = Math.abs(centerTranslationX)
                val centerProgress = (centerDist / slotPx).coerceIn(0f, 1f)
                val centerAlpha = (1f - centerProgress * 0.75f).coerceIn(0.15f, 1f)
                val centerScale = (1f - centerProgress * 0.12f).coerceIn(0.88f, 1f)
                val isCenterTarget = targetIndex == centerIndex
                Text(
                    text = centerName,
                    color = if (isCenterTarget) activeLabelColor else colorScheme.keyForeground.copy(alpha = 0.75f),
                    fontSize = (15.5f * scaleFactor).coerceIn(14f, 18f).sp,
                    fontWeight = if (isCenterTarget) FontWeight.SemiBold else FontWeight.Normal,
                    fontFamily = KiebordTheme.fontFamily,
                    maxLines = 1,
                    modifier = Modifier
                        .graphicsLayer {
                            translationX = centerTranslationX
                            alpha = centerAlpha
                            scaleX = centerScale
                            scaleY = centerScale
                        }
                )

                // Item incoming from right (selected when swiping left)
                val rightName = PredefinedLayouts.displayName(activeLayouts[rightIndex])
                val rightDist = Math.abs(rightTranslationX)
                val rightProgress = (rightDist / slotPx).coerceIn(0f, 1f)
                val rightAlpha = (1f - rightProgress * 0.75f).coerceIn(0.15f, 1f)
                val rightScale = (1f - rightProgress * 0.12f).coerceIn(0.88f, 1f)
                val isRightTarget = targetIndex == rightIndex
                Text(
                    text = rightName,
                    color = if (isRightTarget) activeLabelColor else colorScheme.keyForeground.copy(alpha = 0.75f),
                    fontSize = (15.5f * scaleFactor).coerceIn(14f, 18f).sp,
                    fontWeight = if (isRightTarget) FontWeight.SemiBold else FontWeight.Normal,
                    fontFamily = KiebordTheme.fontFamily,
                    maxLines = 1,
                    modifier = Modifier
                        .graphicsLayer {
                            translationX = rightTranslationX
                            alpha = rightAlpha
                            scaleX = rightScale
                            scaleY = rightScale
                        }
                )
            }

            Icon(
                imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                contentDescription = null,
                tint = if (swipeDeltaX >= commitThresholdPx) {
                    activeLabelColor
                } else {
                    colorScheme.keyForeground.copy(alpha = 0.35f)
                },
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

/**
 * Single-colored, smoothly tapering and fading gesture trail overlay.
 * Renders an ink-ribbon comet trailing the fingertip that dynamically evaporates at the tail.
 */
@Composable
fun GestureTrailOverlay(
    trail: List<Offset>,
    accentColor: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier,
    fadeAlpha: Float = 1f
) {
    if (trail.size < 2 || fadeAlpha <= 0f) return

    val maxTrailDistance = with(LocalDensity.current) { 220.dp.toPx() }
    val baseHeadWidthPx = with(LocalDensity.current) { 9.dp.toPx() }
    val baseTailWidthPx = with(LocalDensity.current) { 1.5.dp.toPx() }

    val headWidthPx = baseHeadWidthPx * (0.35f + 0.65f * fadeAlpha)
    val tailWidthPx = baseTailWidthPx * (0.35f + 0.65f * fadeAlpha)
    val visiblePoints = remember { ArrayList<Offset>(64) }

    androidx.compose.foundation.Canvas(modifier = modifier) {
        // Collect visible trailing points backwards from current fingertip up to maxTrailDistance
        visiblePoints.clear()
        visiblePoints.add(trail.last())
        var accumulatedDist = 0f

        for (i in trail.size - 1 downTo 1) {
            val pHead = trail[i]
            val pPrev = trail[i - 1]
            val segDist = (pHead - pPrev).getDistance()
            if (accumulatedDist + segDist <= maxTrailDistance) {
                accumulatedDist += segDist
                visiblePoints.add(pPrev)
            } else {
                // Interpolate cutoff point to smoothly terminate the tail
                val remaining = maxTrailDistance - accumulatedDist
                if (remaining > 0f && segDist > 0f) {
                    val ratio = remaining / segDist
                    val cutoff = Offset(
                        pHead.x + (pPrev.x - pHead.x) * ratio,
                        pHead.y + (pPrev.y - pHead.y) * ratio
                    )
                    visiblePoints.add(cutoff)
                }
                break
            }
        }

        if (visiblePoints.size < 2) {
            visiblePoints.clear()
            visiblePoints.add(trail.last())
            visiblePoints.add(trail[trail.size - 2])
        }

        // visiblePoints was collected [head -> tail]. Index from n - 1 downTo 1 to draw [tail -> head]
        val n = visiblePoints.size

        // Draw single-colored ribbon tapering quadratically from tail to head
        for (step in 0 until n - 1) {
            val p0 = visiblePoints[n - 1 - step]
            val p1 = visiblePoints[n - 2 - step]
            val progress = (step + 1).toFloat() / (n - 1).toFloat()
            // Quadratic easing gives that slender comet tail and rounded fingertip
            val widthFactor = progress * progress
            val strokeWidth = tailWidthPx + widthFactor * (headWidthPx - tailWidthPx)
            val alpha = (0.08f + progress * 0.77f) * fadeAlpha

            drawLine(
                color = accentColor.copy(alpha = alpha),
                start = p0,
                end = p1,
                strokeWidth = strokeWidth,
                cap = androidx.compose.ui.graphics.StrokeCap.Round
            )
        }

        // Smooth rounded fingertip at the head
        val headPoint = visiblePoints[0]
        drawCircle(
            color = accentColor.copy(alpha = 0.85f * fadeAlpha),
            radius = headWidthPx / 2f,
            center = headPoint
        )
    }
}

/**
 * Confirmation dialog popup shown when a learned suggestion candidate is long-pressed,
 * prompting the user to unlearn and remove it from the personalized dictionary.
 */
@Composable
fun UnlearnWordConfirmationDialog(
    word: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colorScheme = KiebordTheme.colorScheme

    Box(
        modifier = modifier
            .background(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.55f))
            .clickable(
                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                indication = null
            ) { onDismiss() },
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .width(280.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(colorScheme.containerBackground)
                .clickable(
                    interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                    indication = null
                ) {} // consume touch events
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Remove learned word?",
                color = colorScheme.keyForeground,
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = KiebordTheme.fontFamily,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = "Do you want to remove \"$word\" from your learned suggestions?",
                color = colorScheme.keyForeground.copy(alpha = 0.8f),
                fontSize = 14.sp,
                fontFamily = KiebordTheme.fontFamily,
                textAlign = TextAlign.Center,
                lineHeight = 19.sp
            )

            Spacer(modifier = Modifier.height(18.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onDismiss() }
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Cancel",
                        color = colorScheme.keyForeground.copy(alpha = 0.7f),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        fontFamily = KiebordTheme.fontFamily
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                val removeBtnFg = ThemeColorUtils.ensureContrast(
                    foreground = colorScheme.actionKeyForeground,
                    background = colorScheme.accentColor,
                    minContrast = 4.0f
                )
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(colorScheme.accentColor)
                        .clickable { onConfirm() }
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Remove",
                        color = removeBtnFg,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = KiebordTheme.fontFamily
                    )
                }
            }
        }
    }
}

@Composable
private fun VoiceTypingCenterBar(
    statusText: String,
    scaleFactor: Float,
    colorScheme: KeyboardColorScheme,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "VoiceDots")
    val dotScale by infiniteTransition.animateFloat(
        initialValue = 0.8f,
        targetValue = 1.25f,
        animationSpec = infiniteRepeatable(
            animation = tween(650, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "DotScale"
    )

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        // Fixed-width indicator container so pulsating dots never shift the text
        Box(
            modifier = Modifier.width((30 * scaleFactor).dp),
            contentAlignment = Alignment.Center
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy((4 * scaleFactor).dp)
            ) {
                Box(
                    modifier = Modifier
                        .size((6 * scaleFactor).dp)
                        .graphicsLayer {
                            scaleX = dotScale
                            scaleY = dotScale
                        }
                        .clip(CircleShape)
                        .background(colorScheme.accentColor)
                )
                Box(
                    modifier = Modifier
                        .size((8 * scaleFactor).dp)
                        .graphicsLayer {
                            val s = 2.05f - dotScale
                            scaleX = s
                            scaleY = s
                        }
                        .clip(CircleShape)
                        .background(colorScheme.accentColor)
                )
                Box(
                    modifier = Modifier
                        .size((6 * scaleFactor).dp)
                        .graphicsLayer {
                            scaleX = dotScale
                            scaleY = dotScale
                        }
                        .clip(CircleShape)
                        .background(colorScheme.accentColor)
                )
            }
        }

        Spacer(modifier = Modifier.width((8 * scaleFactor).dp))

        // Stable text container so text start position remains identical
        Box(
            modifier = Modifier.widthIn(min = (100 * scaleFactor).dp),
            contentAlignment = Alignment.CenterStart
        ) {
            Text(
                text = statusText,
                color = colorScheme.accentColor,
                fontSize = (15 * scaleFactor).coerceIn(12f, 17f).sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun NumberPadExtendedLayout(
    keyboardRows: List<KeyRow>,
    numberUpperRowHeight: Dp,
    bottomRowHeight: Dp,
    scaleFactor: Float,
    areKeysInteractive: Boolean,
    colorScheme: KeyboardColorScheme,
    showKeyBorders: Boolean,
    showKeyShadows: Boolean,
    showSecondaryKeys: Boolean,
    showKeyPopups: Boolean,
    actionKeyVisual: ActionKeyVisual,
    longPressDelayMs: Long,
    handleKeyPress: (KeyDef) -> Unit,
    handleKeyTapUnified: (KeyDef) -> Unit,
    onKeyLongPress: (KeyDef) -> Unit,
    onDeleteSwipeMove: (Float) -> Unit,
    onDeleteSwipeEnd: () -> Unit,
    onDeleteSwipeCancel: () -> Unit,
    onDeleteLongPressEnd: () -> Unit = {},
    fastDeleteEnabled: Boolean = false,
    selectiveDeleteEnabled: Boolean = true,
    onFastDelete: () -> Unit = {},
    handleShowMoreKeys: (KeyDef, LayoutCoordinates) -> Unit,
    handleMoreKeysHover: (LayoutCoordinates, Offset) -> Unit = { _, _ -> },
    handleMoreKeysCommit: () -> Unit = {}
) {
    val extendedOperators = remember { listOf("+", "-", "*", "/", "(", ")", "#") }
    val opScrollState = rememberScrollState()
    val topRowsHeight = numberUpperRowHeight * 3
    val scrollbarColor = colorScheme.accentColor.takeIf { it != Color.Unspecified } ?: colorScheme.keyForeground

    val fontScaleFactor = scaleFactor.coerceAtMost(1.0f)
    val horizontalPadding = (2.8f * fontScaleFactor).coerceIn(1.2f, 3.2f).dp
    val verticalPadding = (4.2f * scaleFactor).coerceIn(1.5f, 6.0f).dp

    val sampleKeyDef = remember { KeyDef(label = "+", type = KeyType.CHARACTER) }
    val baseVisual = colorScheme.resolveKeycapVisual(
        rowIndex = 0,
        keyIndex = 0,
        key = sampleKeyDef,
        isPressed = false,
        showKeyBorders = showKeyBorders,
        showKeyShadows = showKeyShadows,
        scaleFactor = scaleFactor
    )
    val baseVisualPressed = colorScheme.resolveKeycapVisual(
        rowIndex = 0,
        keyIndex = 0,
        key = sampleKeyDef,
        isPressed = true,
        showKeyBorders = showKeyBorders,
        showKeyShadows = showKeyShadows,
        scaleFactor = scaleFactor
    )
    val containerShape = RoundedCornerShape(baseVisual.cornerRadiusDp ?: (8 * scaleFactor).coerceAtLeast(3f).dp)
    val containerBrush = baseVisual.backgroundBrush

    // Top section: Left scrollable operator strip + Right 3x3 numeric grid & right column
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(topRowsHeight)
    ) {
        // Left Column: Scrollable operator strip container (width 14%)
        Box(
            modifier = Modifier
                .weight(1.4f)
                .fillMaxHeight()
                .padding(horizontal = horizontalPadding, vertical = verticalPadding)
                .then(
                    if (showKeyShadows && baseVisual.shadowElevationDp > 0.dp && baseVisual.shadowColor.alpha > 0.01f) {
                        Modifier.shadow(
                            elevation = baseVisual.shadowElevationDp,
                            shape = containerShape,
                            clip = false,
                            ambientColor = baseVisual.shadowColor,
                            spotColor = baseVisual.shadowColor
                        )
                    } else Modifier
                )
                .clip(containerShape)
                .then(
                    if (containerBrush != null) {
                        Modifier.background(containerBrush, shape = containerShape)
                    } else {
                        Modifier.background(baseVisual.backgroundColor, shape = containerShape)
                    }
                )
                .then(
                    if (showKeyBorders && baseVisual.strokeWidthDp > 0.dp && baseVisual.strokeColor.alpha > 0.01f) {
                        Modifier.border(
                            width = baseVisual.strokeWidthDp,
                            color = baseVisual.strokeColor,
                            shape = containerShape
                        )
                    } else Modifier
                )
        ) {
            val innerHeight = topRowsHeight - verticalPadding * 2
            val opKeyHeight = innerHeight / 4

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        compositingStrategy = CompositingStrategy.Offscreen
                    }
                    .drawWithContent {
                        drawContent()
                        val fadeHeight = 16.dp.toPx()
                        val maxScroll = opScrollState.maxValue
                        val currentScroll = opScrollState.value.toFloat()

                        // Top fading edge when scrolled down
                        if (currentScroll > 0f) {
                            val topAlpha = (currentScroll / fadeHeight).coerceIn(0f, 1f)
                            drawRect(
                                brush = Brush.verticalGradient(
                                    colors = listOf(Color.Transparent, Color.Black),
                                    startY = 0f,
                                    endY = fadeHeight
                                ),
                                topLeft = Offset.Zero,
                                size = Size(size.width, fadeHeight),
                                blendMode = BlendMode.DstIn,
                                alpha = topAlpha
                            )
                        }

                        // Bottom fading edge when more items below
                        if (maxScroll > 0 && currentScroll < maxScroll.toFloat()) {
                            val bottomAlpha = ((maxScroll - currentScroll) / fadeHeight).coerceIn(0f, 1f)
                            drawRect(
                                brush = Brush.verticalGradient(
                                    colors = listOf(Color.Black, Color.Transparent),
                                    startY = size.height - fadeHeight,
                                    endY = size.height
                                ),
                                topLeft = Offset(0f, size.height - fadeHeight),
                                size = Size(size.width, fadeHeight),
                                blendMode = BlendMode.DstIn,
                                alpha = bottomAlpha
                            )
                        }

                        // Scrollbar track & thumb indicator
                        if (maxScroll > 0) {
                            val totalHeight = size.height
                            val visibleRatio = totalHeight / (totalHeight + maxScroll)
                            val thumbHeight = (totalHeight * visibleRatio).coerceIn(18.dp.toPx(), totalHeight)
                            val scrollFraction = currentScroll / maxScroll.toFloat()
                            val thumbY = (totalHeight - thumbHeight) * scrollFraction
                            val thumbWidth = 2.5.dp.toPx()
                            val thumbX = size.width - thumbWidth - 1.5.dp.toPx()

                            drawRoundRect(
                                color = scrollbarColor.copy(alpha = 0.12f),
                                topLeft = Offset(thumbX, 0f),
                                size = Size(thumbWidth, totalHeight),
                                cornerRadius = CornerRadius(thumbWidth / 2f, thumbWidth / 2f)
                            )

                            drawRoundRect(
                                color = scrollbarColor.copy(alpha = 0.65f),
                                topLeft = Offset(thumbX, thumbY),
                                size = Size(thumbWidth, thumbHeight),
                                cornerRadius = CornerRadius(thumbWidth / 2f, thumbWidth / 2f)
                            )
                        }
                    }
                    .verticalScroll(opScrollState)
            ) {
                extendedOperators.forEachIndexed { index, op ->
                    key("ext_op_$op") {
                        ScrollableOperatorKey(
                            operator = op,
                            height = opKeyHeight,
                            scaleFactor = scaleFactor,
                            isInteractive = areKeysInteractive,
                            visual = baseVisual,
                            pressedVisual = baseVisualPressed,
                            showDivider = showKeyBorders && index < extendedOperators.lastIndex,
                            onClick = {
                                val keyDef = KeyDef(label = op, commitText = op)
                                handleKeyPress(keyDef)
                                handleKeyTapUnified(keyDef)
                            },
                            onLongClick = {
                                val keyDef = KeyDef(label = op, commitText = op)
                                handleKeyPress(keyDef)
                                handleKeyTapUnified(keyDef)
                            }
                        )
                    }
                }
            }
        }

        // Right Section: 3 Rows of 4 keys (width 8.6f)
        Column(
            modifier = Modifier
                .weight(8.6f)
                .fillMaxHeight()
        ) {
            for (rIdx in 0..2) {
                val row = keyboardRows[rIdx]
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(numberUpperRowHeight)
                ) {
                    row.keys.drop(1).forEachIndexed { keyIndex, key ->
                        key("cell_${rIdx}_${keyIndex + 1}") {
                            KeycapView(
                                key = key,
                                shiftState = ShiftState.UNSHIFTED,
                                actionKeyVisual = actionKeyVisual,
                                rowIndex = rIdx,
                                keyIndex = keyIndex + 1,
                                isBottomRow = false,
                                isSlidingSymbolMode = false,
                                isSlidingShiftMode = false,
                                showKeyBorders = showKeyBorders,
                                showKeyShadows = showKeyShadows,
                                showSecondaryKeys = showSecondaryKeys,
                                showKeyPopups = showKeyPopups,
                                compactGaps = false,
                                isInteractive = areKeysInteractive,
                                rowHeight = numberUpperRowHeight,
                                scaleFactor = scaleFactor,
                                gestureTypingEnabled = false,
                                longPressDelayMs = longPressDelayMs,
                                modifier = Modifier
                                    .weight(key.widthWeight)
                                    .fillMaxHeight(),
                                onKeyPress = handleKeyPress,
                                onKeyTap = handleKeyTapUnified,
                                onKeyLongPress = onKeyLongPress,
                                onDeleteSwipeMove = onDeleteSwipeMove,
                                onDeleteSwipeEnd = onDeleteSwipeEnd,
                                onDeleteSwipeCancel = onDeleteSwipeCancel,
                                onDeleteLongPressEnd = onDeleteLongPressEnd,
                                fastDeleteEnabled = fastDeleteEnabled,
                                selectiveDeleteEnabled = selectiveDeleteEnabled,
                                onFastDelete = onFastDelete,
                                onShowMoreKeys = handleShowMoreKeys,
                                onMoreKeysHover = handleMoreKeysHover,
                                onMoreKeysCommit = handleMoreKeysCommit
                            )
                        }
                    }
                }
            }
        }
    }

    // Bottom Row: ABC, Space, 0, ., ,, Enter
    val bottomRow = keyboardRows[3]
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(bottomRowHeight)
    ) {
        bottomRow.keys.forEachIndexed { keyIndex, key ->
            val effectiveKey = if (key.type == KeyType.SPACE && key.label.isBlank()) {
                key.copy(label = "Space")
            } else {
                key
            }
            key("cell_3_$keyIndex") {
                KeycapView(
                    key = effectiveKey,
                    shiftState = ShiftState.UNSHIFTED,
                    actionKeyVisual = actionKeyVisual,
                    rowIndex = 3,
                    keyIndex = keyIndex,
                    isBottomRow = true,
                    isSlidingSymbolMode = false,
                    isSlidingShiftMode = false,
                    showKeyBorders = showKeyBorders,
                    showKeyShadows = showKeyShadows,
                    showSecondaryKeys = showSecondaryKeys,
                    showKeyPopups = showKeyPopups,
                    compactGaps = false,
                    isInteractive = areKeysInteractive,
                    rowHeight = bottomRowHeight,
                    scaleFactor = scaleFactor,
                    gestureTypingEnabled = false,
                    longPressDelayMs = longPressDelayMs,
                    modifier = Modifier
                        .weight(effectiveKey.widthWeight)
                        .fillMaxHeight(),
                    onKeyPress = handleKeyPress,
                    onKeyTap = handleKeyTapUnified,
                    onKeyLongPress = onKeyLongPress,
                    onDeleteSwipeMove = onDeleteSwipeMove,
                    onDeleteSwipeEnd = onDeleteSwipeEnd,
                    onDeleteSwipeCancel = onDeleteSwipeCancel,
                    onDeleteLongPressEnd = onDeleteLongPressEnd,
                    fastDeleteEnabled = fastDeleteEnabled,
                    onFastDelete = onFastDelete,
                    onShowMoreKeys = handleShowMoreKeys,
                    onMoreKeysHover = handleMoreKeysHover,
                    onMoreKeysCommit = handleMoreKeysCommit
                )
            }
        }
    }
}

@Composable
private fun ScrollableOperatorKey(
    operator: String,
    height: Dp,
    scaleFactor: Float,
    isInteractive: Boolean,
    visual: ResolvedKeyVisual,
    pressedVisual: ResolvedKeyVisual,
    showDivider: Boolean,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var isPressed by remember { mutableStateOf(false) }
    val fontScaleFactor = scaleFactor.coerceAtMost(1.0f)
    val backgroundBrush = visual.backgroundBrush
    val pressedBrush = pressedVisual.backgroundBrush
    val currentOnClick = rememberUpdatedState(onClick)
    val currentOnLongClick = rememberUpdatedState(onLongClick)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .then(
                if (isPressed) {
                    if (pressedBrush != null) {
                        Modifier.background(pressedBrush)
                    } else {
                        Modifier.background(pressedVisual.backgroundColor)
                    }
                } else {
                    if (backgroundBrush != null) {
                        Modifier.background(backgroundBrush)
                    } else {
                        Modifier.background(visual.backgroundColor)
                    }
                }
            )
            .pointerInput(isInteractive) {
                if (!isInteractive) return@pointerInput
                detectTapGestures(
                    onPress = {
                        isPressed = true
                        tryAwaitRelease()
                        isPressed = false
                    },
                    onTap = { currentOnClick.value() },
                    onLongPress = {
                        val onLong = currentOnLongClick.value
                        if (onLong != null) {
                            onLong()
                        } else {
                            currentOnClick.value()
                        }
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = operator,
            color = if (isPressed) pressedVisual.foregroundColor else visual.foregroundColor,
            fontSize = (20 * fontScaleFactor).coerceAtLeast(14f).sp,
            fontWeight = FontWeight.Medium,
            fontFamily = KiebordTheme.fontFamily,
            textAlign = TextAlign.Center
        )

        if (showDivider && visual.strokeWidthDp > 0.dp && visual.strokeColor.alpha > 0.01f) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(visual.strokeWidthDp)
                    .align(Alignment.BottomCenter)
                    .background(visual.strokeColor.copy(alpha = 0.35f))
            )
        }
    }
}

