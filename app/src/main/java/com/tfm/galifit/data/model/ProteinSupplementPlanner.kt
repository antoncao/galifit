package com.tfm.galifit.data.model

import com.tfm.galifit.util.CalorieCalculator
import com.tfm.galifit.util.GalifitFlowLog
import com.tfm.galifit.util.MealImageResolver
import kotlin.math.roundToInt

private const val SUPPLEMENT_THRESHOLD_GRAMS = 5
const val SUPPLEMENT_NAME = "Suplemento de proteínas"
private const val SUPPLEMENT_SECTION_KEY = "Supplement"
private const val SUPPLEMENT_DRAWABLE_NAME = "scoop_proteina"

val PROTEIN_SUPPLEMENT_IMAGE_URI: String =
    MealImageResolver.drawableUri(SUPPLEMENT_DRAWABLE_NAME)

fun Meal.isProteinSupplementMeal(): Boolean = isProteinSupplementName(name)

fun isProteinSupplementName(name: String): Boolean =
    name.trim().equals(SUPPLEMENT_NAME, ignoreCase = true)

fun FirebaseSavedMealPlan.withProteinSupplementsIfNeeded(
    prefs: UserPreferences
): FirebaseSavedMealPlan {
    removeExistingProteinSupplements()

    if (!prefs.acceptsProteinSupplements) return this

    val targetProtein = prefs.targetProteinGrams.takeIf { it > 0 }
        ?: CalorieCalculator.computeTargetProteinGrams(
            weightKg = prefs.weightKg,
            objective = prefs.objective,
            dietType = prefs.dietType,
            trainingDaysPerWeek = prefs.trainingDaysPerWeek
        )

    if (targetProtein <= 0) return this

    days.forEach { day ->
        val currentProtein = day.meals.sumOf { meal ->
            meal.macros?.proteinGrams ?: 0.0
        }
        val deficit = (targetProtein - currentProtein).roundToInt()
        if (deficit > SUPPLEMENT_THRESHOLD_GRAMS) {
            val supplement = proteinSupplementSummary(deficit)
            day.meals = day.meals + supplement
            GalifitFlowLog.api(
                "Suplemento proteínas: día=${day.dayLabel ?: day.dayIndex} objetivo=${targetProtein}g actual=${currentProtein.roundToInt()}g suplemento=${deficit}g"
            )
        }
    }

    return this
}

private fun FirebaseSavedMealPlan.removeExistingProteinSupplements() {
    days.forEach { day ->
        val before = day.meals.size
        day.meals = day.meals.filterNot { it.isProteinSupplementSummary() }
        if (day.meals.size != before) {
            GalifitFlowLog.api(
                "Suplemento proteínas: eliminados suplementos previos día=${day.dayLabel ?: day.dayIndex}"
            )
        }
    }
}

fun FirebasePlannedMealSummary.isProteinSupplementSummary(): Boolean =
    sectionKey.trim().equals(SUPPLEMENT_SECTION_KEY, ignoreCase = true) ||
        isProteinSupplementName(name)

fun proteinSupplementIngredientLines(proteinGrams: Int): List<String> = listOf(
    "$proteinGrams g de proteína en polvo (whey o similar)",
    "250 ml de agua, leche o bebida vegetal"
)

fun proteinSupplementElaborationSteps(): List<String> = listOf(
    "Añade el líquido a un shaker o vaso.",
    "Incorpora la dosis de proteína en polvo indicada.",
    "Agita o remueve hasta que se disuelva por completo.",
    "Consúmelo preferiblemente después del entrenamiento o entre comidas."
)

fun buildProteinSupplementDetailPayload(
    proteinGrams: Int,
    calories: Int
): CachedRecipeDetailPayload = CachedRecipeDetailPayload(
    title = SUPPLEMENT_NAME,
    detailImageUrl = PROTEIN_SUPPLEMENT_IMAGE_URI,
    caloriesPerServing = calories.toDouble(),
    proteinPerServing = proteinGrams.toDouble(),
    carbsPerServing = 0.0,
    fatPerServing = 0.0,
    fiberPerServing = 0.0,
    ingredientLines = proteinSupplementIngredientLines(proteinGrams),
    elaborationSteps = proteinSupplementElaborationSteps()
)

private fun proteinSupplementSummary(proteinGrams: Int): FirebasePlannedMealSummary =
    FirebasePlannedMealSummary().apply {
        sectionKey = SUPPLEMENT_SECTION_KEY
        name = SUPPLEMENT_NAME
        recipeReferenceUrl = ""
        assignedRecipeUri = null
        thumbnailImageUrl = PROTEIN_SUPPLEMENT_IMAGE_URI
        detailImageUrl = PROTEIN_SUPPLEMENT_IMAGE_URI
        imageUrl = PROTEIN_SUPPLEMENT_IMAGE_URI
        macros = MealMacrosSummary().apply {
            caloriesKcal = proteinGrams * 4.0
            this.proteinGrams = proteinGrams.toDouble()
            carbsGrams = 0.0
            fatGrams = 0.0
            fiberGrams = 0.0
        }
    }
