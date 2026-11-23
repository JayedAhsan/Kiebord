package com.codepotro.borno.feature.ime.ui.suggestion

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.codepotro.borno.data.prefs.KiebordPreferences
import com.codepotro.borno.theme.KiebordTheme
import com.codepotro.borno.theme.effectiveToolbarBackground
import com.codepotro.borno.theme.effectiveToolbarForeground

data class SuggestionCandidate(
    val text: String,
    val isPrimary: Boolean = false,
    val rawInput: String? = null,
    val isLearned: Boolean = false,
    val isEmoji: Boolean = false
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SuggestionStripView(
    candidates: List<SuggestionCandidate>,
    onCandidateClick: (SuggestionCandidate) -> Unit,
    onCandidateLongClick: ((SuggestionCandidate) -> Unit)? = null,
    mode: String = KiebordPreferences.SUGGESTION_BAR_MODE_SCROLLABLE,
    isExpanded: Boolean = false,
    scaleFactor: Float = 1.0f,
    isFloating: Boolean = false,
    maxVisibleCandidates: Int = 3,
    onExpandToggle: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    if (candidates.isEmpty()) return

    val colorScheme = KiebordTheme.colorScheme
    val fontFamily = KiebordTheme.fontFamily
    val isScrollableMode = mode == KiebordPreferences.SUGGESTION_BAR_MODE_SCROLLABLE

    BoxWithConstraints(
        modifier = modifier
            .fillMaxHeight()
            .padding(vertical = (2 * scaleFactor).coerceIn(1f, 3f).dp)
    ) {
        val totalWidthPx = constraints.maxWidth.toFloat()
        val density = LocalDensity.current
        val textMeasurer = rememberTextMeasurer()

        val effectiveScale = if (isFloating) {
            (scaleFactor * 0.94f).coerceIn(0.82f, 1.15f)
        } else {
            scaleFactor.coerceIn(0.85f, 1.25f)
        }

        val primaryFontSize = (18.5f * effectiveScale).coerceIn(15f, 20f).sp
        val secondaryFontSize = (17.5f * effectiveScale).coerceIn(14f, 19f).sp

        val expandBtnSize = (36 * effectiveScale).coerceIn(28f, 38f).dp
        val expandBtnIconSize = (22 * effectiveScale).coerceIn(18f, 22f).dp
        val expandBtnWidthPx = with(density) { expandBtnSize.toPx() }

        val dividerHeight = (18 * effectiveScale).coerceIn(14f, 20f).dp
        val dividerWidthPx = with(density) { 1.dp.toPx() }

        val chipPaddingDp = (8 * effectiveScale).coerceIn(6f, 10f).dp
        val chipPaddingPx = with(density) { chipPaddingDp.toPx() }
        val cornerRadius = (8 * effectiveScale).coerceIn(6f, 10f).dp

        // Dynamically determine visible candidates:
        // Try up to maxVisibleCandidates (4 when enabled or emoji present, else 3) -> 3 -> 2 -> 1.
        // Never truncate any candidate.
        val visibleCandidates = remember(
            candidates,
            totalWidthPx,
            isScrollableMode,
            isExpanded,
            maxVisibleCandidates,
            onExpandToggle != null,
            fontFamily,
            primaryFontSize,
            secondaryFontSize,
            chipPaddingPx,
            expandBtnWidthPx
        ) {
            fun requiredWidth(candidate: SuggestionCandidate): Float {
                val isPrimary = candidate.isPrimary
                val fontSize = if (isPrimary) primaryFontSize else secondaryFontSize
                val fontWeight = if (isPrimary) FontWeight.SemiBold else FontWeight.Normal
                val textLayoutResult = textMeasurer.measure(
                    text = AnnotatedString(candidate.text),
                    style = TextStyle(
                        fontSize = fontSize,
                        fontWeight = fontWeight,
                        fontFamily = fontFamily,
                        letterSpacing = 0.sp
                    ),
                    maxLines = 1
                )
                return textLayoutResult.size.width.toFloat() + chipPaddingPx
            }

            if (isScrollableMode || candidates.isEmpty()) {
                candidates
            } else if (candidates.size == 1) {
                candidates
            } else {
                val availableWithBtn = (totalWidthPx - expandBtnWidthPx).coerceAtLeast(0f)
                val availableWithoutBtn = totalWidthPx.coerceAtLeast(0f)

                var hasEmoji = false
                val checkLimit = minOf(candidates.size, 4)
                for (i in 0 until checkLimit) {
                    if (candidates[i].isEmoji) {
                        hasEmoji = true
                        break
                    }
                }

                if ((maxVisibleCandidates >= 4 || hasEmoji) && candidates.size >= 4) {
                    val pool4 = candidates.take(4)
                    val needsBtn4 = candidates.size > 4 || isExpanded
                    val availWidth4 = if (needsBtn4 && onExpandToggle != null) availableWithBtn else availableWithoutBtn
                    val slotWidth4 = (availWidth4 - 3 * dividerWidthPx) / 4f
                    if (slotWidth4 > 0f && pool4.all { requiredWidth(it) <= slotWidth4 }) {
                        return@remember pool4
                    }
                }

                if (candidates.size >= 3) {
                    val pool3 = if (candidates.size >= 4 &&
                        candidates[3].isEmoji &&
                        !candidates[2].isEmoji
                    ) {
                        listOf(candidates[0], candidates[1], candidates[3])
                    } else {
                        candidates.take(3)
                    }
                    val needsBtn3 = candidates.size > 3 || isExpanded
                    val availWidth3 = if (needsBtn3 && onExpandToggle != null) availableWithBtn else availableWithoutBtn
                    val slotWidth3 = (availWidth3 - 2 * dividerWidthPx) / 3f
                    if (slotWidth3 > 0f && pool3.all { requiredWidth(it) <= slotWidth3 }) {
                        return@remember pool3
                    }
                    if (pool3 != candidates.take(3)) {
                        val fallback3 = candidates.take(3)
                        if (slotWidth3 > 0f && fallback3.all { requiredWidth(it) <= slotWidth3 }) {
                            return@remember fallback3
                        }
                    }
                }

                val pool2 = if (candidates.size >= 3 && candidates[2].isPrimary) {
                    listOf(candidates[0], candidates[2])
                } else {
                    candidates.take(2)
                }
                val needsBtn2 = candidates.size > 2 || isExpanded
                val availWidth2 = if (needsBtn2 && onExpandToggle != null) availableWithBtn else availableWithoutBtn
                val slotWidth2 = (availWidth2 - dividerWidthPx) / 2f
                if (slotWidth2 > 0f && pool2.all { requiredWidth(it) <= slotWidth2 }) {
                    return@remember pool2
                }

                val primary = candidates.firstOrNull { it.isPrimary } ?: candidates.first()
                listOf(primary)
            }
        }

        val showExpandBtn = !isScrollableMode && onExpandToggle != null &&
            (candidates.size > visibleCandidates.size || isExpanded)

        val primaryTextColor = remember(colorScheme) {
            val rawBg = colorScheme.effectiveToolbarBackground
            val targetBg = if (rawBg.alpha < 0.05f) colorScheme.containerBackground else rawBg
            com.codepotro.borno.theme.ThemeColorUtils.ensureContrast(
                foreground = colorScheme.accentColor,
                background = targetBg,
                minContrast = 2.8f,
                fallbackLight = colorScheme.effectiveToolbarForeground,
                fallbackDark = colorScheme.effectiveToolbarForeground
            )
        }

        Row(
            modifier = Modifier.fillMaxHeight(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (isScrollableMode) {
                // Mode 2: Scrollable candidate chips horizontally
                val scrollState = rememberScrollState()
                LaunchedEffect(candidates) {
                    scrollState.scrollTo(0)
                }

                Row(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .horizontalScroll(scrollState),
                    horizontalArrangement = Arrangement.Start,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    candidates.forEachIndexed { index, candidate ->
                        key(candidate.text) {
                            val isPrimary = candidate.isPrimary
                            val isRaw = candidate.rawInput != null && candidate.text == candidate.rawInput
                            val hasLongClickAction = onCandidateLongClick != null &&
                                    candidate.text.isNotBlank() &&
                                    !com.codepotro.borno.feature.ime.ui.emoji.EmojiSearchCatalog.isEmoji(candidate.text)

                            if (index > 0) {
                                Box(
                                    modifier = Modifier
                                        .height(dividerHeight)
                                        .width(1.dp)
                                        .background(colorScheme.effectiveToolbarForeground.copy(alpha = 0.15f))
                                )
                            }

                            val chipClickModifier = if (hasLongClickAction) {
                                Modifier.combinedClickable(
                                    onClick = { onCandidateClick(candidate) },
                                    onLongClick = { onCandidateLongClick(candidate) }
                                )
                            } else {
                                Modifier.clickable { onCandidateClick(candidate) }
                            }

                            Box(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .clip(RoundedCornerShape(cornerRadius))
                                    .then(chipClickModifier)
                                    .padding(horizontal = (12 * effectiveScale).coerceIn(6f, 16f).dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = candidate.text,
                                    color = when {
                                        isPrimary -> primaryTextColor
                                        isRaw -> colorScheme.effectiveToolbarForeground.copy(alpha = 0.88f)
                                        else -> colorScheme.effectiveToolbarForeground.copy(alpha = 0.72f)
                                    },
                                    fontSize = if (isPrimary) primaryFontSize else secondaryFontSize,
                                    fontWeight = if (isPrimary) FontWeight.SemiBold else FontWeight.Normal,
                                    fontFamily = fontFamily,
                                    textAlign = TextAlign.Center,
                                    maxLines = 1,
                                    softWrap = false,
                                    overflow = TextOverflow.Clip
                                )
                            }
                        }
                    }
                }
            } else {
                // Mode 1: Normal mode (1 to 3 slots without truncation, with expand button when needed)
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    visibleCandidates.forEachIndexed { index, candidate ->
                        key(candidate.text) {
                            val isPrimary = candidate.isPrimary
                            val isRaw = candidate.rawInput != null && candidate.text == candidate.rawInput
                            val hasLongClickAction = onCandidateLongClick != null &&
                                    candidate.text.isNotBlank() &&
                                    !com.codepotro.borno.feature.ime.ui.emoji.EmojiSearchCatalog.isEmoji(candidate.text)

                            // Clean vertical divider between candidate slots
                            if (index > 0) {
                                Box(
                                    modifier = Modifier
                                        .height(dividerHeight)
                                        .width(1.dp)
                                        .background(colorScheme.keyForeground.copy(alpha = 0.15f))
                                )
                            }

                            val chipClickModifier = if (hasLongClickAction) {
                                Modifier.combinedClickable(
                                    onClick = { onCandidateClick(candidate) },
                                    onLongClick = { onCandidateLongClick(candidate) }
                                )
                            } else {
                                Modifier.clickable { onCandidateClick(candidate) }
                            }

                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .clip(RoundedCornerShape(cornerRadius))
                                    .then(chipClickModifier)
                                    .padding(horizontal = (4 * effectiveScale).coerceIn(2f, 6f).dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = candidate.text,
                                    color = when {
                                        isPrimary -> primaryTextColor
                                        isRaw -> colorScheme.effectiveToolbarForeground.copy(alpha = 0.88f)
                                        else -> colorScheme.effectiveToolbarForeground.copy(alpha = 0.72f)
                                    },
                                    fontSize = if (isPrimary) primaryFontSize else secondaryFontSize,
                                    fontWeight = if (isPrimary) FontWeight.SemiBold else FontWeight.Normal,
                                    fontFamily = fontFamily,
                                    textAlign = TextAlign.Center,
                                    maxLines = 1,
                                    softWrap = false,
                                    overflow = TextOverflow.Clip
                                )
                            }
                        }
                    }
                }
            }

            // Down expand button when candidates exceed strip capacity or expanded (Normal mode only)
            if (showExpandBtn) {
                Box(
                    modifier = Modifier
                        .size(expandBtnSize)
                        .clip(CircleShape)
                        .clickable { onExpandToggle?.invoke() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isExpanded) Icons.Outlined.KeyboardArrowUp else Icons.Outlined.KeyboardArrowDown,
                        contentDescription = if (isExpanded) "Collapse suggestions" else "Expand suggestions",
                        tint = if (isExpanded) colorScheme.accentColor else colorScheme.effectiveToolbarForeground.copy(alpha = 0.75f),
                        modifier = Modifier.size(expandBtnIconSize)
                    )
                }
            }
        }
    }
}
