package com.tfm.galifit.data.model

import com.google.firebase.firestore.IgnoreExtraProperties

@IgnoreExtraProperties
class FirebaseSavedExercisePlan {

    var createdAtMillis: Long = 0L

    var source: String = "wger_local_generator"

    var planSummary: PlanSummary = PlanSummary()

    var days: List<FirebaseDayExercisePlan> = emptyList()
}

@IgnoreExtraProperties
class PlanSummary {

    var daysPerWeek: Int = 0

    var split: String = ""

    var objective: String = ""

    var level: String = ""

    var location: String = ""
}

@IgnoreExtraProperties
class FirebaseDayExercisePlan {

    var dayIndex: Long = 0L

    var dayName: String = ""

    var focus: String = ""

    var isRest: Boolean = false

    var exercises: List<FirebaseSavedExercise> = emptyList()
}

@IgnoreExtraProperties
class FirebaseSavedExercise {
    var name: String = ""

    var details: String = ""

    var thumbnailImageUrl: String? = null

    var catalogExerciseId: String? = null

    var wgerExerciseId: Long? = null
    var muscleGroup: String? = null
    var equipmentLabel: String? = null
    var sets: Long = 0L
    var repsLow: Long = 0L
    var repsHigh: Long = 0L
    var restSeconds: Long = 0L
    var instructions: String? = null

    var repsOrDuration: String? = null

    var measurementType: String = Exercise.MEASUREMENT_REPS

    var userNotes: String? = null
}

fun FirebaseSavedExercisePlan.toUiDayExercisePlans(): List<DayExercisePlan> {
    if (days.isEmpty()) return emptyList()
    return days.map { day ->
        val label = day.dayName.takeIf { it.isNotBlank() } ?: "Día ${day.dayIndex + 1}"
        DayExercisePlan(
            dayName = label,
            exercises = day.exercises.map { it.toUi() }.toMutableList(),
            focus = day.focus,
            isRest = day.isRest
        )
    }
}

private fun FirebaseSavedExercise.toUi(): Exercise = Exercise(
    name = name,
    details = details,
    thumbnailImageUrl = thumbnailImageUrl,
    catalogExerciseId = catalogExerciseId,
    wgerExerciseId = wgerExerciseId?.toInt(),
    muscleGroup = muscleGroup,
    equipmentLabel = equipmentLabel,
    sets = sets.toInt(),
    repsLow = repsLow.toInt(),
    repsHigh = repsHigh.toInt(),
    restSeconds = restSeconds.toInt(),
    instructions = instructions,
    repsOrDuration = repsOrDuration,
    measurementType = measurementType.ifBlank { Exercise.MEASUREMENT_REPS },
    userNotes = userNotes
)

fun Exercise.toFirebase(): FirebaseSavedExercise {
    val src = this
    return FirebaseSavedExercise().apply {
        name = src.name
        details = src.details
        thumbnailImageUrl = src.thumbnailImageUrl
        catalogExerciseId = src.catalogExerciseId
        wgerExerciseId = src.wgerExerciseId?.toLong()
        muscleGroup = src.muscleGroup
        equipmentLabel = src.equipmentLabel
        sets = src.sets.toLong()
        repsLow = src.repsLow.toLong()
        repsHigh = src.repsHigh.toLong()
        restSeconds = src.restSeconds.toLong()
        instructions = src.instructions
        repsOrDuration = src.repsOrDuration
        measurementType = src.measurementType.ifBlank { Exercise.MEASUREMENT_REPS }
        userNotes = src.userNotes
    }
}

fun defaultEmptyWeekDayExercisePlans(): List<DayExercisePlan> =
    listOf("Lunes", "Martes", "Miércoles", "Jueves", "Viernes", "Sábado", "Domingo")
        .map { DayExercisePlan(it, mutableListOf()) }
