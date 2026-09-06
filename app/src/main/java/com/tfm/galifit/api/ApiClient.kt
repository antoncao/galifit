package com.tfm.galifit.api

import com.google.gson.GsonBuilder
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

class ApiClient {

    public fun getEdamamMealPlanner(): Retrofit {
        val gson = GsonBuilder().create()
        val okHttpClient = OkHttpClient.Builder()
            .addInterceptor(NetworkLoggingInterceptor(tag = "EDAMAM_HTTP"))
            .build()
        return Retrofit.Builder()
            .baseUrl("https://api.edamam.com/")
            .addConverterFactory(GsonConverterFactory.create(gson))
            .client(okHttpClient)
            .build()
    }

    public fun getExerciseDb(): Retrofit {
        val gson = GsonBuilder().create()
        val okHttpClient = OkHttpClient.Builder()
            .addInterceptor(NetworkLoggingInterceptor(tag = "EXERCISEDB_HTTP"))
            .build()
        return Retrofit.Builder()
            .baseUrl("https://cdn.jsdelivr.net/gh/JahelCuadrado/ExerciseGymGifsDB@v1.1.0/api/")
            .addConverterFactory(GsonConverterFactory.create(gson))
            .client(okHttpClient)
            .build()
    }

    public fun getDeepL(apiKey: String): Retrofit {
        val baseUrl = if (apiKey.trim().endsWith(":fx")) {
            "https://api-free.deepl.com/"
        } else {
            "https://api.deepl.com/"
        }
        val gson = GsonBuilder().create()
        val okHttpClient = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val request = chain.request().newBuilder()
                    .addHeader("Authorization", "DeepL-Auth-Key ${apiKey.trim()}")
                    .build()
                chain.proceed(request)
            }
            .addInterceptor(NetworkLoggingInterceptor(tag = "DEEPL_HTTP"))
            .build()
        return Retrofit.Builder()
            .baseUrl(baseUrl)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .client(okHttpClient)
            .build()
    }

    public fun getWger(): Retrofit {
        val gson = GsonBuilder().create()
        val okHttpClient = OkHttpClient.Builder()
            .addInterceptor(NetworkLoggingInterceptor(tag = "WGER_HTTP"))
            .connectTimeout(0, TimeUnit.MILLISECONDS)
            .readTimeout(0, TimeUnit.MILLISECONDS)
            .writeTimeout(0, TimeUnit.MILLISECONDS)
            .callTimeout(0, TimeUnit.MILLISECONDS)
            .build()
        return Retrofit.Builder()
            .baseUrl("https://wger.de/api/v2/")
            .addConverterFactory(GsonConverterFactory.create(gson))
            .client(okHttpClient)
            .build()
    }
}
