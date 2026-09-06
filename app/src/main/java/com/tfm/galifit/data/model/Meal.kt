package com.tfm.galifit.data.model

import android.view.View
import android.widget.TextView

data class Meal(
    val name: String,
    val description: String? = null,
    val calories: Int? = null,
    val proteinGrams: Int? = null,
    val carbsGrams: Int? = null,
    val fatGrams: Int? = null,
    val ingredients: List<String> = emptyList(),
    val elaboration: List<String> = emptyList(),
    val thumbnailImageUrl: String? = null,
    val detailImageUrl: String? = null,
    val thumbnailLocalPath: String? = null,
    val recipeReferenceUrl: String? = null,
    val isCustom: Boolean = false,
    val customDishId: String? = null,
)

fun Meal.isEditableCustomDish(): Boolean =
    isCustom && !customDishId.isNullOrBlank() && !isProteinSupplementMeal()

fun Meal.formatMacrosText(): String {
    val p = proteinGrams
    val c = carbsGrams
    val f = fatGrams
    if (p == null && c == null && f == null) return ""
    val parts = mutableListOf<String>()
    if (p != null) parts.add("Proteínas: ${p}g")
    if (c != null) parts.add("Carb.: ${c}g")
    if (f != null) parts.add("Grasas: ${f}g")
    return parts.joinToString("   ")
}

fun Meal.bindMacroBadges(proteinView: TextView, carbsView: TextView, fatView: TextView) {
    proteinView.bindMacroBadge(proteinGrams, "Prt.")
    carbsView.bindMacroBadge(carbsGrams, "CH.")
    fatView.bindMacroBadge(fatGrams, "Gr.")
}

private fun TextView.bindMacroBadge(grams: Int?, label: String) {
    if (grams != null) {
        text = "$label ${grams}g"
        visibility = View.VISIBLE
    } else {
        visibility = View.GONE
    }
}
