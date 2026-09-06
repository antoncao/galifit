package com.tfm.galifit.data.model

data class DayExercisePlan(
    val dayName: String,
    val exercises: MutableList<Exercise> = mutableListOf(),
    val focus: String = "",
    val isRest: Boolean = false
)
