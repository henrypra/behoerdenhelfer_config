package de.behoerdenhelfer.content.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class FormDto(
    val pages: List<PageDto>,
    val pdfAssetPath: String,
    val date: String? = null,
)

@Serializable
data class PageDto(
    val pageNumber: Int,
    val title: String,
    val fields: List<FieldDto>,
)

@Serializable
data class FieldDto(
    val name: String,
    val type: FieldType,
    val title: String,
    val children: List<FieldDto> = emptyList(),
    @SerialName("input_type") val inputType: InputType? = null,
    @SerialName("yes_option_id") val yesOptionId: String? = null,
    @SerialName("no_option_id") val noOptionId: String? = null,
    @SerialName("yes_no_option_hint") val yesNoOptionHint: String? = null,
    @SerialName("yes_no_option_selected") val yesNoOptionSelected: String? = null,
    @SerialName("profile_key") val profileKey: ProfileKey? = null,
    @SerialName("show_when") val showWhen: ShowWhenDto? = null,
    val hints: List<Int> = emptyList(),
    val exclusive: Boolean? = null,
)

/**
 * Every field type carries the content-schema level it was introduced in. When the
 * app gains a new field type, add it here with the *next* schema level — the
 * generator then computes each form's `minContentSchema` from the types it actually
 * uses, so old app versions know to skip content they cannot render.
 */
@Serializable
enum class FieldType(
    val sinceContentSchema: Int,
) {
    @SerialName("input")
    INPUT(1),

    /**
     * Segmented input group: several short `input` children rendered side by side
     * under one shared title. The group name is a synthetic UI id (never a PDF
     * field); the children carry the real PDF field names.
     */
    @SerialName("input_row")
    INPUT_ROW(2),

    @SerialName("date")
    DATE(1),

    @SerialName("radio")
    RADIO(1),

    @SerialName("checkbox")
    CHECKBOX(1),

    @SerialName("yesno")
    YESNO(1),

    @SerialName("multiselect")
    MULTISELECT(1),

    @SerialName("option")
    OPTION(1),

    @SerialName("section_header")
    SECTION_HEADER(1),
}

/**
 * Keyboard hint for `input` fields. Purely presentational: clients parse leniently
 * and fall back to the text keyboard, so using it does not raise `minContentSchema`.
 */
@Serializable
enum class InputType {
    @SerialName("number")
    NUMBER,

    @SerialName("phone")
    PHONE,
}

@Serializable
enum class ProfileKey {
    FIRST_NAME,
    LAST_NAME,
    BIRTH_DATE,
    BIRTH_PLACE,
    STREET,
    HOUSE_NUMBER,
    POSTAL_CODE,
    CITY,
}

@Serializable
data class ShowWhenDto(
    val field: String,
    val value: String,
)
