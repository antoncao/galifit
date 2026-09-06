package com.tfm.galifit.data.model

data class EdamamMealPlanResponse(
    val selection: List<SelectionItem>? = null,
    val status: String? = null
)

data class SelectionItem(
    val sections: Map<String, SectionDetail?>? = null
)

data class SectionDetail(
    val assigned: String? = null,
    val _links: SectionLinks? = null
)

data class SectionLinks(
    val self: SectionSelf? = null
)

data class SectionSelf(
    val href: String? = null,
    val title: String? = null
)
