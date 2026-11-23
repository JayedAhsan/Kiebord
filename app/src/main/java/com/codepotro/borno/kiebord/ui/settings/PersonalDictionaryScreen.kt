package com.codepotro.borno.kiebord.ui.settings

import android.widget.Toast
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.automirrored.outlined.Shortcut
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.TextFields
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.codepotro.borno.data.dictionary.PersonalDictionaryEntry
import com.codepotro.borno.data.dictionary.PersonalDictionaryRepository
import com.codepotro.borno.data.dictionary.UserDictionaryRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private enum class DictionaryTab(val displayName: String, val langCode: String?) {
    ALL("All Languages", null),
    LEARNT("Learnt", null),
    BANGLA("Bangla (বাংলা)", PersonalDictionaryEntry.LANG_BN),
    ENGLISH("English", PersonalDictionaryEntry.LANG_EN)
}

@Composable
fun PersonalDictionaryScreen(
    repository: PersonalDictionaryRepository,
    userDictionaryRepository: UserDictionaryRepository = UserDictionaryRepository.getInstance(LocalContext.current),
    initialPhrase: String? = null,
    initialLanguage: String? = null,
    focusShortcut: Boolean = false,
    onPrefillConsumed: () -> Unit = {},
    onBack: () -> Unit = {}
) {
    val colors = LocalSettingsColors.current
    val context = LocalContext.current
    val entries by repository.entriesFlow.collectAsState()
    val learntWords by userDictionaryRepository.learnedWordsFlow.collectAsState()

    var selectedTab by remember { mutableStateOf(DictionaryTab.ALL) }
    var showClearAllLearntDialog by remember { mutableStateOf(false) }

    var isFormExpanded by remember { mutableStateOf(false) }
    var phraseText by remember { mutableStateOf("") }
    var shortcutText by remember { mutableStateOf("") }
    var selectedLanguage by remember { mutableStateOf(PersonalDictionaryEntry.LANG_ALL) }
    var isLanguageDropdownExpanded by remember { mutableStateOf(false) }
    var editingEntry by remember { mutableStateOf<PersonalDictionaryEntry?>(null) }
    var highlightShortcut by remember { mutableStateOf(false) }
    val shortcutFocusRequester = remember { FocusRequester() }

    LaunchedEffect(initialPhrase, initialLanguage, focusShortcut) {
        if (!initialPhrase.isNullOrBlank()) {
            val requestedLang = initialLanguage ?: PersonalDictionaryEntry.LANG_ALL
            val existing = repository.findByWord(initialPhrase, requestedLang)
            if (existing != null) {
                editingEntry = existing
                phraseText = existing.word
                shortcutText = existing.shortcut ?: ""
                selectedLanguage = existing.language
            } else {
                editingEntry = null
                phraseText = initialPhrase.trim()
                shortcutText = ""
                selectedLanguage = requestedLang
            }
            selectedTab = when (selectedLanguage.lowercase()) {
                PersonalDictionaryEntry.LANG_BN -> DictionaryTab.BANGLA
                PersonalDictionaryEntry.LANG_EN -> DictionaryTab.ENGLISH
                else -> DictionaryTab.ALL
            }
            isFormExpanded = true
            onPrefillConsumed()
            if (focusShortcut) {
                highlightShortcut = true
                delay(120)
                runCatching { shortcutFocusRequester.requestFocus() }
            }
        }
    }

    var entryPendingDelete by remember { mutableStateOf<PersonalDictionaryEntry?>(null) }

    val isExpanded = isFormExpanded || editingEntry != null

    val filteredEntries by remember(entries, selectedTab) {
        derivedStateOf {
            entries.filter { entry ->
                when (selectedTab) {
                    DictionaryTab.ALL -> true
                    DictionaryTab.LEARNT -> false
                    DictionaryTab.BANGLA -> entry.language == PersonalDictionaryEntry.LANG_BN || entry.language == PersonalDictionaryEntry.LANG_ALL
                    DictionaryTab.ENGLISH -> entry.language == PersonalDictionaryEntry.LANG_EN || entry.language == PersonalDictionaryEntry.LANG_ALL
                }
            }
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        if (selectedTab != DictionaryTab.LEARNT) {
        item(key = "add_word_card") {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .animateContentSize(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = colors.cardBackground)
            ) {
                if (!isExpanded) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(20.dp))
                            .clickable { isFormExpanded = true }
                            .padding(horizontal = 18.dp, vertical = 15.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(colors.accent.copy(alpha = 0.12f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Add,
                                contentDescription = null,
                                tint = colors.accent,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Text(
                            text = "+ Add a Word",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = colors.accent
                        )
                    }
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(colors.inputBackground)
                                    .clickable { isLanguageDropdownExpanded = true }
                                    .padding(horizontal = 14.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.Language,
                                        contentDescription = null,
                                        tint = colors.textSecondary,
                                        modifier = Modifier.size(19.dp)
                                    )
                                    Text(
                                        text = when (selectedLanguage.lowercase()) {
                                            PersonalDictionaryEntry.LANG_BN -> "Bangla (বাংলা)"
                                            PersonalDictionaryEntry.LANG_EN -> "English"
                                            else -> "All Languages"
                                        },
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = colors.textPrimary
                                    )
                                }
                                Icon(
                                    imageVector = Icons.Default.ArrowDropDown,
                                    contentDescription = "Select Language",
                                    tint = colors.textSecondary
                                )
                            }

                            DropdownMenu(
                                expanded = isLanguageDropdownExpanded,
                                onDismissRequest = { isLanguageDropdownExpanded = false },
                                modifier = Modifier.background(colors.dialogBackground)
                            ) {
                                DropdownMenuItem(
                                    text = { Text("All Languages", color = colors.textPrimary) },
                                    trailingIcon = {
                                        if (selectedLanguage == PersonalDictionaryEntry.LANG_ALL) {
                                            Icon(Icons.Outlined.Check, contentDescription = null, tint = colors.accent)
                                        }
                                    },
                                    onClick = {
                                        selectedLanguage = PersonalDictionaryEntry.LANG_ALL
                                        isLanguageDropdownExpanded = false
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Bangla (বাংলা)", color = colors.textPrimary) },
                                    trailingIcon = {
                                        if (selectedLanguage == PersonalDictionaryEntry.LANG_BN) {
                                            Icon(Icons.Outlined.Check, contentDescription = null, tint = colors.accent)
                                        }
                                    },
                                    onClick = {
                                        selectedLanguage = PersonalDictionaryEntry.LANG_BN
                                        isLanguageDropdownExpanded = false
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("English", color = colors.textPrimary) },
                                    trailingIcon = {
                                        if (selectedLanguage == PersonalDictionaryEntry.LANG_EN) {
                                            Icon(Icons.Outlined.Check, contentDescription = null, tint = colors.accent)
                                        }
                                    },
                                    onClick = {
                                        selectedLanguage = PersonalDictionaryEntry.LANG_EN
                                        isLanguageDropdownExpanded = false
                                    }
                                )
                            }
                        }

                        OutlinedTextField(
                            value = phraseText,
                            onValueChange = { phraseText = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("Phrase") },
                            placeholder = { Text("e.g., আসসালামু আলাইকুম or Hello", color = colors.textSecondary, fontSize = 13.sp) },
                            leadingIcon = {
                                Icon(Icons.Outlined.TextFields, contentDescription = null, tint = colors.textSecondary, modifier = Modifier.size(20.dp))
                            },
                            trailingIcon = {
                                if (phraseText.isNotEmpty()) {
                                    IconButton(onClick = { phraseText = "" }) {
                                        Icon(Icons.Outlined.Close, contentDescription = "Clear", tint = colors.textSecondary, modifier = Modifier.size(18.dp))
                                    }
                                }
                            },
                            singleLine = true,
                            shape = RoundedCornerShape(14.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = colors.inputBackground,
                                unfocusedContainerColor = colors.inputBackground,
                                focusedBorderColor = colors.accent,
                                unfocusedBorderColor = Color.Transparent,
                                focusedTextColor = colors.textPrimary,
                                unfocusedTextColor = colors.textPrimary,
                                focusedLabelColor = colors.accent,
                                unfocusedLabelColor = colors.textSecondary
                            )
                        )

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 2.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            HorizontalDivider(
                                modifier = Modifier.fillMaxWidth(),
                                color = colors.divider.copy(alpha = 0.5f)
                            )
                            IconButton(
                                onClick = {
                                    val temp = phraseText
                                    phraseText = shortcutText
                                    shortcutText = temp
                                },
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(colors.inputBackground)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.SwapVert,
                                    contentDescription = "Interchange Phrase and Shortcut",
                                    tint = colors.accent,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }

                        OutlinedTextField(
                            value = shortcutText,
                            onValueChange = {
                                shortcutText = it
                                if (highlightShortcut) highlightShortcut = false
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .focusRequester(shortcutFocusRequester),
                            label = { Text("Shortcut (Optional)") },
                            placeholder = { Text("e.g., asm or hw", color = colors.textSecondary, fontSize = 13.sp) },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Outlined.Shortcut,
                                    contentDescription = null,
                                    tint = if (highlightShortcut) colors.accent else colors.textSecondary,
                                    modifier = Modifier.size(20.dp)
                                )
                            },
                            trailingIcon = {
                                if (shortcutText.isNotEmpty()) {
                                    IconButton(onClick = { shortcutText = "" }) {
                                        Icon(Icons.Outlined.Close, contentDescription = "Clear", tint = colors.textSecondary, modifier = Modifier.size(18.dp))
                                    }
                                }
                            },
                            singleLine = true,
                            shape = RoundedCornerShape(14.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = if (highlightShortcut) colors.accent.copy(alpha = 0.08f) else colors.inputBackground,
                                unfocusedContainerColor = if (highlightShortcut) colors.accent.copy(alpha = 0.08f) else colors.inputBackground,
                                focusedBorderColor = colors.accent,
                                unfocusedBorderColor = if (highlightShortcut) colors.accent else Color.Transparent,
                                focusedTextColor = colors.textPrimary,
                                unfocusedTextColor = colors.textPrimary,
                                focusedLabelColor = colors.accent,
                                unfocusedLabelColor = if (highlightShortcut) colors.accent else colors.textSecondary
                            )
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    isFormExpanded = false
                                    editingEntry = null
                                    highlightShortcut = false
                                    phraseText = ""
                                    shortcutText = ""
                                    selectedLanguage = PersonalDictionaryEntry.LANG_ALL
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp),
                                shape = RoundedCornerShape(50),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    containerColor = colors.inputBackground,
                                    contentColor = colors.textSecondary
                                ),
                                border = null
                            ) {
                                Text("Cancel", fontWeight = FontWeight.Medium, fontSize = 14.sp)
                            }

                            Button(
                                onClick = {
                                    if (phraseText.isNotBlank()) {
                                        if (editingEntry != null) {
                                            repository.update(
                                                editingEntry!!.copy(
                                                    word = phraseText.trim(),
                                                    shortcut = shortcutText.trim().ifEmpty { null },
                                                    language = selectedLanguage
                                                )
                                            )
                                            Toast.makeText(context, "Entry updated", Toast.LENGTH_SHORT).show()
                                        } else {
                                            repository.addOrUpdate(
                                                word = phraseText.trim(),
                                                shortcut = shortcutText.trim().ifEmpty { null },
                                                language = selectedLanguage
                                            )
                                            Toast.makeText(context, "Word added", Toast.LENGTH_SHORT).show()
                                        }
                                        isFormExpanded = false
                                        editingEntry = null
                                        highlightShortcut = false
                                        phraseText = ""
                                        shortcutText = ""
                                        selectedLanguage = PersonalDictionaryEntry.LANG_ALL
                                    }
                                },
                                enabled = phraseText.isNotBlank(),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp),
                                shape = RoundedCornerShape(50),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = colors.accent,
                                    contentColor = Color.White
                                )
                            ) {
                                Icon(
                                    imageVector = if (editingEntry != null) Icons.Outlined.Check else Icons.Outlined.Add,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (editingEntry != null) "Update Word" else "Add Word",
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 14.sp
                                )
                            }
                        }
                    }
                }
            }
        }
        }

        item(key = "filter_tabs") {
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(DictionaryTab.entries) { tab ->
                    val isSelected = selectedTab == tab
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(
                                if (isSelected) colors.accent else colors.cardBackground
                            )
                            .clickable {
                                selectedTab = tab
                            }
                            .padding(horizontal = 18.dp, vertical = 9.dp)
                    ) {
                        Text(
                            text = tab.displayName,
                            fontSize = 13.sp,
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                            color = if (isSelected) Color.White else colors.textSecondary
                        )
                    }
                }
            }
        }

        if (selectedTab == DictionaryTab.LEARNT) {
            item(key = "learnt_list_header") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Learnt Words",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = colors.sectionTitle
                        )
                        Text(
                            text = "${learntWords.size} ${if (learntWords.size == 1) "word" else "words"}",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = colors.textSecondary
                        )
                    }
                    if (learntWords.isNotEmpty()) {
                        Text(
                            text = "Delete All",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFFE53935),
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .clickable { showClearAllLearntDialog = true }
                                .padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            if (learntWords.isEmpty()) {
                item(key = "learnt_empty_state") {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 40.dp, horizontal = 20.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(64.dp)
                                    .clip(CircleShape)
                                    .background(colors.inputBackground),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Outlined.MenuBook,
                                    contentDescription = null,
                                    tint = colors.accent,
                                    modifier = Modifier.size(32.dp)
                                )
                            }

                            Spacer(modifier = Modifier.height(14.dp))

                            Text(
                                text = "No learnt words yet",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = colors.textPrimary
                            )

                            Spacer(modifier = Modifier.height(6.dp))

                            Text(
                                text = "Words you type frequently across all languages will appear here.",
                                fontSize = 13.sp,
                                color = colors.textSecondary,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                    }
                }
            } else {
                items(learntWords, key = { "learnt_$it" }) { word ->
                    LearntWordSwipeCard(
                        word = word,
                        colors = colors,
                        onDelete = {
                            userDictionaryRepository.unlearnWord(word)
                            Toast.makeText(context, "Removed \"$word\"", Toast.LENGTH_SHORT).show()
                        }
                    )
                }
            }
        } else {
        item(key = "list_header") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 6.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Saved Words",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.sectionTitle
                )
                Text(
                    text = "${filteredEntries.size} ${if (filteredEntries.size == 1) "entry" else "entries"}",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = colors.textSecondary
                )
            }
        }

        if (filteredEntries.isEmpty()) {
            item(key = "empty_state") {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 40.dp, horizontal = 20.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .clip(CircleShape)
                                .background(colors.inputBackground),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Outlined.MenuBook,
                                contentDescription = null,
                                tint = colors.accent,
                                modifier = Modifier.size(32.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        Text(
                            text = "No words added yet",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = colors.textPrimary
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        Text(
                            text = "Use \"+ Add a Word\" above to add custom words or shortcuts.",
                            fontSize = 13.sp,
                            color = colors.textSecondary,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            }
        } else {
            items(filteredEntries, key = { it.id }) { entry ->
                PersonalDictionaryEntryCard(
                    entry = entry,
                    colors = colors,
                    onClick = {
                        editingEntry = entry
                        phraseText = entry.word
                        shortcutText = entry.shortcut ?: ""
                        selectedLanguage = entry.language
                        isFormExpanded = true
                    },
                    onDelete = {
                        entryPendingDelete = entry
                    }
                )
            }
        }
        }

        item(key = "bottom_spacer") {
            Spacer(modifier = Modifier.height(32.dp))
        }
    }

    if (entryPendingDelete != null) {
        AlertDialog(
            onDismissRequest = { entryPendingDelete = null },
            containerColor = colors.dialogBackground,
            title = {
                Text(
                    text = "Delete Word?",
                    fontWeight = FontWeight.Bold,
                    color = colors.textPrimary
                )
            },
            text = {
                Text(
                    text = "Are you sure you want to delete \"${entryPendingDelete!!.word}\" from your personal dictionary?",
                    color = colors.textSecondary,
                    fontSize = 14.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val toDelete = entryPendingDelete!!
                        repository.delete(toDelete.id)
                        if (editingEntry?.id == toDelete.id) {
                            editingEntry = null
                            isFormExpanded = false
                            phraseText = ""
                            shortcutText = ""
                            selectedLanguage = PersonalDictionaryEntry.LANG_ALL
                        }
                        entryPendingDelete = null
                        Toast.makeText(context, "Word deleted", Toast.LENGTH_SHORT).show()
                    },
                    shape = RoundedCornerShape(50),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFE53935),
                        contentColor = Color.White
                    )
                ) {
                    Text("Delete", fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { entryPendingDelete = null },
                    shape = RoundedCornerShape(50)
                ) {
                    Text("Cancel", color = colors.textSecondary)
                }
            },
            shape = RoundedCornerShape(24.dp)
        )
    }

    if (showClearAllLearntDialog) {
        AlertDialog(
            onDismissRequest = { showClearAllLearntDialog = false },
            containerColor = colors.dialogBackground,
            title = {
                Text(
                    text = "Delete All Learnt Words?",
                    fontWeight = FontWeight.Bold,
                    color = colors.textPrimary
                )
            },
            text = {
                Text(
                    text = "Are you sure you want to remove all learnt words from your typing history?",
                    color = colors.textSecondary,
                    fontSize = 14.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        userDictionaryRepository.clearAll()
                        showClearAllLearntDialog = false
                        Toast.makeText(context, "All learnt words deleted", Toast.LENGTH_SHORT).show()
                    },
                    shape = RoundedCornerShape(50),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFE53935),
                        contentColor = Color.White
                    )
                ) {
                    Text("Delete All", fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showClearAllLearntDialog = false },
                    shape = RoundedCornerShape(50)
                ) {
                    Text("Cancel", color = colors.textSecondary)
                }
            },
            shape = RoundedCornerShape(24.dp)
        )
    }
}

