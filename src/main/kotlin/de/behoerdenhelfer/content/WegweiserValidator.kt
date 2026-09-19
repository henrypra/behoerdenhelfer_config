package de.behoerdenhelfer.content

import de.behoerdenhelfer.content.model.ApplyMode
import de.behoerdenhelfer.content.model.NodeDto
import de.behoerdenhelfer.content.model.WegweiserDto
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name

/**
 * The rules of `docs/wegweiser-content-review.md` ("Validator") with the Android answers
 * of `docs/backend-reply-2026-09-20.md` applied: closed `documents`/`icon` sets, at most
 * 12 situations, per-language synonym terms, no orphans, no `null`, no HTML/Markdown, and
 * de/en identical once the text fields are stripped.
 */
class WegweiserValidator {
    private val json = Json

    fun validate(
        layout: ContentLayout,
        formIds: Set<String>,
        authorityIds: Set<String>,
        violations: MutableList<Violation>,
    ) {
        val bundle = layout.discoverWegweiser()
        validateNoOrphanFiles(layout, violations)
        bundle ?: return

        val de = parse(bundle.jsonDe, violations)
        val en = parse(bundle.jsonEn, violations)
        if (de == null || en == null) return

        validateFile(de.second, bundle.jsonDe.name, formIds, authorityIds, violations)
        validateFile(en.second, bundle.jsonEn.name, formIds, authorityIds, violations)
        validateParity(de.first, en.first, violations)
    }

    /** Parses strictly and keeps the raw tree, which the null and parity checks need. */
    private fun parse(
        file: Path,
        violations: MutableList<Violation>,
    ): Pair<JsonElement, WegweiserDto>? {
        if (!Files.isRegularFile(file)) {
            violations += Violation(SCOPE, "missing wegweiser file $file")
            return null
        }
        return try {
            val text = Files.readString(file)
            val raw = json.parseToJsonElement(text)
            val nulls = nullPaths(raw, "$")
            nulls.forEach { violations += Violation(SCOPE, "${file.name}: '$it' is null — omit optional fields instead") }
            if (nulls.isNotEmpty()) return null
            raw to json.decodeFromString<WegweiserDto>(text)
        } catch (e: Exception) {
            violations += Violation(SCOPE, "cannot parse ${file.name}: ${e.message}")
            null
        }
    }

    private fun validateFile(
        dto: WegweiserDto,
        fileName: String,
        formIds: Set<String>,
        authorityIds: Set<String>,
        violations: MutableList<Violation>,
    ) {
        val check = FileCheck(fileName, formIds, authorityIds, violations)
        check.schema(dto)
        check.benefits(dto)
        check.results(dto)
        check.situations(dto)
        check.synonyms(dto)
        check.orphans(dto)
    }

