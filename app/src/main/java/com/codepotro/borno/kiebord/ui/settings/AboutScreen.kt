package com.codepotro.borno.kiebord.ui.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Coffee
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Policy
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import com.codepotro.borno.kiebord.R

private const val GITHUB_REPO_URL = "https://github.com/JayedAhsan/Kiebord"
private const val GITHUB_ISSUES_URL = "https://github.com/JayedAhsan/Kiebord/issues"
private const val SUPPORT_URL = "https://jayed.me/support-kiebord"

private data class OpenSourceLib(
    val name: String,
    val author: String,
    val license: String,
    val description: String,
    val url: String = ""
)

private fun openUrl(context: Context, url: String) {
    if (url == "#" || url.isBlank()) {
        Toast.makeText(context, "Link coming soon", Toast.LENGTH_SHORT).show()
        return
    }
    val targetUrl = if (!url.startsWith("http://") && !url.startsWith("https://")) {
        "https://$url"
    } else {
        url
    }
    try {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(targetUrl))
        context.startActivity(intent)
    } catch (_: Exception) {
        Toast.makeText(context, "Could not open browser", Toast.LENGTH_SHORT).show()
    }
}

@Composable
private fun AboutCircularButton(
    icon: ImageVector,
    label: String,
    containerColor: Color,
    contentColor: Color,
    onClick: () -> Unit
) {
    val colors = LocalSettingsColors.current
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.width(88.dp)
    ) {
        IconButton(
            onClick = onClick,
            modifier = Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(containerColor)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = contentColor,
                modifier = Modifier.size(24.dp)
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = label,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = colors.textPrimary,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun OpenSourceLicenseItem(lib: OpenSourceLib) {
    val colors = LocalSettingsColors.current
    val context = LocalContext.current
    val isClickable = lib.url.isNotBlank()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(colors.inputBackground)
            .then(
                if (isClickable) {
                    Modifier.clickable { openUrl(context, lib.url) }
                } else Modifier
            )
            .padding(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = lib.name,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = colors.textPrimary,
                modifier = Modifier.weight(1f)
            )
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(colors.accent.copy(alpha = 0.15f))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(
                    text = lib.license,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.accent
                )
            }
        }
        Spacer(modifier = Modifier.height(3.dp))
        Text(
            text = lib.author,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = colors.textSecondary
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = lib.description,
            fontSize = 11.sp,
            color = colors.textSecondary.copy(alpha = 0.85f),
            lineHeight = 15.sp
        )
    }
}

