package de.behoerdenhelfer.content

import java.nio.file.Files
import java.nio.file.Path
import kotlin.system.exitProcess

fun main(args: Array<String>) {
    val root = Path.of("").toAbsolutePath()
    val layout = ContentLayout(root.resolve("content"))

    when (args.firstOrNull()) {
        "validate" -> {
            val violations = Validator().validate(layout)
            if (violations.isEmpty()) {
                println("content/ is valid.")
            } else {
                fail(violations)
            }
        }

        "generate" -> {
            val publishedManifest = root.resolve("published-config.json").takeIf(Files::isRegularFile)
            when (val result = Generator().generate(layout, root.resolve("dist"), publishedManifest)) {
                is GenerateResult.Success -> {
                    val manifest = result.manifest
                    println(
                        "dist/ generated: config ${manifest.config}, ${manifest.forms.size} forms, " +
                            "${manifest.hints.size} hints catalogs (generatedAt ${manifest.generatedAt}).",
                    )
                }

                is GenerateResult.Failure -> fail(result.violations)
            }
        }

        else -> {
            System.err.println("usage: <validate|generate>")
            exitProcess(2)
        }
    }
}

private fun fail(violations: List<Violation>): Nothing {
    System.err.println("${violations.size} violation(s):")
    violations.forEach { System.err.println("  $it") }
    exitProcess(1)
}
