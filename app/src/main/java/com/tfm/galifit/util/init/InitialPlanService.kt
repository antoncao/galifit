package com.tfm.galifit.util.init

import android.content.Context
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.tfm.galifit.api.APIService
import com.tfm.galifit.api.ApiClient
import com.tfm.galifit.data.model.UserPreferences
import com.tfm.galifit.data.model.cacheMealPlanThumbnails
import com.tfm.galifit.data.model.enrichMealsWithRecipeDetails
import com.tfm.galifit.data.model.toFirebaseSavedMealPlan
import com.tfm.galifit.data.model.toUiDayMealPlans
import com.tfm.galifit.data.model.withProteinSupplementsIfNeeded
import com.tfm.galifit.data.repository.ExercisePlanRepository
import com.tfm.galifit.data.repository.MealPlanRepository
import com.tfm.galifit.domain.usecase.BuildEdamamPlanRequestUseCase
import com.tfm.galifit.domain.usecase.GenerateExerciseRoutineUseCase
import com.tfm.galifit.util.EdamamCredentials
import com.tfm.galifit.util.GalifitFlowLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object InitialPlanService {

    private val buildEdamamPlanRequest = BuildEdamamPlanRequestUseCase()
    private val generateExerciseRoutine = GenerateExerciseRoutineUseCase()
    private val mealPlanRepository = MealPlanRepository()
    private val exercisePlanRepository = ExercisePlanRepository()

    suspend fun generateMealPlan(context: Context, prefs: UserPreferences): Result<Unit> =
        runCatching {
            val uid = Firebase.auth.currentUser?.uid
                ?: throw IllegalStateException("Sin sesión de usuario")

            val body = buildEdamamPlanRequest(prefs)
            val service = ApiClient().getEdamamMealPlanner().create(APIService::class.java)
            val authHeader = EdamamCredentials.basicAuthHeader()
            val accountUser = EdamamCredentials.accountUser

            GalifitFlowLog.api("InitialPlan: solicitando plan semanal a Edamam")
            val response = withContext(Dispatchers.IO) {
                service.generateWeeklyMealPlan(
                    EdamamCredentials.appId,
                    authHeader,
                    accountUser,
                    body
                )
            }
            if (!response.isSuccessful) {
                throw IllegalStateException(
                    "Edamam HTTP ${response.code()}: ${response.errorBody()?.string()}"
                )
            }
            val plan = response.body()
                ?: throw IllegalStateException("Respuesta vacía de Edamam")

            val baseDocument = plan.toFirebaseSavedMealPlan()
            val enrichedDocument = baseDocument.enrichMealsWithRecipeDetails(
                service,
                authHeader,
                accountUser,
                context
            ).withProteinSupplementsIfNeeded(prefs)

            withContext(Dispatchers.IO) {
                enrichedDocument.toUiDayMealPlans().cacheMealPlanThumbnails(context)
            }
            mealPlanRepository.savePlan(uid, enrichedDocument)
            GalifitFlowLog.api("InitialPlan: plan de comidas guardado correctamente")
        }.onFailure {
            GalifitFlowLog.warn("InitialPlan meal: error → ${it.message}")
        }

    suspend fun generateExercisePlan(context: Context, prefs: UserPreferences): Result<Unit> =
        runCatching {
            val uid = Firebase.auth.currentUser?.uid
                ?: throw IllegalStateException("Sin sesión de usuario")

            if (prefs.trainingDaysPerWeek <= 0) {
                GalifitFlowLog.api("InitialPlan exercise: prefs sin trainingDaysPerWeek; se omite")
                throw IllegalStateException("Sin días de entrenamiento configurados")
            }

            GalifitFlowLog.api("InitialPlan: generando rutina semanal (catálogo + motor)")
            val plan = generateExerciseRoutine(context, prefs)
            exercisePlanRepository.savePlan(uid, plan)
            GalifitFlowLog.api("InitialPlan: rutina de ejercicios guardada correctamente")
        }.onFailure {
            GalifitFlowLog.warn("InitialPlan exercise: error → ${it.message}")
        }
}
