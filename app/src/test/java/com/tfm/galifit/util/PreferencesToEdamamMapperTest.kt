package com.tfm.galifit.util

import com.tfm.galifit.data.model.UserPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PreferencesToEdamamMapperTest {

    @Test
    fun buildHealthLabels_paleoDiet_usesUppercaseEdamamLabel() {
        val prefs = UserPreferences(
            dietType = "paleo",
            targetCalories = 2000
        )
        val labels = PreferencesToEdamamMapper.buildHealthLabels(prefs)
        assertTrue(labels.contains("PALEO"))
    }

    @Test
    fun buildHealthLabels_healthConditionAndManualLabel_deduplicates() {
        val prefs = UserPreferences(
            targetCalories = 2000,
            healthConditions = listOf("hypertension"),
            healthLabels = listOf("DASH", "LOW_SODIUM")
        )
        val labels = PreferencesToEdamamMapper.buildHealthLabels(prefs)

        assertEquals(1, labels.count { it == "DASH" })
        assertEquals(1, labels.count { it == "LOW_SODIUM" })

        assertTrue(labels.contains("ALCOHOL_FREE"))
        assertEquals(3, labels.size)
    }

    @Test
    fun buildRequest_threeMeals_hasBreakfastLunchDinnerSections() {
        val prefs = UserPreferences(
            targetCalories = 2100,
            mealsPerDay = 3
        )
        val request = PreferencesToEdamamMapper.buildRequest(prefs)
        val sections = request.plan.sections?.keys.orEmpty()
        assertTrue(sections.contains("Breakfast"))
        assertTrue(sections.contains("Lunch"))
        assertTrue(sections.contains("Dinner"))
    }

    @Test
    fun buildRequest_twoMeals_hasOnlyLunchAndDinner() {
        val prefs = UserPreferences(targetCalories = 2000, mealsPerDay = 2)
        val sections = PreferencesToEdamamMapper.buildRequest(prefs).plan.sections.orEmpty()
        assertEquals(setOf("Lunch", "Dinner"), sections.keys)
    }

    @Test
    fun buildRequest_fiveMeals_includesSnackSections() {
        val prefs = UserPreferences(targetCalories = 2500, mealsPerDay = 5)
        val sections = PreferencesToEdamamMapper.buildRequest(prefs).plan.sections.orEmpty()
        assertEquals(5, sections.size)
        assertTrue(sections.containsKey("Morning Snack"))
        assertTrue(sections.containsKey("Afternoon Snack"))
    }

    @Test
    fun buildRequest_globalFit_appliesTenPercentCalorieMargin() {
        val prefs = UserPreferences(targetCalories = 2000)
        val energy = PreferencesToEdamamMapper.buildRequest(prefs).plan.fit?.get("ENERC_KCAL")
        assertEquals(1800, energy?.min)
        assertEquals(2200, energy?.max)
    }

    @Test
    fun buildRequest_lowCarbDiet_addsCarbUpperBound() {
        val prefs = UserPreferences(targetCalories = 2000, dietType = "low_carb")
        val fit = PreferencesToEdamamMapper.buildRequest(prefs).plan.fit.orEmpty()
        assertEquals(100, fit["CHOCDF"]?.max)
    }

    @Test
    fun buildRequest_ketoDiet_addsStricterCarbBound() {
        val prefs = UserPreferences(targetCalories = 2000, dietType = "keto")
        val fit = PreferencesToEdamamMapper.buildRequest(prefs).plan.fit.orEmpty()
        assertEquals(50, fit["CHOCDF"]?.max)
    }
}
