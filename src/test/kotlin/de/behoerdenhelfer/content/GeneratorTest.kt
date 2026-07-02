package de.behoerdenhelfer.content

import de.behoerdenhelfer.content.model.FileEntry
import de.behoerdenhelfer.content.model.LatestPointer
import de.behoerdenhelfer.content.model.Manifest
import de.behoerdenhelfer.content.model.ManifestFormEntry
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class GeneratorTest {
    private val fixedInstant = Instant.parse("2026-07-02T12:00:00Z")
    private val sut = Generator(clock = { fixedInstant })

    @Test
    fun `generate - when content is valid then writes immutable version folders and a verifying manifest`(
        @TempDir tempDir: Path,
    ) {
        // Given
        val layout = TestContent.writeValid(tempDir.resolve("content").also(Files::createDirectories))
        val distDir = tempDir.resolve("dist")

        // When
        val result = sut.generate(layout, distDir)

        // Then
        val manifest = assertIs<GenerateResult.Success>(result).manifest
        assertEquals("2026-07-02T12:00:00Z", manifest.generatedAt)
        assertEquals(1, manifest.config)
        val manifestBytes = Files.readString(distDir.resolve("config/1.json"))
        assertContains(manifestBytes, "\"schemaVersion\": 1")
        assertContains(manifestBytes, "\"config\": 1")
        assertContains(Files.readString(distDir.resolve("latest.json")), "\"configPath\": \"config/1.json\"")
        val form = manifest.forms.single()
        assertEquals(TestContent.FORM_ID, form.formId)
        assertEquals(1, form.minContentSchema)
        assertEquals("forms/testform/1/de/form.json", form.jsonDe.path)
        assertEquals("forms/testform/1/en/form.json", form.jsonEn.path)
        assertEquals("forms/testform/1/test.pdf", form.pdf.path)
        val hints = manifest.hints.single()
        assertEquals("hints/testhints/1/de/hints.json", hints.jsonDe.path)
        assertEquals("hints/testhints/1/en/hints.json", hints.jsonEn.path)
        listOf(form.jsonDe, form.jsonEn, form.pdf, hints.jsonDe, hints.jsonEn).forEach { entry ->
            val file = distDir.resolve(entry.path)
            assertTrue(Files.isRegularFile(file), "missing ${entry.path}")
            assertEquals(entry.sha256, Sha256.of(file), "sha256 mismatch for ${entry.path}")
            assertEquals(entry.bytes, Files.size(file), "byte size mismatch for ${entry.path}")
        }
    }

    @Test
    fun `generate - when run twice on the same input then the manifest is byte-identical`(
        @TempDir tempDir: Path,
    ) {
        // Given
        val layout = TestContent.writeValid(tempDir.resolve("content").also(Files::createDirectories))

        // When
        sut.generate(layout, tempDir.resolve("dist1"))
        sut.generate(layout, tempDir.resolve("dist2"))

        // Then
        assertEquals(
            Files.readString(tempDir.resolve("dist1/config/1.json")),
            Files.readString(tempDir.resolve("dist2/config/1.json")),
        )
    }

    @Test
    fun `generate - when content is invalid then fails and writes nothing`(
        @TempDir tempDir: Path,
    ) {
        // Given
        val contentDir = tempDir.resolve("content").also(Files::createDirectories)
        val layout = TestContent.writeValid(contentDir)
        TestContent.writePdf(contentDir.resolve("forms/testform/test.pdf")) // flattened
        val distDir = tempDir.resolve("dist")

        // When
        val result = sut.generate(layout, distDir)

        // Then
        assertIs<GenerateResult.Failure>(result)
        assertTrue(Files.notExists(distDir))
    }

    @Test
    fun `generate - when a form JSON changed then its version is derived one higher automatically`(
        @TempDir tempDir: Path,
    ) {
        // Given: publish once, then edit content — no number is touched by hand
        val contentDir = tempDir.resolve("content").also(Files::createDirectories)
        val layout = TestContent.writeValid(contentDir)
        sut.generate(layout, tempDir.resolve("dist"))
        val published = tempDir.resolve("published-config.json")
        Files.copy(tempDir.resolve("dist/config/1.json"), published)
        val deJson = contentDir.resolve("forms/testform/form_testform.json")
        Files.writeString(deJson, Files.readString(deJson).replace("\"title\": \"Name\"", "\"title\": \"Nachname\""))

        // When
        val result = sut.generate(layout, tempDir.resolve("dist"), published)

        // Then: the edited form bumps to 2, the untouched hints stay at 1
        val manifest = assertIs<GenerateResult.Success>(result).manifest
        assertEquals(2, manifest.config)
        assertEquals(2, manifest.forms.single().version)
        assertEquals(
            "forms/testform/2/de/form.json",
            manifest.forms
                .single()
                .jsonDe.path,
        )
        assertEquals(1, manifest.hints.single().version)
    }

    @Test
    fun `generate - when only the PDF changed then the whole bundle version is derived one higher`(
        @TempDir tempDir: Path,
    ) {
        // Given: the bundle is atomic — a PDF-only change must bump it too
        val contentDir = tempDir.resolve("content").also(Files::createDirectories)
        val layout = TestContent.writeValid(contentDir)
        sut.generate(layout, tempDir.resolve("dist"))
        val published = tempDir.resolve("published-config.json")
        Files.copy(tempDir.resolve("dist/config/1.json"), published)
        TestContent.writePdf(contentDir.resolve("forms/testform/test.pdf"), *TestContent.PDF_FIELDS, "txtExtra")

        // When
        val result = sut.generate(layout, tempDir.resolve("dist"), published)

        // Then
        val manifest = assertIs<GenerateResult.Success>(result).manifest
        val form = manifest.forms.single()
        assertEquals(2, form.version)
        assertEquals("forms/testform/2/test.pdf", form.pdf.path)
        assertEquals("forms/testform/2/de/form.json", form.jsonDe.path)
    }

    @Test
    fun `generate - when content is unchanged then republishes the same config byte-identically`(
        @TempDir tempDir: Path,
    ) {
        // Given: publish once, change nothing, regenerate with a different clock
        val layout = TestContent.writeValid(tempDir.resolve("content").also(Files::createDirectories))
        sut.generate(layout, tempDir.resolve("dist"))
        val published = tempDir.resolve("published-config.json")
        Files.copy(tempDir.resolve("dist/config/1.json"), published)
        val laterSut = Generator(clock = { Instant.parse("2026-08-15T08:00:00Z") })

        // When
        val result = laterSut.generate(layout, tempDir.resolve("dist"), published)

        // Then: same config, and the snapshot keeps the originally published bytes
        val manifest = assertIs<GenerateResult.Success>(result).manifest
        assertEquals(1, manifest.config)
        assertEquals("2026-07-02T12:00:00Z", manifest.generatedAt)
        assertEquals(
            Files.readString(published),
            Files.readString(tempDir.resolve("dist/config/1.json")),
        )
    }

    @Test
    fun `generate - when content is reverted to previously published bytes then the version still moves forward`(
        @TempDir tempDir: Path,
    ) {
        // Given: v1 published, edited to v2 published, then the edit is reverted
        val contentDir = tempDir.resolve("content").also(Files::createDirectories)
        val layout = TestContent.writeValid(contentDir)
        sut.generate(layout, tempDir.resolve("dist"))
        val published = tempDir.resolve("published-config.json")
        Files.copy(tempDir.resolve("dist/config/1.json"), published)
        val deJson = contentDir.resolve("forms/testform/form_testform.json")
        Files.writeString(deJson, Files.readString(deJson).replace("\"title\": \"Name\"", "\"title\": \"Nachname\""))
        sut.generate(layout, tempDir.resolve("dist"), published)
        Files.delete(published)
        Files.copy(tempDir.resolve("dist/config/2.json"), published)
        Files.writeString(deJson, Files.readString(deJson).replace("\"title\": \"Nachname\"", "\"title\": \"Name\""))

        // When
        val result = sut.generate(layout, tempDir.resolve("dist"), published)

        // Then: old bytes get a NEW version — a version never points at two byte states
        val manifest = assertIs<GenerateResult.Success>(result).manifest
        assertEquals(3, manifest.forms.single().version)
        assertEquals(3, manifest.config)
    }

    @Test
    fun `immutabilityViolations - when a published path would get different bytes then reports it`() {
        // Given: a published entry and a current plan colliding on one path with a different sha
        val publishedEntry =
            ManifestFormEntry(
                formId = TestContent.FORM_ID,
                version = 1,
                minContentSchema = 1,
                jsonDe = FileEntry("forms/testform/1/de/form.json", "sha-published", 10),
                jsonEn = FileEntry("forms/testform/1/en/form.json", "sha-en", 10),
                pdf = FileEntry("forms/testform/1/test.pdf", "sha-pdf", 10),
            )
        val published =
            Manifest(config = 1, generatedAt = "2026-07-02T12:00:00Z", forms = listOf(publishedEntry), hints = emptyList())
        val current =
            listOf(
                FileEntry("forms/testform/1/de/form.json", "sha-DIFFERENT", 11),
                FileEntry("forms/testform/1/en/form.json", "sha-en", 10),
            )

        // When
        val violations = sut.immutabilityViolations(current, published)

        // Then
        assertEquals(1, violations.size)
        assertContains(violations.single().message, "immutable")
        assertContains(violations.single().message, "forms/testform/1/de/form.json")
    }

    @Test
    fun `generate - when a stale dist exists then it is replaced completely`(
        @TempDir tempDir: Path,
    ) {
        // Given
        val layout = TestContent.writeValid(tempDir.resolve("content").also(Files::createDirectories))
        val distDir = tempDir.resolve("dist")
        Files.createDirectories(distDir.resolve("forms/oldform/7"))
        Files.writeString(distDir.resolve("forms/oldform/7/stale.json"), "{}")

        // When
        val result = sut.generate(layout, distDir)

        // Then
        assertIs<GenerateResult.Success>(result)
        assertTrue(Files.notExists(distDir.resolve("forms/oldform")))
    }
}

