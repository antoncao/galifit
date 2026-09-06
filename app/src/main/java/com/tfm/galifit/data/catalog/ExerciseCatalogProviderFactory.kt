package com.tfm.galifit.data.catalog

import android.content.Context
import com.tfm.galifit.BuildConfig
import com.tfm.galifit.util.GalifitFlowLog

object ExerciseCatalogProviderFactory {

    const val SOURCE_WGER = "wger"

    const val SOURCE_EXERCISEDB = "exercisedb"

    fun create(context: Context): ExerciseCatalogProvider {
        val appContext = context.applicationContext
        val configured = BuildConfig.EXERCISE_CATALOG_SOURCE.trim().lowercase()
        GalifitFlowLog.api("ExerciseCatalogProviderFactory: EXERCISE_CATALOG_SOURCE=$configured")
        return when (configured) {
            SOURCE_WGER -> WgerCatalogProvider(appContext)
            SOURCE_EXERCISEDB -> ExerciseDbCatalogProvider(appContext)
            else -> {
                GalifitFlowLog.warn(
                    "ExerciseCatalogProviderFactory: valor no reconocido '$configured', usando exercisedb"
                )
                ExerciseDbCatalogProvider(appContext)
            }
        }
    }
}
