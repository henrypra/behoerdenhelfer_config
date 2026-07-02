package de.behoerdenhelfer.content

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name

/**
 * Discovers the bundles of the `content/` tree from the directory layout itself —
 * there is no registry file; every folder under `content/forms/` is a form bundle
 * and every `hints_<id>.json` under `content/hints/` is a hints catalog. Ids are
 * SCREAMING_SNAKE_CASE, folders/files lowercase.
 */
class ContentLayout(
    val contentDir: Path,
) {
    val formsDir: Path = contentDir.resolve("forms")
    val hintsDir: Path = contentDir.resolve("hints")

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
