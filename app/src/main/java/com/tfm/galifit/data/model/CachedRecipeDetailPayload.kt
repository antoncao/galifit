package com.tfm.galifit.data.model

import android.content.Context
import com.google.gson.annotations.SerializedName
import com.tfm.galifit.util.TranslationService

data class CachedRecipeDetailPayload(
    @SerializedName("v") val version: Int = CURRENT_VERSION,
    @SerializedName("ref") val recipeReferenceUrl: String = "",
    @SerializedName("at") val fetchedAtMillis: Long = 0L,
    @SerializedName("title") val title: String? = null,
    @SerializedName("detailImageUrl") val detailImageUrl: String? = null,
    @SerializedName("yield") val yield: Double? = null,
    @SerializedName("kcal") val caloriesPerServing: Double? = null,
    @SerializedName("proteinG") val proteinPerServing: Double? = null,
    @SerializedName("carbsG") val carbsPerServing: Double? = null,
    @SerializedName("fatG") val fatPerServing: Double? = null,
    @SerializedName("fiberG") val fiberPerServing: Double? = null,
    @SerializedName("ingredients") val ingredientLines: List<String> = emptyList(),
    @SerializedName("steps") val elaborationSteps: List<String> = emptyList(),
    @SerializedName("sourceUrl") val sourceWebUrl: String? = null
) {
    companion object {

        const val CURRENT_VERSION = 1
    }

    fun isCompatible(): Boolean = version == CURRENT_VERSION
}

fun EdamamRecipeDetail.toCachedPayload(recipeReferenceUrl: String): CachedRecipeDetailPayload {
    val visuals = toVisualsAndMacrosPerServing()
    val m = visuals.macros
    val ref = recipeReferenceUrl.trim()
    return CachedRecipeDetailPayload(
        recipeReferenceUrl = ref,
        fetchedAtMillis = System.currentTimeMillis(),
        title = label,
        detailImageUrl = visuals.detailImageUrl,
        yield = recipeYield,
        caloriesPerServing = m?.caloriesKcal,
        proteinPerServing = m?.proteinGrams,
        carbsPerServing = m?.carbsGrams,
        fatPerServing = m?.fatGrams,
        fiberPerServing = m?.fiberGrams,
        ingredientLines = ingredientLines?.map { it.trim() }?.filter { it.isNotEmpty() } ?: emptyList(),
        elaborationSteps = buildElaborationStepsFromRecipe(),
        sourceWebUrl = sourceRecipeUrl?.trim()?.takeIf { it.isNotEmpty() }
    )
}

suspend fun CachedRecipeDetailPayload.translatedToSpanish(context: Context): CachedRecipeDetailPayload {
    if (!TranslationService.isEnabled()) return this
    val translatedTitle = title?.takeIf { it.isNotBlank() }
        ?.let { TranslationService.translate(context, it) }
        ?: title
    val translatedIngredients = if (ingredientLines.isNotEmpty()) {
        TranslationService.translate(context, ingredientLines)
    } else {
        ingredientLines
    }
    return copy(title = translatedTitle, ingredientLines = translatedIngredients)
}

private fun EdamamRecipeDetail.buildElaborationStepsFromRecipe(): List<String> {
    val src = sourceRecipeUrl?.trim().orEmpty()
    return if (src.isNotEmpty()) {
        listOf(
            "Actualmente no se incluyen pasos de elaboración detallados para esta receta en la aplicación.",
            "Consulta la receta completa en la fuente: $src"
        )
    } else {
        listOf("No hay información de elaboración disponible para esta receta.")
    }
}
