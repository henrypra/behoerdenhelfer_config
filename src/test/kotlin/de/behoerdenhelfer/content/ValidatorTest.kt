package de.behoerdenhelfer.content

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ValidatorTest {
    private val sut = Validator()

    private fun edit(
        file: Path,
        transform: (String) -> String,
    ) {
        Files.writeString(file, transform(Files.readString(file)))
    }

    @Test
    fun `validate - when content is valid then returns no violations`(
        @TempDir contentDir: Path,
    ) {
        // Given
        val layout = TestContent.writeValid(contentDir)

        // When
        val violations = sut.validate(layout)

        // Then
        assertEquals(emptyList(), violations)
    }

    @Test
    fun `validate - when an en hints file has no de counterpart then reports the orphan`(
        @TempDir contentDir: Path,
    ) {
        // Given: an -en catalog alone is undiscoverable and would silently never publish
        val layout = TestContent.writeValid(contentDir)
        Files.writeString(contentDir.resolve("hints/hints_orphan-en.json"), TestContent.HINTS_EN)

        // When
        val violations = sut.validate(layout)

        // Then
        assertEquals(1, violations.size)
        assertContains(violations.single().message, "hints_orphan-en.json")
    }

    @Test
    fun `validate - when the en form file is missing then reports it`(
        @TempDir contentDir: Path,
    ) {
        // Given
        val layout = TestContent.writeValid(contentDir)
        Files.delete(contentDir.resolve("forms/testform/form_testform-en.json"))

        // When
        val violations = sut.validate(layout)

        // Then
        assertEquals(1, violations.size)
        assertContains(violations.single().message, "form_testform-en.json")
    }

    @Test
    fun `validate - when the PDF is a flattened print copy then rejects it`(
        @TempDir contentDir: Path,
    ) {
        // Given
        val layout = TestContent.writeValid(contentDir)
        TestContent.writePdf(contentDir.resolve("forms/testform/test.pdf"))

        // When
        val violations = sut.validate(layout)

        // Then
        assertEquals(1, violations.size)
        assertContains(violations.single().message, "flattened")
    }

    @Test
    fun `validate - when a JSON field name is not in the PDF then reports the field`(
        @TempDir contentDir: Path,
    ) {
        // Given
        val layout = TestContent.writeValid(contentDir)
        listOf("form_testform.json", "form_testform-en.json").forEach { name ->
            edit(contentDir.resolve("forms/testform/$name")) { it.replace("txtName", "txtNmae") }
        }

        // When
        val violations = sut.validate(layout)

        // Then
        assertEquals(1, violations.size)
        assertContains(violations.single().message, "txtNmae")
    }

    @Test
    fun `validate - when ui_ fields and synthetic parents are absent from the PDF then accepts them`(
        @TempDir contentDir: Path,
    ) {
        // Given: fixture PDF contains neither ui_helper nor multi_auswahl nor section_header_test
        val layout = TestContent.writeValid(contentDir)

        // When
        val violations = sut.validate(layout)

        // Then
        assertEquals(emptyList(), violations)
    }

    @Test
    fun `validate - when an input_row group name is absent from the PDF then accepts it`(
        @TempDir contentDir: Path,
    ) {
        // Given: the group name is synthetic, only the child targets the PDF
        val layout = TestContent.writeValid(contentDir)
        listOf("form_testform.json", "form_testform-en.json").forEach { name ->
            edit(contentDir.resolve("forms/testform/$name"), TestContent::withInputRow)
        }

        // When
        val violations = sut.validate(layout)

        // Then
        assertEquals(emptyList(), violations)
    }

    @Test
    fun `validate - when an input_row child is not an input then reports it`(
        @TempDir contentDir: Path,
    ) {
        // Given
        val layout = TestContent.writeValid(contentDir)
        listOf("form_testform.json", "form_testform-en.json").forEach { name ->
            edit(contentDir.resolve("forms/testform/$name")) {
                TestContent.withInputRow(it).replace(
                    "\"type\": \"input\", \"title\": \"Teil 1\"",
                    "\"type\": \"date\", \"title\": \"Teil 1\"",
                )
            }
        }

        // When
        val violations = sut.validate(layout)

        // Then
        assertEquals(1, violations.size)
        assertContains(violations.single().message, "non-input child 'txtName'")
    }

    @Test
    fun `validate - when pdfAssetPath names a missing file then reports it`(
        @TempDir contentDir: Path,
    ) {
        // Given
        val layout = TestContent.writeValid(contentDir)
        listOf("form_testform.json", "form_testform-en.json").forEach { name ->
            edit(contentDir.resolve("forms/testform/$name")) { it.replace("test.pdf", "missing.pdf") }
        }

        // When
        val violations = sut.validate(layout)

        // Then
        assertEquals(1, violations.size)
        assertContains(violations.single().message, "missing.pdf")
    }

    @Test
    fun `validate - when show_when references an unknown field then reports it`(
        @TempDir contentDir: Path,
    ) {
        // Given
        val layout = TestContent.writeValid(contentDir)
        listOf("form_testform.json", "form_testform-en.json").forEach { name ->
            edit(contentDir.resolve("forms/testform/$name")) {
                it.replace("\"field\": \"rbtnJaNein\"", "\"field\": \"rbtnGibtEsNicht\"")
            }
        }

        // When
        val violations = sut.validate(layout)

        // Then
        assertEquals(1, violations.size)
        assertContains(violations.single().message, "rbtnGibtEsNicht")
    }

    @Test
    fun `validate - when a field references an unknown hint number then reports it`(
        @TempDir contentDir: Path,
    ) {
        // Given
        val layout = TestContent.writeValid(contentDir)
        listOf("form_testform.json", "form_testform-en.json").forEach { name ->
            edit(contentDir.resolve("forms/testform/$name")) { it.replace("\"hints\": [1]", "\"hints\": [99]") }
        }

        // When
        val violations = sut.validate(layout)

        // Then
        assertEquals(1, violations.size)
        assertContains(violations.single().message, "unknown hint 99")
    }

    @Test
    fun `validate - when de and en drift structurally then reports the drift`(
        @TempDir contentDir: Path,
    ) {
        // Given
        val layout = TestContent.writeValid(contentDir)
        edit(contentDir.resolve("forms/testform/form_testform-en.json")) {
            it.replace("\"name\": \"txtName\", \"type\": \"input\"", "\"name\": \"txtName\", \"type\": \"date\"")
        }

        // When
        val violations = sut.validate(layout)

        // Then
        assertEquals(1, violations.size)
        assertContains(violations.single().message, "structural drift")
    }

    @Test
    fun `validate - when hints catalogs list different numbers then reports it`(
        @TempDir contentDir: Path,
    ) {
        // Given
        val layout = TestContent.writeValid(contentDir)
        edit(contentDir.resolve("hints/hints_testhints-en.json")) { it.replace("\"number\": 1", "\"number\": 2") }

        // When
        val violations = sut.validate(layout)

        // Then
        assertTrue(violations.any { "different hint numbers" in it.message })
    }

    @Test
    fun `validate - when a form folder is missing its bundle files then reports each missing file`(
        @TempDir contentDir: Path,
    ) {
        // Given: every folder under forms/ is a bundle — an empty one is broken, not ignored
        val layout = TestContent.writeValid(contentDir)
        Files.createDirectories(contentDir.resolve("forms/elterngeld"))

        // When
        val violations = sut.validate(layout)

        // Then: de and en form files are both reported missing
        assertEquals(2, violations.size)
        assertTrue(violations.all { "elterngeld" in it.message && "missing" in it.message })
    }

    @Test
    fun `validate - when a form JSON does not parse then reports it instead of crashing`(
        @TempDir contentDir: Path,
    ) {
        // Given
        val layout = TestContent.writeValid(contentDir)
        Files.writeString(contentDir.resolve("forms/testform/form_testform.json"), "{ not json")

        // When
        val violations = sut.validate(layout)

        // Then
        assertEquals(1, violations.size)
        assertContains(violations.single().message, "cannot parse")
    }
}
