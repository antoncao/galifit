package com.tfm.galifit.data.model

data class EdamamPlanRequest(
    val size: Int,
    val plan: EdamamPlan
)

data class EdamamPlan(
    val accept: EdamamAccept? = null,
    val fit: Map<String, EdamamFitBounds>? = null,
    val exclude: List<String>? = null,
    val sections: Map<String, EdamamSectionConfig>? = null
)

data class EdamamAccept(
    val all: List<EdamamAcceptFilter>
)

data class EdamamAcceptFilter(
    val health: List<String>? = null,
    val meal: List<String>? = null,
    val cuisine: List<String>? = null
)

data class EdamamFitBounds(
    val min: Int? = null,
    val max: Int? = null
)

data class EdamamSectionConfig(
    val accept: EdamamAccept? = null,
    val fit: Map<String, EdamamFitBounds>? = null
)
