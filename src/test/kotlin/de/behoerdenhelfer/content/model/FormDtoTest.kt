package de.behoerdenhelfer.content.model

import de.behoerdenhelfer.content.TestContent
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class FormDtoTest {
    private val sut = Json

    @Test
    fun `decode - when parsing the fixture form then all conventions are mapped`() {
        // Given
        val raw = TestContent.FORM_DE

        // When
        val form = sut.decodeFromString<FormDto>(raw)

        // Then
        assertEquals("test.pdf", form.pdfAssetPath)
        assertEquals(1, form.pages.size)
        val fields = form.pages.single().fields
        assertEquals(FieldType.SECTION_HEADER, fields[0].type)
        assertEquals(ProfileKey.LAST_NAME, fields[1].profileKey)
        assertEquals("0", fields[2].yesOptionId)
        assertEquals(listOf(1), fields[2].hints)
        val option = fields[4].children[1]
        assertEquals(ShowWhenDto(field = "rbtnJaNein", value = "yes"), option.showWhen)
    }

    @Test
    fun `decode - when a field has an unknown type then parsing fails`() {
        // Given
        val raw = TestContent.FORM_DE.replace("\"type\": \"input\"", "\"type\": \"textarea\"")

        // When / Then
        assertFailsWith<SerializationException> { sut.decodeFromString<FormDto>(raw) }
    }

    @Test
    fun `decode - when the JSON has an unknown key then parsing fails`() {
        // Given
        val raw = TestContent.FORM_DE.replace("\"pdfAssetPath\"", "\"unknownKey\": 1, \"pdfAssetPath\"")

        // When / Then
        assertFailsWith<SerializationException> { sut.decodeFromString<FormDto>(raw) }
    }

    @Test
    fun `decode - when profile_key is not in the allowed set then parsing fails`() {
        // Given
        val raw = TestContent.FORM_DE.replace("\"profile_key\": \"LAST_NAME\"", "\"profile_key\": \"SHOE_SIZE\"")

        // When / Then
        assertFailsWith<SerializationException> { sut.decodeFromString<FormDto>(raw) }
    }
}
