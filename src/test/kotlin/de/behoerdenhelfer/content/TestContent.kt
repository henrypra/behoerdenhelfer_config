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

    /**
     * Wraps the txtName input in an `input_row` group (schema level 2): synthetic
     * group name absent from the PDF, the child keeps the real PDF field name and
     * carries an `input_type` keyboard hint. Works on the de and en fixture alike.
     */
    fun withInputRow(formJson: String): String =
        formJson.replace(
            """{ "name": "txtName", "type": "input", "title": "Name", "profile_key": "LAST_NAME" }""",
            """
            {
              "name": "group_name", "type": "input_row", "title": "Name",
              "children": [
                { "name": "txtName", "type": "input", "title": "Teil 1", "input_type": "number" }
              ]
            }
            """.trimIndent(),
        )

    /**
     * Like [withInputRow], but the group is one value split over two PDF blocks
     * (`segment_lengths`), the way a Steuer-ID is spread across comb fields.
     */
    fun withSegmentedInputRow(formJson: String): String =
        formJson.replace(
            """{ "name": "txtName", "type": "input", "title": "Name", "profile_key": "LAST_NAME" }""",
            """
            {
              "name": "group_name", "type": "input_row", "title": "Name",
              "input_type": "number", "segment_lengths": [2, 3],
              "children": [
                { "name": "txtName", "type": "input", "title": "Teil 1", "input_type": "number" },
                { "name": "rbtnJaNein", "type": "input", "title": "Teil 2", "input_type": "number" }
              ]
            }
            """.trimIndent(),
        )

    // language=json
    val HINTS_DE = """{ "hints": [ { "number": 1, "title": "Konto", "text": "Hinweistext" } ] }"""

    // language=json
    val HINTS_EN = """{ "hints": [ { "number": 1, "title": "Account", "text": "Hint text" } ] }"""

    /** Two office types: one fully populated (handles the fixture form), one with only the required fields. */
    val AUTHORITIES_DE =
        """
        {
          "schema": 1,
          "authorities": [
            {
              "id": "testamt",
              "name": "Testamt",
              "does": "Bearbeitet das Testformular.",
              "doesNot": "Kein Kindergeld – das ist die Familienkasse.",
              "handles": ["TESTFORM"],
              "hotline": "0800 4 5555 30",
              "portalUrl": "https://example.org/portal",
              "portalLabel": "example.org",
              "appointmentNeeded": true,
              "bring": ["Ausweis"],
              "rights": ["Eine Begleitperson mitbringen"],
              "howToFindLocal": "Suchen Sie nach „Testamt“ und Ihrer Stadt.",
              "locatorUrl": "https://example.org/suche"
            },
            { "id": "buergeramt", "name": "Bürgeramt", "does": "Meldet Wohnungen an." }
          ]
        }
        """.trimIndent()

    val AUTHORITIES_EN =
        AUTHORITIES_DE
            .replace("Testamt\"", "Test Office\"")
            .replace("Bearbeitet das Testformular.", "Handles the test form.")
            .replace("Kein Kindergeld – das ist die Familienkasse.", "No child benefit – that is the Familienkasse.")
            .replace("\"Ausweis\"", "\"ID card\"")
            .replace("Eine Begleitperson mitbringen", "Bring a companion")
            .replace("Suchen Sie nach „Testamt“ und Ihrer Stadt.", "Search for “Testamt” and your city.")
            .replace("\"Bürgeramt\"", "\"Citizens' Office\"")
            .replace("Meldet Wohnungen an.", "Registers addresses.")

    /**
     * A minimal Wegweiser file exercising every node shape: a question tree (with one
     * nested question), a result reference, a checklist with each item kind, an
     * `apply: app` benefit on the fixture form, an `online` one, an advice-only result and
     * a synonym per target space. Every result and benefit is referenced.
     */
    val WEGWEISER_DE =
        """
        {
          "schema": 1,
          "benefits": [
            {
              "id": "b_app", "name": "Testleistung", "what": "Wird in der App beantragt.",
              "authorityId": "testamt", "apply": "app", "formId": "TESTFORM",
              "deadline": "Bald.", "documents": ["ID_CARD", "TAX_ID"]
            },
            {
              "id": "b_online", "name": "Onlineleistung", "what": "Wird online beantragt.",
              "authorityId": "buergeramt", "apply": "online", "portalUrl": "https://example.org/portal"
            }
          ],
          "results": [
            { "id": "r_app", "benefits": ["b_app"], "steps": ["Antrag ausfüllen", "Abgeben"] },
            { "id": "r_advice", "benefits": [], "steps": ["Beim Bürgeramt fragen"], "extraAuthorityId": "buergeramt" }
          ],
          "situations": [
            {
              "id": "s_frage", "title": "Eine Frage", "icon": "wallet",
              "root": {
                "question": "Brauchen Sie die Leistung?",
                "answers": [
                  { "text": "Ja", "next": { "result": "r_app" } },
                  {
                    "text": "Vielleicht",
                    "next": {
                      "question": "Sicher?",
                      "answers": [
                        { "text": "Ja", "next": { "result": "r_app" } },
                        { "text": "Nein", "next": { "result": "r_advice" }, "fallback": true }
                      ]
                    }
                  },
                  { "text": "Weiß ich nicht", "next": { "result": "r_advice" }, "fallback": true }
                ]
              }
            },
            {
              "id": "s_liste", "title": "Eine Liste", "icon": "globe",
              "root": {
                "checklist": [
                  { "text": "Wohnung anmelden", "authorityId": "buergeramt" },
                  { "text": "Onlineleistung beantragen", "benefitId": "b_online" },
                  { "text": "Konto eröffnen" }
                ]
              }
            }
          ],
          "synonyms": [
            { "terms": ["leistung", "antrag"], "target": { "benefit": "b_app" } },
            { "terms": ["amt"], "target": { "authority": "buergeramt" } },
            { "terms": ["liste"], "target": { "situation": "s_liste" } }
          ]
        }
        """.trimIndent()

    val WEGWEISER_EN =
        WEGWEISER_DE
            .replace("Testleistung", "Test benefit")
            .replace("Wird in der App beantragt.", "Applied for in the app.")
            .replace("Onlineleistung\"", "Online benefit\"")
            .replace("Wird online beantragt.", "Applied for online.")
            .replace("\"Bald.\"", "\"Soon.\"")
            .replace("\"Antrag ausfüllen\", \"Abgeben\"", "\"Fill in the form\", \"Hand it in\"")
            .replace("Beim Bürgeramt fragen", "Ask the citizens' office")
            .replace("Eine Frage", "A question")
            .replace("Brauchen Sie die Leistung?", "Do you need the benefit?")
            .replace("\"Ja\"", "\"Yes\"")
            .replace("\"Vielleicht\"", "\"Maybe\"")
            .replace("\"Sicher?\"", "\"Sure?\"")
            .replace("\"Nein\"", "\"No\"")
            .replace("Weiß ich nicht", "I don't know")
            .replace("Eine Liste", "A list")
            .replace("Wohnung anmelden", "Register your address")
            .replace("Onlineleistung beantragen", "Apply for the online benefit")
            .replace("Konto eröffnen", "Open a bank account")
            .replace("[\"leistung\", \"antrag\"]", "[\"benefit\", \"application\"]")
            .replace("[\"amt\"]", "[\"office\"]")
            .replace("[\"liste\"]", "[\"list\"]")

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
        val authoritiesDir = contentDir.resolve("authorities")
        Files.createDirectories(authoritiesDir)
        Files.writeString(authoritiesDir.resolve("authorities.json"), AUTHORITIES_DE)
        Files.writeString(authoritiesDir.resolve("authorities-en.json"), AUTHORITIES_EN)
        val wegweiserDir = contentDir.resolve("wegweiser")
        Files.createDirectories(wegweiserDir)
        Files.writeString(wegweiserDir.resolve("wegweiser.json"), WEGWEISER_DE)
        Files.writeString(wegweiserDir.resolve("wegweiser-en.json"), WEGWEISER_EN)
        return ContentLayout(contentDir)
    }

    /** Removes one optional bundle folder (`authorities` or `wegweiser`) from a written tree. */
    fun deleteBundle(
        contentDir: Path,
        name: String,
    ) {
        Files.walk(contentDir.resolve(name)).use { paths ->
            paths.sorted(Comparator.reverseOrder()).forEach(Files::delete)
        }
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
