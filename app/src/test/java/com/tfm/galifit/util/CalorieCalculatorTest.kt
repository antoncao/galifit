package com.tfm.galifit.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CalorieCalculatorTest {

    @Test
    fun computeTmb_male_returnsPositiveValue() {
        val tmb = CalorieCalculator.computeTmb("Hombre", 80f, 180f, 30)
        assertTrue(tmb > 1500f)
    }

    @Test
    fun computeTargetCalories_loseWeight_respectsMinimum() {
        val tdee = 2000f
        val target = CalorieCalculator.computeTargetCalories(tdee, "lose_weight", 1f)
        assertTrue(target >= 1200)
        assertTrue(target < tdee.toInt())
    }

    @Test
    fun computeTargetProtein_gainMuscle_scalesWithWeight() {
        val protein = CalorieCalculator.computeTargetProteinGrams(
            weightKg = 70f,
            objective = "gain_muscle",
            dietType = "none",
            trainingDaysPerWeek = 4
        )
        assertTrue(protein >= 120)
    }

    @Test
    fun activityMultiplier_knownLevels_andUnknownDefaultsToSedentary() {
        assertEquals(1.55f, CalorieCalculator.activityMultiplier("moderate"), 0.0001f)
        assertEquals(1.9f, CalorieCalculator.activityMultiplier("very_high"), 0.0001f)

        assertEquals(1.2f, CalorieCalculator.activityMultiplier("desconocido"), 0.0001f)
    }

    @Test
    fun computeTdee_isTmbTimesMultiplier() {
        assertEquals(2400f, CalorieCalculator.computeTdee(2000f, 1.2f), 0.0001f)
    }

    @Test
    fun computeTargetCalories_gainMuscle_addsSurplus_maintainKeepsTdee() {
        val gain = CalorieCalculator.computeTargetCalories(2000f, "gain_muscle", 0.5f)
        assertEquals(2000 + 550, gain)
        val maintain = CalorieCalculator.computeTargetCalories(2000f, "maintain", 1f)
        assertEquals(2000, maintain)
    }

    @Test
    fun computeTargetProtein_highProteinDiet_raisesBaseMultiplier() {

        val protein = CalorieCalculator.computeTargetProteinGrams(
            weightKg = 80f,
            objective = "maintain",
            dietType = "high_protein",
            trainingDaysPerWeek = 0
        )
        assertEquals((80f * 1.8f).toInt(), protein)
    }

    @Test
    fun computeTargetProtein_trainingBonusIsCappedAtTwoGramsPerKg() {

        val protein = CalorieCalculator.computeTargetProteinGrams(
            weightKg = 100f,
            objective = "gain_muscle",
            dietType = "none",
            trainingDaysPerWeek = 5
        )
        assertEquals(200, protein)
    }

    @Test
    fun computeTargetProtein_nonPositiveWeight_returnsZero() {
        assertEquals(0, CalorieCalculator.computeTargetProteinGrams(0f, "gain_muscle", "none", 4))
    }
}
