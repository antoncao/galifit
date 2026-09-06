package com.tfm.galifit.data.model

import com.google.gson.annotations.SerializedName

data class EdamamRecipeDetailResponse(
    val recipe: EdamamRecipeDetail? = null
)

data class EdamamRecipeImageVariant(
    val url: String? = null,
    val width: Int? = null,
    val height: Int? = null
)

data class EdamamRecipeImages(
    @SerializedName("THUMBNAIL") val thumbnail: EdamamRecipeImageVariant? = null,
    @SerializedName("SMALL") val small: EdamamRecipeImageVariant? = null,
    @SerializedName("REGULAR") val regular: EdamamRecipeImageVariant? = null,
    @SerializedName("LARGE") val large: EdamamRecipeImageVariant? = null
)

data class EdamamRecipeDetail(
    val uri: String? = null,
    val label: String? = null,
    val image: String? = null,
    val images: EdamamRecipeImages? = null,
    @SerializedName("yield")
    val recipeYield: Double? = null,
    val ingredientLines: List<String>? = null,
    @SerializedName("url")
    val sourceRecipeUrl: String? = null,
    val totalNutrients: Map<String, EdamamNutrientQuantity>? = null
)

data class EdamamNutrientQuantity(
    val label: String? = null,
    val quantity: Double? = null,
    val unit: String? = null
)

data class EdamamRecipeImagePair(
    val thumbnailUrl: String?,
    val detailImageUrl: String?
)

fun EdamamRecipeDetail.pickThumbnailAndDetailUrls(): EdamamRecipeImagePair {
    val thumb = images?.thumbnail?.url?.takeIf { it.isNotBlank() }
        ?: images?.small?.url?.takeIf { it.isNotBlank() }
        ?: image?.takeIf { it.isNotBlank() }
    val detail = images?.large?.url?.takeIf { it.isNotBlank() }
        ?: images?.regular?.url?.takeIf { it.isNotBlank() }
        ?: images?.small?.url?.takeIf { it.isNotBlank() }
        ?: image?.takeIf { it.isNotBlank() }
    return EdamamRecipeImagePair(thumb, detail)
}

data class EdamamMealMacros(
    val caloriesKcal: Double? = null,
    val proteinGrams: Double? = null,
    val carbsGrams: Double? = null,
    val fatGrams: Double? = null,
    val fiberGrams: Double? = null
)

data class EdamamRecipeVisualsAndMacros(
    val thumbnailUrl: String?,
    val detailImageUrl: String?,
    val macros: EdamamMealMacros?
)

fun EdamamRecipeDetail.toVisualsAndMacrosPerServing(): EdamamRecipeVisualsAndMacros {
    val (thumb, detail) = pickThumbnailAndDetailUrls()
    val nutrients = totalNutrients
        ?: return EdamamRecipeVisualsAndMacros(thumb, detail, null)
    val divisor = recipeYield?.takeIf { it > 0 } ?: 1.0

    fun scaled(tag: String): Double? =
        nutrients[tag]?.quantity?.let { it / divisor }

    val macros = EdamamMealMacros(
        caloriesKcal = scaled("ENERC_KCAL"),
        proteinGrams = scaled("PROCNT"),
        carbsGrams = scaled("CHOCDF"),
        fatGrams = scaled("FAT"),
        fiberGrams = scaled("FIBTG")
    )
    return EdamamRecipeVisualsAndMacros(thumb, detail, macros)
}
