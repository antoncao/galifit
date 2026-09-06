package com.tfm.galifit.data.catalog

import com.tfm.galifit.data.EquipmentKeyMapper

data class ExerciseCatalog(
    val source: String = "",
    val exercises: List<CatalogExercise> = emptyList(),
    val cachedAtMillis: Long = 0L
)

data class CatalogExercise(
    val id: String = "",
    val name: String = "",
    val muscleGroup: String? = null,
    val region: BodyRegion = BodyRegion.OTHER,
    val equipmentKeys: Set<String> = emptySet(),
    val equipmentLabel: String = "Peso corporal",
    val gifUrl: String? = null,
    val instructions: String? = null
) {

    val requiresNoEquipment: Boolean get() = equipmentKeys.isEmpty()

    fun isAvailableFor(userKeys: Set<String>, allowMachines: Boolean): Boolean = when {
        requiresNoEquipment -> true
        EquipmentKeyMapper.MACHINE in equipmentKeys -> allowMachines
        else -> equipmentKeys.any { it in userKeys }
    }
}

enum class BodyRegion {
    CHEST,
    BACK,
    SHOULDERS,
    ARMS,
    LEGS,
    ABS,
    CALVES,
    CARDIO,
    OTHER;

    companion object {

        fun fromExerciseDbMuscle(raw: String): BodyRegion = when (raw.lowercase().trim()) {
            "pectorals" -> CHEST
            "lats", "upper-back", "traps", "spine" -> BACK
            "delts", "levator-scapulae", "serratus-anterior" -> SHOULDERS
            "biceps", "triceps", "forearms" -> ARMS
            "quads", "hamstrings", "glutes", "adductors", "abductors" -> LEGS
            "calves" -> CALVES
            "abs" -> ABS
            "cardio" -> CARDIO
            else -> OTHER
        }

        fun fromWgerCategory(raw: String): BodyRegion = when (raw.lowercase().trim()) {
            "chest" -> CHEST
            "back" -> BACK
            "shoulders" -> SHOULDERS
            "arms" -> ARMS
            "legs" -> LEGS
            "abs" -> ABS
            "calves" -> CALVES
            "cardio" -> CARDIO
            else -> OTHER
        }
    }
}
