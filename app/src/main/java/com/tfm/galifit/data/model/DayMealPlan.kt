package com.tfm.galifit.data.model

data class DayMealPlan(
    val dayName: String,
    val meals: MutableList<Meal> = mutableListOf()
)
