package com.codepotro.borno.feature.ime.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Backspace
import androidx.compose.material.icons.automirrored.outlined.KeyboardReturn
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Search
import com.codepotro.borno.feature.ime.action.ActionKeyType
import com.codepotro.borno.feature.ime.action.ActionKeyVisual

import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Mood
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.findRootCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.LocalContext
import com.codepotro.borno.feature.ime.R
import com.codepotro.borno.common.model.KeyDef
import com.codepotro.borno.common.model.KeyType
import com.codepotro.borno.common.model.ShiftState
import com.codepotro.borno.theme.KeyboardColorScheme
import com.codepotro.borno.theme.KiebordTheme
import com.codepotro.borno.theme.effectiveMoreKeysBackground
import com.codepotro.borno.data.prefs.KiebordPreferences
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Checks whether the given string contains any Bengali characters (letters, vowel signs, digits, punctuation).
 */
fun isBanglaText(str: String?): Boolean {
    if (str.isNullOrEmpty()) return false
    for (i in 0 until str.length) {
        val c = str[i]
        if ((c in '\u0980'..'\u09FF') || c == '\u0964' || c == '\u0965') {
            return true
        }
    }
    return false
}

@Composable
fun KeycapView(
    key: KeyDef,
    shiftState: ShiftState,
    actionKeyVisual: ActionKeyVisual = ActionKeyVisual.DefaultEnter,
    modifier: Modifier = Modifier,
    rowIndex: Int = 0,
    keyIndex: Int = 0,
    isBottomRow: Boolean = false,
    isSlidingSymbolMode: Boolean = false,
    isSlidingShiftMode: Boolean = false,
    showKeyBorders: Boolean = true,
    showKeyShadows: Boolean = true,
    showSecondaryKeys: Boolean = true,
    showKeyPopups: Boolean = true,
    compactGaps: Boolean = false,
    rowHeight: Dp = 61.dp,
    scaleFactor: Float = (rowHeight.value / (if (rowHeight.value >= 58f) 61f else 54f)).coerceIn(0.4f, 1.6f),
    onKeyPress: (KeyDef) -> Unit = {},
    onKeyTap: (KeyDef) -> Unit,
    onKeyLongPress: (KeyDef) -> Unit = {},
    onKeyPositioned: (KeyDef, LayoutCoordinates) -> Unit = { _, _ -> },
    onShowMoreKeys: (KeyDef, LayoutCoordinates) -> Unit = { _, _ -> },
    onMoreKeysHover: (LayoutCoordinates, Offset) -> Unit = { _, _ -> },
    onMoreKeysCommit: () -> Unit = {},
    onSlidingDragStart: (KeyDef, LayoutCoordinates, Offset) -> Unit = { _, _, _ -> },
    onSlidingDragMove: (Offset) -> Unit = {},
    onSlidingDragEnd: (Offset) -> Unit = {},
    onSpacebarTrackpad: (Int) -> Unit = {},
    onSpacebarTrackpadEnd: () -> Unit = {},
    onSpacebarSwipeMove: (Float) -> Unit = {},
    onSpacebarSwipeEnd: (Float) -> Unit = {},
    onSpacebarDwellReady: () -> Unit = {},
    onDeleteSwipeMove: (Float) -> Unit = {},
    onDeleteSwipeEnd: () -> Unit = {},
    onDeleteSwipeCancel: () -> Unit = {},
    onDeleteLongPressEnd: () -> Unit = {},
    fastDeleteEnabled: Boolean = false,
    selectiveDeleteEnabled: Boolean = true,
    spacebarSwipeMode: String = KiebordPreferences.SPACEBAR_MODE_SWIPE_LAYOUT_HOLD_CURSOR,
    cursorTrackpadEnabled: Boolean = true,
    onFastDelete: () -> Unit = {},
    onGestureStart: (LayoutCoordinates, Offset) -> Unit = { _, _ -> },
    onGestureMove: (LayoutCoordinates, Offset) -> Unit = { _, _ -> },
    onGestureEnd: () -> Unit = {},
    isInteractive: Boolean = true,
    gestureTypingEnabled: Boolean = true,
    longPressDelayMs: Long = 300L,
    longPressPrimaryShortcutsEnabled: Boolean = true
) {
    val colorScheme = KiebordTheme.colorScheme
    val density = LocalDensity.current
    val haptic = LocalHapticFeedback.current
    val context = LocalContext.current
    // Capped scale factor for font, icons, and horizontal spacing:
    // When keyboard height increases, font/horizontal scaling MUST NOT increase beyond 1.0f,
    // otherwise keycap labels and bottom row keys expand horizontally and overlap.
    val fontScaleFactor = scaleFactor.coerceAtMost(1.0f)
    val iconScaleFactor = scaleFactor.coerceAtMost(1.0f)
    var isPressed by remember { mutableStateOf(false) }
    var isMoreKeysActive by remember { mutableStateOf(false) }
    var keyCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }

    // Fully resolved keycap styling from rich themebase or flat color scheme
    val keyVisual = remember(colorScheme, rowIndex, keyIndex, key, isPressed, showKeyBorders, showKeyShadows, scaleFactor) {
        colorScheme.resolveKeycapVisual(
            rowIndex = rowIndex,
            keyIndex = keyIndex,
            key = key,
            isPressed = isPressed,
            showKeyBorders = showKeyBorders,
            showKeyShadows = showKeyShadows,
            scaleFactor = scaleFactor
        )
    }

    val activeBackground = keyVisual.backgroundColor
    val bgBrush = keyVisual.backgroundBrush
    val foregroundColor = keyVisual.foregroundColor
    val displaySymbolLabel = key.displayLabel(shiftState)

    // Key shape: stadium pills for action/sym keys or custom pill/radius specs from theme
    val isPillColoredKey = key.type == KeyType.SPACE || key.type == KeyType.SYMBOLS || key.type == KeyType.ACTION
    val customRadiusDp = keyVisual.cornerRadiusDp
    val keyShape = if (keyVisual.isPill) {
        RoundedCornerShape(percent = 50)
    } else if (customRadiusDp != null) {
        RoundedCornerShape(customRadiusDp)
    } else {
        RoundedCornerShape((8 * scaleFactor).coerceAtLeast(3f).dp)
    }

    // In flat theme (!showKeyBorders), reduce height of Space bar, Sym key, and Enter key
    val isReducedHeightKey = !showKeyBorders && isPillColoredKey
    val horizontalPadding = if (compactGaps) {
        (1.5f * fontScaleFactor).coerceIn(0.8f, 2.0f).dp
    } else {
        (2.8f * fontScaleFactor).coerceIn(1.2f, 3.2f).dp
    }
    val verticalPadding = if (compactGaps) {
        if (isReducedHeightKey) (5.5f * scaleFactor).coerceAtLeast(2.0f).dp else (2.2f * scaleFactor).coerceIn(1.0f, 3.2f).dp
    } else {
        if (isReducedHeightKey) (8.5f * scaleFactor).coerceAtLeast(2.8f).dp else (4.2f * scaleFactor).coerceIn(1.5f, 6.0f).dp
    }

    // Capture latest parameters in updated states to allow pointerInput without gesture cancellation
    val currentKey = rememberUpdatedState(key)
    val currentOnKeyPress = rememberUpdatedState(onKeyPress)
    val currentOnTap = rememberUpdatedState(onKeyTap)
    val currentOnLongPress = rememberUpdatedState(onKeyLongPress)
    val currentOnDeleteSwipeMove = rememberUpdatedState(onDeleteSwipeMove)
    val currentOnDeleteSwipeEnd = rememberUpdatedState(onDeleteSwipeEnd)
    val currentOnDeleteSwipeCancel = rememberUpdatedState(onDeleteSwipeCancel)
    val currentOnDeleteLongPressEnd = rememberUpdatedState(onDeleteLongPressEnd)
    val currentFastDeleteEnabled = rememberUpdatedState(fastDeleteEnabled)
    val currentSelectiveDeleteEnabled = rememberUpdatedState(selectiveDeleteEnabled)
    val currentSpacebarSwipeMode = rememberUpdatedState(spacebarSwipeMode)
    val currentCursorTrackpadEnabled = rememberUpdatedState(cursorTrackpadEnabled)
    val currentOnFastDelete = rememberUpdatedState(onFastDelete)
    val currentOnShowMoreKeys = rememberUpdatedState(onShowMoreKeys)
    val currentOnMoreKeysHover = rememberUpdatedState(onMoreKeysHover)
    val currentOnMoreKeysCommit = rememberUpdatedState(onMoreKeysCommit)
    val currentOnSlidingStart = rememberUpdatedState(onSlidingDragStart)
    val currentOnSlidingMove = rememberUpdatedState(onSlidingDragMove)
    val currentOnSlidingEnd = rememberUpdatedState(onSlidingDragEnd)
    val currentOnSpacebarTrackpad = rememberUpdatedState(onSpacebarTrackpad)
    val currentOnSpacebarTrackpadEnd = rememberUpdatedState(onSpacebarTrackpadEnd)
    val currentOnSpacebarSwipeMove = rememberUpdatedState(onSpacebarSwipeMove)
    val currentOnSpacebarSwipeEnd = rememberUpdatedState(onSpacebarSwipeEnd)
    val currentOnSpacebarDwellReady = rememberUpdatedState(onSpacebarDwellReady)
    val currentOnGestureStart = rememberUpdatedState(onGestureStart)
    val currentOnGestureMove = rememberUpdatedState(onGestureMove)
    val currentOnGestureEnd = rememberUpdatedState(onGestureEnd)
    val currentGestureTypingEnabled = rememberUpdatedState(gestureTypingEnabled)
    val currentLongPressDelay = rememberUpdatedState(longPressDelayMs)
    val currentLongPressPrimaryShortcutsEnabled = rememberUpdatedState(longPressPrimaryShortcutsEnabled)
    val currentShiftState = rememberUpdatedState(shiftState)

    val isSpecialNoPopupKey = key.type == KeyType.SPACE || key.commitText == "\u200C" || displaySymbolLabel.contains("ZWNJ") || displaySymbolLabel.contains("\n")
    val showCircularPopup = showKeyPopups && isPressed && key.type == KeyType.CHARACTER && !isSlidingSymbolMode && !isSlidingShiftMode && !isMoreKeysActive && !isSpecialNoPopupKey

    // Key styling: cleanly toggles between keyborder mode (keycap boxes) and flat mode (no boxes except accent pills)
    Box(
        modifier = modifier
            .fillMaxSize()
            .zIndex(if (showCircularPopup) 50f else 0f)
            .onGloballyPositioned { coords ->
                keyCoordinates = coords
                onKeyPositioned(key, coords)
            }
            .pointerInput(isInteractive) {
                    if (!isInteractive) return@pointerInput
                    coroutineScope {
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            down.consume()
                            val initialKey = currentKey.value
                            val initialDownPos = down.position
                            isPressed = true
                            isMoreKeysActive = false
                            currentOnKeyPress.value(initialKey)

                            val isSymbolsToggleKey = initialKey.type == KeyType.SYMBOLS || initialKey.label == "?123" || initialKey.label == "?১২৩" || initialKey.label == "ABC"
                            var isSlidingDrag = false
                            var isSpacebarDwellReady = false
                            var isSpacebarSwipingLayout = false
                            var initialSpacebarSwipeX = 0f
                            var isSpacebarTrackpadActive = false
                            var isGestureTyping = false
                            var isDeleteSwipeActive = false
                            var isFastDeleteArmed = false
                            val fastDeleteThresholdPx = with(density) { 56.dp.toPx() }
                            val fastDeleteCancelThresholdPx = with(density) { 20.dp.toPx() }
                            var gestureEnded = false
                            var lastTrackpadX = 0f
                            var lastTrackpadEventX = 0f
                            var lastTrackpadEventTimeMs = down.uptimeMillis
                            var smoothedTrackpadVelocity = 0f
                            val trackpadBaseStepPx = with(density) { 15.dp.toPx() }

                            val isSlidingInitiator = initialKey.slidingInput || initialKey.type == KeyType.SHIFT || isSymbolsToggleKey

                            val spacebarDwellJob = if (initialKey.type == KeyType.SPACE) {
                                launch {
                                    delay(400L)
                                    isSpacebarDwellReady = true
                                    currentOnSpacebarDwellReady.value()
                                }
                            } else null

                            var isLongPressTriggered = false
                            var isDeleteLongPressActive = false
                            val longPressJob = if (isSlidingInitiator || isSymbolsToggleKey) null else launch {
                                val delayDuration = if (initialKey.type == KeyType.DELETE) {
                                    currentLongPressDelay.value.coerceIn(250L, 400L)
                                } else {
                                    currentLongPressDelay.value
                                }
                                delay(delayDuration)
                                val coords = keyCoordinates
                                isLongPressTriggered = true
                                val activeShift = currentShiftState.value
                                val activeMoreKeys = initialKey.moreKeys(activeShift, currentLongPressPrimaryShortcutsEnabled.value)
                                if (initialKey.type == KeyType.DELETE) {
                                    isDeleteLongPressActive = true
                                    var repeatCount = 0
                                    while (isActive) {
                                        currentOnLongPress.value(initialKey)
                                        repeatCount++
                                        val interval = when {
                                            repeatCount < 10 -> 50L
                                            repeatCount < 25 -> 38L
                                            else -> 28L
                                        }
                                        delay(interval)
                                    }
                                } else if (initialKey.type == KeyType.SPACE) {
                                    // Handled via spacebarDwellJob
                                } else if (activeMoreKeys.isNotEmpty() && coords != null && coords.isAttached) {
                                    isMoreKeysActive = true
                                    currentOnShowMoreKeys.value(initialKey.copy(moreKeys = activeMoreKeys), coords)
                                } else {
                                    currentOnLongPress.value(initialKey)
                                }
                            }

                            try {
                                while (true) {
                                    val event = awaitPointerEvent()
                                    val change = event.changes.firstOrNull { it.id == down.id } ?: break

                                if (!change.pressed) {
                                    // Touch released!
                                    spacebarDwellJob?.cancel()
                                    longPressJob?.cancel()
                                    val wasMoreKeysActive = isMoreKeysActive
                                    isMoreKeysActive = false
                                val delta = change.position - initialDownPos
                                if (isFastDeleteArmed) {
                                    change.consume()
                                    currentOnFastDelete.value()
                                } else if (isDeleteSwipeActive) {
                                    if (delta.x < -18f) {
                                        currentOnDeleteSwipeEnd.value()
                                    } else {
                                        currentOnDeleteSwipeCancel.value()
                                    }
                                } else if (isSpacebarSwipingLayout) {
                                    val effectiveDeltaX = delta.x - initialSpacebarSwipeX
                                    currentOnSpacebarSwipeEnd.value(effectiveDeltaX)
                                } else if (isSpacebarTrackpadActive) {
                                    // Trackpad gesture completed, don't emit normal space tap
                                } else if (isSpacebarDwellReady) {
                                    // Held down space for cursor dwell without dragging
                                } else if (isGestureTyping) {
                                    val coords = keyCoordinates
                                    if (coords != null && coords.isAttached) {
                                        currentOnGestureMove.value(coords, change.position)
                                    }
                                    gestureEnded = true
                                    currentOnGestureEnd.value()
                                } else if (isSlidingDrag) {
                                    currentOnSlidingEnd.value(delta)
                                } else if (wasMoreKeysActive) {
                                    change.consume()
                                    currentOnMoreKeysCommit.value()
                                } else if (isDeleteLongPressActive || isLongPressTriggered) {
                                    change.consume()
                                    // Handled during long-press, do NOT emit tap on release
                                } else {
                                    change.consume()
                                    currentOnTap.value(initialKey)
                                }
                                break
                            }

                            val currentPos = change.position
                            val delta = currentPos - initialDownPos
                            val distance = delta.getDistance()

                                                        // Swipe on Delete key:
                            // 1. Sliding left selects range (Selective Delete)
                            // 2. Sliding up arms Fast Delete (deletes all text before cursor on release)
                            if (initialKey.type == KeyType.DELETE) {
                                if (currentFastDeleteEnabled.value && !isDeleteSwipeActive) {
                                    if (delta.y < -fastDeleteThresholdPx && Math.abs(delta.y) > Math.abs(delta.x) * 1.25f) {
                                        if (!isFastDeleteArmed) {
                                            isFastDeleteArmed = true
                                            longPressJob?.cancel()
                                            isPressed = false
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        }
                                        change.consume()
                                    } else if (isFastDeleteArmed && delta.y > -fastDeleteCancelThresholdPx) {
                                        isFastDeleteArmed = false
                                        change.consume()
                                    } else if (isFastDeleteArmed) {
                                        change.consume()
                                    }
                                }

                                if (!isFastDeleteArmed && !isDeleteLongPressActive && currentSelectiveDeleteEnabled.value) {
                                    if (delta.x < -14f) {
                                        if (!isDeleteSwipeActive) {
                                            isDeleteSwipeActive = true
                                            longPressJob?.cancel()
                                            isPressed = false
                                        }
                                        change.consume()
                                        currentOnDeleteSwipeMove.value(delta.x)
                                    } else if (isDeleteSwipeActive) {
                                        change.consume()
                                        currentOnDeleteSwipeMove.value(delta.x.coerceAtMost(0f))
                                    }
                                }
                            }

                            // Spacebar handling:
                            // 1. Swiping immediately (before 220ms dwell) triggers active layout sliding switch!
                            // 2. Holding still for 220ms (dwell ready) then sliding left/right activates cursor movement!
                            if (initialKey.type == KeyType.SPACE) {
                                val nowMs = change.uptimeMillis
                                val rawDt = nowMs - lastTrackpadEventTimeMs
                                val dxEvent = Math.abs(delta.x - lastTrackpadEventX)
                                if (dxEvent > 0.5f) {
                                    val dt = rawDt.coerceIn(4L, 50L).toFloat()
                                    val instVelocity = dxEvent / dt // px per ms
                                    smoothedTrackpadVelocity = if (smoothedTrackpadVelocity == 0f) {
                                        instVelocity
                                    } else {
                                        smoothedTrackpadVelocity * 0.45f + instVelocity * 0.55f
                                    }
                                    lastTrackpadEventX = delta.x
                                    lastTrackpadEventTimeMs = nowMs
                                } else if (rawDt > 80L) {
                                    smoothedTrackpadVelocity = 0f
                                    lastTrackpadEventTimeMs = nowMs
                                }
                                val velocityDpPerMs = smoothedTrackpadVelocity / density.density.coerceAtLeast(1f)
                                // Velocity-based step scaling (Borno Classic style, density-independent):
                                // slow (~0 dp/ms) -> ~18 dp/char; fast (>=1.2 dp/ms) -> ~5.25 dp/char
                                val velocityScale = (1.2f - velocityDpPerMs * 0.7f).coerceIn(0.35f, 1.35f)
                                val dynamicStepPx = (trackpadBaseStepPx * velocityScale).coerceAtLeast(1f)

                                val isSwipeCursorMode = currentSpacebarSwipeMode.value == KiebordPreferences.SPACEBAR_MODE_SWIPE_CURSOR_HOLD_LAYOUT
                                if (!isSpacebarDwellReady) {
                                    if (isSwipeCursorMode) {
                                        if (currentCursorTrackpadEnabled.value) {
                                            val diffFromLast = delta.x - lastTrackpadX
                                            if (isSpacebarTrackpadActive || Math.abs(diffFromLast) > dynamicStepPx) {
                                                if (!isSpacebarTrackpadActive) {
                                                    spacebarDwellJob?.cancel()
                                                    longPressJob?.cancel()
                                                    isSpacebarTrackpadActive = true
                                                    isPressed = false
                                                }
                                                change.consume()
                                                if (diffFromLast > dynamicStepPx) {
                                                    val steps = (diffFromLast / dynamicStepPx).toInt()
                                                    lastTrackpadX += steps * dynamicStepPx
                                                    currentOnSpacebarTrackpad.value(steps)
                                                } else if (diffFromLast < -dynamicStepPx) {
                                                    val steps = ((lastTrackpadX - delta.x) / dynamicStepPx).toInt()
                                                    lastTrackpadX -= steps * dynamicStepPx
                                                    currentOnSpacebarTrackpad.value(-steps)
                                                }
                                            }
                                        }
                                    } else {
                                        val swipeActivationPx = with(density) { 30.dp.toPx() }
                                        val absX = Math.abs(delta.x)
                                        val absY = Math.abs(delta.y)
                                        if (isSpacebarSwipingLayout || (absX > swipeActivationPx && absX > absY * 1.35f)) {
                                            if (!isSpacebarSwipingLayout) {
                                                spacebarDwellJob?.cancel()
                                                longPressJob?.cancel()
                                                isSpacebarSwipingLayout = true
                                                isPressed = false
                                                initialSpacebarSwipeX = delta.x
                                            }
                                            change.consume()
                                            val effectiveDeltaX = delta.x - initialSpacebarSwipeX
                                            currentOnSpacebarSwipeMove.value(effectiveDeltaX)
                                        }
                                    }
                                } else {
                                    if (isSwipeCursorMode) {
                                        val swipeActivationPx = with(density) { 20.dp.toPx() }
                                        val absX = Math.abs(delta.x)
                                        val absY = Math.abs(delta.y)
                                        if (isSpacebarSwipingLayout || (absX > swipeActivationPx && absX > absY * 1.2f)) {
                                            if (!isSpacebarSwipingLayout) {
                                                isSpacebarSwipingLayout = true
                                                isPressed = false
                                                initialSpacebarSwipeX = delta.x
                                            }
                                            change.consume()
                                            val effectiveDeltaX = delta.x - initialSpacebarSwipeX
                                            currentOnSpacebarSwipeMove.value(effectiveDeltaX)
                                        }
                                    } else if (currentCursorTrackpadEnabled.value) {
                                        change.consume()
                                        val diffFromLast = delta.x - lastTrackpadX
                                        if (diffFromLast > dynamicStepPx) {
                                            isSpacebarTrackpadActive = true
                                            val steps = (diffFromLast / dynamicStepPx).toInt()
                                            lastTrackpadX += steps * dynamicStepPx
                                            currentOnSpacebarTrackpad.value(steps)
                                        } else if (diffFromLast < -dynamicStepPx) {
                                            isSpacebarTrackpadActive = true
                                            val steps = ((lastTrackpadX - delta.x) / dynamicStepPx).toInt()
                                            lastTrackpadX -= steps * dynamicStepPx
                                            currentOnSpacebarTrackpad.value(-steps)
                                        }
                                    }
                                }
                            }

                            // Sliding input detector for ?123 and Shift:
                            // Only active if dragged outside the key boundaries and covered at least the key length minimum
                            if (isSlidingInitiator) {
                                val coords = keyCoordinates
                                val keyWidth = coords?.size?.width?.toFloat() ?: with(density) { 50.dp.toPx() }
                                val keyHeight = coords?.size?.height?.toFloat() ?: with(density) { 44.dp.toPx() }
                                val minKeyLength = minOf(keyWidth, keyHeight)

                                val isOutsideKey = if (coords != null && coords.isAttached) {
                                    currentPos.x < 0f || currentPos.x > keyWidth ||
                                    currentPos.y < 0f || currentPos.y > keyHeight
                                } else {
                                    distance >= minKeyLength
                                }

                                if (isOutsideKey && distance >= minKeyLength) {
                                    if (!isSlidingDrag) {
                                        isSlidingDrag = true
                                        isPressed = false
                                        longPressJob?.cancel()
                                        if (coords != null && coords.isAttached) {
                                            currentOnSlidingStart.value(initialKey, coords, initialDownPos)
                                        }
                                    }
                                }
                            }

                            // Gesture typing detector on character keys
                            if (currentGestureTypingEnabled.value && initialKey.type == KeyType.CHARACTER && !isMoreKeysActive && !isSlidingDrag) {
                                val gestureThresholdPx = with(density) { 28.dp.toPx() }
                                if (!isGestureTyping && distance > gestureThresholdPx) {
                                    longPressJob?.cancel()
                                    isGestureTyping = true
                                    isPressed = false
                                    isMoreKeysActive = false
                                    val coords = keyCoordinates
                                    if (coords != null && coords.isAttached) {
                                        currentOnGestureStart.value(coords, initialDownPos)
                                        currentOnGestureMove.value(coords, currentPos)
                                    }
                                } else if (isGestureTyping) {
                                    val coords = keyCoordinates
                                    if (coords != null && coords.isAttached) {
                                        currentOnGestureMove.value(coords, currentPos)
                                    }
                                }
                            }

                            if (isGestureTyping) {
                                change.consume()
                            } else if (isSlidingDrag) {
                                change.consume()
                                currentOnSlidingMove.value(delta)
                            } else if (isMoreKeysActive) {
                                change.consume()
                                val coords = keyCoordinates
                                if (coords != null && coords.isAttached) {
                                    currentOnMoreKeysHover.value(coords, currentPos)
                                }
                            }
                        }
                            } finally {
                                spacebarDwellJob?.cancel()
                                longPressJob?.cancel()
                                if (isSpacebarTrackpadActive) {
                                    isSpacebarTrackpadActive = false
                                    currentOnSpacebarTrackpadEnd.value()
                                }
                                if (isDeleteLongPressActive) {
                                    isDeleteLongPressActive = false
                                    currentOnDeleteLongPressEnd.value()
                                }
                                if (isGestureTyping && !gestureEnded) {
                                    gestureEnded = true
                                    currentOnGestureEnd.value()
                                }
                                if (isMoreKeysActive) {
                                    isMoreKeysActive = false
                                    currentOnMoreKeysCommit.value()
                                }
                                isPressed = false
                                isMoreKeysActive = false
                            }
                    }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = horizontalPadding, vertical = verticalPadding)
                .then(
                    if (keyVisual.shadowElevationDp > 0.dp && keyVisual.shadowColor.alpha > 0.01f) {
                        Modifier.shadow(
                            elevation = keyVisual.shadowElevationDp,
                            shape = keyShape,
                            clip = false,
                            ambientColor = keyVisual.shadowColor,
                            spotColor = keyVisual.shadowColor
                        )
                    } else {
                        Modifier
                    }
                )
                .clip(keyShape)
                .then(
                    if (bgBrush != null) {
                        Modifier.background(bgBrush)
                    } else {
                        Modifier.background(activeBackground)
                    }
                )
                .then(
                    if (keyVisual.strokeWidthDp > 0.dp && keyVisual.strokeColor.alpha > 0.01f) {
                        Modifier.border(
                            width = keyVisual.strokeWidthDp,
                            color = keyVisual.strokeColor,
                            shape = keyShape
                        )
                    } else {
                        Modifier
                    }
                ),
            contentAlignment = Alignment.Center
        ) {
        // Flick up preview badge in top right corner (hidden when sliding symbol/shift mode is active, secondary keys disabled, or no moreKeys exist)
        val activeFlickUp = key.flickUp(shiftState)
        val activeMoreKeys = key.moreKeys(shiftState, longPressPrimaryShortcutsEnabled)
        if (showSecondaryKeys && !activeFlickUp.isNullOrEmpty() && activeMoreKeys.isNotEmpty() && key.type == KeyType.CHARACTER && !isSlidingSymbolMode && !isSlidingShiftMode) {
            val isBanglaBadge = isBanglaText(activeFlickUp)
            val badgeFontSize = if (isBanglaBadge) 10f else 11.5f
            Text(
                text = activeFlickUp,
                color = if (isPressed && colorScheme.keyPressedForeground != Color.Unspecified) {
                    colorScheme.keyPressedForeground.copy(alpha = 0.7f)
                } else {
                    colorScheme.keyForeground.copy(alpha = 0.5f)
                },
                fontSize = (badgeFontSize * fontScaleFactor).coerceAtLeast(6.0f).sp,
                fontWeight = FontWeight.Normal,
                fontFamily = KiebordTheme.fontFamily,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = (2.2f * fontScaleFactor).dp, end = (3.8f * fontScaleFactor).dp)
            )
        }

        // Custom icon override lookup from theme
        val customIconName = colorScheme.getCustomIcon(key, shiftState)
        val customIconResId = remember(customIconName) {
            if (!customIconName.isNullOrBlank()) {
                context.resources.getIdentifier(customIconName, "drawable", context.packageName)
            } else 0
        }
        val customIconFile = remember(customIconName, customIconResId) {
            if (!customIconName.isNullOrBlank() && customIconResId == 0) {
                try {
                    val f = java.io.File(customIconName)
                    if (f.exists() && f.isFile && f.canRead()) f else null
                } catch (_: Throwable) {
                    null
                }
            } else null
        }
        val customFileBitmap = remember(customIconFile) {
            customIconFile?.let { file ->
                try {
                    BitmapFactory.decodeFile(file.absolutePath)?.asImageBitmap()
                } catch (_: Throwable) {
                    null
                }
            }
        }

        // Key content: Custom Icon / Vector Material Icons vs Typography label
        val actionIconSize = (22 * iconScaleFactor).coerceAtLeast(12f).dp
        if (customIconResId != 0) {
            Icon(
                painter = painterResource(customIconResId),
                contentDescription = key.label,
                tint = foregroundColor,
                modifier = Modifier.size(actionIconSize)
            )
        } else if (customFileBitmap != null) {
            Icon(
                bitmap = customFileBitmap,
                contentDescription = key.label,
                tint = foregroundColor,
                modifier = Modifier.size(actionIconSize)
            )
        } else when (key.type) {
            KeyType.SHIFT -> {
                val shiftIconSize = (25f * iconScaleFactor).coerceAtLeast(14f).dp
                when (shiftState) {
                    ShiftState.UNSHIFTED -> {
                        Icon(
                            painter = painterResource(R.drawable.ic_shift),
                            contentDescription = "Shift",
                            tint = foregroundColor,
                            modifier = Modifier.size(shiftIconSize)
                        )
                    }
                    ShiftState.ONE_SHOT -> {
                        Icon(
                            painter = painterResource(R.drawable.ic_shift_active),
                            contentDescription = "Shift One-Shot",
                            tint = foregroundColor,
                            modifier = Modifier.size(shiftIconSize)
                        )
                    }
                    ShiftState.CAPS_LOCK -> {
                        Icon(
                            painter = painterResource(R.drawable.ic_caps_lock),
                            contentDescription = "Caps Lock",
                            tint = foregroundColor,
                            modifier = Modifier.size(shiftIconSize)
                        )
                    }
                }
            }
            KeyType.DELETE -> {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.Backspace,
                    contentDescription = "Backspace",
                    tint = foregroundColor,
                    modifier = Modifier.size(actionIconSize)
                )
            }
            KeyType.ACTION -> {
                if (actionKeyVisual.customLabel != null) {
                    Text(
                        text = actionKeyVisual.customLabel,
                        color = foregroundColor,
                        fontSize = (13f * fontScaleFactor).coerceAtLeast(8f).sp,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = KiebordTheme.fontFamily,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )
                } else {
                    val icon = when (actionKeyVisual.type) {
                        ActionKeyType.SEARCH -> Icons.Outlined.Search
                        ActionKeyType.SEND -> Icons.AutoMirrored.Outlined.Send
                        ActionKeyType.GO -> Icons.AutoMirrored.Outlined.ArrowForward
                        ActionKeyType.NEXT -> Icons.AutoMirrored.Outlined.ArrowForward
                        ActionKeyType.DONE -> Icons.Outlined.Check
                        ActionKeyType.PREVIOUS -> Icons.AutoMirrored.Outlined.ArrowBack
                        ActionKeyType.ENTER, ActionKeyType.CUSTOM_LABEL -> Icons.AutoMirrored.Outlined.KeyboardReturn
                    }
                    Icon(
                        imageVector = icon,
                        contentDescription = actionKeyVisual.contentDescription,
                        tint = foregroundColor,
                        modifier = Modifier.size(actionIconSize)
                    )
                }
            }
            KeyType.EMOJI -> {
                Icon(
                    imageVector = Icons.Outlined.Mood,
                    contentDescription = "Emoji",
                    tint = foregroundColor,
                    modifier = Modifier.size((20 * iconScaleFactor).coerceAtLeast(11f).dp)
                )
            }
            KeyType.SWITCH_LANGUAGE -> {
                Icon(
                    imageVector = Icons.Outlined.Language,
                    contentDescription = "Switch Language",
                    tint = foregroundColor,
                    modifier = Modifier.size((20 * iconScaleFactor).coerceAtLeast(11f).dp)
                )
            }
            KeyType.SPACE -> {
                Text(
                    text = key.displayLabel(shiftState).ifEmpty { "English" },
                    color = if (isPressed && colorScheme.keyPressedForeground != Color.Unspecified) {
                        colorScheme.keyPressedForeground
                    } else {
                        colorScheme.keyForeground.copy(alpha = 0.45f)
                    },
                    fontSize = (15.5f * fontScaleFactor).coerceAtLeast(8f).sp,
                    fontWeight = FontWeight.Medium,
                    fontFamily = KiebordTheme.fontFamily,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
            }
            else -> {
                val isBangla = isBanglaText(displaySymbolLabel)
                val isMultiline = displaySymbolLabel.contains("\n")
                val baseCharSize = if (isMultiline) {
                    11.5f
                } else if (displaySymbolLabel.length > 2) {
                    if (isBangla) 15f else 17f
                } else if (displaySymbolLabel.length > 1) {
                    if (isBangla) 18.5f else 21f
                } else {
                    if (isBangla) 22.5f else 26f
                }
                val rawSize = if (isMultiline) 11.5f else if (key.type == KeyType.SYMBOLS) (if (isBangla) 15f else 17f) else baseCharSize
                val textSize = (rawSize * fontScaleFactor).coerceAtLeast(8.5f).sp
                val isUppercase = displaySymbolLabel.length == 1 && displaySymbolLabel.first().isUpperCase()
                val verticalShift = if (isUppercase) (1.2f * fontScaleFactor).dp else 0.dp
                Text(
                    text = displaySymbolLabel,
                    color = foregroundColor,
                    fontSize = textSize,
                    fontWeight = if (key.type == KeyType.SYMBOLS) FontWeight.Bold else FontWeight.Medium,
                    fontFamily = KiebordTheme.fontFamily,
                    maxLines = if (isMultiline) 2 else 1,
                    softWrap = isMultiline,
                    modifier = if (verticalShift > 0.dp) Modifier.offset(y = verticalShift) else Modifier,
                    style = androidx.compose.ui.text.TextStyle(
                        fontFamily = KiebordTheme.fontFamily,
                        platformStyle = androidx.compose.ui.text.PlatformTextStyle(includeFontPadding = false),
                        lineHeight = if (isMultiline) (12f * fontScaleFactor).sp else textSize,
                        lineHeightStyle = androidx.compose.ui.text.style.LineHeightStyle(
                            alignment = androidx.compose.ui.text.style.LineHeightStyle.Alignment.Center,
                            trim = androidx.compose.ui.text.style.LineHeightStyle.Trim.Both
                        ),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                )
            }
        }
    }

        // Circular Keypress Preview Popup
        if (showCircularPopup) {
            val popupDiameter = (52 * scaleFactor).coerceIn(44f, 60f).dp
            val defaultGapDp = (12 * scaleFactor).coerceIn(8f, 16f).dp
            val defaultOffsetY = -(popupDiameter + defaultGapDp)

            val coords = keyCoordinates
            val (clampedOffsetX, clampedOffsetY) = if (coords != null && coords.isAttached) {
                try {
                    val rootCoords = coords.findRootCoordinates()
                    val rootWidth = rootCoords.size.width.toFloat()
                    val rootPos = coords.positionInRoot()
                    val keyCenterX = rootPos.x + coords.size.width / 2f
                    val halfPopupPx = with(density) { (popupDiameter / 2).toPx() }
                    val marginPx = with(density) { 6.dp.toPx() }
                    val minCenterX = halfPopupPx + marginPx
                    val maxCenterX = rootWidth - halfPopupPx - marginPx
                    val nudgePx = when {
                        keyCenterX < minCenterX -> minCenterX - keyCenterX
                        keyCenterX > maxCenterX -> maxCenterX - keyCenterX
                        else -> 0f
                    }
                    val offsetX = with(density) { nudgePx.toDp() }

                    // Prevent popup from being cut off at the top of the keyboard window
                    val keyTopInRoot = with(density) { rootPos.y.toDp() }
                    val minTopDp = 4.dp
                    val offsetY = (keyTopInRoot + defaultOffsetY).coerceAtLeast(minTopDp) - keyTopInRoot
                    offsetX to offsetY
                } catch (_: Exception) {
                    0.dp to defaultOffsetY
                }
            } else {
                0.dp to defaultOffsetY
            }

            val isLight = colorScheme == KeyboardColorScheme.DefaultLight
            val popupBg = colorScheme.effectiveMoreKeysBackground
            val popupFg = com.codepotro.borno.theme.ThemeColorUtils.ensureContrast(
                foreground = colorScheme.keyForeground,
                background = popupBg,
                minContrast = 3.0f
            )

            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .offset(x = clampedOffsetX, y = clampedOffsetY)
                    .wrapContentSize(align = Alignment.Center, unbounded = true)
                    .requiredSize(popupDiameter)
                    .aspectRatio(1f, matchHeightConstraintsFirst = false)
                    .shadow(
                        elevation = (4 * scaleFactor).dp,
                        shape = CircleShape,
                        clip = false,
                        ambientColor = Color(0x38000000),
                        spotColor = Color(0x30000000)
                    )
                    .clip(CircleShape)
                    .background(popupBg)
                    .then(
                        if (!isLight && colorScheme.keyBorder.alpha > 0.01f) {
                            Modifier.border(
                                width = 0.5.dp,
                                color = Color(0x22FFFFFF),
                                shape = CircleShape
                            )
                        } else {
                            Modifier
                        }
                    ),
                contentAlignment = Alignment.Center
            ) {
                val isBanglaPreview = isBanglaText(displaySymbolLabel)
                val popupFontSize = if (isBanglaPreview) 23.5f else 27f
                Text(
                    text = displaySymbolLabel,
                    color = popupFg,
                    fontSize = (popupFontSize * scaleFactor).coerceIn(18f, 34f).sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = KiebordTheme.fontFamily,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    style = androidx.compose.ui.text.TextStyle(
                        platformStyle = androidx.compose.ui.text.PlatformTextStyle(includeFontPadding = false),
                        lineHeightStyle = androidx.compose.ui.text.style.LineHeightStyle(
                            alignment = androidx.compose.ui.text.style.LineHeightStyle.Alignment.Center,
                            trim = androidx.compose.ui.text.style.LineHeightStyle.Trim.Both
                        )
                    )
                )
            }
        }
    }
}