@Composable
private fun PersonalDictionaryEntryCard(
    entry: PersonalDictionaryEntry,
    colors: SettingsColors,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = colors.cardBackground)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = entry.word,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.textPrimary
                )

                val shortcut = entry.shortcut
                if (!shortcut.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.Shortcut,
                            contentDescription = null,
                            tint = colors.accent,
                            modifier = Modifier.size(15.dp)
                        )
                        Text(
                            text = shortcut,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = colors.textSecondary
                        )
                    }
                }
            }

            IconButton(
                onClick = onDelete,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.Delete,
                    contentDescription = "Delete",
                    tint = colors.textSecondary.copy(alpha = 0.7f),
                    modifier = Modifier.size(19.dp)
                )
            }
        }
    }
}

@Composable
private fun LearntWordSwipeCard(
    word: String,
    colors: SettingsColors,
    onDelete: () -> Unit
) {
    var cardWidthPx by remember(word) { mutableStateOf(0f) }
    val offsetX = remember(word) { Animatable(0f) }
    val scope = rememberCoroutineScope()

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .clip(RoundedCornerShape(18.dp))
            .onSizeChanged { cardWidthPx = it.width.toFloat() }
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFFE53935))
                .padding(end = 20.dp),
            contentAlignment = Alignment.CenterEnd
        ) {
            Icon(
                imageVector = Icons.Filled.Delete,
                contentDescription = "Delete learnt word",
                tint = Color.White,
                modifier = Modifier.size(22.dp)
            )
        }

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .offset { IntOffset(offsetX.value.roundToInt(), 0) }
                .pointerInput(word) {
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            scope.launch {
                                val threshold = if (cardWidthPx > 0f) cardWidthPx * 0.35f else 180f
                                if (offsetX.value <= -threshold) {
                                    val target = if (cardWidthPx > 0f) -cardWidthPx else -500f
                                    offsetX.animateTo(target, animationSpec = spring())
                                    onDelete()
                                } else {
                                    offsetX.animateTo(0f, animationSpec = spring())
                                }
                            }
                        },
                        onDragCancel = {
                            scope.launch {
                                offsetX.animateTo(0f, animationSpec = spring())
                            }
                        },
                        onHorizontalDrag = { _, dragAmount ->
                            scope.launch {
                                val minOffset = if (cardWidthPx > 0f) -cardWidthPx else -500f
                                val newOffset = (offsetX.value + dragAmount).coerceIn(minOffset, 0f)
                                offsetX.snapTo(newOffset)
                            }
                        }
                    )
                },
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = colors.cardBackground)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = word,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.textPrimary,
                    modifier = Modifier.weight(1f)
                )

                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Delete,
                        contentDescription = "Delete",
                        tint = colors.textSecondary.copy(alpha = 0.7f),
                        modifier = Modifier.size(19.dp)
                    )
                }
            }
        }
    }
}
