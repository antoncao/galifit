package com.tfm.galifit.util.routine

import com.tfm.galifit.BuildConfig
import com.tfm.galifit.util.GalifitFlowLog

object RoutineGeneratorFactory {

    fun create(): ExerciseRoutineGenerator {
        val configured = BuildConfig.ROUTINE_GENERATOR.trim().lowercase()
        GalifitFlowLog.api("RoutineGeneratorFactory: ROUTINE_GENERATOR=$configured")
        return when (configured) {
            "gemini" -> GeminiRoutineGenerator()
            "local" -> LocalRoutineGenerator()
            else -> {
                GalifitFlowLog.warn(
                    "RoutineGeneratorFactory: valor no reconocido '$configured', usando local"
                )
                LocalRoutineGenerator()
            }
        }
    }
}
