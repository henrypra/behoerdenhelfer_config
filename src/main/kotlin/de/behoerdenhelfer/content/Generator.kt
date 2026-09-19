package de.behoerdenhelfer.content

import de.behoerdenhelfer.content.model.FileEntry
import de.behoerdenhelfer.content.model.FormDto
import de.behoerdenhelfer.content.model.LatestPointer
import de.behoerdenhelfer.content.model.Manifest
import de.behoerdenhelfer.content.model.ManifestBundleEntry
import de.behoerdenhelfer.content.model.ManifestFormEntry
import de.behoerdenhelfer.content.model.ManifestHintsEntry
import kotlinx.serialization.json.Json
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.time.Instant
import java.time.temporal.ChronoUnit
import kotlin.io.path.name

sealed interface GenerateResult {
    data class Success(
        val manifest: Manifest,
    ) : GenerateResult

    data class Failure(
        val violations: List<Violation>,
    ) : GenerateResult
}

/**
 * Produces `dist/`: immutable per-form version folders (language files grouped in
 * `de/`/`en/` subfolders), an immutable config snapshot `config/<config>.json` (the
 * manifest), and the single mutable file `latest.json` pointing at it. Validates
 * first and refuses to write anything on violations.
 *
 * Every number is derived, never hand-maintained. Per bundle: file hashes identical
 * to the published config → same version; changed → published version + 1. Globally:
 * content identical to the published config → same config number, byte-identical
 * snapshot (re-deploys are no-ops); content changed → published config + 1. A human
 * only ever edits content files — there is no number a human can get wrong.
 */
