package com.tfm.galifit.data.model

object EdamamHealthCatalog {

    data class HealthCondition(
        val key: String,
        val displayName: String,
        val edamamLabels: List<String>
    ) {
        fun displayLabel(): String {
            val tags = edamamLabels
                .map { labelDisplayName(it) }
                .filter { !it.equals(displayName, ignoreCase = true) }
            if (tags.isEmpty()) return displayName
            return "$displayName (${tags.joinToString(", ")})"
        }
    }

    val conditions: List<HealthCondition> = listOf(
        HealthCondition(
            key = "hypertension",
            displayName = "Hipertensión",
            edamamLabels = listOf("DASH", "LOW_SODIUM")
        ),
        HealthCondition(
            key = "diabetes",
            displayName = "Diabetes",
            edamamLabels = listOf("SUGAR_CONSCIOUS", "LOW_SUGAR")
        ),
        HealthCondition(
            key = "kidney",
            displayName = "Salud renal",
            edamamLabels = listOf("KIDNEY_FRIENDLY", "LOW_POTASSIUM")
        ),
        HealthCondition(
            key = "celiac",
            displayName = "Celiaquía",
            edamamLabels = listOf("GLUTEN_FREE", "WHEAT_FREE")
        ),
        HealthCondition(
            key = "fodmap",
            displayName = "Digestivo",
            edamamLabels = listOf("FODMAP_FREE")
        ),
        HealthCondition(
            key = "immune",
            displayName = "Apoyo inmunológico",
            edamamLabels = listOf("IMMUNO_SUPPORTIVE")
        )
    )

    val allHealthLabels: List<String> = listOf(
        "ALCOHOL_FREE",
        "CELERY_FREE",
        "CRUSTACEAN_FREE",
        "DAIRY_FREE",
        "DASH",
        "EGG_FREE",
        "FISH_FREE",
        "FODMAP_FREE",
        "GLUTEN_FREE",
        "IMMUNO_SUPPORTIVE",
        "KETO_FRIENDLY",
        "KIDNEY_FRIENDLY",
        "LOW_POTASSIUM",
        "LOW_SODIUM",
        "LOW_SUGAR",
        "LUPINE_FREE",
        "MEDITERRANEAN",
        "MOLLUSK_FREE",
        "MUSTARD_FREE",
        "PALEO",
        "PESCATARIAN",
        "PEANUT_FREE",
        "PORK_FREE",
        "RED_MEAT_FREE",
        "SESAME_FREE",
        "SHELLFISH_FREE",
        "SOY_FREE",
        "SUGAR_CONSCIOUS",
        "SULFITE_FREE",
        "TREE_NUT_FREE",
        "VEGAN",
        "VEGETARIAN",
        "WHEAT_FREE"
    ).distinct()

    val allergyRestrictionLabels: List<String> = listOf(
        "GLUTEN_FREE",
        "WHEAT_FREE",
        "DAIRY_FREE",
        "EGG_FREE",
        "SOY_FREE",
        "TREE_NUT_FREE",
        "PEANUT_FREE",
        "SHELLFISH_FREE",
        "CRUSTACEAN_FREE",
        "MOLLUSK_FREE",
        "CELERY_FREE",
        "MUSTARD_FREE",
        "SESAME_FREE",
        "LUPINE_FREE",
        "SULFITE_FREE",
        "FISH_FREE"
    )

    val dietDerivedHealthLabels: Set<String> = setOf(
        "VEGAN",
        "VEGETARIAN",
        "PESCATARIAN",
        "MEDITERRANEAN",
        "KETO_FRIENDLY",
        "PALEO"
    )

    val avoidFoodHealthLabels: Set<String> = setOf(
        "PORK_FREE",
        "RED_MEAT_FREE",
        "FISH_FREE",
        "CRUSTACEAN_FREE",
        "MOLLUSK_FREE",
        "MUSTARD_FREE",
        "SESAME_FREE",
        "LUPINE_FREE",
        "SULFITE_FREE",
        "ALCOHOL_FREE"
    )

    val conditionDerivedLabels: Set<String> =
        conditions.flatMap { it.edamamLabels }.map { it.uppercase() }.toSet()