    private class FileCheck(
        private val fileName: String,
        private val formIds: Set<String>,
        private val authorityIds: Set<String>,
        private val violations: MutableList<Violation>,
    ) {
        private val benefitIds = mutableSetOf<String>()
        private val resultIds = mutableSetOf<String>()
        private val situationIds = mutableSetOf<String>()
        private val referencedResults = mutableSetOf<String>()
        private val referencedBenefits = mutableSetOf<String>()

        private fun violation(message: String) {
            violations += Violation(SCOPE, "$fileName: $message")
        }

        private fun ids(
            section: String,
            ids: List<String>,
        ): Set<String> {
            ids
                .groupBy { it }
                .filterValues { it.size > 1 }
                .keys
                .forEach { violation("$section: duplicate id '$it'") }
            ids.filterNot { ID_PATTERN.matches(it) }.forEach { violation("$section: id '$it' does not match ${ID_PATTERN.pattern}") }
            return ids.toSet()
        }

        private fun text(
            where: String,
            value: String,
        ) {
            if (value.isBlank()) violation("$where is blank")
            if (HTML_PATTERN.containsMatchIn(value)) violation("$where contains HTML: '${value.take(40)}'")
            if (MARKDOWN_PATTERN.containsMatchIn(value)) violation("$where contains Markdown: '${value.take(40)}'")
        }

        private fun texts(
            where: String,
            values: List<String>,
        ) {
            if (values.isEmpty()) violation("$where is empty")
            values.forEach { text(where, it) }
        }

        private fun authority(
            where: String,
            id: String,
        ) {
            if (id !in authorityIds) violation("$where references unknown authority '$id'")
        }

        fun schema(dto: WegweiserDto) {
            if (dto.schema != SCHEMA) violation("schema is ${dto.schema}, expected $SCHEMA")
        }

        fun benefits(dto: WegweiserDto) {
            benefitIds += ids("benefits", dto.benefits.map { it.id })
            dto.benefits.forEach { b ->
                val where = "benefit '${b.id}'"
                text("$where name", b.name)
                text("$where what", b.what)
                b.deadline?.let { text("$where deadline", it) }
                authority(where, b.authorityId)
                when (b.apply) {
                    ApplyMode.APP -> {
                        if (b.formId == null) violation("$where has apply 'app' but no formId")
                    }
                    ApplyMode.ONLINE -> {
                        if (b.portalUrl == null) violation("$where has apply 'online' but no portalUrl")
                    }
                    ApplyMode.PAPER -> Unit
                }
                if (b.formId != null && b.apply != ApplyMode.APP) violation("$where has a formId but apply is not 'app'")
                b.formId?.let { if (it !in formIds) violation("$where references unknown form id '$it'") }
                b.portalUrl?.let { if (!isHttpsUrl(it)) violation("$where portalUrl '$it' must be an https:// URL without whitespace") }
                b.documents
                    .groupBy { it }
                    .filterValues { it.size > 1 }
                    .keys
                    .forEach { violation("$where lists document '$it' twice") }
                b.documents.filterNot { it in DOCUMENTS }.forEach {
                    violation(
                        "$where uses unknown document '$it' (closed set for schema $SCHEMA)",
                    )
                }
            }
        }

        fun results(dto: WegweiserDto) {
            resultIds += ids("results", dto.results.map { it.id })
            dto.results.forEach { r ->
                val where = "result '${r.id}'"
                texts("$where steps", r.steps)
                r.benefits
                    .groupBy { it }
                    .filterValues { it.size > 1 }
                    .keys
                    .forEach { violation("$where lists benefit '$it' twice") }
                r.benefits.forEach { if (it !in benefitIds) violation("$where references unknown benefit '$it'") }
                referencedBenefits += r.benefits
                r.extraAuthorityId?.let { authority(where, it) }
                if (r.benefits.isEmpty() && r.extraAuthorityId == null) {
                    violation("$where has neither benefits nor an extraAuthorityId — it leads nowhere")
                }
            }
        }

        fun situations(dto: WegweiserDto) {
            if (dto.situations.isEmpty()) violation("situations is empty")
            if (dto.situations.size > MAX_SITUATIONS) violation("${dto.situations.size} situations, at most $MAX_SITUATIONS allowed")
            situationIds += ids("situations", dto.situations.map { it.id })
            dto.situations.forEach { s ->
                val where = "situation '${s.id}'"
                text("$where title", s.title)
                if (s.icon !in ICONS) violation("$where uses unknown icon '${s.icon}' (closed set for schema $SCHEMA)")
                node(s.root, "$where root", depth = 1)
            }
        }

        /** @param depth number of questions on the path including this node, if it is one. */
        private fun node(
            node: NodeDto,
            where: String,
            depth: Int,
        ) {
            val shapes =
                listOfNotNull(node.question?.let { "question" }, node.result?.let { "result" }, node.checklist?.let { "checklist" })
            if (shapes.size != 1) {
                violation("$where must be exactly one of question, result or checklist (has ${shapes.ifEmpty { listOf("none") }})")
                return
            }
            when (shapes.single()) {
                "question" -> {
                    text("$where question", node.question!!)
                    val answers = node.answers
                    if (answers == null) {
                        violation("$where has a question but no answers")
                        return
                    }
                    if (node.answers.size !in MIN_ANSWERS..MAX_ANSWERS) {
                        violation("$where has ${answers.size} answers, expected $MIN_ANSWERS–$MAX_ANSWERS")
                    }
                    val fallbacks = answers.count { it.fallback }
                    if (fallbacks != 1) violation("$where has $fallbacks fallback answers, expected exactly 1")
                    if (depth > MAX_DEPTH) violation("$where is a question at depth $depth, deeper than $MAX_DEPTH")
                    answers.forEachIndexed { i, a ->
                        text("$where answer ${i + 1}", a.text)
                        node(a.next, "$where answer ${i + 1} next", depth + 1)
                    }
                }
                "result" -> {
                    if (node.answers != null) violation("$where is a result but has answers")
                    if (node.result!! !in resultIds) violation("$where references unknown result '${node.result}'")
                    referencedResults += node.result
                }
                "checklist" -> {
                    if (node.answers != null) violation("$where is a checklist but has answers")
                    val items = node.checklist!!
                    if (items.isEmpty()) violation("$where checklist is empty")
                    items.forEachIndexed { i, item ->
                        val itemWhere = "$where item ${i + 1}"
                        text(itemWhere, item.text)
                        if (item.authorityId != null && item.benefitId != null) {
                            violation("$itemWhere carries both authorityId and benefitId — at most one")
                        }
                        item.authorityId?.let { authority(itemWhere, it) }
                        item.benefitId?.let {
                            if (it !in benefitIds) violation("$itemWhere references unknown benefit '$it'")
                            referencedBenefits += it
                        }
                    }
                }
            }
        }

        fun synonyms(dto: WegweiserDto) {
            val seen = mutableSetOf<String>()
            dto.synonyms.forEachIndexed { i, s ->
                val where = "synonym ${i + 1}"
                if (s.terms.isEmpty()) violation("$where has no terms")
                s.terms.forEach { term ->
                    if (term.isBlank()) violation("$where has a blank term")
                    if (term != term.trim()) violation("$where term '$term' has surrounding whitespace")
                    if (term != term.lowercase()) violation("$where term '$term' is not lowercase")
                    if (!seen.add(term)) violation("$where term '$term' appears more than once in the file")
                }
                val t = s.target
                val targets =
                    listOfNotNull(t.benefit?.let { "benefit" }, t.authority?.let { "authority" }, t.situation?.let { "situation" })
                if (targets.size != 1) {
                    violation("$where target must be exactly one of benefit, authority or situation")
                    return@forEachIndexed
                }
                t.benefit?.let {
                    if (it !in benefitIds) violation("$where targets unknown benefit '$it'")
                    referencedBenefits += it
                }
                t.authority?.let { authority(where, it) }
                t.situation?.let { if (it !in situationIds) violation("$where targets unknown situation '$it'") }
            }
        }

        fun orphans(dto: WegweiserDto) {
            (resultIds - referencedResults).sorted().forEach { violation("result '$it' is not reachable from any situation") }
            (benefitIds - referencedBenefits).sorted().forEach {
                violation(
                    "benefit '$it' is not referenced by any result, checklist or synonym",
                )
            }
        }
    }

