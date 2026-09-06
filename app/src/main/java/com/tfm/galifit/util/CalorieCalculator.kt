package com.tfm.galifit.util

object CalorieCalculator {

    fun computeTmb(sex: String, weightKg: Float, heightCm: Float, age: Int): Float {
        val base = (10f * weightKg) + (6.25f * heightCm) - (5f * age)
        return if (sex == "Hombre") base + 5f else base - 161f
    }

    fun activityMultiplier(activityLevel: String): Float = when (activityLevel) {
        "sedentary"   -> 1.2f
        "light"       -> 1.375f
        "moderate"    -> 1.55f
        "high"        -> 1.725f
        "very_high"   -> 1.9f
        else          -> 1.2f
    }

    fun computeTdee(tmb: Float, multiplier: Float): Float = tmb * multiplier

    fun computeTargetCalories(
        tdee: Float,
        objective: String,
        weeklyGoalKg: Float
    ): Int {
        val adjustment = weeklyGoalKg * 1100f
        return when (objective) {
            "lose_weight"  -> (tdee - adjustment).toInt().coerceAtLeast(1200)
            "gain_muscle"  -> (tdee + adjustment).toInt()
            else           -> tdee.toInt()
        }
    }

    fun computeTargetProteinGrams(
        weightKg: Float,
        objective: String,
        dietType: String,
        trainingDaysPerWeek: Int = 0
    ): Int {
        if (weightKg <= 0f) return 0

        val baseMultiplier = when (objective) {
            "gain_muscle" -> 1.8f
            "lose_weight" -> 1.6f
            else -> 1.2f
        }
        val dietMultiplier = if (dietType == "high_protein") 1.8f else baseMultiplier
        val trainingMultiplier = if (trainingDaysPerWeek >= 4) {
            (dietMultiplier + 0.2f).coerceAtMost(2.0f)
        } else {
            dietMultiplier
        }

        return (weightKg * trainingMultiplier).toInt().coerceAtLeast(0)
    }
}
