package de.behoerdenhelfer.content.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * `wegweiser.json` — situation → questions → result, per `docs/android-content-contract.md`
 * §2 with the answers of `docs/backend-reply-2026-09-20.md` applied. Parsed strictly: a
 * misspelt key or an unknown `apply` value fails validation instead of silently vanishing.
 * Nullable fields exist only so that an *absent* key parses; a literal `null` is rejected
 * by the validator, which walks the raw JSON for it.
 */
@Serializable
data class WegweiserDto(
    val schema: Int,
    val benefits: List<BenefitDto>,
    val results: List<ResultDto>,
    val situations: List<SituationDto>,
    val synonyms: List<SynonymDto>,
)

@Serializable
data class BenefitDto(
    val id: String,
    val name: String,
    val what: String,
    val authorityId: String,
    val apply: ApplyMode,
    /** Manifest form id, required iff `apply == app`. */
    val formId: String? = null,
    /** Required iff `apply == online`. */
    val portalUrl: String? = null,
    val deadline: String? = null,
    /** Names from the closed `documents` set of the current schema. */
    val documents: List<String> = emptyList(),
)

@Serializable
enum class ApplyMode {
    @SerialName("app")
    APP,

    @SerialName("online")
    ONLINE,

    @SerialName("paper")
    PAPER,
}

@Serializable
data class ResultDto(
    val id: String,
    val benefits: List<String>,
    val steps: List<String>,
    /** An office to link when the result is advice rather than a benefit. */
    val extraAuthorityId: String? = null,
)

@Serializable
data class SituationDto(
    val id: String,
    val title: String,
    val icon: String,
    val root: NodeDto,
)

/** Exactly one of the three shapes: question, result reference, or checklist. */
@Serializable
data class NodeDto(
    val question: String? = null,
    val answers: List<AnswerDto>? = null,
    val result: String? = null,
    val checklist: List<ChecklistItemDto>? = null,
)

@Serializable
data class AnswerDto(
    val text: String,
    val next: NodeDto,
    val fallback: Boolean = false,
)

/** Carries at most one reference: `authorityId` *or* `benefitId`. */
@Serializable
data class ChecklistItemDto(
    val text: String,
    val authorityId: String? = null,
    val benefitId: String? = null,
)

@Serializable
data class SynonymDto(
    /** Per language, lowercase, unique across the file. */
    val terms: List<String>,
    val target: SynonymTargetDto,
)

/** Exactly one of the three id spaces. */
@Serializable
data class SynonymTargetDto(
    val benefit: String? = null,
    val authority: String? = null,
    val situation: String? = null,
)
