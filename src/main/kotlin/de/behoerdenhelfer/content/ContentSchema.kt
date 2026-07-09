package de.behoerdenhelfer.content

import de.behoerdenhelfer.content.model.FieldDto
import de.behoerdenhelfer.content.model.FormDto

/**
 * The content schema is the contract between the JSON files and the app's parser
 * (`FormResponseDto`). Level 1 = the original field types and keys. Level 2 =
 * the `input_row` segmented input group (the `input_type` keyboard hint shipped
 * with the same app update, but old clients ignore unknown keys, so it stays
 * consumable at level 1). Anything the app must be updated for (a new field type,
 * a new behavioral key) bumps [CURRENT] — and content *using* the new feature
 * automatically gets a higher `minContentSchema` in the manifest, so older app
 * versions skip it and keep their last understood copy.
 */
object ContentSchema {
    /** Highest schema level this repo can produce. */
    const val CURRENT = 2

    /** Hints catalogs are plain number/title/text since level 1. */
    const val HINTS = 1

    /**
     * The minimum schema level a client needs to render [form] — the maximum level
     * of any feature the form actually uses. A form that sticks to old field types
     * stays consumable by old clients even after the repo learns new ones.
     */
    fun requiredFor(form: FormDto): Int {
        var required = 1

        fun walk(field: FieldDto) {
            required = maxOf(required, field.type.sinceContentSchema)
            field.children.forEach(::walk)
        }
        form.pages.forEach { page -> page.fields.forEach(::walk) }
        return required
    }
}
