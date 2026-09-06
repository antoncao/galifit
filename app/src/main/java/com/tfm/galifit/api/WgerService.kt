package com.tfm.galifit.api

import com.tfm.galifit.data.model.WgerCategory
import com.tfm.galifit.data.model.WgerEquipment
import com.tfm.galifit.data.model.WgerExerciseInfo
import com.tfm.galifit.data.model.WgerLanguage
import com.tfm.galifit.data.model.WgerMuscle
import com.tfm.galifit.data.model.WgerPaginatedResponse
import retrofit2.http.GET
import retrofit2.http.Query

interface WgerService {

    @GET("exerciseinfo/")
    suspend fun listExercises(
        @Query("limit") limit: Int = 200,
        @Query("offset") offset: Int = 0,
        @Query("category") category: Int? = null,
        @Query("equipment") equipment: String? = null,
        @Query("muscles") muscles: Int? = null
    ): WgerPaginatedResponse<WgerExerciseInfo>

    @GET("equipment/")
    suspend fun listEquipment(@Query("limit") limit: Int = 100): WgerPaginatedResponse<WgerEquipment>

    @GET("exercisecategory/")
    suspend fun listCategories(@Query("limit") limit: Int = 100): WgerPaginatedResponse<WgerCategory>

    @GET("muscle/")
    suspend fun listMuscles(@Query("limit") limit: Int = 100): WgerPaginatedResponse<WgerMuscle>

    @GET("language/")
    suspend fun listLanguages(@Query("limit") limit: Int = 100): WgerPaginatedResponse<WgerLanguage>
}
