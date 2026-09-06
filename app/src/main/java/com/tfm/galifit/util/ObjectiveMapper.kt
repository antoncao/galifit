package com.tfm.galifit.util

object ObjectiveMapper {

    val BODY_OBJECTIVE_LABELS = arrayOf(
        "Perder grasa",
        "Ganar músculo",
        "Estar en forma"
    )

    val BODY_OBJECTIVE_KEYS = arrayOf("lose_weight", "gain_muscle", "maintain")

    fun nutritionObjective(bodyObjective: String): String = when (bodyObjective) {
        "lose_weight", "lose_fat" -> "lose_weight"
        "gain_muscle" -> "gain_muscle"
        "maintain", "stay_fit", "eat_healthier", "specific_diet" -> "maintain"
        else -> "maintain"
    }

    fun trainingObjective(bodyObjective: String): String = when (nutritionObjective(bodyObjective)) {
        "lose_weight" -> "fat_loss"
        "gain_muscle" -> "hypertrophy"
        else -> "mobility"
    }

    fun needsWeeklyWeightGoal(bodyObjective: String): Boolean {
        val key = nutritionObjective(bodyObjective)
        return key == "lose_weight" || key == "gain_muscle"
    }

    fun isMuscleGain(bodyObjective: String): Boolean =
        nutritionObjective(bodyObjective) == "gain_muscle"
}
