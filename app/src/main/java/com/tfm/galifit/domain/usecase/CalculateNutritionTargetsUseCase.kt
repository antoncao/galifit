package com.tfm.galifit.domain.usecase

import com.tfm.galifit.data.model.UserPreferences
import com.tfm.galifit.util.CalorieCalculator

data class NutritionTargets(
    val tmb: Float,
    val tdee: Float,
    val targetCalories: Int,
    val targetProteinGrams: Int
)

class CalculateNutritionTargetsUseCase {

    operator fun invoke(
        sex: String,
        weightKg: Float,
        heightCm: Float,
        age: Int,
        activityMultiplier: Float,
        objective: String,
        weeklyWeightGoalKg: Float,
        dietType: String,
        trainingDaysPerWeek: Int
    ): NutritionTargets {
        val tmb = CalorieCalculator.computeTmb(sex, weightKg, heightCm, age)
        val tdee = CalorieCalculator.computeTdee(tmb, activityMultiplier)
        val targetCalories = CalorieCalculator.computeTargetCalories(tdee, objective, weeklyWeightGoalKg)
        val targetProteinGrams = CalorieCalculator.computeTargetProteinGrams(
            weightKg = weightKg,
            objective = objective,
            dietType = dietType,
            trainingDaysPerWeek = trainingDaysPerWeek
        )
        return NutritionTargets(tmb, tdee, targetCalories, targetProteinGrams)
    }

    operator fun invoke(prefs: UserPreferences): NutritionTargets = invoke(
        sex = prefs.sex,
        weightKg = prefs.weightKg,
        heightCm = prefs.heightCm,
        age = prefs.age,
        activityMultiplier = prefs.activityMultiplier,
        objective = prefs.objective,
        weeklyWeightGoalKg = prefs.weeklyWeightGoalKg,
        dietType = prefs.dietType,
        trainingDaysPerWeek = prefs.trainingDaysPerWeek
    )
}
