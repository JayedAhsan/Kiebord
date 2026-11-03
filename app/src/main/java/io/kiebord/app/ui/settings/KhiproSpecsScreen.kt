package io.kiebord.app.ui.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Keyboard
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.kiebord.feature.ime.khipro.CheckResult
import io.kiebord.feature.ime.khipro.KhiproSpecRepository
import io.kiebord.feature.ime.khipro.KhiproSpecs
import io.kiebord.feature.ime.khipro.KhiproVariant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Manual OTA management for the Khipro m17n spec data: shows each layout's
 * current spec version, checks upstream releases on demand, installs only
 * conformance-validated specs, and can reset to the bundled ones.
 * No background checks — the network is touched only on explicit taps.
 */
@Composable
fun KhiproSpecsScreen(
    onBack: () -> Unit = {},
) {
    val scrollState = rememberScrollState()
    val colors = LocalSettingsColors.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val prefs = remember {
        context.getSharedPreferences("kiebord_runtime", Context.MODE_PRIVATE)
    }
    val repo = remember {
        KhiproSpecRepository(
            assetReader = { path ->
                try {
                    context.assets.open(path).bufferedReader().use { it.readText() }
                } catch (_: Exception) {
                    null
                }
            },
            filesDir = context.filesDir,
            loadPref = { key, def -> prefs.getString(key, def) },
            storePref = { key, value -> prefs.edit().putString(key, value).apply() },
            removePref = { key -> prefs.edit().remove(key).apply() },
            fetcher = { url -> KhiproSpecRepository.defaultFetcher(url) },
        )
    }

    var classicVersion by remember { mutableStateOf(repo.installedVersion(KhiproVariant.CLASSIC)) }
    var darkVersion by remember { mutableStateOf(repo.installedVersion(KhiproVariant.DARK)) }
    var classicStatus by remember { mutableStateOf("Checking is manual — tap below") }
    var darkStatus by remember { mutableStateOf("Checking is manual — tap below") }
    var classicBusy by remember { mutableStateOf(false) }
    var darkBusy by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(bottom = 32.dp),
    ) {
        Spacer(modifier = Modifier.height(4.dp))

        // 1. Info / Disclaimer Card matching VoiceTypingScreen & SettingsMainScreen banner style
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = colors.cardBackground),
            border = BorderStroke(1.dp, colors.cardBorder)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 16.dp),
                verticalAlignment = Alignment.Top
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(colors.iconBackground),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Info,
                        contentDescription = null,
                        tint = colors.accent,
                        modifier = Modifier.size(22.dp)
                    )
                }

                Spacer(modifier = Modifier.width(14.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "On-Demand Spec Updates",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = colors.textPrimary
                    )
                    Text(
                        text = "Input-method table data for Khipro layouts is downloaded directly from official spec releases and validated against conformance tables before installing. No background network checks are performed.",
                        fontSize = 12.sp,
                        lineHeight = 17.sp,
                        color = colors.textSecondary,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }
        }

        // 2. Installed Layout Specifications Group
        SettingsCategoryHeader(title = "Installed Layout Specifications")

        SettingsGroup(modifier = Modifier.padding(horizontal = 16.dp)) {
            KhiproVariant.entries.forEachIndexed { index, variant ->
                val version = if (variant == KhiproVariant.CLASSIC) classicVersion else darkVersion
                val status = if (variant == KhiproVariant.CLASSIC) classicStatus else darkStatus
                val busy = if (variant == KhiproVariant.CLASSIC) classicBusy else darkBusy
                val downloaded = repo.isDownloaded(variant)
                val bundled = repo.bundledVersion(variant)
                val position = if (index == 0) GroupPosition.TOP else GroupPosition.BOTTOM

                SettingsItemCard(position = position) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 14.dp)
                    ) {
                        // Header row with Icon, Title and Badges
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(colors.iconBackground),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Keyboard,
                                    contentDescription = null,
                                    tint = colors.accent,
                                    modifier = Modifier.size(22.dp)
                                )
                            }

                            Spacer(modifier = Modifier.width(14.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        text = if (variant == KhiproVariant.CLASSIC) "Khipro Classic" else "Khipro Dark",
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = colors.textPrimary
                                    )

                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(colors.accent.copy(alpha = 0.15f))
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            text = version,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = colors.accent
                                        )
                                    }

                                    if (downloaded) {
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(6.dp))
                                                .background(colors.activeCardBg)
                                                .border(1.dp, colors.activeCardBorder, RoundedCornerShape(6.dp))
                                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                        ) {
                                            Text(
                                                text = "UPDATED",
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = colors.activeCardText
                                            )
                                        }
                                    } else {
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(6.dp))
                                                .background(colors.iconBackground)
                                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                        ) {
                                            Text(
                                                text = "BUNDLED",
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = colors.textSecondary
                                            )
                                        }
                                    }
                                }

                                if (downloaded && version != bundled) {
                                    Text(
                                        text = "Bundled baseline: $bundled",
                                        fontSize = 12.sp,
                                        lineHeight = 16.sp,
                                        color = colors.textSecondary,
                                        modifier = Modifier.padding(top = 2.dp)
                                    )
                                }
                            }
                        }

                        // Status Row
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (busy) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(12.dp),
                                    strokeWidth = 2.dp,
                                    color = colors.accent
                                )
                            } else {
                                val dotColor = when {
                                    status.startsWith("Updated") || status.startsWith("Up to date") -> colors.activeCardIcon
                                    status.startsWith("Rejected") || status.startsWith("Failed") -> colors.warningBtnBg
                                    else -> colors.textSecondary.copy(alpha = 0.6f)
                                }
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(dotColor)
                                )
                            }
                            Text(
                                text = status,
                                fontSize = 12.sp,
                                lineHeight = 16.sp,
                                color = colors.textSecondary
                            )
                        }

                        // Actions Row
                        Spacer(modifier = Modifier.height(14.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Button(
                                onClick = {
                                    if (variant == KhiproVariant.CLASSIC) classicBusy = true else darkBusy = true
                                    if (variant == KhiproVariant.CLASSIC) classicStatus = "Checking…" else darkStatus = "Checking…"
                                    scope.launch {
                                        val result = withContext(Dispatchers.IO) {
                                            repo.checkForUpdate(variant)
                                        }
                                        val newVersion = repo.installedVersion(variant)
                                        val message = when (result) {
                                            is CheckResult.Updated -> "Updated to ${result.version}"
                                            is CheckResult.UpToDate -> "Up to date"
                                            is CheckResult.Rejected -> "Rejected: ${result.reason}"
                                            is CheckResult.Failed -> "Failed: ${result.reason}"
                                        }
                                        if (variant == KhiproVariant.CLASSIC) {
                                            classicVersion = newVersion
                                            classicStatus = message
                                            classicBusy = false
                                        } else {
                                            darkVersion = newVersion
                                            darkStatus = message
                                            darkBusy = false
                                        }
                                    }
                                },
                                enabled = !busy,
                                shape = CircleShape,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = colors.accent,
                                    contentColor = Color.White
                                ),
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                                modifier = Modifier.height(36.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Refresh,
                                    contentDescription = null,
                                    modifier = Modifier.size(15.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (busy) "Checking…" else "Check for updates",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }

                            if (downloaded) {
                                TextButton(
                                    onClick = {
                                        if (variant == KhiproVariant.CLASSIC) classicBusy = true else darkBusy = true
                                        scope.launch {
                                            withContext(Dispatchers.IO) { repo.resetToBundled(variant) }
                                            val newVersion = repo.installedVersion(variant)
                                            if (variant == KhiproVariant.CLASSIC) {
                                                classicVersion = newVersion
                                                classicStatus = "Reset to bundled"
                                                classicBusy = false
                                            } else {
                                                darkVersion = newVersion
                                                darkStatus = "Reset to bundled"
                                                darkBusy = false
                                            }
                                        }
                                    },
                                    enabled = !busy,
                                    shape = CircleShape,
                                    colors = ButtonDefaults.textButtonColors(
                                        contentColor = colors.textSecondary
                                    ),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                                    modifier = Modifier.height(36.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.Restore,
                                        contentDescription = null,
                                        modifier = Modifier.size(15.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Reset to bundled",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // 3. Upstream Repositories Group
        SettingsCategoryHeader(title = "Upstream Repositories")

        SettingsGroup(modifier = Modifier.padding(horizontal = 16.dp)) {
            SettingsNavigationItem(
                title = "Khipro Classic Repository",
                subtitle = KhiproSpecs.CLASSIC_REPO,
                icon = Icons.Outlined.Code,
                position = GroupPosition.TOP,
                showChevron = true,
                onClick = {
                    openUrl(context, "https://github.com/${KhiproSpecs.CLASSIC_REPO}")
                }
            )

            SettingsNavigationItem(
                title = "Khipro Dark Repository",
                subtitle = KhiproSpecs.DARK_REPO,
                icon = Icons.Outlined.Code,
                position = GroupPosition.BOTTOM,
                showChevron = true,
                onClick = {
                    openUrl(context, "https://github.com/${KhiproSpecs.DARK_REPO}")
                }
            )
        }
    }
}

private fun openUrl(context: Context, url: String) {
    if (url.isBlank()) return
    try {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        context.startActivity(intent)
    } catch (_: Exception) {
        Toast.makeText(context, "Could not open browser", Toast.LENGTH_SHORT).show()
    }
}
