package com.tfm.galifit.data.model

data class ShoppingListRequest(
    val entries: List<ShoppingListEntry>
)

data class ShoppingListEntry(
    val quantity: Int,
    val measure: String,
    val item: String
)

data class ShoppingListResponse(
    val entries: List<ShoppingListIngredient>
)

data class ShoppingListIngredient(
    val foodId: String,
    val food: String,
    val quantities: List<IngredientQuantity>
)

data class IngredientQuantity(
    val quantity: Double,
    val measure: String,
    val qualifiers: List<String>? = null
)

fun IngredientQuantity.toDisplayText(): String {
    val unit = measureToDisplayUnit(measure)
    val qualifierStr = qualifiers
        ?.map { qualifierToDisplayText(it) }
        ?.joinToString(", ")
        ?.let { " ($it)" }
        ?: ""
    val qty = formatQuantity(quantity)
    return "$qty $unit$qualifierStr"
}

fun ShoppingListIngredient.toSingleLineQuantity(): String {
    val merged = mergeQuantities(quantities)
    return merged.joinToString(" + ") { it.toDisplayText() }
}

private fun formatQuantity(value: Double): String {
    return if (value == value.toLong().toDouble()) {
        value.toLong().toString()
    } else {
        String.format("%.1f", value)
    }
}

private fun mergeQuantities(quantities: List<IngredientQuantity>): List<IngredientQuantity> {
    val grouped = quantities.groupBy { it.measure }
    return grouped.map { (measure, group) ->
        if (group.size == 1) {
            group.first()
        } else {
            val allQualifiers = group.flatMap { it.qualifiers ?: emptyList() }.distinct()
            IngredientQuantity(
                quantity = group.sumOf { it.quantity },
                measure = measure,
                qualifiers = allQualifiers.ifEmpty { null }
            )
        }
    }
}

private val QUALIFIER_MAP = mapOf(
    "Qualifier_small" to "pequeño",
    "Qualifier_medium" to "mediano",
    "Qualifier_large" to "grande",
    "Qualifier_extra_large" to "extra grande",
    "Qualifier_thin" to "fino",
    "Qualifier_thick" to "grueso",
    "Qualifier_whole" to "entero",
    "Qualifier_half" to "medio",
    "Qualifier_quarter" to "cuarto",
    "Qualifier_chopped" to "picado",
    "Qualifier_diced" to "cortado",
    "Qualifier_sliced" to "en rodajas",
    "Qualifier_minced" to "picado fino",
    "Qualifier_fresh" to "fresco",
    "Qualifier_dried" to "seco",
    "Qualifier_frozen" to "congelado",
    "Qualifier_canned" to "en lata",
    "Qualifier_cooked" to "cocinado",
    "Qualifier_raw" to "crudo",
    "Qualifier_boneless" to "sin hueso",
    "Qualifier_skinless" to "sin piel"
)

private fun qualifierToDisplayText(qualifierUri: String): String {
    val fragment = qualifierUri.substringAfterLast("#", "")
    return QUALIFIER_MAP[fragment]
        ?: fragment.removePrefix("Qualifier_").replace("_", " ").ifBlank { qualifierUri }
}

private val MEASURE_MAP = mapOf(
    "Measure_gram" to "g",
    "Measure_kilogram" to "kg",
    "Measure_milliliter" to "ml",
    "Measure_liter" to "l",
    "Measure_unit" to "uds",
    "Measure_ounce" to "oz",
    "Measure_pound" to "lb",
    "Measure_tablespoon" to "cda",
    "Measure_teaspoon" to "cdta",
    "Measure_cup" to "taza",
    "Measure_serving" to "ración",
    "Measure_pinch" to "pizca",
    "Measure_drop" to "gota",
    "Measure_piece" to "uds",
    "Measure_slice" to "rebanada",
    "Measure_stick" to "barra",
    "Measure_clove" to "diente",
    "Measure_handful" to "puñado",
    "Measure_can" to "lata",
    "Measure_package" to "paquete",
    "Measure_bunch" to "manojo",
    "Measure_leaf" to "hoja",
    "Measure_strip" to "tira",
    "Measure_stalk" to "tallo",
    "Measure_sprig" to "ramita",
    "Measure_ear" to "mazorca",
    "Measure_dash" to "chorrito",
    "Measure_jar" to "tarro",
    "Measure_bottle" to "botella",
    "Measure_bag" to "bolsa",
    "Measure_box" to "caja",
    "Measure_head" to "cabeza"
)

fun measureToDisplayUnit(measureUri: String): String {
    val fragment = measureUri.substringAfterLast("#", "")
    return MEASURE_MAP[fragment] ?: fragment.removePrefix("Measure_").ifBlank { "uds" }
}
