package com.codepotro.borno.data.langpack

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonNames

@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class LanguagePackMetadata(
    val id: String,
    val version: Int = 1,
    val language: String,
    val locale: String = language,
    val displayName: String,
    @JsonNames("language_display_name", "language_displayName")
    val languageDisplayName: String? = null,
    val author: String = "",
    val license: String = "",
    @JsonNames("acknowledgement", "acknowledgments", "acknowledgements", "credits")
    val credit: String? = null,
    val layouts: List<String> = emptyList(),
    val dictionaryFile: String? = null,
    @JsonNames("dictionary_credit", "wordlistCredit", "wordlist_credit", "dictionaryAcknowledgement", "dictionary_acknowledgement")
    val dictionaryCredit: String? = null,
    @JsonNames("dictionary_license", "wordlistLicense", "wordlist_license")
    val dictionaryLicense: String? = null,
    @JsonNames("dictionary_source", "wordlistSource", "wordlist_source")
    val dictionarySource: String? = null,
    val scriptRulesFile: String? = null,
    val charFoldingFile: String? = null,
    val currencySymbol: String? = null
)

