package io.kiebord.common.model

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonNames
import kotlinx.serialization.json.JsonPrimitive

object LayoutFlags {
    const val NO_AUTO_CAPITALIZATION = "NoAutoCapitalization"
    const val NO_AUTO_SPACE = "NoAutoSpace"
    const val NO_DOUBLE_SPACE_PERIOD = "NoDoubleSpacePeriod"
    const val NO_SUGGESTIONS = "NoSuggestions"
    const val NO_LEARNED_WORDS = "NoLearnedWords"
    const val NO_GESTURE = "NoGesture"
    const val NO_AUTO_CORRECT = "NoAutoCorrect"
    const val NO_REMOVE_SPACE_BEFORE_PUNCTUATION = "NoRemoveSpaceBeforePunctuation"
}

object FlexibleStringListSerializer : KSerializer<List<String>> {
    override val descriptor: SerialDescriptor = ListSerializer(String.serializer()).descriptor

    override fun deserialize(decoder: Decoder): List<String> {
        val jsonDecoder = decoder as? JsonDecoder ?: return ListSerializer(String.serializer()).deserialize(decoder)
        return when (val elem = jsonDecoder.decodeJsonElement()) {
            is JsonArray -> elem.mapNotNull { (it as? JsonPrimitive)?.content }
            is JsonPrimitive -> {
                val str = elem.content
                if (str.isEmpty()) {
                    emptyList()
                } else if (str.contains(',') || str.contains(';')) {
                    str.split(',', ';').map { it.trim() }.filter { it.isNotEmpty() }
                } else if (str.contains(' ')) {
                    str.split(' ').map { it.trim() }.filter { it.isNotEmpty() }
                } else {
                    listOf(str)
                }
            }
            else -> emptyList()
        }
    }

    override fun serialize(encoder: Encoder, value: List<String>) {
        ListSerializer(String.serializer()).serialize(encoder, value)
    }
}

object FlexibleCharListSerializer : KSerializer<List<String>> {
    override val descriptor: SerialDescriptor = ListSerializer(String.serializer()).descriptor

    override fun deserialize(decoder: Decoder): List<String> {
        val jsonDecoder = decoder as? JsonDecoder ?: return ListSerializer(String.serializer()).deserialize(decoder)
        return when (val elem = jsonDecoder.decodeJsonElement()) {
            is JsonArray -> elem.mapNotNull { (it as? JsonPrimitive)?.content }
            is JsonPrimitive -> {
                val str = elem.content
                if (str.isEmpty()) emptyList()
                else str.map { it.toString() }
            }
            else -> emptyList()
        }
    }

    override fun serialize(encoder: Encoder, value: List<String>) {
        ListSerializer(String.serializer()).serialize(encoder, value)
    }
}

