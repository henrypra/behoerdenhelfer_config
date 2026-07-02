package de.behoerdenhelfer.content

import de.behoerdenhelfer.content.model.FieldDto
import de.behoerdenhelfer.content.model.FieldType
import de.behoerdenhelfer.content.model.FormDto
import de.behoerdenhelfer.content.model.HintsCatalogDto
import kotlinx.serialization.json.Json
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name
import kotlin.streams.asSequence

data class Violation(
    val scope: String,
    val message: String,
) {
    override fun toString(): String = "[$scope] $message"
}

/**
 * Refuses to publish broken content — the same checks the app enforces in its
 * integration tests. Runs via `./gradlew validate` and as the first step of
 * `generate`.
 */
class Validator(
    private val pdfFieldNames: (Path) -> Set<String> = PdfFieldReader::fieldNames,
) {
    private val json = Json

    fun validate(layout: ContentLayout): List<Violation> {
        val violations = mutableListOf<Violation>()
        val hintNumbers = validateHints(layout, violations)
        layout.discoverForms().forEach { bundle ->
            validateForm(bundle, hintNumbers, violations)
        }
        validateNoOrphanFiles(layout, violations)
        return violations
    }

    /** Validates all hints catalogs and returns the set of valid hint numbers. */
    private fun validateHints(
        layout: ContentLayout,
        violations: MutableList<Violation>,
    ): Set<Int> {
        val numbers = mutableSetOf<Int>()
        layout.discoverHints().forEach { bundle ->
            val hintsId = bundle.hintsId
            val de = parseHints(bundle.jsonDe, hintsId, violations)
            val en = parseHints(bundle.jsonEn, hintsId, violations)
            if (de == null || en == null) return@forEach
            val deNumbers = de.hints.map { it.number }
            val enNumbers = en.hints.map { it.number }
            if (deNumbers.size != deNumbers.toSet().size) {
                violations += Violation(hintsId, "duplicate hint numbers in ${bundle.jsonDe.name}")
            }
            if (deNumbers != enNumbers) {
                violations += Violation(hintsId, "de and en hints catalogs list different hint numbers")
            }
            numbers += deNumbers
        }
        return numbers
    }

    private fun parseHints(
        file: Path,
        hintsId: String,
        violations: MutableList<Violation>,
    ): HintsCatalogDto? {
        if (!Files.isRegularFile(file)) {
            violations += Violation(hintsId, "missing hints file $file")
            return null
        }
        return try {
            json.decodeFromString<HintsCatalogDto>(Files.readString(file))
        } catch (e: Exception) {
            violations += Violation(hintsId, "cannot parse ${file.name}: ${e.message}")
            null
        }
    }

    private fun validateForm(
        bundle: FormBundle,
        hintNumbers: Set<Int>,
        violations: MutableList<Violation>,
    ) {
        val de = parseForm(bundle.jsonDe, bundle.formId, violations)
        val en = parseForm(bundle.jsonEn, bundle.formId, violations)
        if (de == null || en == null) return

        StructureComparator.firstDifference(de, en)?.let {
            violations += Violation(bundle.formId, "de/en structural drift: $it")
        }
        if (de.pdfAssetPath.isBlank()) {
            violations += Violation(bundle.formId, "pdfAssetPath is blank")
            return
        }
        val pdf = bundle.pdf(de.pdfAssetPath)
        if (!Files.isRegularFile(pdf)) {
            violations += Violation(bundle.formId, "pdfAssetPath '${de.pdfAssetPath}' does not exist in ${bundle.dir}")
            return
        }

        val namesInPdf =
            try {
                pdfFieldNames(pdf)
            } catch (e: Exception) {
                violations += Violation(bundle.formId, "cannot read PDF '${de.pdfAssetPath}': ${e.message}")
                return
            }
        if (namesInPdf.isEmpty()) {
            violations +=
                Violation(
                    bundle.formId,
                    "'${de.pdfAssetPath}' has no AcroForm fields — flattened print copy, use the fillable original",
                )
        } else {
            pdfTargetingNames(de).forEach { name ->
                if (name !in namesInPdf) {
                    violations += Violation(bundle.formId, "field '$name' not found in PDF '${de.pdfAssetPath}'")
                }
            }
        }

        val allNames = mutableSetOf<String>()
        walkFields(de) { allNames += it.name }
        walkFields(de) { field ->
            field.showWhen?.let { showWhen ->
                if (showWhen.field !in allNames) {
                    violations +=
                        Violation(
                            bundle.formId,
                            "field '${field.name}' has show_when on unknown field '${showWhen.field}'",
                        )
                }
            }
        }

        walkFields(de) { field ->
            field.hints.forEach { number ->
                if (number !in hintNumbers) {
                    violations += Violation(bundle.formId, "field '${field.name}' references unknown hint $number")
                }
            }
        }
    }

    private fun parseForm(
        file: Path,
        formId: String,
        violations: MutableList<Violation>,
    ): FormDto? {
        if (!Files.isRegularFile(file)) {
            violations += Violation(formId, "missing form file $file")
            return null
        }
        return try {
            json.decodeFromString<FormDto>(Files.readString(file))
        } catch (e: Exception) {
            violations += Violation(formId, "cannot parse ${file.name}: ${e.message}")
            null
        }
    }

    /**
     * Names that must exist as AcroForm fields: `ui_`-prefixed names are app-only
     * helpers, `radio`/`multiselect` parents are synthetic, `section_header`s are
     * presentational — everything else targets the PDF.
     */
    private fun pdfTargetingNames(form: FormDto): List<String> {
        val names = mutableListOf<String>()
        walkFields(form) { field ->
            val appOnly = field.name.startsWith("ui_") || field.type in SYNTHETIC_TYPES
            if (!appOnly) names += field.name
        }
        return names
    }

    private fun walkFields(
        form: FormDto,
        action: (FieldDto) -> Unit,
    ) {
        fun recurse(field: FieldDto) {
            action(field)
            field.children.forEach(::recurse)
        }
        form.pages.forEach { page -> page.fields.forEach(::recurse) }
    }

    /**
     * Every file under content/ must belong to a discovered bundle — a stray file
     * (e.g. an `-en` hints catalog without its de counterpart) would silently never
     * be published, so it is a violation instead.
     */
    private fun validateNoOrphanFiles(
        layout: ContentLayout,
        violations: MutableList<Violation>,
    ) {
        if (Files.isDirectory(layout.formsDir)) {
            layout.formsDir.listDirectoryEntries().filter { Files.isRegularFile(it) }.forEach { file ->
                violations += Violation("forms", "unexpected file '${file.name}' — forms live in per-form folders")
            }
        }
        val knownHintsFiles =
            layout
                .discoverHints()
                .flatMap { listOf(it.jsonDe.name, it.jsonEn.name) }
                .toSet()
        if (Files.isDirectory(layout.hintsDir)) {
            Files.list(layout.hintsDir).use { entries ->
                entries.asSequence().filter { Files.isRegularFile(it) }.forEach { file ->
                    if (file.name !in knownHintsFiles) {
                        violations +=
                            Violation(
                                "hints",
                                "orphan file '${file.name}' does not belong to any hints catalog " +
                                    "(missing its 'hints_<id>.json' de counterpart?)",
                            )
                    }
                }
            }
        }
    }

    private companion object {
        val SYNTHETIC_TYPES = setOf(FieldType.SECTION_HEADER, FieldType.RADIO, FieldType.MULTISELECT)
    }
}
