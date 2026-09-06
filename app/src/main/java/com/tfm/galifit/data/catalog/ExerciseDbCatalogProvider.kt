package com.tfm.galifit.data.catalog

import android.content.Context
import com.tfm.galifit.api.ApiClient
import com.tfm.galifit.api.ExerciseDbService
import com.tfm.galifit.data.EquipmentKeyMapper
import com.tfm.galifit.data.model.ExerciseDbExercise
import com.tfm.galifit.util.GalifitFlowLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ExerciseDbCatalogProvider(
    context: Context,
    private val service: ExerciseDbService =
        ApiClient().getExerciseDb().create(ExerciseDbService::class.java),
    private val language: String = "es",
    maxAgeMillis: Long = DEFAULT_MAX_AGE_MILLIS
) : CachedExerciseCatalogProvider(context, maxAgeMillis) {

    override val cacheFileName: String = "exercisedb_catalog.json"
    override val sourceTag: String = ExerciseCatalogProviderFactory.SOURCE_EXERCISEDB

    override suspend fun download(): ExerciseCatalog = withContext(Dispatchers.IO) {
        val tDl0 = System.currentTimeMillis()
        val response = service.listExercises(language)
        GalifitFlowLog.api(
            "⏱ [exercisedb] DL: listExercises(lang=$language)=${System.currentTimeMillis() - tDl0}ms " +
                "(crudos=${response.exercises.size})"
        )

        val tMap0 = System.currentTimeMillis()
        val resolved = response.exercises.mapNotNull { it.toCatalogExercise() }
        GalifitFlowLog.api(
            "⏱ [exercisedb] DL: normalizado=${System.currentTimeMillis() - tMap0}ms " +
                "(${response.exercises.size}→${resolved.size})"
        )

        ExerciseCatalog(
            source = sourceTag,
            exercises = resolved,
            cachedAtMillis = System.currentTimeMillis()
        )
    }

    private fun ExerciseDbExercise.toCatalogExercise(): CatalogExercise? {
        val cleanName = name.trim()
        if (cleanName.isBlank()) return null
        val exerciseId = id.takeIf { it.isNotBlank() } ?: slug.takeIf { it.isNotBlank() } ?: return null

        val equipmentKey = EquipmentKeyMapper.keyFromExerciseDbEquipment(equipment)
        val equipmentKeys = if (equipmentKey == null) emptySet() else setOf(equipmentKey)
        val equipmentLabel = if (equipmentKey == null && equipment.lowercase().trim() != "other") {
            "Peso corporal"
        } else {
            EquipmentKeyMapper.labelFromExerciseDbEquipment(equipment)
        }

        val instructionsText = instructions
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .joinToString(" ")
            .take(600)
            .takeIf { it.isNotBlank() }

        return CatalogExercise(
            id = exerciseId,
            name = cleanName,
            muscleGroup = muscle.takeIf { it.isNotBlank() }?.let(::muscleLabel),
            region = BodyRegion.fromExerciseDbMuscle(muscle),
            equipmentKeys = equipmentKeys,
            equipmentLabel = equipmentLabel,
            gifUrl = gifUrl?.takeIf { it.isNotBlank() },
            instructions = instructionsText
        )
    }

    private fun muscleLabel(raw: String): String = when (raw.lowercase().trim()) {
        "abductors" -> "Abductores"
        "adductors" -> "Aductores"
        "abs" -> "Abdomen"
        "biceps" -> "Bíceps"
        "calves" -> "Gemelos"
        "cardio" -> "Cardio"
        "delts" -> "Hombros"
        "forearms" -> "Antebrazos"
        "glutes" -> "Glúteos"
        "hamstrings" -> "Isquiotibiales"
        "lats" -> "Dorsales"
        "levator-scapulae" -> "Cuello"
        "pectorals" -> "Pecho"
        "quads" -> "Cuádriceps"
        "serratus-anterior" -> "Serrato"
        "spine" -> "Lumbares"
        "traps" -> "Trapecio"
        "triceps" -> "Tríceps"
        "upper-back" -> "Espalda alta"
        else -> raw.replaceFirstChar { it.uppercase() }
    }
}
