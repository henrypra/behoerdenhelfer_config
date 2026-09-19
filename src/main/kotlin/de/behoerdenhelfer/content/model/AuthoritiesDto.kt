package de.behoerdenhelfer.content.model

import kotlinx.serialization.Serializable

/**
 * `authorities.json` — one page per office *type* (Familienkasse, Jobcenter, …),
 * never a concrete local branch. Shape per `docs/android-content-contract.md` §1.2.
 * Parsed strictly so a misspelt key fails validation instead of silently vanishing
 * (the app ignores unknown keys).
 */
@Serializable
data class AuthoritiesDto(
    val schema: Int,
    val authorities: List<AuthorityDto>,
)

@Serializable
data class AuthorityDto(
    val id: String,
    val name: String,
    val does: String,
    val doesNot: String? = null,
    /** Form ids from the manifest (`KINDERGELD`, `BUERGERGELD_HA`, …); used for cross-links. */
    val handles: List<String> = emptyList(),
    /** Nationwide number only, formatted for reading; the app dials it as typed. */
    val hotline: String? = null,
    val portalUrl: String? = null,
    val portalLabel: String? = null,
    val appointmentNeeded: Boolean = false,
    val bring: List<String> = emptyList(),
    val rights: List<String> = emptyList(),
    val howToFindLocal: String? = null,
    /** Official locator (BA, DRV, BZSt, …) — never a third-party map. */
    val locatorUrl: String? = null,
) {
    /** Everything a translator may change; the rest must be identical in de and en. */
    val textFields: Map<String, List<String>>
        get() =
            mapOf(
                "name" to listOf(name),
                "does" to listOf(does),
                "doesNot" to listOfNotNull(doesNot),
                "portalLabel" to listOfNotNull(portalLabel),
                "bring" to bring,
                "rights" to rights,
                "howToFindLocal" to listOfNotNull(howToFindLocal),
            )

    /** The language-neutral part of the entry, for de/en parity checks. */
    fun withoutText(): AuthorityDto =
        copy(
            name = "",
            does = "",
            doesNot = null,
            portalLabel = null,
            bring = emptyList(),
            rights = emptyList(),
            howToFindLocal = null,
        )
}