    /** de and en must be identical once every translatable field is removed. */
    private fun validateParity(
        de: JsonElement,
        en: JsonElement,
        violations: MutableList<Violation>,
    ) {
        firstDifference(stripText(de), stripText(en), "$")?.let {
            violations += Violation(SCOPE, "de/en drift at $it: only text fields (${TEXT_FIELDS.joinToString()}) may differ")
        }
    }

    private fun stripText(element: JsonElement): JsonElement =
        when (element) {
            is JsonObject -> JsonObject(element.filterKeys { it !in TEXT_FIELDS }.mapValues { stripText(it.value) })
            is JsonArray -> JsonArray(element.map(::stripText))
            else -> element
        }

    private fun firstDifference(
        a: JsonElement,
        b: JsonElement,
        path: String,
    ): String? {
        if (a is JsonObject && b is JsonObject) {
            (a.keys + b.keys).forEach { key ->
                val av = a[key] ?: return "$path.$key (missing in de)"
                val bv = b[key] ?: return "$path.$key (missing in en)"
                firstDifference(av, bv, "$path.$key")?.let { return it }
            }
            return null
        }
        if (a is JsonArray && b is JsonArray) {
            if (a.size != b.size) return "$path (${a.size} entries in de, ${b.size} in en)"
            a.zip(b).forEachIndexed { i, (av, bv) -> firstDifference(av, bv, "$path[$i]")?.let { return it } }
            return null
        }
        return if (a == b) null else "$path ('$a' in de, '$b' in en)"
    }

