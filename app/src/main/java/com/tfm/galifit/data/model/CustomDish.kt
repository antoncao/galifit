package com.tfm.galifit.data.model

import com.google.firebase.firestore.IgnoreExtraProperties

@IgnoreExtraProperties
class CustomDish {

    var id: String = ""

    var name: String = ""

    var calories: Int = 0

    var proteins: Int = 0

    var carbs: Int = 0

    var fats: Int = 0

    var ingredients: List<String> = emptyList()

    var elaboration: List<String> = emptyList()

    var imageUrl: String? = null

    var createdAtMillis: Long = 0L
}

fun CustomDish.toMeal(): Meal = Meal(
    name = name,
    calories = calories,
    proteinGrams = proteins,
    carbsGrams = carbs,
    fatGrams = fats,
    ingredients = ingredients,
    elaboration = elaboration,
    thumbnailImageUrl = imageUrl?.takeIf { it.isNotBlank() },
    detailImageUrl = imageUrl?.takeIf { it.isNotBlank() },
    isCustom = true,
    customDishId = id
)
