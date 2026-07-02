package de.behoerdenhelfer.content.model

import kotlinx.serialization.Serializable

@Serializable
data class HintsCatalogDto(
    val hints: List<HintDto>,
)

@Serializable
data class HintDto(
    val number: Int,
    val title: String,
    val text: String,
)
