package com.tfm.galifit.data.repository

import com.google.firebase.Firebase
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.firestore
import com.tfm.galifit.data.model.FirebaseSavedExercisePlan
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

class ExercisePlanRepository {

    private fun plansRef(uid: String): CollectionReference =
        Firebase.firestore.collection("users").document(uid).collection("exercisePlans")

    suspend fun savePlan(uid: String, plan: FirebaseSavedExercisePlan): String =
        withContext(Dispatchers.IO) {
            plansRef(uid).add(plan).await().id
        }
}
