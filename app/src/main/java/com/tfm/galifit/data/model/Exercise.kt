package com.tfm.galifit.data.model

data class Exercise(
    val name: String = "",
    val details: String = "",
    val thumbnailImageUrl: String? = null,
    val catalogExerciseId: String? = null,
    val wgerExerciseId: Int? = null,
    val muscleGroup: String? = null,
    val equipmentLabel: String? = null,
    val sets: Int = 0,
    val repsLow: Int = 0,
    val repsHigh: Int = 0,
    val restSeconds: Int = 0,
    val instructions: String? = null,
    val repsOrDuration: String? = null,
    val measurementType: String = MEASUREMENT_REPS,
    val userNotes: String? = null
) {
    companion object {

        const val MEASUREMENT_REPS = "reps"

        const val MEASUREMENT_DURATION = "duration"
    }
}