    private fun nullPaths(
        element: JsonElement,
        path: String,
    ): List<String> =
        when (element) {
            is JsonNull -> listOf(path)
            is JsonObject -> element.flatMap { (k, v) -> nullPaths(v, "$path.$k") }
            is JsonArray -> element.flatMapIndexed { i, v -> nullPaths(v, "$path[$i]") }
            else -> emptyList()
        }

    private fun validateNoOrphanFiles(
        layout: ContentLayout,
        violations: MutableList<Violation>,
    ) {
        if (!Files.isDirectory(layout.wegweiserDir)) return
        val known = layout.wegweiserBundle().let { setOf(it.jsonDe.name, it.jsonEn.name) }
        layout.wegweiserDir.listDirectoryEntries().filter { it.name !in known }.forEach { file ->
            violations += Violation(SCOPE, "unexpected file '${file.name}' — only wegweiser.json and wegweiser-en.json belong here")
        }
    }

    companion object {
        const val SCOPE = "wegweiser"
        const val SCHEMA = 1
        const val MAX_SITUATIONS = 12
        const val MIN_ANSWERS = 2
        const val MAX_ANSWERS = 4
        const val MAX_DEPTH = 3
        val ID_PATTERN = Regex("^[a-z][a-z0-9_]*$")
        val HTML_PATTERN = Regex("<[^>]*>|&[a-z]+;|&#[0-9]+;")
        val MARKDOWN_PATTERN = Regex("""\*\*|__|`|\]\(|^#|^[-*] """)

        /** Translatable fields; everything else must be identical in de and en. */
        val TEXT_FIELDS = setOf("name", "what", "deadline", "steps", "title", "question", "text", "terms")

        /**
         * Closed sets for `schema: 1`, mirroring the app's `GuideDocument` names and icon
         * ids (`docs/backend-reply-2026-09-20.md`). Adding a value = `schema: 2` here and
         * [ContentSchema.WEGWEISER] = 3 — never a silent extension.
         */
        val DOCUMENTS =
            setOf(
                "ID_CARD",
                "TAX_ID",
                "BANK_DETAILS",
                "BIRTH_CERTIFICATE",
                "RENTAL_CONTRACT",
                "RENT_STATEMENT",
                "HEATING_BILL",
                "INCOME_PROOF",
                "BANK_STATEMENTS",
                "ASSET_PROOF",
                "EMPLOYMENT_TERMINATION",
                "PREVIOUS_DECISION",
                "HEALTH_INSURANCE",
                "MATERNITY_CERTIFICATE",
                "REGISTRATION_CERTIFICATE",
                "MEDICAL_REPORTS",
            )
        val ICONS = setOf("child", "wallet", "home", "globe", "work", "hospital", "people", "clock")

        private fun isHttpsUrl(url: String): Boolean = url.startsWith("https://") && url.length > 8 && url.none { it.isWhitespace() }
    }
}
