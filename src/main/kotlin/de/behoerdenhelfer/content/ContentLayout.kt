package de.behoerdenhelfer.content

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name

/**
 * Discovers the bundles of the `content/` tree from the directory layout itself —
 * there is no registry file; every folder under `content/forms/` is a form bundle
 * and every `hints_<id>.json` under `content/hints/` is a hints catalog. Ids are
 * SCREAMING_SNAKE_CASE, folders/files lowercase. `content/authorities/` and
 * `content/wegweiser/` each hold one optional, singular bundle (`<name>.json` +
 * `<name>-en.json`).
 */
class ContentLayout(
    val contentDir: Path,
) {
    val formsDir: Path = contentDir.resolve("forms")
    val hintsDir: Path = contentDir.resolve("hints")
    val authoritiesDir: Path = contentDir.resolve("authorities")
    val wegweiserDir: Path = contentDir.resolve("wegweiser")

    fun discoverForms(): List<FormBundle> =
        if (Files.isDirectory(formsDir)) {
            formsDir
                .listDirectoryEntries()
                .filter { Files.isDirectory(it) }
                .sortedBy { it.name }
                .map { formBundle(it.name.uppercase()) }
        } else {
            emptyList()
        }

    fun discoverHints(): List<HintsBundle> =
        if (Files.isDirectory(hintsDir)) {
            hintsDir
                .listDirectoryEntries("hints_*.json")
                .filter { !it.name.endsWith("-en.json") }
                .sortedBy { it.name }
                .map {
                    hintsBundle(
                        it.name
                            .removePrefix("hints_")
                            .removeSuffix(".json")
                            .uppercase(),
                    )
                }
        } else {
            emptyList()
        }

    /** The authorities bundle, or null when the repo does not ship one (the manifest then omits the key). */
    fun discoverAuthorities(): JsonPairBundle? = authoritiesBundle().takeIf { it.exists() }

    fun authoritiesBundle(): JsonPairBundle = JsonPairBundle(authoritiesDir, "authorities")

    /** The Wegweiser bundle, or null when the repo does not ship one (the manifest then omits the key). */
    fun discoverWegweiser(): JsonPairBundle? = wegweiserBundle().takeIf { it.exists() }

    fun wegweiserBundle(): JsonPairBundle = JsonPairBundle(wegweiserDir, "wegweiser")

    fun formBundle(formId: String): FormBundle {
        val folder = formId.lowercase()
        val dir = formsDir.resolve(folder)
        return FormBundle(
            formId = formId,
            folder = folder,
            dir = dir,
            jsonDe = dir.resolve("form_$folder.json"),
            jsonEn = dir.resolve("form_$folder-en.json"),
        )
    }

    fun hintsBundle(hintsId: String): HintsBundle {
        val folder = hintsId.lowercase()
        val base = "hints_$folder"
        return HintsBundle(
            hintsId = hintsId,
            folder = folder,
            jsonDe = hintsDir.resolve("$base.json"),
            jsonEn = hintsDir.resolve("$base-en.json"),
        )
    }
}

data class FormBundle(
    val formId: String,
    val folder: String,
    val dir: Path,
    val jsonDe: Path,
    val jsonEn: Path,
) {
    /** The PDF belonging to the bundle; its name comes from the form JSON's `pdfAssetPath`. */
    fun pdf(pdfAssetPath: String): Path = dir.resolve(pdfAssetPath)
}

data class HintsBundle(
    val hintsId: String,
    val folder: String,
    val jsonDe: Path,
    val jsonEn: Path,
)

/** A singular de + en JSON bundle: `<dir>/<name>.json` and `<dir>/<name>-en.json`. */
data class JsonPairBundle(
    val dir: Path,
    val name: String,
) {
    val jsonDe: Path = dir.resolve("$name.json")
    val jsonEn: Path = dir.resolve("$name-en.json")

    /** Present as soon as either file exists — a lone `-en` file is then reported, not ignored. */
    fun exists(): Boolean = Files.isRegularFile(jsonDe) || Files.isRegularFile(jsonEn)
}
