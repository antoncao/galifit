package com.tfm.galifit.data.model

data class ExerciseDbExercisesResponse(
    val count: Int = 0,
    val exercises: List<ExerciseDbExercise> = emptyList()
)

data class ExerciseDbExercise(
    val id: String = "",
    val slug: String = "",
    val name: String = "",
    val muscle: String = "",
    val bodyPart: String = "",
    val equipment: String = "",
    val category: String = "",
    val secondaryMuscles: List<String> = emptyList(),
    val instructions: List<String> = emptyList(),
    val gifUrl: String? = null
)
