package io.kiebord.app.ui.settings

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.Backspace
import androidx.compose.material.icons.automirrored.outlined.KeyboardReturn
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DragHandle
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import io.kiebord.common.model.KeyRow
import io.kiebord.common.model.KeyType
import io.kiebord.common.model.KeyboardLayoutDef
import io.kiebord.data.layout.LayoutRepository
import io.kiebord.data.layout.PredefinedLayouts
import io.kiebord.data.prefs.KiebordPreferences
import io.kiebord.theme.KeyboardColorScheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private data class LayoutCardData(
    val id: String,
    val name: String,
    val badge: String,
    val description: String,
    val layoutDef: KeyboardLayoutDef
)

@Composable
fun KeyboardLayoutsScreen(
    preferences: KiebordPreferences,
    onUpdatePrimaryLayout: (String) -> Unit,
    onToggleLayoutEnabled: (String, Boolean) -> Unit,
    onReorderEnabledLayouts: (List<String>) -> Unit = {},
    onToggleExtraRow: (String, String, Boolean) -> Unit = { _, _, _ -> },
    onBack: () -> Unit = {}
) {
    val context = LocalContext.current
    val layoutRepo = remember(context) { LayoutRepository.getInstance(context) }
    var reloadTrigger by remember { mutableStateOf(0) }

    val scrollState = rememberScrollState()
    val colors = LocalSettingsColors.current

    val englishCard = remember {
        LayoutCardData(
            id = KiebordPreferences.LAYOUT_EN_QWERTY,
            name = "English (QWERTY)",
            badge = "EN",
            description = "Standard Latin layout with gesture typing",
            layoutDef = PredefinedLayouts.EnglishQwerty
        )
    }

    val banglaCards = remember {
        listOf(
            LayoutCardData(
                id = KiebordPreferences.LAYOUT_BN_BORNO,
                name = "বর্ণ ফোনেটিক",
                badge = "বর্ণ",
                description = "Phonetic (ami → আমি)",
                layoutDef = PredefinedLayouts.BengaliBorno
            ),
            LayoutCardData(
                id = KiebordPreferences.LAYOUT_BN_AVRO,
                name = "অভ্র ফোনেটিক",
                badge = "অভ্র",
                description = "Phonetic (ami → আমি)",
                layoutDef = PredefinedLayouts.BengaliAvro
            ),
            LayoutCardData(
                id = KiebordPreferences.LAYOUT_BN_EASY,
                name = "বর্ণ ইজি",
                badge = "বর্ণ ইজি",
                description = "Easy 5-row alphabetical layout",
                layoutDef = PredefinedLayouts.BengaliBornoEasy
            ),
            LayoutCardData(
                id = KiebordPreferences.LAYOUT_BN_NATIONAL,
                name = "জাতীয় (BSTI)",
                badge = "জাতীয়",
                description = "National BSTI layout",
                layoutDef = PredefinedLayouts.BengaliNational
            ),
            LayoutCardData(
                id = KiebordPreferences.LAYOUT_BN_PROBHAT,
                name = "প্রভাত",
                badge = "প্রভাত",
                description = "Fixed traditional layout",
                layoutDef = PredefinedLayouts.BengaliProbhat
            ),
            LayoutCardData(
                id = KiebordPreferences.LAYOUT_BN_KHIPRO_CLASSIC,
                name = "ক্ষিপ্র ক্লাসিক",
                badge = "ক্ষিপ্র",
                description = "Compositional m17n spec (khipro)",
                layoutDef = layoutRepo.getLayout(KiebordPreferences.LAYOUT_BN_KHIPRO_CLASSIC)
            ),
            LayoutCardData(
                id = KiebordPreferences.LAYOUT_BN_KHIPRO_DARK,
                name = "ক্ষিপ্র ডার্ক",
                badge = "ডার্ক",
                description = "Compositional, manual conjuncts (fuser /)",
                layoutDef = layoutRepo.getLayout(KiebordPreferences.LAYOUT_BN_KHIPRO_DARK)
            )
        )
    }

    var arabicLayout by remember {
        mutableStateOf(
            layoutRepo.getCachedLayout(KiebordPreferences.LAYOUT_AR_ARABIC)
                ?: PredefinedLayouts.fromId(KiebordPreferences.LAYOUT_AR_ARABIC)
        )
    }
    val arabicCard = remember(arabicLayout) {
        LayoutCardData(
            id = KiebordPreferences.LAYOUT_AR_ARABIC,
            name = "Arabic (العربية)",
            badge = "عربي",
            description = "Standard Arabic with Harakat diacritics",
            layoutDef = arabicLayout
        )
    }

    var availableMeta by remember {
        mutableStateOf(layoutRepo.getCachedAvailableLayouts() ?: emptyList())
    }

    LaunchedEffect(reloadTrigger) {
        val loadedArabic = withContext(Dispatchers.IO) {
            layoutRepo.getLayout(KiebordPreferences.LAYOUT_AR_ARABIC)
        }
        val loadedMeta = withContext(Dispatchers.IO) {
            layoutRepo.getAvailableLayouts()
        }
        arabicLayout = loadedArabic
        availableMeta = loadedMeta
    }

    val curatedIds = remember(englishCard, banglaCards, arabicCard) {
        (listOf(englishCard.id) + banglaCards.map { it.id } + listOf(arabicCard.id)).toSet()
    }
    val otherBundledMeta = remember(availableMeta, curatedIds) {
        availableMeta.filter { !it.isCustom && it.id !in curatedIds && !it.id.contains("symbols") }
    }
    var otherBundledCards by remember { mutableStateOf<List<LayoutCardData>>(emptyList()) }

    LaunchedEffect(otherBundledMeta, reloadTrigger) {
        val cards = withContext(Dispatchers.IO) {
            otherBundledMeta.map { meta ->
                val def = layoutRepo.getLayout(meta.id)
                val badge = (meta.displayName ?: meta.language).uppercase().take(4)
                val desc = "Bundled ${meta.displayName ?: meta.name} layout"
                LayoutCardData(
                    id = meta.id,
                    name = meta.displayName ?: meta.name,
                    badge = badge,
                    description = desc,
                    layoutDef = def
                )
            }
        }
        otherBundledCards = cards
    }

    val customMeta = remember(availableMeta) { availableMeta.filter { it.isCustom } }
    var customCards by remember { mutableStateOf<List<LayoutCardData>>(emptyList()) }

    LaunchedEffect(customMeta, reloadTrigger) {
        val cards = withContext(Dispatchers.IO) {
            customMeta.map { meta ->
                val def = layoutRepo.getLayout(meta.id)
                LayoutCardData(
                    id = meta.id,
                    name = meta.displayName ?: meta.name,
                    badge = meta.language.uppercase(),
                    description = "Custom imported layout",
                    layoutDef = def
                )
            }
        }
        customCards = cards
    }

    val allKnownCards = remember(englishCard, banglaCards, arabicCard, otherBundledCards, customCards) {
        listOf(englishCard) + banglaCards + listOf(arabicCard) + otherBundledCards + customCards
    }
    var orderedActiveIds by remember(preferences.enabledLayoutIds) {
        mutableStateOf(preferences.enabledLayoutIds.toList())
    }
    val activeCards = remember(orderedActiveIds, preferences.enabledLayoutIds, allKnownCards) {
        val cardMap = allKnownCards.associateBy { it.id }
        val ordered = orderedActiveIds.mapNotNull { cardMap[it] }
        val remaining = allKnownCards.filter { preferences.enabledLayoutIds.contains(it.id) && it.id !in orderedActiveIds }
        ordered + remaining
    }
    val currentActiveIdsState = rememberUpdatedState(activeCards.map { it.id })
    var draggedLayoutId by remember { mutableStateOf<String?>(null) }
    var dragOffsetY by remember { mutableFloatStateOf(0f) }
    var swipedLayoutId by remember { mutableStateOf<String?>(null) }
    var swipeOffsetX by remember { mutableFloatStateOf(0f) }
    var rowHeightPx by remember { mutableFloatStateOf(0f) }
    val density = LocalDensity.current
    val groupSpacingPx = with(density) { 3.dp.toPx() }
    val swipeThresholdPx = with(density) { 72.dp.toPx() }

    val isEnglishEnabled = preferences.enabledLayoutIds.contains(englishCard.id)
    val isAnyBanglaEnabled = preferences.enabledLayoutIds.any { it.startsWith("bn") }
    val isArabicEnabled = preferences.enabledLayoutIds.contains(arabicCard.id)

    var isEnglishExpanded by remember { mutableStateOf(false) }
    var isBanglaExpanded by remember { mutableStateOf(false) }
    var isArabicExpanded by remember { mutableStateOf(false) }
    var isArabicOptionsOpen by remember { mutableStateOf(false) }
    var selectedCustomSettingsId by remember { mutableStateOf<String?>(null) }
    var isCustomExpanded by remember { mutableStateOf(false) }
    var pendingDeleteCard by remember { mutableStateOf<LayoutCardData?>(null) }

    val handleToggleLayout: (LayoutCardData) -> Unit = { card ->
        val currentlyEnabled = preferences.enabledLayoutIds.contains(card.id)
        val isLastRemaining = preferences.enabledLayoutIds.size <= 1 && currentlyEnabled
        if (isLastRemaining) {
            Toast.makeText(
                context,
                "At least one keyboard layout must remain active",
                Toast.LENGTH_SHORT
            ).show()
        } else {
            onToggleLayoutEnabled(card.id, !currentlyEnabled)
        }
    }



    if (pendingDeleteCard != null) {
        val target = pendingDeleteCard!!
        AlertDialog(
            onDismissRequest = { pendingDeleteCard = null },
            containerColor = colors.dialogBackground,
            title = {
                Text(
                    text = "Remove Custom Layout?",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.textPrimary
                )
            },
            text = {
                Text(
                    text = "Are you sure you want to delete \"${target.name}\"? You can import its .json file again anytime.",
                    fontSize = 14.sp,
                    color = colors.textSecondary
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val isTargetLastRemaining =
                            preferences.enabledLayoutIds.size <= 1 && preferences.enabledLayoutIds.contains(target.id)
                        if (isTargetLastRemaining) {
                            onToggleLayoutEnabled(KiebordPreferences.LAYOUT_EN_QWERTY, true)
                        }
                        layoutRepo.deleteCustomLayout(target.id)
                        onToggleLayoutEnabled(target.id, false)
                        if (selectedCustomSettingsId == target.id) {
                            selectedCustomSettingsId = null
                        }
                        reloadTrigger++
                        Toast.makeText(context, "Removed ${target.name}", Toast.LENGTH_SHORT).show()
                        pendingDeleteCard = null
                    }
                ) {
                    Text(
                        text = "Remove",
                        color = Color(0xFFEF4444),
                        fontWeight = FontWeight.SemiBold
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeleteCard = null }) {
                    Text(
                        text = "Cancel",
                        color = colors.textSecondary
                    )
                }
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(bottom = 32.dp)
    ) {
        SettingsCategoryHeader(title = "Active Layouts (${activeCards.size})")

        SettingsGroup(modifier = Modifier.padding(horizontal = 16.dp)) {
            for ((index, card) in activeCards.withIndex()) {
                key(card.id) {
                    val position = when {
                        activeCards.size == 1 -> GroupPosition.ALONE
                        index == 0 -> GroupPosition.TOP
                        index == activeCards.lastIndex -> GroupPosition.BOTTOM
                        else -> GroupPosition.MIDDLE
                    }
                    val isDragging = draggedLayoutId == card.id
                    val isSwiping = swipedLayoutId == card.id
                    val isPrimary = preferences.primaryLayoutId == card.id ||
                        (!preferences.enabledLayoutIds.contains(preferences.primaryLayoutId) && index == 0)

                    SettingsItemCard(
                        position = position,
                        modifier = Modifier
                            .fillMaxWidth()
                            .zIndex(if (isDragging) 1f else 0f)
                            .onSizeChanged { size ->
                                if (size.height > 0) {
                                    rowHeightPx = size.height.toFloat()
                                }
                            }
                            .pointerInput(card.id) {
                                detectHorizontalDragGestures(
                                    onDragStart = {
                                        swipedLayoutId = card.id
                                        swipeOffsetX = 0f
                                    },
                                    onDragEnd = {
                                        val finalOffset = swipeOffsetX
                                        swipedLayoutId = null
                                        swipeOffsetX = 0f
                                        if (kotlin.math.abs(finalOffset) >= swipeThresholdPx) {
                                            handleToggleLayout(card)
                                        }
                                    },
                                    onDragCancel = {
                                        swipedLayoutId = null
                                        swipeOffsetX = 0f
                                    },
                                    onHorizontalDrag = { change, dragAmount ->
                                        change.consume()
                                        swipeOffsetX += dragAmount
                                    }
                                )
                            }
                            .graphicsLayer {
                                translationX = if (isSwiping) swipeOffsetX else 0f
                                translationY = if (isDragging) dragOffsetY else 0f
                                alpha = if (isSwiping) {
                                    (1f - (kotlin.math.abs(swipeOffsetX) / (swipeThresholdPx * 2f)).coerceIn(0f, 0.65f))
                                } else 1f
                                scaleX = if (isDragging) 1.02f else 1f
                                scaleY = if (isDragging) 1.02f else 1f
                                shadowElevation = if (isDragging) 12f else 0f
                            }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    if (!isPrimary) {
                                        onUpdatePrimaryLayout(card.id)
                                    }
                                }
                                .padding(start = 6.dp, end = 16.dp, top = 12.dp, bottom = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .pointerInput(card.id) {
                                        detectVerticalDragGestures(
                                            onDragStart = {
                                                draggedLayoutId = card.id
                                                dragOffsetY = 0f
                                            },
                                            onDragEnd = {
                                                draggedLayoutId = null
                                                dragOffsetY = 0f
                                                onReorderEnabledLayouts(currentActiveIdsState.value)
                                            },
                                            onDragCancel = {
                                                draggedLayoutId = null
                                                dragOffsetY = 0f
                                            },
                                            onVerticalDrag = { change, dragAmount ->
                                                change.consume()
                                                dragOffsetY += dragAmount
                                                val stepPx = (if (rowHeightPx > 0f) rowHeightPx else 56.dp.toPx()) + groupSpacingPx
                                                val currentList = currentActiveIdsState.value.toMutableList()
                                                val curIdx = currentList.indexOf(card.id)
                                                if (curIdx != -1 && stepPx > 0f) {
                                                    if (dragOffsetY > stepPx * 0.55f && curIdx < currentList.lastIndex) {
                                                        val next = currentList[curIdx + 1]
                                                        currentList[curIdx + 1] = card.id
                                                        currentList[curIdx] = next
                                                        orderedActiveIds = currentList
                                                        dragOffsetY -= stepPx
                                                    } else if (dragOffsetY < -stepPx * 0.55f && curIdx > 0) {
                                                        val prev = currentList[curIdx - 1]
                                                        currentList[curIdx - 1] = card.id
                                                        currentList[curIdx] = prev
                                                        orderedActiveIds = currentList
                                                        dragOffsetY += stepPx
                                                    }
                                                }
                                            }
                                        )
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.DragHandle,
                                    contentDescription = "Reorder",
                                    tint = if (isDragging) colors.accent else colors.textSecondary,
                                    modifier = Modifier.size(22.dp)
                                )
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = card.name,
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = colors.textPrimary
                                    )
                                    if (isPrimary) {
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Box(
                                            modifier = Modifier
                                                .clip(CircleShape)
                                                .background(colors.accent.copy(alpha = 0.15f))
                                                .padding(horizontal = 8.dp, vertical = 2.dp)
                                        ) {
                                            Text(
                                                text = "Default",
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = colors.accent
                                            )
                                        }
                                    }
                                }
                                Text(
                                    text = card.description,
                                    fontSize = 12.sp,
                                    color = colors.textSecondary,
                                    modifier = Modifier.padding(top = 2.dp),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        }

        SettingsCategoryHeader(title = "Available Keyboard Layouts")

        SettingsGroup(modifier = Modifier.padding(horizontal = 16.dp)) {
            // 1. English Item
            SettingsItemCard(
                position = GroupPosition.TOP,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column {
                    LanguageSectionHeader(
                        title = "English",
                        subtitle = "Standard Latin layout with gesture typing",
                        badgeText = "EN",
                        activeCount = if (isEnglishEnabled) 1 else 0,
                        totalCount = 1,
                        isExpanded = isEnglishExpanded,
                        onToggleExpand = { isEnglishExpanded = !isEnglishExpanded },
                        directToggleState = isEnglishEnabled,
                        onDirectToggle = { handleToggleLayout(englishCard) }
                    )

                    AnimatedVisibility(
                        visible = isEnglishExpanded,
                        enter = expandVertically() + fadeIn(),
                        exit = shrinkVertically() + fadeOut()
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 16.dp, end = 16.dp, bottom = 18.dp, top = 4.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            KeyboardLayoutPreviewItem(
                                cardData = englishCard,
                                preferences = preferences,
                                isEnabled = isEnglishEnabled,
                                isPrimary = preferences.primaryLayoutId == englishCard.id && isEnglishEnabled,
                                onToggle = { handleToggleLayout(englishCard) }
                            )
                        }
                    }
                }
            }

            // 2. Bangla Item with Horizontal Carousel
            SettingsItemCard(
                position = GroupPosition.MIDDLE,
                modifier = Modifier.fillMaxWidth()
            ) {
                val activeBanglaCount = banglaCards.count { preferences.enabledLayoutIds.contains(it.id) }

                Column {
                    LanguageSectionHeader(
                        title = "Bangla (বাংলা)",
                        subtitle = "বর্ণ ফোনেটিক, অভ্র, জাতীয় (BSTI), প্রভাত",
                        badgeText = "বাংলা",
                        activeCount = activeBanglaCount,
                        totalCount = banglaCards.size,
                        isExpanded = isBanglaExpanded,
                        onToggleExpand = { isBanglaExpanded = !isBanglaExpanded }
                    )

                    AnimatedVisibility(
                        visible = isBanglaExpanded,
                        enter = expandVertically() + fadeIn(),
                        exit = shrinkVertically() + fadeOut()
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 18.dp, top = 4.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            LazyRow(
                                modifier = Modifier.fillMaxWidth(),
                                contentPadding = PaddingValues(horizontal = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                                items(
                                    items = banglaCards,
                                    key = { it.id }
                                ) { cardData ->
                                    val isEnabled = preferences.enabledLayoutIds.contains(cardData.id)

                                    KeyboardLayoutPreviewItem(
                                        cardData = cardData,
                                        preferences = preferences,
                                        isEnabled = isEnabled,
                                        isPrimary = preferences.primaryLayoutId == cardData.id && isEnabled,
                                        onToggle = { handleToggleLayout(cardData) }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 3. Arabic Item
            val hasOtherBundled = otherBundledCards.isNotEmpty()
            val hasCustom = customCards.isNotEmpty()
            SettingsItemCard(
                position = if (hasOtherBundled || hasCustom) GroupPosition.MIDDLE else GroupPosition.BOTTOM,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column {
                    LanguageSectionHeader(
                        title = "Arabic (العربية)",
                        subtitle = "Standard Arabic with Harakat diacritics",
                        badgeText = "عربي",
                        activeCount = if (isArabicEnabled) 1 else 0,
                        totalCount = 1,
                        isExpanded = isArabicExpanded,
                        onToggleExpand = { isArabicExpanded = !isArabicExpanded },
                        directToggleState = isArabicEnabled,
                        onDirectToggle = { handleToggleLayout(arabicCard) }
                    )

                    AnimatedVisibility(
                        visible = isArabicExpanded,
                        enter = expandVertically() + fadeIn(),
                        exit = shrinkVertically() + fadeOut()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 16.dp, end = 16.dp, bottom = 18.dp, top = 4.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            KeyboardLayoutPreviewItem(
                                cardData = arabicCard,
                                preferences = preferences,
                                isEnabled = isArabicEnabled,
                                isPrimary = preferences.primaryLayoutId == arabicCard.id && isArabicEnabled,
                                onToggle = { handleToggleLayout(arabicCard) }
                            )

                            // Dynamic Extra Rows options
                            if (arabicCard.layoutDef.extraRows.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(12.dp))
                                ExtraRowsOptionsSection(
                                    cardData = arabicCard,
                                    preferences = preferences,
                                    isOpen = isArabicOptionsOpen,
                                    onToggleOpen = { isArabicOptionsOpen = !isArabicOptionsOpen },
                                    onToggleExtraRow = onToggleExtraRow
                                )
                            }
                        }
                    }
                }
            }

            // 4. Other Bundled Layouts (e.g. Borno Math)
            otherBundledCards.forEachIndexed { index, card ->
                key(card.id) {
                    val isLastBundled = index == otherBundledCards.lastIndex
                    val itemPosition = if (isLastBundled && !hasCustom) GroupPosition.BOTTOM else GroupPosition.MIDDLE
                    BundledLayoutSectionItem(
                        card = card,
                        preferences = preferences,
                        onToggleLayout = handleToggleLayout,
                        onToggleExtraRow = onToggleExtraRow,
                        position = itemPosition
                    )
                }
            }

            // 5. Custom Imported Layouts (if present)
            if (hasCustom) {
                val activeCustomCount = customCards.count { preferences.enabledLayoutIds.contains(it.id) }
                SettingsItemCard(
                    position = GroupPosition.BOTTOM,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column {
                        LanguageSectionHeader(
                            title = "Custom Layouts",
                            subtitle = "${customCards.size} imported layout(s) installed",
                            badgeText = "JSON",
                            activeCount = activeCustomCount,
                            totalCount = customCards.size,
                            isExpanded = isCustomExpanded,
                            onToggleExpand = { isCustomExpanded = !isCustomExpanded }
                        )

                        AnimatedVisibility(
                            visible = isCustomExpanded,
                            enter = expandVertically() + fadeIn(),
                            exit = shrinkVertically() + fadeOut()
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 18.dp, top = 4.dp)
                            ) {
                                LazyRow(
                                    modifier = Modifier.fillMaxWidth(),
                                    contentPadding = PaddingValues(horizontal = 16.dp),
                                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                                ) {
                                    items(
                                        items = customCards,
                                        key = { it.id }
                                    ) { cardData ->
                                        val isEnabled = preferences.enabledLayoutIds.contains(cardData.id)
                                        val hasExtraRows = cardData.layoutDef.extraRows.isNotEmpty()
                                        val isCardSettingsOpen = selectedCustomSettingsId == cardData.id

                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            KeyboardLayoutPreviewItem(
                                                cardData = cardData,
                                                preferences = preferences,
                                                isEnabled = isEnabled,
                                                isPrimary = preferences.primaryLayoutId == cardData.id && isEnabled,
                                                onToggle = { handleToggleLayout(cardData) }
                                            )

                                            Spacer(modifier = Modifier.height(8.dp))

                                            Row(
                                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                if (hasExtraRows) {
                                                    Row(
                                                        modifier = Modifier
                                                            .clip(CircleShape)
                                                            .background(
                                                                if (isCardSettingsOpen) colors.accent.copy(alpha = 0.15f)
                                                                else colors.iconBackground
                                                            )
                                                            .clickable {
                                                                selectedCustomSettingsId =
                                                                    if (isCardSettingsOpen) null else cardData.id
                                                            }
                                                            .padding(horizontal = 10.dp, vertical = 6.dp),
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        Icon(
                                                            imageVector = Icons.Filled.Settings,
                                                            contentDescription = "Options",
                                                            tint = if (isCardSettingsOpen) colors.accent else colors.textSecondary,
                                                            modifier = Modifier.size(14.dp)
                                                        )
                                                        Spacer(modifier = Modifier.width(4.dp))
                                                        Text(
                                                            text = "Options",
                                                            fontSize = 11.sp,
                                                            fontWeight = FontWeight.Medium,
                                                            color = if (isCardSettingsOpen) colors.accent else colors.textSecondary
                                                        )
                                                    }
                                                }

                                                Row(
                                                    modifier = Modifier
                                                        .clip(CircleShape)
                                                        .background(colors.iconBackground)
                                                        .clickable { pendingDeleteCard = cardData }
                                                        .padding(horizontal = 10.dp, vertical = 6.dp),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Outlined.Delete,
                                                        contentDescription = "Remove layout",
                                                        tint = Color(0xFFEF4444),
                                                        modifier = Modifier.size(14.dp)
                                                    )
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                    Text(
                                                        text = "Remove",
                                                        fontSize = 11.sp,
                                                        fontWeight = FontWeight.Medium,
                                                        color = Color(0xFFEF4444)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }

                                val activeCustomSettingsCard = customCards.find { it.id == selectedCustomSettingsId }
                                AnimatedVisibility(
                                    visible = activeCustomSettingsCard != null && activeCustomSettingsCard.layoutDef.extraRows.isNotEmpty(),
                                    enter = expandVertically() + fadeIn(),
                                    exit = shrinkVertically() + fadeOut()
                                ) {
                                    if (activeCustomSettingsCard != null) {
                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(start = 16.dp, end = 16.dp, top = 12.dp)
                                        ) {
                                            ExtraRowsOptionsSection(
                                                cardData = activeCustomSettingsCard,
                                                preferences = preferences,
                                                isOpen = true,
                                                onToggleOpen = { selectedCustomSettingsId = null },
                                                onToggleExtraRow = onToggleExtraRow
                                            )
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

@Composable
private fun LanguageSectionHeader(
    title: String,
    subtitle: String,
    badgeText: String,
    activeCount: Int,
    totalCount: Int,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    directToggleState: Boolean? = null,
    onDirectToggle: (() -> Unit)? = null
) {
    val colors = LocalSettingsColors.current
    val isAnyActive = activeCount > 0

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggleExpand)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {

        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = title,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.textPrimary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(
                            if (isAnyActive) colors.accent.copy(alpha = 0.15f)
                            else colors.iconBackground
                        )
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = if (totalCount == 1) {
                            if (isAnyActive) "Active" else "Off"
                        } else {
                            if (isAnyActive) "$activeCount active" else "Off"
                        },
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (isAnyActive) colors.accent else colors.textSecondary
                    )
                }
            }
            Text(
                text = subtitle,
                fontSize = 12.sp,
                color = colors.textSecondary,
                modifier = Modifier.padding(top = 2.dp)
            )
        }

        if (directToggleState != null && onDirectToggle != null) {
            Switch(
                checked = directToggleState,
                onCheckedChange = { onDirectToggle() },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = colors.accent,
                    uncheckedThumbColor = colors.textSecondary,
                    uncheckedTrackColor = colors.iconBackground,
                    uncheckedBorderColor = colors.inputBorder
                ),
                modifier = Modifier.padding(end = 6.dp)
            )
        }

        val chevronRotation by animateFloatAsState(
            targetValue = if (isExpanded) 90f else 0f,
            label = "SectionChevronRotation"
        )
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = if (isExpanded) "Collapse" else "Expand",
            tint = colors.textSecondary,
            modifier = Modifier
                .size(20.dp)
                .graphicsLayer { rotationZ = chevronRotation }
        )
    }
}

@Composable
private fun ExtraRowsOptionsSection(
    cardData: LayoutCardData,
    preferences: KiebordPreferences,
    isOpen: Boolean,
    onToggleOpen: () -> Unit,
    onToggleExtraRow: (String, String, Boolean) -> Unit
) {
    val colors = LocalSettingsColors.current

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier
                .clip(CircleShape)
                .clickable(onClick = onToggleOpen)
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Filled.Settings,
                contentDescription = "Options",
                tint = if (isOpen) colors.accent else colors.textSecondary,
                modifier = Modifier.size(15.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "${cardData.name} Options",
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (isOpen) colors.accent else colors.textSecondary
            )
            Spacer(modifier = Modifier.width(4.dp))
            val chevronRotation by animateFloatAsState(
                targetValue = if (isOpen) 90f else 0f,
                label = "ExtraRowChevronRotation"
            )
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = if (isOpen) colors.accent else colors.textSecondary,
                modifier = Modifier
                    .size(16.dp)
                    .graphicsLayer { rotationZ = chevronRotation }
            )
        }

        AnimatedVisibility(
            visible = isOpen,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                for (extraRow in cardData.layoutDef.extraRows) {
                    val isRowEnabled = preferences.isExtraRowEnabled(
                        cardData.id,
                        extraRow.id,
                        extraRow.defaultEnabled
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(colors.iconBackground.copy(alpha = 0.6f))
                            .border(1.dp, colors.cardBorder, RoundedCornerShape(12.dp))
                            .clickable {
                                onToggleExtraRow(cardData.id, extraRow.id, !isRowEnabled)
                            }
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = extraRow.title,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = colors.textPrimary
                            )
                            Text(
                                text = "Show extra row above keyboard",
                                fontSize = 11.sp,
                                color = colors.textSecondary
                            )
                        }
                        Switch(
                            checked = isRowEnabled,
                            onCheckedChange = { checked ->
                                onToggleExtraRow(cardData.id, extraRow.id, checked)
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = colors.accent,
                                uncheckedThumbColor = colors.textSecondary,
                                uncheckedTrackColor = colors.iconBackground,
                                uncheckedBorderColor = colors.inputBorder
                            )
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BundledLayoutSectionItem(
    card: LayoutCardData,
    preferences: KiebordPreferences,
    onToggleLayout: (LayoutCardData) -> Unit,
    onToggleExtraRow: (String, String, Boolean) -> Unit,
    position: GroupPosition
) {
    val isEnabled = preferences.enabledLayoutIds.contains(card.id)
    var isExpanded by remember { mutableStateOf(false) }
    var isOptionsOpen by remember { mutableStateOf(false) }

    SettingsItemCard(
        position = position,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column {
            LanguageSectionHeader(
                title = card.name,
                subtitle = card.description,
                badgeText = card.badge,
                activeCount = if (isEnabled) 1 else 0,
                totalCount = 1,
                isExpanded = isExpanded,
                onToggleExpand = { isExpanded = !isExpanded },
                directToggleState = isEnabled,
                onDirectToggle = { onToggleLayout(card) }
            )

            AnimatedVisibility(
                visible = isExpanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, bottom = 18.dp, top = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    KeyboardLayoutPreviewItem(
                        cardData = card,
                        preferences = preferences,
                        isEnabled = isEnabled,
                        isPrimary = preferences.primaryLayoutId == card.id && isEnabled,
                        onToggle = { onToggleLayout(card) }
                    )

                    if (card.layoutDef.extraRows.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(12.dp))
                        ExtraRowsOptionsSection(
                            cardData = card,
                            preferences = preferences,
                            isOpen = isOptionsOpen,
                            onToggleOpen = { isOptionsOpen = !isOptionsOpen },
                            onToggleExtraRow = onToggleExtraRow
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun KeyboardLayoutPreviewItem(
    cardData: LayoutCardData,
    preferences: KiebordPreferences,
    isEnabled: Boolean,
    isPrimary: Boolean = false,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalSettingsColors.current
    val context = LocalContext.current
    val currentScheme = remember(context, preferences.theme) {
        io.kiebord.theme.ThemeRepository.getInstance(context).getColorScheme(preferences.theme)
    }

    val itemWidth = 176.dp
    val thumbnailHeight = 124.dp

    Column(
        modifier = modifier
            .width(itemWidth)
            .clip(RoundedCornerShape(16.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onToggle
            ),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .width(itemWidth)
                .height(thumbnailHeight)
                .clip(RoundedCornerShape(14.dp))
                .background(currentScheme.containerBackground)
                .border(
                    width = if (isEnabled) 2.dp else 1.dp,
                    color = if (isEnabled) colors.accent else colors.cardBorder,
                    shape = RoundedCornerShape(14.dp)
                )
        ) {
            KeyboardMiniPreview(
                layout = cardData.layoutDef,
                colorScheme = currentScheme,
                extraRowOverrides = preferences.extraRowOverrides,
                showKeyBorders = preferences.showKeyBorders,
                modifier = Modifier
                    .fillMaxSize()
                    .alpha(if (isEnabled) 1f else 0.65f)
            )

            // Top-right selection badge overlay
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp)
                    .size(20.dp)
                    .clip(CircleShape)
                    .background(
                        if (isEnabled) colors.accent
                        else colors.cardBackground.copy(alpha = 0.75f)
                    )
                    .border(
                        width = 1.dp,
                        color = if (isEnabled) colors.accent else colors.textSecondary.copy(alpha = 0.5f),
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (isEnabled) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "Enabled",
                        tint = Color.White,
                        modifier = Modifier.size(13.dp)
                    )
                }
            }

            if (isPrimary) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(6.dp)
                        .clip(CircleShape)
                        .background(colors.accent)
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "Default",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.width(itemWidth),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Text(
                text = cardData.name,
                fontSize = 13.sp,
                fontWeight = if (isEnabled) FontWeight.Bold else FontWeight.Medium,
                color = if (isEnabled) colors.textPrimary else colors.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        if (cardData.description.isNotEmpty()) {
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = cardData.description,
                fontSize = 11.sp,
                color = colors.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun KeyboardMiniPreview(
    layout: KeyboardLayoutDef,
    colorScheme: KeyboardColorScheme,
    extraRowOverrides: Map<String, Boolean>,
    showKeyBorders: Boolean = true,
    modifier: Modifier = Modifier
) {
    val enabledExtra = remember(layout, extraRowOverrides) {
        layout.extraRows.filter { extraRow ->
            extraRowOverrides["${layout.id}_${extraRow.id}"] ?: extraRow.defaultEnabled
        }.map { KeyRow(it.keys) }
    }
    val allRows = remember(layout, enabledExtra) {
        enabledExtra + layout.rows
    }

    val context = LocalContext.current
    val typeface = remember(context) {
        androidx.core.content.res.ResourcesCompat.getFont(context, io.kiebord.core.theme.R.font.keyboard_font)
            ?: android.graphics.Typeface.DEFAULT
    }

    val textPaint = remember(typeface) {
        android.graphics.Paint().apply {
            isAntiAlias = true
            textAlign = android.graphics.Paint.Align.CENTER
            this.typeface = typeface
        }
    }

    val shiftPainter = painterResource(io.kiebord.feature.ime.R.drawable.ic_shift)
    val deletePainter = rememberVectorPainter(Icons.AutoMirrored.Outlined.Backspace)
    val actionPainter = rememberVectorPainter(Icons.AutoMirrored.Outlined.KeyboardReturn)
    val languagePainter = rememberVectorPainter(Icons.Outlined.Language)

    Canvas(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 6.dp, vertical = 8.dp)
    ) {
        val totalWidth = size.width
        val totalHeight = size.height
        val numRows = allRows.size
        if (numRows == 0 || totalWidth <= 0f || totalHeight <= 0f) return@Canvas

        val rowSpacingPx = 3.dp.toPx()
        val keySpacingPx = 2.dp.toPx()
        val cornerRadiusPx = 3.dp.toPx()

        val totalRowSpacing = (numRows - 1) * rowSpacingPx
        val rowHeight = ((totalHeight - totalRowSpacing) / numRows).coerceAtLeast(0f)

        for ((rowIndex, row) in allRows.withIndex()) {
            val rowY = rowIndex * (rowHeight + rowSpacingPx)
            val hasStartPadding = row.startPaddingWeight > 0f
            val hasEndPadding = row.endPaddingWeight > 0f
            val totalItemCount = (if (hasStartPadding) 1 else 0) + row.keys.size + (if (hasEndPadding) 1 else 0)
            val totalSpacing = if (totalItemCount > 1) (totalItemCount - 1) * keySpacingPx else 0f
            val availableKeySpace = (totalWidth - totalSpacing).coerceAtLeast(0f)

            val totalWeight = (if (hasStartPadding) row.startPaddingWeight else 0f) +
                    (if (hasEndPadding) row.endPaddingWeight else 0f) +
                    row.keys.sumOf { it.widthWeight.toDouble() }.toFloat()

            var currentX = 0f
            if (hasStartPadding && totalWeight > 0f) {
                val startPadWidth = (row.startPaddingWeight / totalWeight) * availableKeySpace
                currentX += startPadWidth + keySpacingPx
            }

            for (key in row.keys) {
                val keyWidth = if (totalWeight > 0f) {
                    (key.widthWeight / totalWeight) * availableKeySpace
                } else 0f

                val isComma = key.type == KeyType.CHARACTER && (key.label == "," || key.label == "،")
                val isPill = key.type == KeyType.ACTION || key.type == KeyType.SYMBOLS || (!showKeyBorders && key.type == KeyType.SPACE)
                val bg = when {
                    key.type == KeyType.ACTION -> colorScheme.accentColor
                    key.type == KeyType.SYMBOLS -> colorScheme.actionKeyBackground
                    key.type == KeyType.SPACE -> colorScheme.keyBackground
                    showKeyBorders && (key.type == KeyType.SHIFT || key.type == KeyType.DELETE || isComma) -> colorScheme.actionKeyBackground
                    showKeyBorders -> colorScheme.keyBackground
                    else -> colorScheme.containerBackground
                }
                val fg = when {
                    key.type == KeyType.ACTION -> colorScheme.actionKeyForeground
                    else -> colorScheme.keyForeground
                }

                val keyCornerRadius = if (isPill) {
                    CornerRadius(rowHeight / 2f, rowHeight / 2f)
                } else {
                    CornerRadius(cornerRadiusPx, cornerRadiusPx)
                }

                drawRoundRect(
                    color = bg,
                    topLeft = Offset(currentX, rowY),
                    size = Size(keyWidth, rowHeight),
                    cornerRadius = keyCornerRadius
                )

                if (key.type == KeyType.CHARACTER || key.type == KeyType.SPACE || key.type == KeyType.SYMBOLS) {
                    val label = if (key.type == KeyType.SPACE) "" else key.label
                    if (label.isNotEmpty()) {
                        textPaint.color = fg.toArgb()
                        val centerX = currentX + keyWidth / 2f
                        if (label.contains("\n")) {
                            val lines = label.split("\n")
                            textPaint.textSize = 5.sp.toPx()
                            val fontMetrics = textPaint.fontMetrics
                            val lineHeight = fontMetrics.descent - fontMetrics.ascent
                            val totalH = lineHeight * lines.size
                            val startY = rowY + (rowHeight - totalH) / 2f - fontMetrics.ascent
                            for ((lineIdx, line) in lines.withIndex()) {
                                drawContext.canvas.nativeCanvas.drawText(line, centerX, startY + lineIdx * lineHeight, textPaint)
                            }
                        } else {
                            textPaint.textSize = (if (label.length > 2) 6.sp else 8.sp).toPx()
                            val fontMetrics = textPaint.fontMetrics
                            val baselineY = rowY + (rowHeight - fontMetrics.ascent - fontMetrics.descent) / 2f
                            drawContext.canvas.nativeCanvas.drawText(label, centerX, baselineY, textPaint)
                        }
                    }
                } else if (key.type == KeyType.SHIFT) {
                    val iconSize = 9.5.dp.toPx()
                    val iconLeft = currentX + (keyWidth - iconSize) / 2f
                    val iconTop = rowY + (rowHeight - iconSize) / 2f
                    translate(left = iconLeft, top = iconTop) {
                        with(shiftPainter) {
                            draw(size = Size(iconSize, iconSize), colorFilter = ColorFilter.tint(fg))
                        }
                    }
                } else if (key.type == KeyType.DELETE) {
                    val iconSize = 9.dp.toPx()
                    val iconLeft = currentX + (keyWidth - iconSize) / 2f
                    val iconTop = rowY + (rowHeight - iconSize) / 2f
                    translate(left = iconLeft, top = iconTop) {
                        with(deletePainter) {
                            draw(size = Size(iconSize, iconSize), colorFilter = ColorFilter.tint(fg))
                        }
                    }
                } else if (key.type == KeyType.ACTION) {
                    val iconSize = 9.dp.toPx()
                    val iconLeft = currentX + (keyWidth - iconSize) / 2f
                    val iconTop = rowY + (rowHeight - iconSize) / 2f
                    translate(left = iconLeft, top = iconTop) {
                        with(actionPainter) {
                            draw(size = Size(iconSize, iconSize), colorFilter = ColorFilter.tint(fg))
                        }
                    }
                } else if (key.type == KeyType.SWITCH_LANGUAGE) {
                    val iconSize = 9.dp.toPx()
                    val iconLeft = currentX + (keyWidth - iconSize) / 2f
                    val iconTop = rowY + (rowHeight - iconSize) / 2f
                    translate(left = iconLeft, top = iconTop) {
                        with(languagePainter) {
                            draw(size = Size(iconSize, iconSize), colorFilter = ColorFilter.tint(fg))
                        }
                    }
                }

                currentX += keyWidth + keySpacingPx
            }
        }
    }
}
