package com.tfm.galifit.data.model

import android.content.Context
import com.tfm.galifit.api.APIService
import com.tfm.galifit.util.GalifitFlowLog
import com.tfm.galifit.util.MealImageResolver
import com.tfm.galifit.util.MealThumbnailDiskCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

private data class RefreshedRecipeImages(
    val thumbnailUrl: String?,
    val detailImageUrl: String?,
    val macros: EdamamMealMacros?
)

suspend fun List<DayMealPlan>.refreshPresignedRecipeImages(
    service: APIService,
    authHeader: String,
    edamamAccountUser: String
): List<DayMealPlan> {
    val input = this
    return withContext(Dispatchers.IO) {
        val distinctRefs = input.flatMap { day ->
            day.meals.mapNotNull { m ->
                m.recipeReferenceUrl?.trim()?.takeIf { it.isNotEmpty() }
            }
        }.distinct()

        if (distinctRefs.isEmpty()) {
            GalifitFlowLog.api("refreshPresignedRecipeImages: sin recipeReferenceUrl, omitido")
            return@withContext input
        }

        GalifitFlowLog.api(
            "refreshPresignedRecipeImages: ${distinctRefs.size} recetas únicas → GET detalle (renovar URLs miniatura/detalle)"
        )
        val refToImages: Map<String, RefreshedRecipeImages?> = coroutineScope {
            distinctRefs.map { ref ->
                async { ref to fetchRecipeImagePair(service, ref, authHeader, edamamAccountUser) }
            }.awaitAll().toMap()
        }
        GalifitFlowLog.api("refreshPresignedRecipeImages: completado, mapas=${refToImages.size}")

        input.map { day ->
            DayMealPlan(
                dayName = day.dayName,
                meals = day.meals.map { meal ->
                    val ref = meal.recipeReferenceUrl?.trim().orEmpty()
                    val fresh = refToImages[ref]
                    if (fresh != null) {
                        meal.copy(
                            thumbnailImageUrl = fresh.thumbnailUrl ?: meal.thumbnailImageUrl,
                            detailImageUrl = fresh.detailImageUrl ?: meal.detailImageUrl,
                            calories = meal.calories ?: fresh.macros?.caloriesKcal?.roundToInt(),
                            proteinGrams = meal.proteinGrams ?: fresh.macros?.proteinGrams?.roundToInt(),
                            carbsGrams = meal.carbsGrams ?: fresh.macros?.carbsGrams?.roundToInt(),
                            fatGrams = meal.fatGrams ?: fresh.macros?.fatGrams?.roundToInt()
                        )
                    } else {
                        meal
                    }
                }.toMutableList()
            )
        }
    }
}

suspend fun List<DayMealPlan>.cacheMealPlanThumbnails(context: Context): List<DayMealPlan> =
    withContext(Dispatchers.IO) {
        GalifitFlowLog.disk("cacheMealPlanThumbnails: inicio (comprobar disco / descargar miniaturas)")
        map { day ->
            DayMealPlan(
                dayName = day.dayName,
                meals = day.meals.map { meal ->
                    val thumbUrl = meal.thumbnailImageUrl?.trim().orEmpty()
                    if (thumbUrl.isEmpty() || MealImageResolver.isLocalDrawableUri(thumbUrl)) {
                        return@map meal
                    }
                    val key = meal.recipeReferenceUrl?.trim()?.takeIf { it.isNotEmpty() } ?: thumbUrl
                    val path = MealThumbnailDiskCache.ensureCached(context, thumbUrl, key)
                    meal.copy(thumbnailLocalPath = path ?: meal.thumbnailLocalPath)
                }.toMutableList()
            )
        }.also {
            GalifitFlowLog.disk("cacheMealPlanThumbnails: fin")
        }
    }

private suspend fun fetchRecipeImagePair(
    service: APIService,
    recipeUrl: String,
    authHeader: String,
    edamamAccountUser: String
): RefreshedRecipeImages? {
    return try {
        GalifitFlowLog.api("GET receta (refresh URLs): …${recipeUrl.takeLast(48)}")
        val response = service.getRecipeByUrl(
            url = recipeUrl,
            authHeader = authHeader,
            edamamAccountUser = edamamAccountUser
        )
        if (!response.isSuccessful) {
            GalifitFlowLog.warn("GET receta fallo HTTP ${response.code()}")
            return null
        }
        val recipe = response.body()?.recipe ?: return null
        val visuals = recipe.toVisualsAndMacrosPerServing()
        RefreshedRecipeImages(visuals.thumbnailUrl, visuals.detailImageUrl, visuals.macros)
    } catch (e: Exception) {
        GalifitFlowLog.warn("GET receta excepción: ${e.message}")
        null
    }
}
