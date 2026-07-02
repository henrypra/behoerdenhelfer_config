package de.behoerdenhelfer.content

import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.pdmodel.encryption.AccessPermission
import org.apache.pdfbox.pdmodel.encryption.StandardProtectionPolicy
import org.apache.pdfbox.pdmodel.interactive.form.PDAcroForm
import org.apache.pdfbox.pdmodel.interactive.form.PDField
import org.apache.pdfbox.pdmodel.interactive.form.PDNonTerminalField
import org.apache.pdfbox.pdmodel.interactive.form.PDTextField
import java.nio.file.Files
import java.nio.file.Path

/**
 * Builds a tiny valid content tree (one form + one hints catalog) that individual
 * tests break in targeted ways. The form covers every field-name convention:
 * `ui_` helper, synthetic multiselect parent, section header, show_when, hints
 * reference.
 */
object TestContent {
    const val FORM_ID = "TESTFORM"
    const val HINTS_ID = "TESTHINTS"

    val PDF_FIELDS = arrayOf("txtName", "rbtnJaNein", "chkbA", "chkbB")

    // language=json
    val FORM_DE =
        """
        {
          "pages": [
            {
              "pageNumber": 1,
              "title": "Seite 1",
              "fields": [
                { "name": "section_header_test", "type": "section_header", "title": "Abschnitt" },
                { "name": "txtName", "type": "input", "title": "Name", "profile_key": "LAST_NAME" },
                {
                  "name": "rbtnJaNein", "type": "yesno", "title": "Ja oder nein?",
                  "yes_option_id": "0", "no_option_id": "1", "hints": [1]
                },
                { "name": "ui_helper", "type": "yesno", "title": "App-interner Helfer" },
                {
                  "name": "multi_auswahl", "type": "multiselect", "title": "Auswahl",
                  "children": [
                    { "name": "chkbA", "type": "option", "title": "A" },
                    {
                      "name": "chkbB", "type": "option", "title": "B",
                      "show_when": { "field": "rbtnJaNein", "value": "yes" }
                    }
                  ]
                }
              ]
            }
          ],
          "pdfAssetPath": "test.pdf"
        }
        """.trimIndent()

    val FORM_EN =
        FORM_DE
            .replace("Seite 1", "Page 1")
            .replace("Abschnitt", "Section")
            .replace("Ja oder nein?", "Yes or no?")
            .replace("App-interner Helfer", "App-internal helper")
            .replace("\"title\": \"Auswahl\"", "\"title\": \"Selection\"")

    // language=json
    val HINTS_DE = """{ "hints": [ { "number": 1, "title": "Konto", "text": "Hinweistext" } ] }"""

    // language=json
    val HINTS_EN = """{ "hints": [ { "number": 1, "title": "Account", "text": "Hint text" } ] }"""

    /** Writes a complete valid content tree under [contentDir] and returns its layout. */
    fun writeValid(contentDir: Path): ContentLayout {
        val formDir = contentDir.resolve("forms/testform")
        Files.createDirectories(formDir)
        Files.writeString(formDir.resolve("form_testform.json"), FORM_DE)
        Files.writeString(formDir.resolve("form_testform-en.json"), FORM_EN)
        writePdf(formDir.resolve("test.pdf"), *PDF_FIELDS)
        val hintsDir = contentDir.resolve("hints")
        Files.createDirectories(hintsDir)
        Files.writeString(hintsDir.resolve("hints_testhints.json"), HINTS_DE)
        Files.writeString(hintsDir.resolve("hints_testhints-en.json"), HINTS_EN)
        return ContentLayout(contentDir)
    }

    /** Writes a PDF whose AcroForm contains the given flat field names; none = flattened print copy. */
    fun writePdf(
        target: Path,
        vararg fieldNames: String,
        ownerPassword: String? = null,
    ) {
        PDDocument().use { document ->
            document.addPage(PDPage())
            if (fieldNames.isNotEmpty()) {
                val acroForm = PDAcroForm(document)
                document.documentCatalog.acroForm = acroForm
                acroForm.fields =
                    fieldNames.map { name ->
                        PDTextField(acroForm).apply { partialName = name }
                    }
            }
            if (ownerPassword != null) {
                document.protect(StandardProtectionPolicy(ownerPassword, "", AccessPermission()))
            }
            document.save(target.toFile())
        }
    }

    /** Writes a PDF with an XFA-style hierarchy: parent[0] > child names. */
    fun writeHierarchicalPdf(
        target: Path,
        parentName: String,
        vararg childNames: String,
    ) {
        PDDocument().use { document ->
            document.addPage(PDPage())
            val acroForm = PDAcroForm(document)
            document.documentCatalog.acroForm = acroForm
            val parent = PDNonTerminalField(acroForm)
            parent.partialName = parentName
            parent.children =
                childNames.map<String, PDField> { name ->
                    PDTextField(acroForm).apply { partialName = name }
                }
            acroForm.fields = listOf(parent)
            document.save(target.toFile())
        }
    }
}
