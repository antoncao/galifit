package com.tfm.galifit.api

import com.tfm.galifit.data.model.EdamamMealPlanResponse
import com.tfm.galifit.data.model.EdamamPlanRequest
import com.tfm.galifit.data.model.EdamamRecipeDetailResponse
import com.tfm.galifit.data.model.ShoppingListRequest
import com.tfm.galifit.data.model.ShoppingListResponse
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Headers
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query
import retrofit2.http.Url

interface APIService {

    @Headers("Content-Type: application/json")
    @POST("api/meal-planner/v1/{appId}/select")
    suspend fun generateWeeklyMealPlan(
        @Path("appId") appId: String,
        @Header("Authorization") authHeader: String,
        @Header("Edamam-Account-User") edamamAccountUser: String,
        @Body body: EdamamPlanRequest
    ): Response<EdamamMealPlanResponse>

    @GET
    suspend fun getRecipeByUrl(
        @Url url: String,
        @Header("Authorization") authHeader: String,
        @Header("Edamam-Account-User") edamamAccountUser: String
    ): Response<EdamamRecipeDetailResponse>

    @Headers("Content-Type: application/json")
    @POST("api/shopping-list/v2")
    suspend fun getShoppingList(
        @Header("Edamam-Account-User") edamamAccountUser: String,
        @Query("app_id") appId: String,
        @Query("app_key") appKey: String,
        @Body body: ShoppingListRequest
    ): Response<ShoppingListResponse>
}
