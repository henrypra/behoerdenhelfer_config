package de.behoerdenhelfer.content

import de.behoerdenhelfer.content.model.FieldDto
import de.behoerdenhelfer.content.model.FormDto

/**
 * Checks that the de and en JSON of one form have identical structure — same field
 * names, types, children, show_when, etc. Only `title` texts may differ. This
 * prevents translation drift.
 */
object StructureComparator {
    /** Returns a human-readable description of the first structural difference, or null if identical. */
    fun firstDifference(
        de: FormDto,
        en: FormDto,
    ): String? {
        if (de.pdfAssetPath != en.pdfAssetPath) {
            return "pdfAssetPath differs: '${de.pdfAssetPath}' (de) vs '${en.pdfAssetPath}' (en)"
        }
        if (de.pages.size != en.pages.size) {
            return "page count differs: ${de.pages.size} (de) vs ${en.pages.size} (en)"
        }
        de.pages.zip(en.pages).forEach { (dePage, enPage) ->
            if (dePage.pageNumber != enPage.pageNumber) {
                return "pageNumber differs: ${dePage.pageNumber} (de) vs ${enPage.pageNumber} (en)"
            }
            firstFieldDifference(dePage.fields, enPage.fields, "page ${dePage.pageNumber}")?.let { return it }
        }
        return null
    }

    private fun firstFieldDifference(
        de: List<FieldDto>,
        en: List<FieldDto>,
        where: String,
    ): String? {
        if (de.size != en.size) {
            return "$where: field count differs: ${de.size} (de) vs ${en.size} (en)"
        }
        de.zip(en).forEach { (deField, enField) ->
            if (withoutText(deField) != withoutText(enField)) {
                return "$where: field '${deField.name}' (de) differs structurally from '${enField.name}' (en)"
            }
            firstFieldDifference(deField.children, enField.children, "$where > ${deField.name}")?.let { return it }
        }
        return null
    }

    private fun withoutText(field: FieldDto): FieldDto = field.copy(title = "", children = emptyList())
}
