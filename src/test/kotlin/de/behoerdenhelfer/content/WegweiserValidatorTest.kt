package de.behoerdenhelfer.content

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WegweiserValidatorTest {
    private val sut = Validator()

    private fun editBoth(
        contentDir: Path,
        transform: (String) -> String,
    ) {
        listOf("wegweiser.json", "wegweiser-en.json").forEach { name ->
            val file = contentDir.resolve("wegweiser/$name")
            Files.writeString(file, transform(Files.readString(file)))
        }
    }

    private fun editEn(
        contentDir: Path,
        transform: (String) -> String,
    ) {
        val file = contentDir.resolve("wegweiser/wegweiser-en.json")
        Files.writeString(file, transform(Files.readString(file)))
    }

    private fun violations(contentDir: Path): List<Violation> =
        sut.validate(ContentLayout(contentDir)).filter { it.scope == WegweiserValidator.SCOPE }

    /** Asserts one violation per language file, both mentioning [fragment]. */
    private fun assertBothFiles(
        violations: List<Violation>,
        fragment: String,
    ) {
        assertEquals(2, violations.size, "expected one violation per language, got: $violations")
        violations.forEach { assertContains(it.message, fragment) }
    }

    @Test
    fun `validate - when the fixture bundle is valid then reports nothing`(
        @TempDir contentDir: Path,
    ) {
        TestContent.writeValid(contentDir)
        assertEquals(emptyList(), violations(contentDir))
    }

    @Test
    fun `validate - when there is no wegweiser folder then the bundle is simply absent`(
        @TempDir contentDir: Path,
    ) {
        TestContent.writeValid(contentDir)
        TestContent.deleteBundle(contentDir, "wegweiser")
        assertEquals(emptyList(), sut.validate(ContentLayout(contentDir)))
    }

    @Test
    fun `validate - when the en file is missing then reports it`(
        @TempDir contentDir: Path,
    ) {
        TestContent.writeValid(contentDir)
        Files.delete(contentDir.resolve("wegweiser/wegweiser-en.json"))
        val violations = violations(contentDir)
        assertEquals(1, violations.size)
        assertContains(violations.single().message, "wegweiser-en.json")
    }

    @Test
    fun `validate - when a field is null then rejects it with its path`(
        @TempDir contentDir: Path,
    ) {
        // Given: the contract says omit, never null
        TestContent.writeValid(contentDir)
        editBoth(contentDir) {
            it.replace("\"deadline\": \"Bald.\",", "\"deadline\": null,").replace("\"deadline\": \"Soon.\",", "\"deadline\": null,")
        }

        val violations = violations(contentDir)

        assertBothFiles(violations, "'$.benefits[0].deadline' is null")
    }

    @Test
    fun `validate - when a key is misspelt then the strict parser rejects the file`(
        @TempDir contentDir: Path,
    ) {
        TestContent.writeValid(contentDir)
        editBoth(contentDir) { it.replace("\"deadline\"", "\"deadlin\"") }
        assertBothFiles(violations(contentDir), "cannot parse")
    }

    @Test
    fun `validate - when a document is outside the closed set then rejects it`(
        @TempDir contentDir: Path,
    ) {
        TestContent.writeValid(contentDir)
        editBoth(contentDir) { it.replace("\"TAX_ID\"", "\"PASSPORT\"") }
        assertBothFiles(violations(contentDir), "unknown document 'PASSPORT'")
    }

    @Test
    fun `validate - when an icon is outside the closed set then rejects it`(
        @TempDir contentDir: Path,
    ) {
        TestContent.writeValid(contentDir)
        editBoth(contentDir) { it.replace("\"icon\": \"wallet\"", "\"icon\": \"money\"") }
        assertBothFiles(violations(contentDir), "unknown icon 'money'")
    }

    @Test
    fun `validate - when there are more than 12 situations then rejects it`(
        @TempDir contentDir: Path,
    ) {
        // Given: 2 fixture situations + 11 copies of the checklist one = 13
        TestContent.writeValid(contentDir)
        editBoth(contentDir) { json ->
            val list = json.substringAfter("\"situations\": [").substringBefore("\n  ],")
            val checklist = list.substring(list.lastIndexOf("    {\n      \"id\": \"s_liste\""))
            val copies = (1..11).joinToString(",\n") { checklist.replace("\"s_liste\"", "\"s_kopie$it\"") }
            json.replace(list, "$list,\n$copies")
        }
        assertBothFiles(violations(contentDir), "13 situations, at most 12")
    }

    @Test
    fun `validate - when a question has two fallbacks then rejects it`(
        @TempDir contentDir: Path,
    ) {
        // Given: the outer "Ja"/"Yes" answer also becomes a fallback
        TestContent.writeValid(contentDir)
        editBoth(contentDir) {
            it
                .replace(
                    "{ \"text\": \"Ja\", \"next\": { \"result\": \"r_app\" } },\n          {",
                    "{ \"text\": \"Ja\", \"next\": { \"result\": \"r_app\" }, \"fallback\": true },\n          {",
                ).replace(
                    "{ \"text\": \"Yes\", \"next\": { \"result\": \"r_app\" } },\n          {",
                    "{ \"text\": \"Yes\", \"next\": { \"result\": \"r_app\" }, \"fallback\": true },\n          {",
                )
        }
        assertBothFiles(violations(contentDir), "2 fallback answers, expected exactly 1")
    }

    @Test
    fun `validate - when a question has a single answer then rejects it`(
        @TempDir contentDir: Path,
    ) {
        TestContent.writeValid(contentDir)
        editBoth(contentDir) {
            it
                .replace("{ \"text\": \"Ja\", \"next\": { \"result\": \"r_app\" } },\n                ", "")
                .replace("{ \"text\": \"Yes\", \"next\": { \"result\": \"r_app\" } },\n                ", "")
        }
        assertBothFiles(violations(contentDir), "1 answers, expected 2–4")
    }

    @Test
    fun `validate - when a question sits deeper than 3 then rejects it`(
        @TempDir contentDir: Path,
    ) {
        // Given: the depth-2 question's fallback leads into two more questions → a question at depth 4
        TestContent.writeValid(contentDir)

        fun q(
            inner: String,
            text: String,
        ) =
            "{ \"question\": \"$text\", \"answers\": [ { \"text\": \"a\", \"next\": $inner }, { \"text\": \"b\", \"next\": $inner, \"fallback\": true } ] }"
        val leaf = "{ \"result\": \"r_advice\" }"
        editBoth(contentDir) {
            it.replace(
                "\"next\": $leaf, \"fallback\": true }\n              ]",
                "\"next\": ${q(q(leaf, "Tiefer?"), "Tief?")}, \"fallback\": true }\n              ]",
            )
        }
        // Then: the depth-4 question appears under both answers of the depth-3 one, per language
        val violations = violations(contentDir)
        assertEquals(4, violations.size, violations.toString())
        violations.forEach { assertContains(it.message, "depth 4, deeper than 3") }
    }

    @Test
    fun `validate - when a result is not reachable from any situation then reports the orphan`(
        @TempDir contentDir: Path,
    ) {
        TestContent.writeValid(contentDir)
        editBoth(contentDir) {
            it.replace(
                "\"extraAuthorityId\": \"buergeramt\" }",
                "\"extraAuthorityId\": \"buergeramt\" }, { \"id\": \"r_verwaist\", \"benefits\": [\"b_app\"], \"steps\": [\"x\"] }",
            )
        }
        assertBothFiles(violations(contentDir), "result 'r_verwaist' is not reachable")
    }

    @Test
    fun `validate - when a benefit is referenced nowhere then reports the orphan`(
        @TempDir contentDir: Path,
    ) {
        // Given: b_online is referenced only by the checklist item — drop that reference
        TestContent.writeValid(contentDir)
        editBoth(contentDir) { it.replace(", \"benefitId\": \"b_online\"", "") }
        assertBothFiles(violations(contentDir), "benefit 'b_online' is not referenced")
    }

    @Test
    fun `validate - when an app benefit has no formId then rejects it`(
        @TempDir contentDir: Path,
    ) {
        TestContent.writeValid(contentDir)
        editBoth(contentDir) { it.replace("\"apply\": \"app\", \"formId\": \"TESTFORM\",", "\"apply\": \"app\",") }
        assertBothFiles(violations(contentDir), "apply 'app' but no formId")
    }

    @Test
    fun `validate - when a formId is not a form of this repo then rejects it`(
        @TempDir contentDir: Path,
    ) {
        TestContent.writeValid(contentDir)
        editBoth(contentDir) { it.replace("\"formId\": \"TESTFORM\"", "\"formId\": \"WOHNGELD\"") }
        assertBothFiles(violations(contentDir), "unknown form id 'WOHNGELD'")
    }

    @Test
    fun `validate - when an online benefit has no portalUrl then rejects it`(
        @TempDir contentDir: Path,
    ) {
        TestContent.writeValid(contentDir)
        editBoth(contentDir) { it.replace(", \"portalUrl\": \"https://example.org/portal\"", "") }
        assertBothFiles(violations(contentDir), "apply 'online' but no portalUrl")
    }

    @Test
    fun `validate - when an authorityId is not in the authorities bundle then rejects it`(
        @TempDir contentDir: Path,
    ) {
        TestContent.writeValid(contentDir)
        editBoth(contentDir) { it.replace("\"extraAuthorityId\": \"buergeramt\"", "\"extraAuthorityId\": \"rathaus\"") }
        assertBothFiles(violations(contentDir), "unknown authority 'rathaus'")
    }

    @Test
    fun `validate - when a checklist item carries both references then rejects it`(
        @TempDir contentDir: Path,
    ) {
        TestContent.writeValid(contentDir)
        editBoth(contentDir) { it.replace(", \"benefitId\": \"b_online\"", ", \"benefitId\": \"b_online\", \"authorityId\": \"testamt\"") }
        assertBothFiles(violations(contentDir), "both authorityId and benefitId")
    }

    @Test
    fun `validate - when a synonym term is uppercase or repeated then rejects it`(
        @TempDir contentDir: Path,
    ) {
        TestContent.writeValid(contentDir)
        editBoth(contentDir) { it.replace("[\"amt\"]", "[\"Amt\", \"antrag\"]").replace("[\"office\"]", "[\"Office\", \"application\"]") }
        val violations = violations(contentDir)
        assertEquals(4, violations.size, violations.toString())
        assertTrue(violations.any { "'Amt' is not lowercase" in it.message })
        assertTrue(violations.any { "'antrag' appears more than once" in it.message })
    }

    @Test
    fun `validate - when only synonym terms differ between de and en then accepts it`(
        @TempDir contentDir: Path,
    ) {
        // Given: the fixture already has German and English terms — that is the agreed shape
        TestContent.writeValid(contentDir)
        assertEquals(emptyList(), violations(contentDir))
    }

    @Test
    fun `validate - when a language-neutral field differs between de and en then reports the path`(
        @TempDir contentDir: Path,
    ) {
        // Given: en moves the fallback to another answer — structure, not text
        TestContent.writeValid(contentDir)
        editEn(contentDir) {
            it
                .replace(
                    "{ \"text\": \"I don't know\", \"next\": { \"result\": \"r_advice\" }, \"fallback\": true }",
                    "{ \"text\": \"I don't know\", \"next\": { \"result\": \"r_app\" }, \"fallback\": true }",
                )
        }
        val violations = violations(contentDir)
        assertEquals(1, violations.size, violations.toString())
        assertContains(violations.single().message, "de/en drift at $.situations[0].root.answers[2].next.result")
    }

    @Test
    fun `validate - when a text contains Markdown then rejects it`(
        @TempDir contentDir: Path,
    ) {
        TestContent.writeValid(contentDir)
        editBoth(contentDir) {
            it.replace("\"Konto eröffnen\"", "\"**Konto** eröffnen\"").replace("\"Open a bank account\"", "\"**Open** a bank account\"")
        }
        assertBothFiles(violations(contentDir), "contains Markdown")
    }

    @Test
    fun `validate - when a stray file sits in the wegweiser folder then reports it`(
        @TempDir contentDir: Path,
    ) {
        TestContent.writeValid(contentDir)
        Files.writeString(contentDir.resolve("wegweiser/notes.md"), "todo")
        val violations = violations(contentDir)
        assertEquals(1, violations.size)
        assertContains(violations.single().message, "notes.md")
    }
}
