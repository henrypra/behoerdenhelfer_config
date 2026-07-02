package de.behoerdenhelfer.content

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.test.assertContains
import kotlin.test.assertTrue

class PdfFieldReaderTest {
    private val sut = PdfFieldReader

    @Test
    fun `fieldNames - when PDF has flat AcroForm fields then returns their names`(
        @TempDir tempDir: Path,
    ) {
        // Given
        val pdf = tempDir.resolve("flat.pdf")
        TestContent.writePdf(pdf, "txtfPersonVorname", "rbtnWohnsitz")

        // When
        val names = sut.fieldNames(pdf)

        // Then
        assertContains(names, "txtfPersonVorname")
        assertContains(names, "rbtnWohnsitz")
    }

    @Test
    fun `fieldNames - when PDF has a hierarchical field tree then walks it and returns qualified and partial names`(
        @TempDir tempDir: Path,
    ) {
        // Given
        val pdf = tempDir.resolve("tree.pdf")
        TestContent.writeHierarchicalPdf(pdf, "topmostSubform", "KG-Nr", "Telefon")

        // When
        val names = sut.fieldNames(pdf)

        // Then
        assertContains(names, "topmostSubform.KG-Nr")
        assertContains(names, "topmostSubform.Telefon")
        assertContains(names, "KG-Nr")
    }

    @Test
    fun `fieldNames - when PDF has no AcroForm then returns empty set`(
        @TempDir tempDir: Path,
    ) {
        // Given
        val pdf = tempDir.resolve("flattened.pdf")
        TestContent.writePdf(pdf)

        // When
        val names = sut.fieldNames(pdf)

        // Then
        assertTrue(names.isEmpty())
    }

    @Test
    fun `fieldNames - when PDF is owner-password encrypted then still reads the fields`(
        @TempDir tempDir: Path,
    ) {
        // Given
        val pdf = tempDir.resolve("encrypted.pdf")
        TestContent.writePdf(pdf, "txtName", ownerPassword = "owner-secret")

        // When
        val names = sut.fieldNames(pdf)

        // Then
        assertContains(names, "txtName")
    }
}
