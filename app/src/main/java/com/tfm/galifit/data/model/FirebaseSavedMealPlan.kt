package com.tfm.galifit.data.model

import com.google.firebase.firestore.IgnoreExtraProperties
import kotlin.math.roundToInt

@IgnoreExtraProperties
class FirebaseSavedMealPlan {

    var createdAtMillis: Long = 0L

    var edamamStatus: String? = null

    var source: String = "edamam_meal_planner"

    var days: List<FirebaseMealPlanDay> = emptyList()
}

@IgnoreExtraProperties
class FirebaseMealPlanDay {

    var dayIndex: Long = 0L

    var dayLabel: String? = null

    var meals: List<FirebasePlannedMealSummary> = emptyList()
}

@IgnoreExtraProperties
class FirebasePlannedMealSummary {

    var sectionKey: String = ""
    var name: String = ""

    var recipeReferenceUrl: String = ""

    var assignedRecipeUri: String? = null

    var thumbnailImageUrl: String? = null

    var detailImageUrl: String? = null

    var imageUrl: String? = null
    var macros: MealMacrosSummary? = null

    var customDishId: String? = null
}

@IgnoreExtraProperties
class MealMacrosSummary {
    var caloriesKcal: Double? = null
    var proteinGrams: Double? = null
    var carbsGrams: Double? = null
    var fatGrams: Double? = null
    var fiberGrams: Double? = null
}

fun EdamamMealPlanResponse.toFirebaseSavedMealPlan(
    dayLabels: List<String> = DEFAULT_WEEK_DAY_LABELS
): FirebaseSavedMealPlan {
    val selectionItems = selection.orEmpty()

    val mappedDays = selectionItems.mapIndexed { index, item ->
        val orderedSections = item.sections.orEmpty().entries.sortedBy { (key, _) ->
            SECTION_ORDER.indexOf(key).takeIf { it >= 0 } ?: SECTION_ORDER.size
        }
        FirebaseMealPlanDay().apply {
            dayIndex = index.toLong()
            dayLabel = dayLabels.getOrNull(index)
            meals = orderedSections.mapNotNull { (sectionKey, detail) ->
                val selfLink = detail?._links?.self ?: return@mapNotNull null
                val href = selfLink.href?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                FirebasePlannedMealSummary().apply {
                    this.sectionKey = sectionKey
                    name = selfLink.title.orEmpty()
                    recipeReferenceUrl = href
                    assignedRecipeUri = detail.assigned
                    thumbnailImageUrl = null
                    detailImageUrl = null
                    imageUrl = null
                    macros = null
                }
            }
        }
    }

    if (mappedDays.none { it.meals.isNotEmpty() }) {
        throw IllegalStateException(
            "Edamam no devolvió ningún plato asignable (status=${status ?: "desconocido"}, días=${selectionItems.size})"
        )
    }

    return FirebaseSavedMealPlan().apply {
        createdAtMillis = System.currentTimeMillis()
        edamamStatus = status
        source = "edamam_meal_planner"
        days = mappedDays.toSevenDayPlan(dayLabels)
    }
}

private val SECTION_ORDER = listOf(
    "Breakfast",
    "Morning Snack",
    "Lunch",
    "Afternoon Snack",
    "Dinner",
    "Snack"
)

val DEFAULT_WEEK_DAY_LABELS = listOf(
    "Lunes", "Martes", "Miércoles", "Jueves", "Viernes", "Sábado", "Domingo"
)

private fun List<FirebaseMealPlanDay>.toSevenDayPlan(dayLabels: List<String>): List<FirebaseMealPlanDay> {
    val templates = filter { it.meals.isNotEmpty() }
    if (templates.isEmpty()) return this
    if (templates.size >= 7) {
        return templates.take(7).mapIndexed { idx, day ->
            day.copyForWeekIndex(idx, dayLabels.getOrElse(idx) { "Día ${idx + 1}" })
        }
    }
    return (0 until 7).map { idx ->
        templates[idx % templates.size].copyForWeekIndex(
            weekIndex = idx,
            dayLabel = dayLabels.getOrElse(idx) { "Día ${idx + 1}" }
        )
    }
}

private fun FirebasePlannedMealSummary.copyForNewDay(): FirebasePlannedMealSummary =
    FirebasePlannedMealSummary().apply {
        sectionKey = this@copyForNewDay.sectionKey
        name = this@copyForNewDay.name
        recipeReferenceUrl = this@copyForNewDay.recipeReferenceUrl
        assignedRecipeUri = this@copyForNewDay.assignedRecipeUri
        thumbnailImageUrl = this@copyForNewDay.thumbnailImageUrl
        detailImageUrl = this@copyForNewDay.detailImageUrl
        imageUrl = this@copyForNewDay.imageUrl
        customDishId = this@copyForNewDay.customDishId
        macros = this@copyForNewDay.macros?.let { m ->
            MealMacrosSummary().apply {
                caloriesKcal = m.caloriesKcal
                proteinGrams = m.proteinGrams
                carbsGrams = m.carbsGrams
                fatGrams = m.fatGrams
                fiberGrams = m.fiberGrams
            }
        }
    }