class Generator(
    private val validator: Validator = Validator(),
    private val clock: () -> Instant = Instant::now,
) {
    private val json =
        Json {
            prettyPrint = true
            // schemaVersion has a default and would otherwise be dropped from the manifest.
            encodeDefaults = true
            // Optional entries (authorities) are omitted rather than written as null, so a
            // manifest without them is byte-compatible with clients that predate the key.
            explicitNulls = false
        }

    /**
     * @param publishedManifest the last published config snapshot (checked in at
     *   the repo root as `published-config.json`, updated by CI after each deploy).
     *   Baseline for version derivation and the config counter; pass null before
     *   the first publish.
     */
    fun generate(
        layout: ContentLayout,
        distDir: Path,
        publishedManifest: Path? = null,
    ): GenerateResult {
        val violations = validator.validate(layout).toMutableList()
        if (violations.isNotEmpty()) return GenerateResult.Failure(violations)

        val publishedFile = publishedManifest?.takeIf(Files::isRegularFile)
        val published = publishedFile?.let { json.decodeFromString<Manifest>(Files.readString(it)) }
        val plans = buildFilePlans(layout, published)
        val forms = plans.forms.map { it.entry }
        val hints = plans.hints.map { it.entry }
        val authorities = plans.authorities?.entry
        val wegweiser = plans.wegweiser?.entry

        violations += manifestViolations(forms, hints, authorities, wegweiser, published)
        if (violations.isNotEmpty()) return GenerateResult.Failure(violations)

        val unchanged =
            published != null &&
                published.forms == forms &&
                published.hints == hints &&
                published.authorities == authorities &&
                published.wegweiser == wegweiser
        val manifest: Manifest
        val manifestBytes: String
        if (unchanged) {
            // Re-publish the exact published bytes (same config, same generatedAt) so
            // deploying an unchanged repo can never rewrite a published snapshot.
            manifest = published!!
            manifestBytes = Files.readString(publishedFile)
        } else {
            manifest =
                Manifest(
                    schemaVersion = 1,
                    config = (published?.config ?: 0) + 1,
                    generatedAt = clock().truncatedTo(ChronoUnit.SECONDS).toString(),
                    forms = forms,
                    hints = hints,
                    authorities = authorities,
                    wegweiser = wegweiser,
                )
            manifestBytes = json.encodeToString(manifest) + "\n"
        }

        writeDist(distDir, plans, manifest.config, manifestBytes)
        return GenerateResult.Success(manifest)
    }

    private data class FilePlan(
        val source: Path,
        val entry: FileEntry,
    )

    private class DistPlans(
        val forms: List<PlannedForm>,
        val hints: List<PlannedHints>,
        val authorities: PlannedBundle?,
        val wegweiser: PlannedBundle?,
    ) {
        val allFiles: List<FilePlan>
            get() =
                forms.flatMap { it.files } +
                    hints.flatMap { it.files } +
                    listOfNotNull(authorities, wegweiser).flatMap { it.files }
    }

    private class PlannedForm(
        val entry: ManifestFormEntry,
        val files: List<FilePlan>,
    )

    private class PlannedHints(
        val entry: ManifestHintsEntry,
        val files: List<FilePlan>,
    )

    private class PlannedBundle(
        val entry: ManifestBundleEntry,
        val files: List<FilePlan>,
    )

    private fun buildFilePlans(
        layout: ContentLayout,
        published: Manifest?,
    ): DistPlans {
        val forms =
            layout.discoverForms().map { bundle ->
                val form = json.decodeFromString<FormDto>(Files.readString(bundle.jsonDe))
                val pdf = bundle.pdf(form.pdfAssetPath)
                val deSha = Sha256.of(bundle.jsonDe)
                val enSha = Sha256.of(bundle.jsonEn)
                val pdfSha = Sha256.of(pdf)
                val publishedEntry = published?.forms?.find { it.formId == bundle.formId }
                val version = derivedFormVersion(publishedEntry, deSha, enSha, pdfSha, pdf.name)
                val base = "forms/${bundle.folder}/$version"
                val jsonDe = filePlan(bundle.jsonDe, "$base/de/form.json", deSha)
                val jsonEn = filePlan(bundle.jsonEn, "$base/en/form.json", enSha)
                // The PDF is language-neutral and keeps its original name (pdfAssetPath).
                val pdfPlan = filePlan(pdf, "$base/${pdf.name}", pdfSha)
                PlannedForm(
                    entry =
                        ManifestFormEntry(
                            formId = bundle.formId,
                            version = version,
                            minContentSchema = ContentSchema.requiredFor(form),
                            jsonDe = jsonDe.entry,
                            jsonEn = jsonEn.entry,
                            pdf = pdfPlan.entry,
                        ),
                    files = listOf(jsonDe, jsonEn, pdfPlan),
                )
            }
        val hints =
            layout.discoverHints().map { bundle ->
                val deSha = Sha256.of(bundle.jsonDe)
                val enSha = Sha256.of(bundle.jsonEn)
                val publishedEntry = published?.hints?.find { it.hintsId == bundle.hintsId }
                val version =
                    derivedJsonPairVersion(
                        publishedEntry?.version,
                        publishedEntry?.jsonDe?.sha256,
                        publishedEntry?.jsonEn?.sha256,
                        deSha,
                        enSha,
                    )
                val base = "hints/${bundle.folder}/$version"
                val jsonDe = filePlan(bundle.jsonDe, "$base/de/hints.json", deSha)
                val jsonEn = filePlan(bundle.jsonEn, "$base/en/hints.json", enSha)
                PlannedHints(
                    entry =
                        ManifestHintsEntry(
                            hintsId = bundle.hintsId,
                            version = version,
                            minContentSchema = ContentSchema.HINTS,
                            jsonDe = jsonDe.entry,
                            jsonEn = jsonEn.entry,
                        ),
                    files = listOf(jsonDe, jsonEn),
                )
            }
        val authorities =
            layout.discoverAuthorities()?.let { planJsonPair(it, published?.authorities, ContentSchema.AUTHORITIES) }
        val wegweiser =
            layout.discoverWegweiser()?.let { planJsonPair(it, published?.wegweiser, ContentSchema.WEGWEISER) }
        return DistPlans(forms, hints, authorities, wegweiser)
    }

    /**
     * A singular de + en bundle is versioned like every other one: published bytes are
     * immutable under `<name>/<version>/{de,en}/<name>.json`, and the manifest path is
     * the only interface the app builds URLs from.
     */
    private fun planJsonPair(
        bundle: JsonPairBundle,
        publishedEntry: ManifestBundleEntry?,
        minContentSchema: Int,
    ): PlannedBundle {
        val deSha = Sha256.of(bundle.jsonDe)
        val enSha = Sha256.of(bundle.jsonEn)
        val version =
            derivedJsonPairVersion(
                publishedEntry?.version,
                publishedEntry?.jsonDe?.sha256,
                publishedEntry?.jsonEn?.sha256,
                deSha,
                enSha,
            )
        val base = "${bundle.name}/$version"
        val jsonDe = filePlan(bundle.jsonDe, "$base/de/${bundle.name}.json", deSha)
        val jsonEn = filePlan(bundle.jsonEn, "$base/en/${bundle.name}.json", enSha)
        return PlannedBundle(
            entry =
                ManifestBundleEntry(
                    version = version,
                    minContentSchema = minContentSchema,
                    jsonDe = jsonDe.entry,
                    jsonEn = jsonEn.entry,
                ),
            files = listOf(jsonDe, jsonEn),
        )
    }

    /**
     * A bundle's version is derived, never hand-maintained: identical bytes (and
     * PDF name) as the published entry → same version; anything changed → published
     * version + 1; not published yet → 1. Versions can therefore never go backwards
     * and never stay put on a content change.
     */
    private fun derivedFormVersion(
        published: ManifestFormEntry?,
        deSha: String,
        enSha: String,
        pdfSha: String,
        pdfName: String,
    ): Int {
        published ?: return 1
        val unchanged =
            deSha == published.jsonDe.sha256 &&
                enSha == published.jsonEn.sha256 &&
                pdfSha == published.pdf.sha256 &&
                pdfName == published.pdf.path.substringAfterLast('/')
        return if (unchanged) published.version else published.version + 1
    }

    /**
     * Two-file (de + en JSON) bundles share one derivation: same bytes as the published
     * entry → same version; anything changed → published version + 1; new → 1.
     */
    private fun derivedJsonPairVersion(
        publishedVersion: Int?,
        publishedDeSha: String?,
        publishedEnSha: String?,
        deSha: String,
        enSha: String,
    ): Int {
        publishedVersion ?: return 1
        val unchanged = deSha == publishedDeSha && enSha == publishedEnSha
        return if (unchanged) publishedVersion else publishedVersion + 1
    }

    private fun filePlan(
        source: Path,
        distPath: String,
        sha256: String,
    ): FilePlan = FilePlan(source, FileEntry(path = distPath, sha256 = sha256, bytes = Files.size(source)))

    /** Manifest-level rules: unique paths + published paths are immutable. */
    private fun manifestViolations(
        forms: List<ManifestFormEntry>,
        hints: List<ManifestHintsEntry>,
        authorities: ManifestBundleEntry?,
        wegweiser: ManifestBundleEntry?,
        published: Manifest?,
    ): List<Violation> {
        val violations = mutableListOf<Violation>()

        val entries =
            Manifest(
                config = 0,
                generatedAt = "",
                forms = forms,
                hints = hints,
                authorities = authorities,
                wegweiser = wegweiser,
            ).fileEntries()
        entries.groupBy { it.path }.filterValues { it.size > 1 }.keys.forEach { path ->
            violations += Violation("manifest", "path '$path' is referenced by more than one entry")
        }

        if (published != null) violations += immutabilityViolations(entries, published)
        return violations
    }

    /**
     * Defense in depth behind version derivation: different bytes at an
     * already-published path would poison year-long CDN caches. Unreachable while
     * derivation is correct — if it fires, the derivation itself is broken.
     */
    internal fun immutabilityViolations(
        current: List<FileEntry>,
        published: Manifest,
    ): List<Violation> {
        val publishedByPath = published.fileEntries().associateBy { it.path }
        return current.mapNotNull { entry ->
            val old = publishedByPath[entry.path]
            if (old != null && old.sha256 != entry.sha256) {
                Violation(
                    "manifest",
                    "would rewrite published path '${entry.path}' with different bytes — " +
                        "published files are immutable",
                )
            } else {
                null
            }
        }
    }

    private fun writeDist(
        distDir: Path,
        plans: DistPlans,
        config: Int,
        manifestBytes: String,
    ) {
        if (Files.exists(distDir)) {
            Files.walk(distDir).use { paths ->
                paths.sorted(Comparator.reverseOrder()).forEach(Files::delete)
            }
        }
        plans.allFiles.forEach { plan ->
            val target = distDir.resolve(plan.entry.path)
            Files.createDirectories(target.parent)
            // Publish the original bytes untouched — especially the PDFs.
            Files.copy(plan.source, target, StandardCopyOption.COPY_ATTRIBUTES)
        }
        val configPath = "config/$config.json"
        val configFile = distDir.resolve(configPath)
        Files.createDirectories(configFile.parent)
        Files.writeString(configFile, manifestBytes)
        Files.writeString(
            distDir.resolve("latest.json"),
            json.encodeToString(LatestPointer(config = config, configPath = configPath)) + "\n",
        )
    }
}
