package de.behoerdenhelfer.content

import de.behoerdenhelfer.content.model.FieldType
import de.behoerdenhelfer.content.model.FormDto
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ContentSchemaTest {
    private val sut = ContentSchema

    @Test
    fun `requiredFor - when a form uses only level 1 field types then requires schema 1`() {
        // Given
        val form = Json.decodeFromString<FormDto>(TestContent.FORM_DE)

        // When
        val required = sut.requiredFor(form)

        // Then
        assertEquals(1, required)
    }

    @Test
    fun `CURRENT - always covers every declared field type level`() {
        // Given
        val highestFieldTypeLevel = FieldType.entries.maxOf { it.sinceContentSchema }

        // When / Then: a type above CURRENT could never be published correctly
        assertTrue(sut.CURRENT >= highestFieldTypeLevel)
        assertTrue(sut.CURRENT >= sut.HINTS)
    }
}
