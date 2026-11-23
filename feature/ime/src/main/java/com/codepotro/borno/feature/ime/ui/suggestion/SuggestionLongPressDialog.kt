package com.codepotro.borno.feature.ime.ui.suggestion

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.codepotro.borno.theme.KiebordTheme
import com.codepotro.borno.theme.ThemeColorUtils

/**
 * Overlay dialog shown when a suggestion candidate is long-pressed.
 *
 * - If [isLearned] is true, offers both Set/Edit Mapping and Remove Word actions
 *   so learned words can still be mapped in Personal Dictionary as well as unlearned.
 * - If [isLearned] is false, shows a direct Set/Edit Mapping confirmation dialog.
 */
@Composable
fun SuggestionLongPressDialog(
    word: String,
    isLearned: Boolean,
    hasMapping: Boolean,
    onSetMapping: () -> Unit,
    onRemoveWord: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colorScheme = KiebordTheme.colorScheme
    val mappingActionLabel = if (hasMapping) "Edit Mapping" else "Set Mapping"
    val primaryBtnFg = ThemeColorUtils.ensureContrast(
        foreground = colorScheme.actionKeyForeground,
        background = colorScheme.accentColor,
        minContrast = 4.0f
    )
    val secondaryBtnFg = ThemeColorUtils.ensureContrast(
        foreground = colorScheme.actionKeyForeground,
        background = colorScheme.actionKeyBackground,
        minContrast = 4.0f
    )

    Box(
        modifier = modifier
            .background(Color.Black.copy(alpha = 0.55f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
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
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) {} // consume touch events
                .padding(horizontal = 20.dp, vertical = 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (isLearned) {
                Text(
                    text = "\"$word\"",
                    color = colorScheme.keyForeground,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = KiebordTheme.fontFamily,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = "Choose an action for this learned word",
                    color = colorScheme.keyForeground.copy(alpha = 0.78f),
                    fontSize = 13.sp,
                    fontFamily = KiebordTheme.fontFamily,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(14.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(colorScheme.accentColor)
                        .clickable { onSetMapping() }
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = mappingActionLabel,
                        color = primaryBtnFg,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = KiebordTheme.fontFamily
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(colorScheme.actionKeyBackground)
                        .clickable { onRemoveWord() }
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Remove Word",
                        color = secondaryBtnFg,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        fontFamily = KiebordTheme.fontFamily
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onDismiss() }
                        .padding(horizontal = 16.dp, vertical = 6.dp),
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
            } else {
                val bodyText = if (hasMapping) {
                    "Edit the shortcut mapping for \"$word\" in Personal Dictionary?"
                } else {
                    "Add a shortcut mapping for \"$word\" in Personal Dictionary?"
                }

                Text(
                    text = mappingActionLabel,
                    color = colorScheme.keyForeground,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = KiebordTheme.fontFamily,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = bodyText,
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

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(colorScheme.accentColor)
                            .clickable { onSetMapping() }
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = mappingActionLabel,
                            color = primaryBtnFg,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            fontFamily = KiebordTheme.fontFamily
                        )
                    }
                }
            }
        }
    }
}
