package com.example.auralocalai.data.medical

import kotlinx.serialization.Serializable

/** A trusted website the clinical Q&A may search (via Brave, restricted with site:). */
@Serializable
data class TrustedSite(
    val domain: String,
    val name: String,
    val enabled: Boolean = true
)

@Serializable
data class MedicalConfig(
    val sites: List<TrustedSite> = DefaultMedical.sites,
    val usePubMed: Boolean = true,
    val useSites: Boolean = true,
    val useLibrary: Boolean = true,
    /** The app stops using Brave for the Q&A after this many searches in a calendar month. */
    val monthlyBraveLimit: Int = 1000
)

@Serializable
data class BraveUsage(
    val month: String = "",
    val count: Int = 0
)

/** A document the user imported into the offline library, split into searchable passages. */
@Serializable
data class LibraryDocument(
    val id: String,
    val name: String,
    val addedAt: Long,
    val passages: List<String>
)

enum class SourceKind { PUBMED, WEB, LIBRARY, NEWS }

/** One numbered source shown under an answer and given to the model. */
@Serializable
data class MedicalSource(
    val kind: String,
    val title: String,
    val url: String = "",
    val snippet: String,
    val origin: String
)

@Serializable
data class MedicalAnswer(
    val id: String,
    val question: String,
    val answer: String,
    val sources: List<MedicalSource>,
    val createdAt: Long,
    val searchQuery: String = ""
)

object DefaultMedical {
    val sites: List<TrustedSite> = listOf(
        TrustedSite("ggzstandaarden.nl", "GGZ Standaarden"),
        TrustedSite("akwaggz.nl", "Akwa GGZ"),
        TrustedSite("trimbos.nl", "Trimbos-instituut"),
        TrustedSite("nispa.nl", "NISPA"),
        TrustedSite("farmacotherapeutischkompas.nl", "Farmacotherapeutisch Kompas"),
        TrustedSite("richtlijnendatabase.nl", "Richtlijnendatabase (FMS)"),
        TrustedSite("nhg.org", "NHG-Standaarden"),
        TrustedSite("vilans.nl", "Vilans")
    )
}
