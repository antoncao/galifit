package com.tfm.galifit.util

import com.tfm.galifit.data.model.EdamamAccept
import com.tfm.galifit.data.model.EdamamAcceptFilter
import com.tfm.galifit.data.model.EdamamFitBounds
import com.tfm.galifit.data.model.EdamamHealthCatalog
import com.tfm.galifit.data.model.EdamamPlan
import com.tfm.galifit.data.model.EdamamPlanRequest
import com.tfm.galifit.data.model.EdamamSectionConfig
import com.tfm.galifit.data.model.UserPreferences
import com.tfm.galifit.util.PreferencesToEdamamMapper.buildHealthLabels
import com.tfm.galifit.util.PreferencesToEdamamMapper.buildRequest
import com.tfm.galifit.util.PreferencesToEdamamMapper.mealSection
import com.tfm.galifit.util.PreferencesToEdamamMapper.snackSection

object PreferencesToEdamamMapper {

    fun defaultRequest(): EdamamPlanRequest = buildRequest(UserPreferences(targetCalories = 2000))

    fun buildRequest(prefs: UserPreferences): EdamamPlanRequest {
        val healthLabels = buildHealthLabels(prefs)
        val globalFit = buildGlobalFit(prefs)
        val acceptFilters = buildAcceptFilters(healthLabels, prefs.cuisinePreferences)

        return EdamamPlanRequest(
            size = 7,
            plan = EdamamPlan(
                accept = if (acceptFilters.isNotEmpty()) EdamamAccept(all = acceptFilters) else null,
                fit = globalFit,
                exclude = null,
                sections = buildSections(prefs)
            )
        )
    }

    private fun buildAcceptFilters(
        healthLabels: List<String>,
        cuisinePreferences: List<String>
    ): List<EdamamAcceptFilter> {
        val filters = mutableListOf<EdamamAcceptFilter>()
        if (healthLabels.isNotEmpty()) {
            filters.add(EdamamAcceptFilter(health = healthLabels))
        }
        val cuisines = cuisinePreferences
            .map { it.trim().lowercase() }
            .filter { it.isNotBlank() }
            .distinct()
        if (cuisines.isNotEmpty()) {
            filters.add(EdamamAcceptFilter(cuisine = cuisines))
        }
        return filters
    }

    fun buildHealthLabels(prefs: UserPreferences): List<String> =
        EdamamHealthCatalog.resolveHealthLabels(prefs)

    private fun buildGlobalFit(prefs: UserPreferences): Map<String, EdamamFitBounds> {
        val cal = prefs.targetCalories.takeIf { it > 0 } ?: 2000
        val calMargin = (cal * 0.10).toInt()
        val fit = mutableMapOf(
            "ENERC_KCAL" to EdamamFitBounds(min = cal - calMargin, max = cal + calMargin),
        )

        when (prefs.dietType) {
            "low_carb"     -> fit["CHOCDF"] = EdamamFitBounds(max = 100)
            "high_protein" -> fit["PROCNT"] = EdamamFitBounds(min = 120)
            "low_fat"      -> fit["FAT"] = EdamamFitBounds(max = 50)
            "keto"         -> fit["CHOCDF"] = EdamamFitBounds(max = 50)
        }

        return fit
    }

    private fun buildSections(prefs: UserPreferences): Map<String, EdamamSectionConfig> {
        val cal = prefs.targetCalories.takeIf { it > 0 } ?: 2000

        return when (prefs.mealsPerDay) {
            2 -> {
                val lunch = (cal * 0.55).toInt()
                val dinner = (cal * 0.45).toInt()
                val margin = 250
                mapOf(
                    "Lunch" to mealSection("lunch/dinner", lunch - margin, lunch + margin),
                    "Dinner" to mealSection("lunch/dinner", dinner - margin, dinner + margin)
                )
            }
            4 -> {
                val bk = (cal * 0.25).toInt()
                val lunch = (cal * 0.30).toInt()
                val snack = (cal * 0.15).toInt()
                val dinner = (cal * 0.30).toInt()
                val margin = 250
                val snackMargin = 120
                mapOf(
                    "Breakfast" to mealSection("breakfast", bk - margin, bk + margin),
                    "Lunch" to mealSection("lunch/dinner", lunch - margin, lunch + margin),
                    "Snack" to snackSection(snack - snackMargin, snack + snackMargin),
                    "Dinner" to mealSection("lunch/dinner", dinner - margin, dinner + margin)
                )
            }
            5 -> {
                val bk = (cal * 0.20).toInt()
                val snackAm = (cal * 0.10).toInt()
                val lunch = (cal * 0.30).toInt()
                val snackPm = (cal * 0.10).toInt()
                val dinner = (cal * 0.30).toInt()
                val margin = 250
                val snackMargin = 120
                mapOf(
                    "Breakfast" to mealSection("breakfast", bk - margin, bk + margin),
                    "Morning Snack" to snackSection(snackAm - snackMargin, snackAm + snackMargin),
                    "Lunch" to mealSection("lunch/dinner", lunch - margin, lunch + margin),
                    "Afternoon Snack" to snackSection(snackPm - snackMargin, snackPm + snackMargin),
                    "Dinner" to mealSection("lunch/dinner", dinner - margin, dinner + margin)
                )
            }
            else -> {
                val bk = (cal * 0.25).toInt()
                val lunch = (cal * 0.40).toInt()
                val dinner = (cal * 0.35).toInt()
                val margin = 250
                mapOf(
                    "Breakfast" to mealSection("breakfast", bk - margin, bk + margin),
                    "Lunch" to mealSection("lunch/dinner", lunch - margin, lunch + margin),
                    "Dinner" to mealSection("lunch/dinner", dinner - margin, dinner + margin)
                )
            }
        }
    }

    private fun mealSection(mealType: String, calMin: Int, calMax: Int): EdamamSectionConfig {
        val safeMin = calMin.coerceAtLeast(100)
        val safeMax = calMax.coerceAtLeast(safeMin)
        return EdamamSectionConfig(
            accept = EdamamAccept(all = listOf(EdamamAcceptFilter(meal = listOf(mealType)))),
            fit = mapOf("ENERC_KCAL" to EdamamFitBounds(min = safeMin, max = safeMax))
        )
    }

    private fun snackSection(calMin: Int, calMax: Int): EdamamSectionConfig {
        val safeMin = calMin.coerceAtLeast(50)
        val safeMax = calMax.coerceAtLeast(safeMin)
        return EdamamSectionConfig(
            fit = mapOf("ENERC_KCAL" to EdamamFitBounds(min = safeMin, max = safeMax))
        )
    }
}