@Serializable
data class KeyDef(
    val label: String = "",
    val shiftedLabel: String? = null,
    val type: KeyType = KeyType.CHARACTER,
    val icon: String? = null,
    val widthWeight: Float = 1.0f,
    val flickUp: String? = null,
    val moreKeys: List<String> = emptyList(),
    val moreKeysColumns: Int? = null,
    val slidingInput: Boolean = false,
    val commitText: String? = null,
    val shiftedCommitText: String? = null,
    val shiftedFlickUp: String? = null,
    val shiftedMoreKeys: List<String>? = null
) {
    fun displayLabel(shiftState: ShiftState): String {
        return when (shiftState) {
            ShiftState.UNSHIFTED -> label
            ShiftState.ONE_SHOT, ShiftState.CAPS_LOCK -> shiftedLabel ?: label.uppercase()
        }
    }

    fun textToCommit(shiftState: ShiftState): String {
        return when (shiftState) {
            ShiftState.UNSHIFTED -> commitText ?: label
            ShiftState.ONE_SHOT, ShiftState.CAPS_LOCK -> shiftedCommitText ?: shiftedLabel ?: (commitText ?: label).uppercase()
        }
    }

    fun isEnterKey(): Boolean = type == KeyType.ACTION || icon == "enter"

    fun resolveActionMoreKeys(base: List<String> = moreKeys): List<String> {
        if (!isEnterKey()) return base
        val result = base.toMutableList()
        if ("ACTION_EMOJI" !in result && "action:emoji" !in result && "EMOJI" !in result) {
            result.add(0, "ACTION_EMOJI")
        }
        if ("ACTION_NEW_LINE" !in result && "ACTION_NEWLINE" !in result && "action:newline" !in result && "NEWLINE" !in result) {
            val emojiIdx = result.indexOfFirst { it == "ACTION_EMOJI" || it == "action:emoji" || it == "EMOJI" }
            if (emojiIdx >= 0) {
                result.add(emojiIdx + 1, "ACTION_NEW_LINE")
            } else {
                result.add("ACTION_NEW_LINE")
            }
        }
        return result
    }

    fun moreKeys(shiftState: ShiftState): List<String> {
        val base = when (shiftState) {
            ShiftState.UNSHIFTED -> moreKeys
            ShiftState.ONE_SHOT, ShiftState.CAPS_LOCK -> shiftedMoreKeys ?: moreKeys
        }
        return if (isEnterKey()) resolveActionMoreKeys(base) else base
    }

    fun flickUp(shiftState: ShiftState): String? {
        val raw = when (shiftState) {
            ShiftState.UNSHIFTED -> flickUp
            ShiftState.ONE_SHOT, ShiftState.CAPS_LOCK -> shiftedFlickUp ?: (if (shiftedMoreKeys != null) shiftedMoreKeys.firstOrNull { !it.startsWith("ACTION_") && !it.startsWith("action:") } else flickUp)
        }
        return if (raw.isNullOrEmpty() || raw.startsWith("ACTION_") || raw.startsWith("action:")) null else raw
    }
}

@Serializable
data class KeyRow(
    val keys: List<KeyDef>,
    val startPaddingWeight: Float = 0f,
    val endPaddingWeight: Float = 0f,
    val startPaddingDp: Float = 0f,
    val endPaddingDp: Float = 0f
) {
    constructor(
        keys: List<KeyDef>,
        sidePaddingWeight: Float
    ) : this(
        keys = keys,
        startPaddingWeight = sidePaddingWeight,
        endPaddingWeight = sidePaddingWeight
    )
}

@Serializable
data class LayoutExtraRow(
    val id: String,
    val title: String,
    val defaultEnabled: Boolean = true,
    val keys: List<KeyDef>
)

@OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
@Serializable
data class KeyboardLayoutDef(
    val id: String = "symbols",
    val name: String = "Symbols",
    val displayName: String? = null,
    val description: String? = null,
    val language: String = "",
    val direction: String = "LTR",
    @JsonNames("gesture_mode", "gestureMode")
    val gestureMode: String? = null,
    val processor: String? = null,
    val rules: String? = null,
    val flag: String? = null,
    @Serializable(with = FlexibleStringListSerializer::class)
    val flags: List<String> = emptyList(),
    @Serializable(with = FlexibleCharListSerializer::class)
    @JsonNames("extraCompositionChars", "extra_composition_chars")
    val extraCompositionChars: List<String> = emptyList(),
    val extraRows: List<LayoutExtraRow> = emptyList(),
    val numberRow: KeyRow? = null,
    val shiftedNumberRow: KeyRow? = null,
    val symbolsLayout: KeyboardLayoutDef? = null,
    val symbols: KeyboardLayoutDef? = null,
    val moreSymbolsLayout: KeyboardLayoutDef? = null,
    val rows: List<KeyRow> = emptyList()
) {
    fun hasFlag(flagName: String): Boolean {
        if (flag != null && flag.split(',', ';', ' ').any { it.trim().equals(flagName, ignoreCase = true) }) {
            return true
        }
        return flags.any { it.equals(flagName, ignoreCase = true) }
    }

    val noAutoCapitalization: Boolean get() = hasFlag(LayoutFlags.NO_AUTO_CAPITALIZATION)
    val noAutoSpace: Boolean get() = hasFlag(LayoutFlags.NO_AUTO_SPACE)
    val noDoubleSpacePeriod: Boolean get() = hasFlag(LayoutFlags.NO_DOUBLE_SPACE_PERIOD)
    val noSuggestions: Boolean get() = hasFlag(LayoutFlags.NO_SUGGESTIONS)
    val noLearnedWords: Boolean get() = hasFlag(LayoutFlags.NO_LEARNED_WORDS)
    val noGesture: Boolean get() = hasFlag(LayoutFlags.NO_GESTURE)
    val noAutoCorrect: Boolean get() = hasFlag(LayoutFlags.NO_AUTO_CORRECT)
    val noRemoveSpaceBeforePunctuation: Boolean get() = hasFlag(LayoutFlags.NO_REMOVE_SPACE_BEFORE_PUNCTUATION)

    fun isExtraCompositionChar(text: String): Boolean {
        if (text.isEmpty()) return false
        return extraCompositionChars.contains(text) ||
                (text.length == 1 && extraCompositionChars.any { it.length == 1 && it[0] == text[0] })
    }

    fun effectiveDisplayName(): String = displayName ?: name.ifEmpty { id }
    fun effectiveSymbolsLayout(): KeyboardLayoutDef? = symbolsLayout ?: symbols
    fun effectiveMoreSymbolsLayout(): KeyboardLayoutDef? = moreSymbolsLayout
    fun isSymbolsLayout(): Boolean = id.startsWith("symbols") || id.contains("_symbols") || language == "sym"

    fun isQwertyBased(): Boolean {
        if (isSymbolsLayout() || noGesture) return false
        if (gestureMode?.equals("qwerty", ignoreCase = true) == true) return true
        if (id == "en_qwerty" || id == "bn_borno" || id == "bn_avro" || id == "tr_q" || id == "tr_q_compact" || id == "tr_f" || language == "tr") return true
        return rows.any { row ->
            val charLabels = row.keys.filter { it.type == KeyType.CHARACTER }.map { it.label.lowercase() }
            charLabels.size >= 6 && charLabels.take(6) == listOf("q", "w", "e", "r", "t", "y")
        }
    }

    /**
     * Resolves extra rows for a symbols layout based on the active alphabet layout.
     * For each enabled extra row in the alphabet layout, uses the corresponding row in this symbols layout
     * if found, otherwise moves the alphabet extra row to this symbols layout.
     */
    fun resolveExtraRowsForSymbols(
        alphaLayout: KeyboardLayoutDef,
        extraRowOverrides: Map<String, Boolean> = emptyMap()
    ): List<KeyRow> {
        if (alphaLayout === this || alphaLayout.isSymbolsLayout()) {
            return this.extraRows.filter { extraRow ->
                val primaryId = this.id.removeSuffix("_symbols").removeSuffix("_symbols_more").removeSuffix("_1").removeSuffix("_2")
                extraRowOverrides["${this.id}_${extraRow.id}"]
                    ?: extraRowOverrides["${primaryId}_${extraRow.id}"]
                    ?: extraRowOverrides[extraRow.id]
                    ?: extraRow.defaultEnabled
            }.map { KeyRow(it.keys) }
        }

        val enabledAlphaExtra = alphaLayout.extraRows.filter { extraRow ->
            val primaryId = alphaLayout.id.removeSuffix("_symbols").removeSuffix("_symbols_more").removeSuffix("_1").removeSuffix("_2")
            extraRowOverrides["${alphaLayout.id}_${extraRow.id}"]
                ?: extraRowOverrides["${primaryId}_${extraRow.id}"]
                ?: extraRowOverrides[extraRow.id]
                ?: extraRow.defaultEnabled
        }

        return enabledAlphaExtra.map { alphaExtra ->
            val corresponding = this.extraRows.find { it.id == alphaExtra.id }
            if (corresponding != null) {
                KeyRow(corresponding.keys)
            } else {
                KeyRow(alphaExtra.keys)
            }
        }
    }

    /**
     * Resolves the 4 core rows for a symbols layout based on whether the top number row is enabled.
     * When showNumberRow is true and the first core row consists of digits (or shift 1 symbols),
     * it is replaced with the alternate symbols row so numbers are not duplicated across rows.
     */
    fun resolveCoreRowsForSymbols(showNumberRow: Boolean): List<KeyRow> {
        if (this.rows.isEmpty()) return emptyList()

        // 6-row layouts (e.g. Borno Easy symbols) preserve all 6 core rows across both modes
        if (this.rows.size >= 6) {
            return this.rows
        }

        val isMoreSymbols = this.id.contains("symbols_2") || this.id.contains("more")

        if (!showNumberRow) {
            return if (this.rows.size >= 5) {
                if (isDigitsRow(this.rows[0]) || isMoreSymbols) {
                    listOf(this.rows[0]) + this.rows.drop(2)
                } else {
                    this.rows
                }
            } else {
                this.rows
            }
        }

        // When showNumberRow is true:
        if (this.rows.size >= 5) {
            if (isDigitsRow(this.rows[0]) || isMoreSymbols) {
                return this.rows.drop(1)
            }
        }

        if (this.rows.size == 4) {
            if (isMoreSymbols) {
                return listOf(SymbolsMoreAlternateRow) + this.rows.drop(1)
            } else if (isDigitsRow(this.rows[0])) {
                return listOf(SymbolsAlternateRow) + this.rows.drop(1)
            }
        }

        return this.rows
    }

    companion object {
        val SymbolsAlternateRow = KeyRow(
            listOf(
                KeyDef("~"),
                KeyDef("<", moreKeys = listOf("<", "«", "≤")),
                KeyDef(">", moreKeys = listOf(">", "»", "≥")),
                KeyDef("_"),
                KeyDef("`"),
                KeyDef("º", moreKeys = listOf("º", "°")),
                KeyDef("\\"),
                KeyDef("=", moreKeys = listOf("=", "≠", "≈", "∞")),
                KeyDef("{"),
                KeyDef("}")
            )
        )

        val SymbolsMoreAlternateRow = KeyRow(
            listOf(
                KeyDef("≠"),
                KeyDef("≤"),
                KeyDef("≥"),
                KeyDef("∫"),
                KeyDef("Σ"),
                KeyDef("≈"),
                KeyDef("±"),
                KeyDef("∓"),
                KeyDef("½"),
                KeyDef("∞")
            )
        )

        fun isDigitsRow(row: KeyRow): Boolean {
            val charKeys = row.keys.filter { it.type == KeyType.CHARACTER }
            if (charKeys.isEmpty()) return false
            val digitCount = charKeys.count { key ->
                val text = key.label.trim()
                text.isNotEmpty() && text.all { ch ->
                    ch.isDigit() || ch in '٠'..'٩' || ch in '۰'..'۹' || ch in '০'..'৯'
                }
            }
            return digitCount >= 7 || (digitCount >= charKeys.size / 2 && digitCount >= 4)
        }

        fun isDuplicateRow(
            extraRow: LayoutExtraRow,
            candidateRows: List<KeyRow>,
            candidateExtra: List<LayoutExtraRow> = emptyList()
        ): Boolean {
            if (candidateExtra.any { it.id == extraRow.id }) return true

            val extraLabels = extraRow.keys
                .filter { it.type == KeyType.CHARACTER }
                .mapNotNull { key ->
                    val text = key.label.ifBlank { key.commitText ?: "" }.trim()
                    text.ifEmpty { null }
                }
                .toSet()
            if (extraLabels.isEmpty()) return false

            for (extra in candidateExtra) {
                val candidateLabels = extra.keys
                    .filter { it.type == KeyType.CHARACTER }
                    .mapNotNull { key ->
                        val text = key.label.ifBlank { key.commitText ?: "" }.trim()
                        text.ifEmpty { null }
                    }
                    .toSet()
                if (candidateLabels.isEmpty()) continue
                val intersection = extraLabels.intersect(candidateLabels)
                val minThreshold = minOf(extraLabels.size, candidateLabels.size)
                if (intersection.size >= minThreshold || (minThreshold > 2 && intersection.size >= (minThreshold + 1) / 2)) {
                    return true
                }
            }

            for (row in candidateRows) {
                val candidateLabels = row.keys
                    .filter { it.type == KeyType.CHARACTER }
                    .mapNotNull { key ->
                        val text = key.label.ifBlank { key.commitText ?: "" }.trim()
                        text.ifEmpty { null }
                    }
                    .toSet()
                if (candidateLabels.isEmpty()) continue
                val intersection = extraLabels.intersect(candidateLabels)
                val minThreshold = minOf(extraLabels.size, candidateLabels.size)
                if (intersection.size >= minThreshold || (minThreshold > 2 && intersection.size >= (minThreshold + 1) / 2)) {
                    return true
                }
            }
            return false
        }
    }
}
