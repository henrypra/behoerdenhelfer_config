package de.behoerdenhelfer.content

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.assertEquals

class Sha256Test {
    private val sut = Sha256

    @Test
    fun `of - when hashing known bytes then returns known lowercase hex digest`() {
        // Given
        val bytes = "abc".toByteArray()

        // When
        val digest = sut.of(bytes)

        // Then
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", digest)
    }

    @Test
    fun `of - when hashing a file then digest matches the file bytes`(
        @TempDir tempDir: Path,
    ) {
        // Given
        val file = tempDir.resolve("data.bin")
        Files.write(file, byteArrayOf(1, 2, 3))

        // When
        val digest = sut.of(file)

        // Then
        assertEquals(sut.of(byteArrayOf(1, 2, 3)), digest)
    }
}