private fun FirebaseMealPlanDay.copyForWeekIndex(weekIndex: Int, dayLabel: String): FirebaseMealPlanDay =
    FirebaseMealPlanDay().apply {
        dayIndex = weekIndex.toLong()
        this.dayLabel = dayLabel
        meals = this@copyForWeekIndex.meals.map { it.copyForNewDay() }
    }

fun FirebaseSavedMealPlan.toUiDayMealPlans(): List<DayMealPlan> {
    if (days.isEmpty()) return emptyList()
    return days.map { day ->
        val label = day.dayLabel?.takeIf { it.isNotBlank() }
            ?: "Día ${day.dayIndex + 1}"
        DayMealPlan(
            dayName = label,
            meals = day.meals.map { summary ->
                val isSupplement = summary.isProteinSupplementSummary()
                val proteinGrams = summary.macros?.proteinGrams?.roundToInt() ?: 0
                val calories = summary.macros?.caloriesKcal?.roundToInt()
                    ?: (proteinGrams * 4)
                Meal(
                    name = summary.name,
                    thumbnailImageUrl = when {
                        isSupplement -> summary.thumbnailImageUrl?.takeIf { it.isNotBlank() }
                            ?: summary.imageUrl?.takeIf { it.isNotBlank() }
                            ?: PROTEIN_SUPPLEMENT_IMAGE_URI
                        else -> summary.thumbnailImageUrl?.takeIf { it.isNotBlank() }
                            ?: summary.imageUrl?.takeIf { it.isNotBlank() }
                    },
                    detailImageUrl = when {
                        isSupplement -> summary.detailImageUrl?.takeIf { it.isNotBlank() }
                            ?: summary.imageUrl?.takeIf { it.isNotBlank() }
                            ?: PROTEIN_SUPPLEMENT_IMAGE_URI
                        else -> summary.detailImageUrl?.takeIf { it.isNotBlank() }
                            ?: summary.imageUrl?.takeIf { it.isNotBlank() }
                    },
                    calories = calories,
                    proteinGrams = summary.macros?.proteinGrams?.roundToInt(),
                    carbsGrams = summary.macros?.carbsGrams?.roundToInt(),
                    fatGrams = summary.macros?.fatGrams?.roundToInt(),
                    description = formatMealMacrosLine(summary),
                    ingredients = if (isSupplement) {
                        proteinSupplementIngredientLines(proteinGrams)
                    } else {
                        emptyList()
                    },
                    elaboration = if (isSupplement) {
                        proteinSupplementElaborationSteps()
                    } else {
                        emptyList()
                    },
                    recipeReferenceUrl = summary.recipeReferenceUrl.takeIf { it.isNotBlank() },
                    isCustom = !summary.customDishId.isNullOrBlank(),
                    customDishId = summary.customDishId
                )
            }.toMutableList()
        )
    }
}

private fun formatMealMacrosLine(summary: FirebasePlannedMealSummary): String? {
    val m = summary.macros ?: return null
    val parts = buildList {
        m.proteinGrams?.let { add("P: ${it.roundToInt()}g") }
        m.carbsGrams?.let { add("C: ${it.roundToInt()}g") }
        m.fatGrams?.let { add("G: ${it.roundToInt()}g") }
        m.caloriesKcal?.let { add("${it.roundToInt()} kcal") }
    }
    return parts.takeIf { it.isNotEmpty() }?.joinToString(" · ")
}

fun defaultEmptyWeekDayMealPlans(): List<DayMealPlan> =
    DEFAULT_WEEK_DAY_LABELS.map { DayMealPlan(it) }

fun Meal.toCustomPlannedSummary(sectionKey: String = "Custom"): FirebasePlannedMealSummary {
    val self = this
    val img = self.thumbnailImageUrl?.takeIf { it.isNotBlank() }
        ?: self.detailImageUrl?.takeIf { it.isNotBlank() }
    return FirebasePlannedMealSummary().apply {
        this.sectionKey = sectionKey
        name = self.name
        recipeReferenceUrl = ""
        assignedRecipeUri = null
        thumbnailImageUrl = img
        detailImageUrl = img
        imageUrl = img
        customDishId = self.customDishId
        macros = MealMacrosSummary().apply {
            caloriesKcal = self.calories?.toDouble()
            proteinGrams = self.proteinGrams?.toDouble()
            carbsGrams = self.carbsGrams?.toDouble()
            fatGrams = self.fatGrams?.toDouble()
        }
    }
}
