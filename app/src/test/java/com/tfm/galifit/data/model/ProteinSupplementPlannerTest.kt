package com.tfm.galifit.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProteinSupplementPlannerTest {

    @Test
    fun withProteinSupplementsIfNeeded_addsSupplementWhenDeficit() {
        val plan = FirebaseSavedMealPlan().apply {
            days = listOf(
                FirebaseMealPlanDay().apply {
                    dayLabel = "Lunes"
                    meals = listOf(
                        FirebasePlannedMealSummary().apply {
                            name = "Ensalada"
                            macros = MealMacrosSummary().apply { proteinGrams = 20.0 }
                        }
                    )
                }
            )
        }
        val prefs = UserPreferences(
            acceptsProteinSupplements = true,
            targetProteinGrams = 80,
            weightKg = 70f,
            objective = "gain_muscle"
        )
        val result = plan.withProteinSupplementsIfNeeded(prefs)
        val day = result.days.first()
        assertEquals(2, day.meals.size)
        assertTrue(day.meals.any { it.name == "Suplemento de proteínas" })
    }

    @Test
    fun withProteinSupplementsIfNeeded_skipsWhenUserDeclines() {
        val plan = FirebaseSavedMealPlan().apply {
            days = listOf(
                FirebaseMealPlanDay().apply {
                    meals = listOf(
                        FirebasePlannedMealSummary().apply {
                            macros = MealMacrosSummary().apply { proteinGrams = 10.0 }
                        }
                    )
                }
            )
        }
        val prefs = UserPreferences(
            acceptsProteinSupplements = false,
            targetProteinGrams = 100
        )
        val result = plan.withProteinSupplementsIfNeeded(prefs)
        assertEquals(1, result.days.first().meals.size)
    }
}
