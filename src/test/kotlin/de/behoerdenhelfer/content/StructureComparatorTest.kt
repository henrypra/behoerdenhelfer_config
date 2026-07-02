package de.behoerdenhelfer.content

import de.behoerdenhelfer.content.model.FormDto
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Test
import kotlin.test.assertContains
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class StructureComparatorTest {
    private val sut = StructureComparator
    private val json = Json

    private fun form(raw: String): FormDto = json.decodeFromString(raw)

    @Test
    fun `firstDifference - when only titles differ then returns null`() {
        // Given
        val de = form(TestContent.FORM_DE)
        val en = form(TestContent.FORM_EN)

        // When
        val difference = sut.firstDifference(de, en)

        // Then
        assertNull(difference)
    }

    @Test
    fun `firstDifference - when a field type differs then reports the field`() {
        // Given
        val de = form(TestContent.FORM_DE)
        val en =
            form(TestContent.FORM_EN.replace("\"name\": \"txtName\", \"type\": \"input\"", "\"name\": \"txtName\", \"type\": \"date\""))

        // When
        val difference = sut.firstDifference(de, en)

        // Then
        assertNotNull(difference)
        assertContains(difference, "txtName")
    }

    @Test
    fun `firstDifference - when en is missing a child option then reports the parent`() {
        // Given
        val de = form(TestContent.FORM_DE)
        val en =
            form(
                TestContent.FORM_EN.replace(
                    """{ "name": "chkbA", "type": "option", "title": "A" },""",
                    "",
                ),
            )

        // When
        val difference = sut.firstDifference(de, en)

        // Then
        assertNotNull(difference)
        assertContains(difference, "multi_auswahl")
        assertContains(difference, "field count differs")
    }

    @Test
    fun `firstDifference - when show_when differs then reports a structural difference`() {
        // Given
        val de = form(TestContent.FORM_DE)
        val en = form(TestContent.FORM_EN.replace("\"value\": \"yes\"", "\"value\": \"no\""))

        // When
        val difference = sut.firstDifference(de, en)

        // Then
        assertNotNull(difference)
        assertContains(difference, "chkbB")
    }

    @Test
    fun `firstDifference - when pdfAssetPath differs then reports it`() {
        // Given
        val de = form(TestContent.FORM_DE)
        val en = form(TestContent.FORM_EN.replace("test.pdf", "other.pdf"))

        // When
        val difference = sut.firstDifference(de, en)

        // Then
        assertNotNull(difference)
        assertContains(difference, "pdfAssetPath")
    }
}
