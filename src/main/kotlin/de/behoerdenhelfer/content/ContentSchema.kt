package de.behoerdenhelfer.content

import de.behoerdenhelfer.content.model.FieldDto
import de.behoerdenhelfer.content.model.FormDto

/**
 * The content schema is the contract between the JSON files and the app's parser
 * (`FormResponseDto`). Level 1 = the original field types and keys. Level 2 =
 * the `input_row` segmented input group (the `input_type` keyboard hint shipped
 * with the same app update, but old clients ignore unknown keys, so it stays
 * consumable at level 1; likewise `segment_lengths` on an `input_row` only
 * upgrades the rendering, older clients keep the segmented row). Anything the
 * app must be updated for (a new field type,
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
     * The authorities bundle shipped with the level-2 app generation; older clients
     * never look at the manifest key, so the gate only matters for future changes
     * to the file format (`docs/android-content-contract.md` §1.1).
     */
    const val AUTHORITIES = 2

    /**
     * The Wegweiser file at `schema: 1`. The closed `documents`/`icon` sets are part of
     * that schema: adding a value means `schema: 2` in the file *and* raising this to 3,
     * so older apps keep the previous bundle instead of a file they cannot render
     * (`docs/backend-reply-2026-09-20.md`, item 3).
     */
    const val WEGWEISER = 2

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
