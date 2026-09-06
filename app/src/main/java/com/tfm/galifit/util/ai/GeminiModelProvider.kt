package com.tfm.galifit.util.ai

import android.content.Context
import com.google.firebase.Firebase
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.ai.GenerativeModel
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.GenerativeBackend
import com.google.firebase.ai.type.content
import com.tfm.galifit.BuildConfig
import com.tfm.galifit.util.GalifitFlowLog

object GeminiModelProvider {

    const val MODEL_NAME = "gemini-flash-latest"

    private const val SECONDARY_APP_NAME = "gemini"

    @Volatile
    private var initialized = false

    @Volatile
    private var useSecondary = false

    fun initialize(context: Context) {
        if (initialized) return
        synchronized(this) {
            if (initialized) return

            val projectId = BuildConfig.GEMINI_PROJECT_ID.trim()
            val appId = BuildConfig.GEMINI_APP_ID.trim()
            val apiKey = BuildConfig.GEMINI_API_KEY.trim()

            if (projectId.isNotEmpty() && appId.isNotEmpty() && apiKey.isNotEmpty()) {
                runCatching { FirebaseApp.getInstance(SECONDARY_APP_NAME) }
                    .recoverCatching {
                        val options = FirebaseOptions.Builder()
                            .setProjectId(projectId)
                            .setApplicationId(appId)
                            .setApiKey(apiKey)
                            .build()
                        FirebaseApp.initializeApp(context.applicationContext, options, SECONDARY_APP_NAME)
                    }
                    .onSuccess {
                        useSecondary = true
                        GalifitFlowLog.api("Gemini: usando proyecto secundario (cuenta B) '$projectId'")
                    }
                    .onFailure {
                        useSecondary = false
                        GalifitFlowLog.warn(
                            "Gemini: no se pudo inicializar la cuenta B (${it.message}); se usa el proyecto por defecto"
                        )
                    }
            } else {
                GalifitFlowLog.api(
                    "Gemini: sin credenciales de cuenta B; se usa el proyecto por defecto (cuenta A)"
                )
            }
            initialized = true
        }
    }

    private fun aiApp(): FirebaseApp =
        if (useSecondary) {
            runCatching { FirebaseApp.getInstance(SECONDARY_APP_NAME) }
                .getOrElse { FirebaseApp.getInstance() }
        } else {
            FirebaseApp.getInstance()
        }

    fun generativeModel(systemInstruction: String? = null): GenerativeModel {
        val ai = Firebase.ai(app = aiApp(), backend = GenerativeBackend.googleAI())
        return if (systemInstruction != null) {
            ai.generativeModel(
                modelName = MODEL_NAME,
                systemInstruction = content { text(systemInstruction) }
            )
        } else {
            ai.generativeModel(MODEL_NAME)
        }
    }
}
