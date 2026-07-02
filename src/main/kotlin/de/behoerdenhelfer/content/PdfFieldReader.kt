package de.behoerdenhelfer.content

import org.apache.pdfbox.pdmodel.PDDocument
import java.nio.file.Path

/**
 * Reads the complete AcroForm field name set of a PDF. The document is opened
 * read-only and never re-saved — published PDF bytes must stay untouched
 * (the KG1 is encrypted + an XFA hybrid; the app strips both when filling).
 */
object PdfFieldReader {
    /**
     * Walks the *whole hierarchical* field tree (top-level fields alone are not
     * enough for XFA-style PDFs) and returns every fully-qualified and partial
     * name. Names are opaque strings — no splitting or unescaping.
     * An empty result means a flattened print copy without form fields.
     */
    fun fieldNames(pdf: Path): Set<String> =
        PDDocument.load(pdf.toFile()).use { document ->
            val acroForm = document.documentCatalog.acroForm ?: return@use emptySet()
            val names = mutableSetOf<String>()
            for (field in acroForm.fieldTree) {
                field.fullyQualifiedName?.let(names::add)
                field.partialName?.let(names::add)
            }
            names
        }
}
