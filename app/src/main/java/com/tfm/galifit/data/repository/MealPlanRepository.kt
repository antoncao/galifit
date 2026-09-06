package com.tfm.galifit.data.repository

import com.google.firebase.Firebase
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.Source
import com.google.firebase.firestore.firestore
import com.tfm.galifit.data.model.FirebaseSavedMealPlan
import com.tfm.galifit.util.GalifitFlowLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

class MealPlanRepository {

    private fun plansRef(uid: String): CollectionReference =
        Firebase.firestore.collection("users").document(uid).collection("mealPlans")

    suspend fun savePlan(uid: String, plan: FirebaseSavedMealPlan): String =
        withContext(Dispatchers.IO) {
            plansRef(uid).add(plan).await().id
        }

    suspend fun updatePlan(uid: String, planId: String, plan: FirebaseSavedMealPlan) {
        withContext(Dispatchers.IO) {
            plansRef(uid).document(planId).set(plan).await()
        }
    }

    suspend fun getLatestPlanWithId(uid: String): Pair<String, FirebaseSavedMealPlan>? =
        withContext(Dispatchers.IO) {
            val latestQuery = plansRef(uid)
                .orderBy("createdAtMillis", Query.Direction.DESCENDING)
                .limit(1)

            val cacheSnapshot = runCatching { latestQuery.get(Source.CACHE).await() }
                .onFailure { GalifitFlowLog.warn("MealPlanRepository: lectura CACHE falló ${it.message}") }
                .getOrNull()

            val snapshot = if (cacheSnapshot != null && !cacheSnapshot.isEmpty) {
                cacheSnapshot
            } else {
                withTimeoutOrNull(SERVER_READ_TIMEOUT_MS) {
                    latestQuery.get(Source.SERVER).await()
                } ?: cacheSnapshot
            }

            val doc = snapshot?.documents?.firstOrNull() ?: return@withContext null
            val plan = doc.toObject(FirebaseSavedMealPlan::class.java) ?: return@withContext null
            doc.id to plan
        }

    companion object {

        const val SERVER_READ_TIMEOUT_MS = 8_000L

        const val SERVER_WRITE_TIMEOUT_MS = 12_000L
    }
}
