package com.codepotro.borno.data.langpack

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonNames

@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class PackCatalogEntry(
    val id: String,
    val version: Int = 1,
    val name: String,
    @JsonNames("language_display_name", "language_displayName")
    val languageDisplayName: String? = null,
    val author: String = "",
    @JsonNames("acknowledgement", "acknowledgments", "acknowledgements", "credits")
    val credit: String? = null,
    val description: String = "",
    val downloadUrl: String,
    val sha256: String,
    val sizeBytes: Long = 0L,
    val hasDictionary: Boolean = false,
    @JsonNames("dictionary_credit", "wordlistCredit", "wordlist_credit")
    val dictionaryCredit: String? = null,
    @JsonNames("dictionary_license", "wordlistLicense", "wordlist_license")
    val dictionaryLicense: String? = null,
    val layouts: List<String> = emptyList()
)

