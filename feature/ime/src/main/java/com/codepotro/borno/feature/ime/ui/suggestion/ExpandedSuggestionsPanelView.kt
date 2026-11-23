package com.codepotro.borno.feature.ime.ui.suggestion

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Backspace
import androidx.compose.material.icons.automirrored.outlined.KeyboardReturn
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.codepotro.borno.theme.KiebordTheme
import com.codepotro.borno.theme.ThemeColorUtils
import kotlinx.coroutines.launch

@OptIn(ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
@Composable
fun ExpandedSuggestionsPanelView(
    candidates: List<SuggestionCandidate>,
    onCandidateClick: (SuggestionCandidate) -> Unit,
    onCandidateLongClick: ((SuggestionCandidate) -> Unit)? = null,
    onDelete: () -> Unit,
    onEnter: () -> Unit,
    height: Dp,
    scaleFactor: Float = 1.0f,
    isFloating: Boolean = false,
    modifier: Modifier = Modifier
) {
    val colorScheme = KiebordTheme.colorScheme
    val scrollState = rememberScrollState()
    val coroutineScope = rememberCoroutineScope()
    val density = LocalDensity.current
    val panelInteractionSource = remember { MutableInteractionSource() }

    val effectiveScale = if (isFloating) scaleFactor.coerceIn(0.85f, 1.15f) else scaleFactor.coerceIn(0.85f, 1.25f)
    val primaryFontSize = (20f * effectiveScale).coerceIn(16f, 22f).sp
    val secondaryFontSize = (19f * effectiveScale).coerceIn(15f, 21f).sp
    val chipPaddingH = (14 * effectiveScale).coerceIn(10f, 16f).dp
    val chipPaddingV = (10 * effectiveScale).coerceIn(8f, 12f).dp
    val dividerHeight = (18 * effectiveScale).coerceIn(14f, 20f).dp
    val actionColWidth = (52 * effectiveScale).coerceIn(44f, 56f).dp
    val actionIconSize = (20 * effectiveScale).coerceIn(18f, 22f).dp

    val scrollStepPx = with(density) { (54 * effectiveScale).dp.toPx() }
    val dividerColor = colorScheme.keyForeground.copy(alpha = 0.12f)

    val actionKeyFg = ThemeColorUtils.ensureContrast(
        foreground = colorScheme.actionKeyForeground,
        background = colorScheme.actionKeyBackground,
        minContrast = 4.0f
    )
    val enterKeyBg = if (colorScheme.isAdaptive) colorScheme.actionKeyBackground else colorScheme.accentColor
    val enterKeyFg = ThemeColorUtils.ensureContrast(
        foreground = if (colorScheme.isAdaptive) {
            colorScheme.actionKeyForeground
        } else if (colorScheme.accentForeground != Color.Unspecified) {
            colorScheme.accentForeground
        } else {
            colorScheme.actionKeyForeground
        },
        background = enterKeyBg,
        minContrast = 4.0f
    )

    val panelBg = colorScheme.containerBackground.copy(alpha = 1f)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .background(panelBg)
            .clickable(
                interactionSource = panelInteractionSource,
                indication = null
            ) { /* Absorb clicks on empty space so they never pass through to underlying keys */ }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            // 1. Dynamic-width Candidate Flow area (sized according to candidate text with clean vertical dividers)
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .verticalScroll(scrollState)
            ) {
                FlowRow(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    candidates.forEach { candidate ->
                        key(candidate.text) {
                            val isPrimary = candidate.isPrimary
                            val isRaw = candidate.rawInput != null && candidate.text == candidate.rawInput
                            val hasLongClickAction = onCandidateLongClick != null &&
                                    candidate.text.isNotBlank() &&
                                    !com.codepotro.borno.feature.ime.ui.emoji.EmojiSearchCatalog.isEmoji(candidate.text)

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
                                    .drawBehind {
                                        // Clean vertical divider centered vertically, matching suggestion bar aesthetic
                                        val dividerHeightPx = dividerHeight.toPx()
                                        val startY = (size.height - dividerHeightPx) / 2f
                                        drawLine(
                                            color = dividerColor,
                                            start = Offset(size.width, startY),
                                            end = Offset(size.width, startY + dividerHeightPx),
                                            strokeWidth = 1.dp.toPx()
                                        )
                                    }
                                    .then(chipClickModifier)
                                    .padding(horizontal = chipPaddingH, vertical = chipPaddingV),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = candidate.text,
                                    color = when {
                                        isPrimary -> colorScheme.accentColor
                                        isRaw -> colorScheme.keyForeground.copy(alpha = 0.9f)
                                        else -> colorScheme.keyForeground.copy(alpha = 0.78f)
                                    },
                                    fontSize = if (isPrimary) primaryFontSize else secondaryFontSize,
                                    fontWeight = if (isPrimary) FontWeight.SemiBold else FontWeight.Normal,
                                    fontFamily = KiebordTheme.fontFamily,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                }
            }

            // Vertical divider between candidate area and right action keys
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(1.dp)
                    .background(colorScheme.keyForeground.copy(alpha = 0.15f))
            )

            // 2. Right action keys column (Up, Down, Delete, Enter)
            Column(
                modifier = Modifier
                    .width(actionColWidth)
                    .fillMaxHeight()
                    .padding(horizontal = (4 * effectiveScale).coerceIn(2f, 4f).dp, vertical = 2.dp),
                verticalArrangement = Arrangement.SpaceEvenly,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Key 1 (Top): Up Arrow to scroll vertically
                val canScrollUp = scrollState.value > 0
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(vertical = 2.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(colorScheme.actionKeyBackground)
                        .clickable(enabled = canScrollUp) {
                            coroutineScope.launch {
                                val target = (scrollState.value - scrollStepPx.toInt()).coerceAtLeast(0)
                                scrollState.animateScrollTo(target)
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Outlined.KeyboardArrowUp,
                        contentDescription = "Scroll Up",
                        tint = actionKeyFg.copy(alpha = if (canScrollUp) 0.95f else 0.35f),
                        modifier = Modifier.size(actionIconSize)
                    )
                }

                // Key 2: Down Arrow to scroll vertically
                val canScrollDown = scrollState.value < scrollState.maxValue
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(vertical = 2.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(colorScheme.actionKeyBackground)
                        .clickable(enabled = canScrollDown) {
                            coroutineScope.launch {
                                val target = (scrollState.value + scrollStepPx.toInt()).coerceAtMost(scrollState.maxValue)
                                scrollState.animateScrollTo(target)
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Outlined.KeyboardArrowDown,
                        contentDescription = "Scroll Down",
                        tint = actionKeyFg.copy(alpha = if (canScrollDown) 0.95f else 0.35f),
                        modifier = Modifier.size(actionIconSize)
                    )
                }

                // Key 3: Delete / Backspace key
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(vertical = 2.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(colorScheme.actionKeyBackground)
                        .clickable { onDelete() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.Backspace,
                        contentDescription = "Delete",
                        tint = actionKeyFg,
                        modifier = Modifier.size(actionIconSize)
                    )
                }

                // Key 4 (Bottom): Enter / Action key
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(vertical = 2.dp)
                        .clip(RoundedCornerShape(percent = 50))
                        .background(enterKeyBg)
                        .clickable { onEnter() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.KeyboardReturn,
                        contentDescription = "Enter",
                        tint = enterKeyFg,
                        modifier = Modifier.size(actionIconSize)
                    )
                }
            }
        }
    }
}
