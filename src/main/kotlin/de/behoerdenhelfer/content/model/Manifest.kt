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
)

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
data class FileEntry(
    val path: String,
    val sha256: String,
    val bytes: Long,
)
