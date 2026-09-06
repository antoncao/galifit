package com.tfm.galifit.util.routine

import com.tfm.galifit.data.catalog.ExerciseCatalog
import com.tfm.galifit.data.model.FirebaseSavedExercisePlan
import com.tfm.galifit.data.model.UserPreferences

interface ExerciseRoutineGenerator {

    suspend fun generate(prefs: UserPreferences, catalog: ExerciseCatalog): FirebaseSavedExercisePlan
}