class ManifestSerializationTest {
    private val sut = Json { prettyPrint = true }

    @Test
    fun `decode - when reading a written latest json then it points at the config snapshot`(
        @TempDir tempDir: Path,
    ) {
        // Given
        val layout = TestContent.writeValid(tempDir.resolve("content").also(Files::createDirectories))
        val distDir = tempDir.resolve("dist")
        Generator(clock = { Instant.parse("2026-07-02T12:00:00Z") }).generate(layout, distDir)

        // When
        val pointer = sut.decodeFromString<LatestPointer>(Files.readString(distDir.resolve("latest.json")))

        // Then
        assertEquals(1, pointer.config)
        assertEquals("config/1.json", pointer.configPath)
        assertTrue(Files.isRegularFile(distDir.resolve(pointer.configPath)))
    }

    @Test
    fun `encode - when serializing a manifest then decoding it yields the same manifest`(
        @TempDir tempDir: Path,
    ) {
        // Given
        val layout = TestContent.writeValid(tempDir.resolve("content").also(Files::createDirectories))
        val generator = Generator(clock = { Instant.parse("2026-07-02T12:00:00Z") })
        val manifest = (generator.generate(layout, tempDir.resolve("dist")) as GenerateResult.Success).manifest

        // When
        val roundTripped = sut.decodeFromString<Manifest>(sut.encodeToString(Manifest.serializer(), manifest))

        // Then
        assertEquals(manifest, roundTripped)
        assertEquals(1, roundTripped.schemaVersion)
    }
}
