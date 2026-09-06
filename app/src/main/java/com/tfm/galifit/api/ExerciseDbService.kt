package com.tfm.galifit.api

import com.tfm.galifit.data.model.ExerciseDbExercisesResponse
import retrofit2.http.GET
import retrofit2.http.Path

interface ExerciseDbService {

    @GET("{lang}/exercises.json")
    suspend fun listExercises(@Path("lang") lang: String = "es"): ExerciseDbExercisesResponse
}