    val additionalSelectableLabels: List<String> = allHealthLabels
        .filter { it !in allergyRestrictionLabels }
        .filter { it !in conditionDerivedLabels }
        .filter { it !in dietDerivedHealthLabels }
        .filter { it !in avoidFoodHealthLabels }
        .sorted()

    @Deprecated("Usar additionalSelectableLabels")
    val manualSelectableLabels: List<String> = additionalSelectableLabels

    private val labelNamesEs: Map<String, String> = mapOf(
        "ALCOHOL_FREE" to "Sin alcohol",
        "CELERY_FREE" to "Sin apio",
        "CRUSTACEAN_FREE" to "Sin crustáceos",
        "DAIRY_FREE" to "Sin lácteos",
        "DASH" to "Enfoque DASH",
        "EGG_FREE" to "Sin huevo",
        "FISH_FREE" to "Sin pescado",
        "FODMAP_FREE" to "Sin FODMAP",
        "GLUTEN_FREE" to "Sin gluten",
        "IMMUNO_SUPPORTIVE" to "Apoyo inmunológico",
        "KETO_FRIENDLY" to "Apto keto",
        "KIDNEY_FRIENDLY" to "Apto riñón",
        "LOW_POTASSIUM" to "Bajo en potasio",
        "LOW_SODIUM" to "Bajo en sodio",
        "LOW_SUGAR" to "Bajo en azúcar",
        "LUPINE_FREE" to "Sin altramuz",
        "MEDITERRANEAN" to "Mediterránea",
        "MOLLUSK_FREE" to "Sin moluscos",
        "MUSTARD_FREE" to "Sin mostaza",
        "PALEO" to "Paleo",
        "PESCATARIAN" to "Pescetariana",
        "PEANUT_FREE" to "Sin cacahuete",
        "PORK_FREE" to "Sin cerdo",
        "RED_MEAT_FREE" to "Sin carne roja",
        "SESAME_FREE" to "Sin sésamo",
        "SHELLFISH_FREE" to "Sin mariscos",
        "SOY_FREE" to "Sin soja",
        "SUGAR_CONSCIOUS" to "Azúcar limitado",
        "SULFITE_FREE" to "Sin sulfitos",
        "TREE_NUT_FREE" to "Sin frutos secos",
        "VEGAN" to "Vegana",
        "VEGETARIAN" to "Vegetariana",
        "WHEAT_FREE" to "Sin trigo"
    )

    fun labelDisplayName(label: String): String =
        labelNamesEs[label.trim().uppercase()]
            ?: label.trim().uppercase().replace('_', ' ').lowercase()
                .replaceFirstChar { it.uppercase() }

    fun labelsForConditions(conditionKeys: List<String>): List<String> =
        conditionKeys.flatMap { key ->
            conditions.find { it.key == key }?.edamamLabels.orEmpty()
        }

    fun resolveHealthLabels(prefs: UserPreferences): List<String> {
        val labels = mutableListOf<String>()

        labels.add("ALCOHOL_FREE")

        when (prefs.dietType) {
            "vegetarian" -> labels.add("VEGETARIAN")
            "vegan" -> labels.add("VEGAN")
            "pescatarian" -> labels.add("PESCATARIAN")
            "mediterranean" -> labels.add("MEDITERRANEAN")
            "keto" -> labels.add("KETO_FRIENDLY")
            "paleo" -> labels.add("PALEO")
        }

        labels.addAll(prefs.restrictions.map { it.trim().uppercase() })
        labels.addAll(prefs.avoidedFoods.map { it.trim().uppercase() })
        labels.addAll(labelsForConditions(prefs.healthConditions))
        labels.addAll(prefs.healthLabels.map { it.trim().uppercase() })

        return labels
            .filter { it.isNotBlank() }
            .distinct()
    }

    fun normalizeLegacyPaleoPreference(prefs: UserPreferences): UserPreferences {
        val remainingLabels = prefs.healthLabels.filter { it.trim().uppercase() != "PALEO" }
        val hadPaleoLabel = remainingLabels.size != prefs.healthLabels.size
        if (!hadPaleoLabel) return prefs
        val dietUnset = prefs.dietType.isBlank() || prefs.dietType.equals("none", ignoreCase = true)
        return prefs.copy(
            dietType = if (dietUnset) "paleo" else prefs.dietType,
            healthLabels = remainingLabels
        )
    }
}
