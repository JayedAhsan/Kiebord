package com.codepotro.borno.kiebord.ui.onboarding

import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.codepotro.borno.kiebord.R
import com.codepotro.borno.kiebord.ui.settings.LocalSettingsColors
import com.codepotro.borno.data.prefs.KiebordPreferences
import com.codepotro.borno.data.prefs.KiebordPreferencesRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

enum class OnboardingStep {
    WELCOME,
    ENABLE,
    SELECT,
    LAYOUT,
    PREFERENCES
}

@Composable
fun OnboardingScreen(
    preferences: KiebordPreferences,
    isImeEnabled: Boolean,
    isImeSelected: Boolean,
    onOpenSettings: () -> Unit,
    onSwitchIme: () -> Unit,
    onSaveLayouts: (enabledLayouts: Set<String>, primaryLayout: String) -> Unit,
    onFinishOnboarding: (showNumberRow: Boolean, showSecondaryKeys: Boolean, showKeyBorders: Boolean) -> Unit,
    onComplete: () -> Unit,
    onOpenKeyboardLayouts: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val colors = LocalSettingsColors.current

    // Determine starting step based on current system state
    var currentStep by rememberSaveable {
        mutableStateOf(
            if (!isImeEnabled) OnboardingStep.WELCOME
            else if (!isImeSelected) OnboardingStep.SELECT
            else OnboardingStep.LAYOUT
        )
    }

    // Step navigation tracking
    var stepHistory by remember { mutableStateOf(listOf<OnboardingStep>()) }

    fun navigateToStep(step: OnboardingStep) {
        stepHistory = stepHistory + currentStep
        currentStep = step
    }

    fun navigateBack() {
        if (stepHistory.isNotEmpty()) {
            currentStep = stepHistory.last()
            stepHistory = stepHistory.dropLast(1)
        } else {
            currentStep = OnboardingStep.WELCOME
        }
    }

    BackHandler(enabled = currentStep != OnboardingStep.WELCOME) {
        navigateBack()
    }

    // Polling effect when on ENABLE or SELECT step
    LaunchedEffect(currentStep, isImeEnabled, isImeSelected) {
        if (currentStep == OnboardingStep.ENABLE && isImeEnabled) {
            delay(650L) // Delight delay showing morph checkmark
            navigateToStep(OnboardingStep.SELECT)
        } else if (currentStep == OnboardingStep.SELECT && isImeSelected) {
            delay(650L) // Delight delay showing morph checkmark
            navigateToStep(OnboardingStep.LAYOUT)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background)
    ) {
        // Top Navigation Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (currentStep != OnboardingStep.WELCOME) {
                IconButton(onClick = { navigateBack() }) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = colors.textPrimary
                    )
                }
            } else {
                Spacer(modifier = Modifier.size(48.dp))
            }

            Spacer(modifier = Modifier.weight(1f))

            // Step Indicator dots
            if (currentStep != OnboardingStep.WELCOME) {
                val stepIdx = when (currentStep) {
                    OnboardingStep.ENABLE -> 0
                    OnboardingStep.SELECT -> 1
                    OnboardingStep.LAYOUT -> 2
                    OnboardingStep.PREFERENCES -> 3
                    else -> 0
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    for (i in 0..3) {
                        Box(
                            modifier = Modifier
                                .height(6.dp)
                                .width(if (i == stepIdx) 24.dp else 6.dp)
                                .clip(CircleShape)
                                .background(if (i == stepIdx) colors.accent else colors.iconBackground)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.weight(1f))
            Spacer(modifier = Modifier.size(48.dp))
        }

        // Inner Step Content with smooth slide/fade animations
        Box(modifier = Modifier.fillMaxSize()) {
            AnimatedContent(
                targetState = currentStep,
                transitionSpec = {
                    (slideInHorizontally { it / 2 } + fadeIn(tween(260))) togetherWith
                            (slideOutHorizontally { -it / 2 } + fadeOut(tween(200)))
                },
                label = "OnboardingStepTransition"
            ) { step ->
                when (step) {
                    OnboardingStep.WELCOME -> {
                        WelcomeStep(
                            onGetStarted = {
                                if (!isImeEnabled) {
                                    navigateToStep(OnboardingStep.ENABLE)
                                } else if (!isImeSelected) {
                                    navigateToStep(OnboardingStep.SELECT)
                                } else {
                                    navigateToStep(OnboardingStep.LAYOUT)
                                }
                            }
                        )
                    }

                    OnboardingStep.ENABLE -> {
                        EnableStep(
                            isImeEnabled = isImeEnabled,
                            onOpenSettings = onOpenSettings
                        )
                    }

                    OnboardingStep.SELECT -> {
                        SelectStep(
                            isImeSelected = isImeSelected,
                            onSwitchIme = onSwitchIme
                        )
                    }

                    OnboardingStep.LAYOUT -> {
                        OnboardingLayoutSelectionStep(
                            initialEnabledLayouts = preferences.enabledLayoutIds,
                            initialPrimaryLayout = preferences.primaryLayoutId,
                            onNext = { enabledLayouts, primaryLayout ->
                                onSaveLayouts(enabledLayouts, primaryLayout)
                                navigateToStep(OnboardingStep.PREFERENCES)
                            },
                            onSkip = {
                                navigateToStep(OnboardingStep.PREFERENCES)
                            },
                            onOpenAllLayouts = onOpenKeyboardLayouts
                        )
                    }

                    OnboardingStep.PREFERENCES -> {
                        OnboardingPreferencesStep(
                            primaryLayoutId = preferences.primaryLayoutId,
                            theme = preferences.theme,
                            initialShowNumberRow = preferences.showNumberRow,
                            initialShowSecondaryKeys = preferences.showSecondaryKeys,
                            initialShowKeyBorders = preferences.showKeyBorders,
                            onFinish = { showNumRow, showSecondary, showBorders ->
                                onFinishOnboarding(showNumRow, showSecondary, showBorders)
                                onComplete()
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun WelcomeStep(
    onGetStarted: () -> Unit
) {
    val colors = LocalSettingsColors.current
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(horizontal = 28.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Spacer(modifier = Modifier.weight(1f))

        // App Icon
        Image(
            painter = painterResource(id = R.drawable.ic_launcher),
            contentDescription = "Kiebord Logo",
            modifier = Modifier
                .size(108.dp)
                .clip(RoundedCornerShape(26.dp))
        )

        Spacer(modifier = Modifier.height(36.dp))

        // Morph Greeting Text (from Borno's GreetingAnimator)
        MorphGreetingText(
            textColor = if (colors.isDark) Color.White else Color.Black
        )

        Spacer(modifier = Modifier.weight(1.2f))

        Button(
            onClick = onGetStarted,
            shape = CircleShape,
            colors = ButtonDefaults.buttonColors(
                containerColor = colors.accent,
                contentColor = Color.White
            ),
            contentPadding = PaddingValues(horizontal = 32.dp),
            modifier = Modifier
                .defaultMinSize(minWidth = 190.dp)
                .height(48.dp)
        ) {
            Text(
                text = "Get Started",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.width(8.dp))
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
        }

        Spacer(modifier = Modifier.height(20.dp))
    }
}

@Composable
private fun EnableStep(
    isImeEnabled: Boolean,
    onOpenSettings: () -> Unit
) {
    val colors = LocalSettingsColors.current
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(horizontal = 24.dp, vertical = 20.dp)
    ) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .background(colors.accent.copy(alpha = 0.15f))
                .padding(horizontal = 12.dp, vertical = 6.dp)
        ) {
            Text(
                text = "Step 1 of 4",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = colors.accent
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Enable Kiebord",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = colors.textPrimary
        )

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = "Turn on Kiebord in your device's language and input settings to activate keyboard services.",
            fontSize = 13.sp,
            lineHeight = 18.sp,
            color = colors.textSecondary
        )

        Spacer(modifier = Modifier.height(28.dp))

        // Instructional Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = colors.cardBackground)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    text = "Quick Instructions:",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.textPrimary
                )
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "1. Tap 'Enable in Settings' below\n2. Find 'Kiebord' in the keyboard list\n3. Toggle the switch to ON\n4. Return to this screen to continue",
                    fontSize = 13.sp,
                    lineHeight = 22.sp,
                    color = colors.textSecondary
                )
            }
        }

        Spacer(modifier = Modifier.height(36.dp))

        // Morph Action Button
        MorphActionButton(
            text = "Enable in Settings",
            successText = "Kiebord Enabled!",
            icon = Icons.Outlined.Settings,
            isCompleted = isImeEnabled,
            onClick = onOpenSettings,
            accentColor = colors.accent
        )
    }
}

@Composable
private fun SelectStep(
    isImeSelected: Boolean,
    onSwitchIme: () -> Unit
) {
    val colors = LocalSettingsColors.current
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(horizontal = 24.dp, vertical = 20.dp)
    ) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .background(colors.accent.copy(alpha = 0.15f))
                .padding(horizontal = 12.dp, vertical = 6.dp)
        ) {
            Text(
                text = "Step 2 of 4",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = colors.accent
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Select Kiebord",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = colors.textPrimary
        )

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = "Choose Kiebord as your active input method so you can type in all your favorite apps.",
            fontSize = 13.sp,
            lineHeight = 18.sp,
            color = colors.textSecondary
        )

        Spacer(modifier = Modifier.height(28.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = colors.cardBackground)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    text = "Almost Ready:",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.textPrimary
                )
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "1. Tap 'Switch Input Method' below\n2. Select 'Kiebord' from the list\n",
                    fontSize = 13.sp,
                    lineHeight = 22.sp,
                    color = colors.textSecondary
                )
            }
        }

        Spacer(modifier = Modifier.height(36.dp))

        // Morph Action Button
        MorphActionButton(
            text = "Switch Input Method",
            successText = "Kiebord Selected!",
            icon = Icons.Outlined.SwapHoriz,
            isCompleted = isImeSelected,
            onClick = onSwitchIme,
            accentColor = colors.accent
        )
    }
}
