package com.tfm.galifit.data.model

data class WgerPaginatedResponse<T>(
    val count: Int = 0,
    val next: String? = null,
    val previous: String? = null,
    val results: List<T> = emptyList()
)

data class WgerExerciseInfo(
    val id: Int = 0,
    val uuid: String? = null,
    val name: String? = null,
    val description: String? = null,
    val category: WgerCategory? = null,
    val muscles: List<WgerMuscle> = emptyList(),
    val muscles_secondary: List<WgerMuscle> = emptyList(),
    val equipment: List<WgerEquipment> = emptyList(),
    val images: List<WgerImage> = emptyList(),
    val translations: List<WgerTranslation> = emptyList()
)

data class WgerTranslation(
    val id: Int = 0,
    val language: Int = 0,
    val name: String = "",
    val description: String = ""
)

data class WgerCategory(
    val id: Int = 0,
    val name: String = ""
)

data class WgerMuscle(
    val id: Int = 0,
    val name: String = "",
    val name_en: String? = null,
    val is_front: Boolean? = null
)

data class WgerEquipment(
    val id: Int = 0,
    val name: String = ""
)

data class WgerImage(
    val id: Int = 0,
    val image: String = "",
    val is_main: Boolean = false
)

data class WgerLanguage(
    val id: Int = 0,
    val short_name: String = "",
    val full_name: String = ""
)
