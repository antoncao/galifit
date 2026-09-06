package com.tfm.galifit.data.model

import android.content.Context
import com.tfm.galifit.api.APIService
import com.tfm.galifit.util.GalifitFlowLog
import com.tfm.galifit.util.TranslationService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext

suspend fun FirebaseSavedMealPlan.enrichMealsWithRecipeDetails(
    service: APIService,
    authHeader: String,
    edamamAccountUser: String,
    context: Context? = null
): FirebaseSavedMealPlan = withContext(Dispatchers.IO) {
    val totalWithRef =
        days.sumOf { day -> day.meals.count { it.recipeReferenceUrl.trim().isNotEmpty() } }
    GalifitFlowLog.api(
        "enrichMealsWithRecipeDetails: $totalWithRef comidas con recipeReferenceUrl → GET detalle Edamam (paralelo)"
    )
    val result = coroutineScope {
        val enrichedDays = days.map { day ->
            val enrichedMeals = day.meals.map { meal ->
                async {
                    enrichSingleMeal(meal, service, authHeader, edamamAccountUser, context)
                }
            }.awaitAll()
            day.withMeals(enrichedMeals)
        }
        this@enrichMealsWithRecipeDetails.withDays(enrichedDays)
    }
    GalifitFlowLog.api("enrichMealsWithRecipeDetails: terminado")
    result
}

private fun FirebaseMealPlanDay.withMeals(newMeals: List<FirebasePlannedMealSummary>): FirebaseMealPlanDay =
    FirebaseMealPlanDay().apply {
        dayIndex = this@withMeals.dayIndex
        dayLabel = this@withMeals.dayLabel
        meals = newMeals
    }

private fun FirebaseSavedMealPlan.withDays(newDays: List<FirebaseMealPlanDay>): FirebaseSavedMealPlan =
    FirebaseSavedMealPlan().apply {
        createdAtMillis = this@withDays.createdAtMillis
        edamamStatus = this@withDays.edamamStatus
        source = this@withDays.source
        days = newDays
    }

private suspend fun enrichSingleMeal(
    meal: FirebasePlannedMealSummary,
    service: APIService,
    authHeader: String,
    edamamAccountUser: String,
    context: Context?
): FirebasePlannedMealSummary {
    val href = meal.recipeReferenceUrl.trim()
    if (href.isEmpty()) return meal.cloneSummary()

    return try {
        GalifitFlowLog.debug("enrichMeal GET: «${meal.name}» …${href.takeLast(48)}")
        val response = service.getRecipeByUrl(
            url = href,
            authHeader = authHeader,
            edamamAccountUser = edamamAccountUser
        )
        if (!response.isSuccessful) {
            GalifitFlowLog.warn("enrichMeal HTTP ${response.code()} «${meal.name}»")
            return meal.cloneSummary()
        }

        val recipe = response.body()?.recipe ?: return meal.cloneSummary()
        val visuals = recipe.toVisualsAndMacrosPerServing()
        val originalName = recipe.label?.takeIf { it.isNotBlank() } ?: meal.name
        val translatedName = TranslationService.translate(context, originalName)

        FirebasePlannedMealSummary().apply {
            sectionKey = meal.sectionKey
            name = translatedName
            recipeReferenceUrl = meal.recipeReferenceUrl
            assignedRecipeUri = meal.assignedRecipeUri
            thumbnailImageUrl = visuals.thumbnailUrl
                ?: meal.thumbnailImageUrl
                ?: meal.imageUrl
            detailImageUrl = visuals.detailImageUrl
                ?: meal.detailImageUrl
                ?: meal.imageUrl
            imageUrl = null
            macros = visuals.macros?.toFirestoreMealMacros() ?: meal.macros?.cloneMacros()
        }
    } catch (e: Exception) {
        GalifitFlowLog.warn("enrichMeal excepción «${meal.name}»: ${e.message}")
        meal.cloneSummary()
    }
}

private fun FirebasePlannedMealSummary.cloneSummary(): FirebasePlannedMealSummary =
    FirebasePlannedMealSummary().apply {
        sectionKey = this@cloneSummary.sectionKey
        name = this@cloneSummary.name
        recipeReferenceUrl = this@cloneSummary.recipeReferenceUrl
        assignedRecipeUri = this@cloneSummary.assignedRecipeUri
        thumbnailImageUrl = this@cloneSummary.thumbnailImageUrl
        detailImageUrl = this@cloneSummary.detailImageUrl
        imageUrl = this@cloneSummary.imageUrl
        macros = this@cloneSummary.macros?.cloneMacros()
    }

private fun MealMacrosSummary.cloneMacros(): MealMacrosSummary =
    MealMacrosSummary().apply {
        caloriesKcal = this@cloneMacros.caloriesKcal
        proteinGrams = this@cloneMacros.proteinGrams
        carbsGrams = this@cloneMacros.carbsGrams
        fatGrams = this@cloneMacros.fatGrams
        fiberGrams = this@cloneMacros.fiberGrams
    }

private fun EdamamMealMacros.toFirestoreMealMacros(): MealMacrosSummary =
    MealMacrosSummary().apply {
        caloriesKcal = this@toFirestoreMealMacros.caloriesKcal
        proteinGrams = this@toFirestoreMealMacros.proteinGrams
        carbsGrams = this@toFirestoreMealMacros.carbsGrams
        fatGrams = this@toFirestoreMealMacros.fatGrams
        fiberGrams = this@toFirestoreMealMacros.fiberGrams
    }
