package com.tfm.galifit.domain.usecase

import android.content.Context
import com.tfm.galifit.data.catalog.ExerciseCatalogProviderFactory
import com.tfm.galifit.data.model.FirebaseSavedExercisePlan
import com.tfm.galifit.data.model.UserPreferences
import com.tfm.galifit.util.routine.RoutineGeneratorFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class GenerateExerciseRoutineUseCase {

    suspend operator fun invoke(
        context: Context,
        prefs: UserPreferences
    ): FirebaseSavedExercisePlan {
        val catalogProvider = ExerciseCatalogProviderFactory.create(context.applicationContext)
        val generator = RoutineGeneratorFactory.create()
        val catalog = withContext(Dispatchers.IO) { catalogProvider.loadCatalog() }
        return withContext(Dispatchers.Default) { generator.generate(prefs, catalog) }
    }
}
