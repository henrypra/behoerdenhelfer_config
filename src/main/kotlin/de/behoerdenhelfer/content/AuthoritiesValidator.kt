package de.behoerdenhelfer.content

import de.behoerdenhelfer.content.model.AuthoritiesDto
import kotlinx.serialization.json.Json
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name

/**
 * The rules of `docs/android-content-contract.md` §1.4 for the authorities bundle:
 * `schema == 1`, non-empty, unique lowercase ids, `handles` are known form ids,
 * hotline/URL formats, de/en id parity and no HTML in text. On top of the contract,
 * every language-neutral field must be identical in de and en — the same "only the
 * text differs" rule [StructureComparator] enforces for forms.
 */
class AuthoritiesValidator {
    private val json = Json

    fun validate(
        layout: ContentLayout,
        formIds: Set<String>,
        violations: MutableList<Violation>,
    ) {
        val bundle = layout.discoverAuthorities()
        validateNoOrphanFiles(layout, violations)
        bundle ?: return

        val de = parse(bundle.jsonDe, violations)
        val en = parse(bundle.jsonEn, violations)
        if (de == null || en == null) return

        validateFile(de, bundle.jsonDe.name, formIds, violations)
        validateFile(en, bundle.jsonEn.name, formIds, violations)
        validateParity(de, en, violations)
    }

    private fun parse(
        file: Path,
        violations: MutableList<Violation>,
    ): AuthoritiesDto? {
        if (!Files.isRegularFile(file)) {
            violations += Violation(SCOPE, "missing authorities file $file")
            return null
        }
        return try {
            json.decodeFromString<AuthoritiesDto>(Files.readString(file))
        } catch (e: Exception) {
            violations += Violation(SCOPE, "cannot parse ${file.name}: ${e.message}")
            null
        }
    }

    private fun validateFile(
        dto: AuthoritiesDto,
        fileName: String,
        formIds: Set<String>,
        violations: MutableList<Violation>,
    ) {
        fun violation(message: String) {
            violations += Violation(SCOPE, "$fileName: $message")
        }

        if (dto.schema != SCHEMA) violation("schema is ${dto.schema}, expected $SCHEMA")
        if (dto.authorities.isEmpty()) violation("authorities list is empty")

        dto.authorities
            .groupBy { it.id }
            .filterValues { it.size > 1 }
            .keys
            .forEach { violation("duplicate id '$it'") }

        dto.authorities.forEach { authority ->
            val id = authority.id
            if (!ID_PATTERN.matches(id)) violation("id '$id' does not match ${ID_PATTERN.pattern}")
            if (authority.name.isBlank()) violation("'$id' has a blank name")
            if (authority.does.isBlank()) violation("'$id' has a blank 'does'")

            authority.handles.forEach { formId ->
                if (formId !in formIds) violation("'$id' handles unknown form id '$formId'")
            }
            if (authority.handles.size != authority.handles.toSet().size) violation("'$id' lists a form id twice in handles")

            authority.hotline?.let { hotline ->
                if (!HOTLINE_PATTERN.matches(hotline)) violation("'$id' hotline '$hotline' does not match ${HOTLINE_PATTERN.pattern}")
            }
            listOf("portalUrl" to authority.portalUrl, "locatorUrl" to authority.locatorUrl).forEach { (field, url) ->
                if (url != null && !isHttpsUrl(url)) violation("'$id' $field '$url' must be an https:// URL without whitespace")
            }
            if (authority.portalLabel != null && authority.portalUrl == null) {
                violation("'$id' has a portalLabel but no portalUrl")
            }

            authority.textFields.forEach { (field, texts) ->
                texts.forEach { text ->
                    if (text.isBlank()) violation("'$id' $field contains a blank entry")
                    if (HTML_PATTERN.containsMatchIn(text)) violation("'$id' $field contains HTML: '${text.take(40)}'")
                }
            }
        }
    }

    private fun validateParity(
        de: AuthoritiesDto,
        en: AuthoritiesDto,
        violations: MutableList<Violation>,
    ) {
        val deById = de.authorities.associateBy { it.id }
        val enById = en.authorities.associateBy { it.id }
        (deById.keys - enById.keys).forEach { violations += Violation(SCOPE, "id '$it' exists in de but not in en") }
        (enById.keys - deById.keys).forEach { violations += Violation(SCOPE, "id '$it' exists in en but not in de") }
        deById.forEach { (id, deEntry) ->
            val enEntry = enById[id] ?: return@forEach
            if (deEntry.withoutText() != enEntry.withoutText()) {
                violations += Violation(SCOPE, "de/en drift for '$id': only text fields may differ between languages")
            }
        }
    }

    /** Any other file in `content/authorities/` would silently never be published. */
    private fun validateNoOrphanFiles(
        layout: ContentLayout,
        violations: MutableList<Violation>,
    ) {
        if (!Files.isDirectory(layout.authoritiesDir)) return
        val known = layout.authoritiesBundle().let { setOf(it.jsonDe.name, it.jsonEn.name) }
        layout.authoritiesDir.listDirectoryEntries().filter { it.name !in known }.forEach { file ->
            violations += Violation(SCOPE, "unexpected file '${file.name}' — only authorities.json and authorities-en.json belong here")
        }
    }

    private fun isHttpsUrl(url: String): Boolean = url.startsWith("https://") && url.length > 8 && url.none { it.isWhitespace() }

    companion object {
        const val SCOPE = "authorities"
        const val SCHEMA = 1
        val ID_PATTERN = Regex("^[a-z][a-z0-9_]*$")
        val HOTLINE_PATTERN = Regex("^[0-9 +/()-]+$")
        val HTML_PATTERN = Regex("<[^>]*>|&[a-z]+;|&#[0-9]+;")
    }
}
