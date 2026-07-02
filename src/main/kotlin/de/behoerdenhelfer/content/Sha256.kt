package de.behoerdenhelfer.content

import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest

object Sha256 {
    fun of(file: Path): String = of(Files.readAllBytes(file))

    fun of(bytes: ByteArray): String =
        MessageDigest
            .getInstance("SHA-256")
            .digest(bytes)
            .joinToString("") { "%02x".format(it) }
}
