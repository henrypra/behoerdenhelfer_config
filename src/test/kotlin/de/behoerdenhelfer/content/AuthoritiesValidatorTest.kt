package de.behoerdenhelfer.content

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AuthoritiesValidatorTest {
    private val sut = Validator()

    private fun editBoth(
        contentDir: Path,
        transform: (String) -> String,
    ) {
        listOf("authorities.json", "authorities-en.json").forEach { name ->
            val file = contentDir.resolve("authorities/$name")
            Files.writeString(file, transform(Files.readString(file)))
        }
    }

    private fun violations(contentDir: Path): List<Violation> =
        sut.validate(ContentLayout(contentDir)).filter { it.scope == AuthoritiesValidator.SCOPE }

    @Test
    fun `validate - when the fixture bundle is valid then reports nothing`(
        @TempDir contentDir: Path,
    ) {
        // Given
        TestContent.writeValid(contentDir)

        // When / Then
        assertEquals(emptyList(), violations(contentDir))
    }

    @Test
    fun `validate - when there is no authorities folder then the bundle is simply absent`(
        @TempDir contentDir: Path,
    ) {
        // Given: the bundle is optional — a repo without it must validate like before
        TestContent.writeValid(contentDir)
        TestContent.deleteBundle(contentDir, "authorities")
        // wegweiser references authority ids, so it goes too
        TestContent.deleteBundle(contentDir, "wegweiser")

        // When / Then
        assertEquals(emptyList(), sut.validate(ContentLayout(contentDir)))
    }

    @Test
    fun `validate - when the en file is missing then reports it`(
        @TempDir contentDir: Path,
    ) {
        // Given
        TestContent.writeValid(contentDir)
        Files.delete(contentDir.resolve("authorities/authorities-en.json"))

        // When
        val violations = violations(contentDir)

        // Then
        assertEquals(1, violations.size)
        assertContains(violations.single().message, "authorities-en.json")
    }

    @Test
    fun `validate - when schema is not 1 then rejects it`(
        @TempDir contentDir: Path,
    ) {
        // Given
        TestContent.writeValid(contentDir)
        editBoth(contentDir) { it.replace("\"schema\": 1", "\"schema\": 2") }

        // When
        val violations = violations(contentDir)

        // Then
        assertEquals(2, violations.size)
        violations.forEach { assertContains(it.message, "schema is 2") }
    }

    @Test
    fun `validate - when an id has uppercase or a space then rejects it`(
        @TempDir contentDir: Path,
    ) {
        // Given
        TestContent.writeValid(contentDir)
        editBoth(contentDir) { it.replace("\"id\": \"buergeramt\"", "\"id\": \"Buerger amt\"") }

        // When
        val violations = violations(contentDir)

        // Then
        assertEquals(2, violations.size)
        violations.forEach { assertContains(it.message, "does not match") }
    }

    @Test
    fun `validate - when an id appears twice then reports the duplicate`(
        @TempDir contentDir: Path,
    ) {
        // Given
        TestContent.writeValid(contentDir)
        editBoth(contentDir) { it.replace("\"id\": \"buergeramt\"", "\"id\": \"testamt\"") }

        // When
        val violations = violations(contentDir)

        // Then
        assertEquals(2, violations.size)
        violations.forEach { assertContains(it.message, "duplicate id 'testamt'") }
    }

    @Test
    fun `validate - when handles names a form that is not in the repo then reports it`(
        @TempDir contentDir: Path,
    ) {
        // Given
        TestContent.writeValid(contentDir)
        editBoth(contentDir) { it.replace("\"handles\": [\"TESTFORM\"]", "\"handles\": [\"TESTFORM\", \"WOHNGELD\"]") }

        // When
        val violations = violations(contentDir)

        // Then
        assertEquals(2, violations.size)
        violations.forEach { assertContains(it.message, "unknown form id 'WOHNGELD'") }
    }

    @Test
    fun `validate - when the hotline contains letters then rejects it`(
        @TempDir contentDir: Path,
    ) {
        // Given
        TestContent.writeValid(contentDir)
        editBoth(contentDir) { it.replace("0800 4 5555 30", "0800 BAFOEG1") }

        // When
        val violations = violations(contentDir)

        // Then
        assertEquals(2, violations.size)
        violations.forEach { assertContains(it.message, "hotline") }
    }

    @Test
    fun `validate - when a URL is not https then rejects it`(
        @TempDir contentDir: Path,
    ) {
        // Given
        TestContent.writeValid(contentDir)
        editBoth(contentDir) { it.replace("https://example.org/suche", "http://example.org/suche") }

        // When
        val violations = violations(contentDir)

        // Then
        assertEquals(2, violations.size)
        violations.forEach { assertContains(it.message, "locatorUrl") }
    }

    @Test
    fun `validate - when a portalLabel has no portalUrl then rejects it`(
        @TempDir contentDir: Path,
    ) {
        // Given
        TestContent.writeValid(contentDir)
        editBoth(contentDir) { it.replace("\"portalUrl\": \"https://example.org/portal\",\n", "") }

        // When
        val violations = violations(contentDir)

        // Then
        assertEquals(2, violations.size)
        violations.forEach { assertContains(it.message, "portalLabel but no portalUrl") }
    }

    @Test
    fun `validate - when a text field contains HTML then rejects it`(
        @TempDir contentDir: Path,
    ) {
        // Given
        TestContent.writeValid(contentDir)
        editBoth(contentDir) { it.replace("\"Ausweis\"", "\"<b>Ausweis</b>\"").replace("\"ID card\"", "\"<b>ID card</b>\"") }

        // When
        val violations = violations(contentDir)

        // Then
        assertEquals(2, violations.size)
        violations.forEach { assertContains(it.message, "contains HTML") }
    }

    @Test
    fun `validate - when de and en list different ids then reports both directions`(
        @TempDir contentDir: Path,
    ) {
        // Given
        val en = contentDir.resolve("authorities/authorities-en.json")
        TestContent.writeValid(contentDir)
        Files.writeString(en, Files.readString(en).replace("\"id\": \"buergeramt\"", "\"id\": \"citizens_office\""))

        // When
        val violations = violations(contentDir)

        // Then
        assertEquals(2, violations.size)
        assertTrue(violations.any { "'buergeramt' exists in de but not in en" in it.message })
        assertTrue(violations.any { "'citizens_office' exists in en but not in de" in it.message })
    }

    @Test
    fun `validate - when a language-neutral field differs between de and en then reports drift`(
        @TempDir contentDir: Path,
    ) {
        // Given: en flips appointmentNeeded — only text may differ
        val en = contentDir.resolve("authorities/authorities-en.json")
        TestContent.writeValid(contentDir)
        Files.writeString(en, Files.readString(en).replace("\"appointmentNeeded\": true", "\"appointmentNeeded\": false"))

        // When
        val violations = violations(contentDir)

        // Then
        assertEquals(1, violations.size)
        assertContains(violations.single().message, "de/en drift for 'testamt'")
    }

    @Test
    fun `validate - when a key is misspelt then the strict parser rejects the file`(
        @TempDir contentDir: Path,
    ) {
        // Given: the app would silently ignore "hotlin", so the backend must not
        TestContent.writeValid(contentDir)
        editBoth(contentDir) { it.replace("\"hotline\"", "\"hotlin\"") }

        // When
        val violations = violations(contentDir)

        // Then
        assertEquals(2, violations.size)
        violations.forEach { assertContains(it.message, "cannot parse") }
    }

    @Test
    fun `validate - when a stray file sits in the authorities folder then reports it`(
        @TempDir contentDir: Path,
    ) {
        // Given
        TestContent.writeValid(contentDir)
        Files.writeString(contentDir.resolve("authorities/authorities-fr.json"), TestContent.AUTHORITIES_EN)

        // When
        val violations = violations(contentDir)

        // Then
        assertEquals(1, violations.size)
        assertContains(violations.single().message, "authorities-fr.json")
    }
}
