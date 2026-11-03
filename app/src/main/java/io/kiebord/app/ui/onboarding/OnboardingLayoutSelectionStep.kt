package io.kiebord.app.ui.onboarding

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Keyboard
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.kiebord.app.ui.settings.GroupPosition
import io.kiebord.app.ui.settings.LocalSettingsColors
import io.kiebord.app.ui.settings.SettingsGroup
import io.kiebord.app.ui.settings.SettingsToggleItem
import io.kiebord.data.prefs.KiebordPreferences

private data class LayoutOption(
    val id: String,
    val title: String,
    val subtitle: String
)

@Composable
fun OnboardingLayoutSelectionStep(
    initialEnabledLayouts: Set<String>,
    initialPrimaryLayout: String,
    onNext: (enabledLayouts: Set<String>, primaryLayout: String) -> Unit,
    onSkip: () -> Unit,
    onOpenAllLayouts: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalSettingsColors.current
    val scrollState = rememberScrollState()

    val layoutOptions = remember {
        listOf(
            LayoutOption(
                id = KiebordPreferences.LAYOUT_EN_QWERTY,
                title = "English",
                subtitle = "Standard Latin layout with gesture typing"
            ),
            LayoutOption(
                id = KiebordPreferences.LAYOUT_BN_BORNO,
                title = "বর্ণ ফোনেটিক",
                subtitle = "Smart transliteration: type 'ami' -> 'আমি'"
            ),
            LayoutOption(
                id = KiebordPreferences.LAYOUT_BN_AVRO,
                title = "অভ্র",
                subtitle = "Popular phonetic parser by OmicronLab"
            ),
            LayoutOption(
                id = KiebordPreferences.LAYOUT_BN_EASY,
                title = "বর্ণ ইজি",
                subtitle = "Easy alphabetical layout"
            ),
            LayoutOption(
                id = KiebordPreferences.LAYOUT_BN_NATIONAL,
                title = "জাতীয় (BSTI)",
                subtitle = "Official national standard Bengali layout"
            ),
            LayoutOption(
                id = KiebordPreferences.LAYOUT_BN_PROBHAT,
                title = "প্রভাত",
                subtitle = "Popular fixed Bengali layout"
            ),
            LayoutOption(
                id = KiebordPreferences.LAYOUT_BN_KHIPRO_CLASSIC,
                title = "ক্ষিপ্র ক্লাসিক",
                subtitle = "Compositional Bangla, no Shift needed"
            ),
            LayoutOption(
                id = KiebordPreferences.LAYOUT_BN_KHIPRO_DARK,
                title = "ক্ষিপ্র ডার্ক",
                subtitle = "Compositional with manual conjuncts"
            )
        )
    }

    val displayedLayouts = remember(layoutOptions) { layoutOptions.take(6) }

    var enabledLayouts by remember(initialEnabledLayouts) { mutableStateOf(initialEnabledLayouts) }
    var primaryLayout by remember(initialPrimaryLayout) { mutableStateOf(initialPrimaryLayout) }

    LaunchedEffect(initialEnabledLayouts, initialPrimaryLayout) {
        enabledLayouts = initialEnabledLayouts
        primaryLayout = initialPrimaryLayout
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(scrollState)
            .padding(horizontal = 24.dp)
            .padding(bottom = 36.dp)
    ) {
        // Step progress pill
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .background(colors.accent.copy(alpha = 0.15f))
                .padding(horizontal = 12.dp, vertical = 6.dp)
        ) {
            Text(
                text = "Step 3 of 4",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = colors.accent
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        Text(
            text = "Make it yours",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = colors.textPrimary
        )

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = "Choose the layouts you'd like to use. You can easily switch between them anytime while typing.",
            fontSize = 13.sp,
            lineHeight = 18.sp,
            color = colors.textSecondary
        )

        Spacer(modifier = Modifier.height(20.dp))

        // Material 3 Layout List with Switches (at most 5 layouts)
        SettingsGroup {
            displayedLayouts.forEachIndexed { index, opt ->
                val isChecked = enabledLayouts.contains(opt.id)
                val isPrimary = primaryLayout == opt.id
                val position = when {
                    displayedLayouts.size == 1 -> GroupPosition.ALONE
                    index == 0 -> GroupPosition.TOP
                    index == displayedLayouts.size - 1 -> GroupPosition.BOTTOM
                    else -> GroupPosition.MIDDLE
                }

                SettingsToggleItem(
                    title = opt.title,
                    subtitle = opt.subtitle,
                    badge = if (isPrimary) "Primary" else null,
                    checked = isChecked,
                    position = position,
                    onCheckedChange = { checked ->
                        if (checked) {
                            enabledLayouts = enabledLayouts + opt.id
                        } else if (enabledLayouts.size > 1) {
                            val updated = enabledLayouts - opt.id
                            enabledLayouts = updated
                            if (primaryLayout == opt.id) {
                                primaryLayout = updated.first()
                            }
                        }
                    }
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // All Layouts Button (opens Keyboard Layouts screen)
        OutlinedButton(
            onClick = onOpenAllLayouts,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp),
            contentPadding = PaddingValues(horizontal = 16.dp),
            colors = ButtonDefaults.outlinedButtonColors(
                containerColor = colors.cardBackground,
                contentColor = colors.textPrimary
            ),
            border = BorderStroke(1.dp, colors.cardBorder)
        ) {
            Icon(
                imageVector = Icons.Outlined.Keyboard,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = colors.accent
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = "All Layouts",
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = colors.textPrimary
            )
            Spacer(modifier = Modifier.weight(1f))
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = colors.textSecondary.copy(alpha = 0.7f)
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Bottom Action Bar: Skip & Next
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(
                onClick = onSkip,
                shape = CircleShape,
                modifier = Modifier.height(46.dp)
            ) {
                Text(
                    text = "Skip",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.textSecondary
                )
            }

            Button(
                onClick = { onNext(enabledLayouts, primaryLayout) },
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(
                    containerColor = colors.accent,
                    contentColor = Color.White
                ),
                modifier = Modifier
                    .height(46.dp)
                    .padding(start = 12.dp)
            ) {
                Text(
                    text = "Next",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.width(6.dp))
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}
