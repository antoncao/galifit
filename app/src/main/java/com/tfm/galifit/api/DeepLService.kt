package com.tfm.galifit.api

import com.tfm.galifit.data.model.DeepLTranslateRequest
import com.tfm.galifit.data.model.DeepLTranslateResponse
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.Headers
import retrofit2.http.POST

interface DeepLService {

    @Headers("Content-Type: application/json")
    @POST("v2/translate")
    suspend fun translate(
        @Body body: DeepLTranslateRequest
    ): Response<DeepLTranslateResponse>
}