@Composable
fun AboutScreen(
    onResetDefaults: () -> Unit = {},
    onBack: () -> Unit = {}
) {
    val scrollState = rememberScrollState()
    val context = LocalContext.current
    val colors = LocalSettingsColors.current

    var showLicensesDialog by remember { mutableStateOf(false) }

    val openSourceLibraries = remember {
        listOf(
            OpenSourceLib(
                name = "AndroidX & Jetpack Compose",
                author = "Google LLC / The Android Open Source Project",
                license = "Apache 2.0",
                description = "Modern declarative UI toolkit, Material 3 design components, runtime lifecycle, and foundation utilities."
            ),
            OpenSourceLib(
                name = "AndroidX Emoji2 & EmojiPicker",
                author = "Google LLC / The Android Open Source Project",
                license = "Apache 2.0",
                description = "Emoji compatibility font renderer and integrated interactive emoji picker."
            ),
            OpenSourceLib(
                name = "emojilib",
                author = "Mu-An Chiou (muan) / contributors",
                license = "MIT",
                description = "Keyword dictionary for emoji search and exact keyword matching.",
                url = "https://github.com/muan/emojilib"
            ),
            OpenSourceLib(
                name = "Kotlin & Kotlin Coroutines",
                author = "JetBrains s.r.o. / Kotlin Foundation",
                license = "Apache 2.0",
                description = "Modern programming language and asynchronous coroutine primitives for concurrent processing."
            ),
            OpenSourceLib(
                name = "AndroidX DataStore",
                author = "Google LLC / The Android Open Source Project",
                license = "Apache 2.0",
                description = "Modern, transactional, thread-safe asynchronous key-value data storage solution."
            ),
            OpenSourceLib(
                name = "Rust Native Engine (memmap2, parking_lot, jni-rs)",
                author = "Various Contributors",
                license = "MIT / Apache 2.0",
                description = "High-performance memory mapping, synchronization primitives, and JNI bridge for the spatial typing core."
            ),
            OpenSourceLib(
                name = "AOSP LatinIME",
                author = "Google LLC / The Android Open Source Project",
                license = "Apache 2.0",
                description = "Reference keyboard service architecture, key event dispatching, and gesture typing pipeline from the Android Open Source Project.",
                url = "https://android.googlesource.com/platform/packages/inputmethods/LatinIME/"
            ),
            OpenSourceLib(
                name = "Khipro Classic & Khipro Dark (m17n Touchscreen Rules)",
                author = "Khipro Team (rank_coder)",
                license = "MIT",
                description = "Touchscreen-optimized m17n input method rules for Bangla typing (https://github.com/KhiproTeam/khipro-mim-touchscreen and https://github.com/KhiproTeam/khipro-dark-m17n).",
                url = "https://github.com/KhiproTeam/khipro-mim-touchscreen"
            ),
            OpenSourceLib(
                name = "BornoTSF & Bangla Layout Standards",
                author = "Jayed Ahsan Saad / Open Source Community",
                license = "GPL 3.0 / Open Standard",
                description = "Core phonetic transliteration engine ported from BornoTSF, alongside National (BSTI) and Probhat specifications."
            ),
            OpenSourceLib(
                name = "Material Symbols & Google Icons",
                author = "Google LLC",
                license = "Apache 2.0",
                description = "Standard system and keyboard icons designed by Google."
            )
        )
    }

    if (showLicensesDialog) {
        AlertDialog(
            onDismissRequest = { showLicensesDialog = false },
            properties = DialogProperties(usePlatformDefaultWidth = false),
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.82f)
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            title = {
                Text(
                    text = "Open Source Licenses",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.textPrimary
                )
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(top = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Kiebord is the result of over 6 years of development on Borno Keyboard and heavily relies on its foundational source code and transliteration architecture, though Borno Keyboard itself is closed source. This codebase underwent an intensive AI-driven migration and heavy refactoring (transitioning from Java & C++ to modern Kotlin & Rust), with each change carefully reviewed and verified by me. Kiebord also proudly builds upon many remarkable open-source components. We gratefully acknowledge the following projects:",
                        fontSize = 12.sp,
                        color = colors.textSecondary,
                        lineHeight = 16.sp,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                    openSourceLibraries.forEach { lib ->
                        OpenSourceLicenseItem(lib = lib)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showLicensesDialog = false }) {
                    Text(
                        text = "Close",
                        fontWeight = FontWeight.SemiBold,
                        color = colors.accent
                    )
                }
            },
            containerColor = colors.dialogBackground,
            shape = RoundedCornerShape(20.dp)
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(horizontal = 20.dp, vertical = 20.dp)
    ) {
        Spacer(modifier = Modifier.height(12.dp))

        // Hero Brand Section
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Image(
                painter = painterResource(R.drawable.ic_launcher_foreground),
                contentDescription = "Kiebord Logo",
                modifier = Modifier.size(84.dp)
            )

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = "Kiebord",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = colors.textPrimary
            )

            Text(
                text = "Version 0.3.3",
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = colors.textSecondary,
                modifier = Modifier.padding(top = 4.dp)
            )

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = "Kiebord (Borno Community Edition) is a FREE, Open Source IME for Android",
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = colors.textSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 12.dp)
            )
        }

        Spacer(modifier = Modifier.height(28.dp))

        SettingsCategoryHeader(title = "About & Licensing")

        SettingsGroup {
            SettingsNavigationItem(
                title = "Maintainer",
                subtitle = "Jayed Ahsan Saad (Codepotro)",
                icon = Icons.Outlined.Person,
                position = GroupPosition.TOP,
                onClick = { openUrl(context, "https://jayed.me") }
            )

            SettingsNavigationItem(
                title = "License",
                subtitle = "GNU General Public License v3.0",
                icon = Icons.Outlined.Policy,
                position = GroupPosition.MIDDLE,
                onClick = { openUrl(context, GITHUB_REPO_URL) }
            )

            SettingsNavigationItem(
                title = "Open Source Licenses",
                subtitle = "Third-party libraries, tools & notices",
                icon = Icons.Outlined.Code,
                position = GroupPosition.BOTTOM,
                onClick = { showLicensesDialog = true }
            )
        }

        Spacer(modifier = Modifier.height(32.dp))

        // Circular Action Buttons with text labels below
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.Top
        ) {
            // Git Repo button
            AboutCircularButton(
                icon = Icons.Outlined.Code,
                label = "Git Repo",
                containerColor = colors.iconBackground,
                contentColor = colors.textPrimary,
                onClick = { openUrl(context, GITHUB_REPO_URL) }
            )

            // Report Issue button
            AboutCircularButton(
                icon = Icons.Outlined.BugReport,
                label = "Report Issue",
                containerColor = colors.iconBackground,
                contentColor = colors.textPrimary,
                onClick = { openUrl(context, GITHUB_ISSUES_URL) }
            )

            // Support button with warm golden yellow branding
            AboutCircularButton(
                icon = Icons.Outlined.Coffee,
                label = "Support",
                containerColor = Color(0xFFFFAF02),
                contentColor = Color(0xFF1F1200),
                onClick = { openUrl(context, SUPPORT_URL) }
            )
        }

        Spacer(modifier = Modifier.height(32.dp))
    }
}
