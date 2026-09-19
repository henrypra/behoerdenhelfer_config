package de.behoerdenhelfer.content.model

import kotlinx.serialization.Serializable

/**
 * `dist/latest.json` — the only mutable file on the host. Points at the current
 * immutable config snapshot.
 */
@Serializable
data class LatestPointer(
    val config: Int,
    val configPath: String,
)

/** An immutable config snapshot (the manifest), published as `dist/config/<config>.json`. */
@Serializable
data class Manifest(
    val schemaVersion: Int = 1,
    val config: Int,
    val generatedAt: String,
    val forms: List<ManifestFormEntry>,
    val hints: List<ManifestHintsEntry>,
    /**
     * The singular office-type bundle (`docs/android-content-contract.md` §1.1). Optional:
     * the key is omitted entirely when the repo has no authorities content, so manifests
     * stay byte-compatible with clients that predate it.
     */
    val authorities: ManifestAuthoritiesEntry? = null,
) {
    /** Every published file the manifest references, for path uniqueness and immutability checks. */
    fun fileEntries(): List<FileEntry> =
        forms.flatMap { listOf(it.jsonDe, it.jsonEn, it.pdf) } +
            hints.flatMap { listOf(it.jsonDe, it.jsonEn) } +
            listOfNotNull(authorities).flatMap { listOf(it.jsonDe, it.jsonEn) }
}

@Serializable
data class ManifestFormEntry(
    val formId: String,
    val version: Int,
    val minContentSchema: Int,
    val jsonDe: FileEntry,
    val jsonEn: FileEntry,
    val pdf: FileEntry,
)

@Serializable
data class ManifestHintsEntry(
    val hintsId: String,
    val version: Int,
    val minContentSchema: Int,
    val jsonDe: FileEntry,
    val jsonEn: FileEntry,
)

@Serializable
data class ManifestAuthoritiesEntry(
    val version: Int,
    val minContentSchema: Int,
    val jsonDe: FileEntry,
    val jsonEn: FileEntry,
)

@Serializable
data class FileEntry(
    val path: String,
    val sha256: String,
    val bytes: Long,
)
