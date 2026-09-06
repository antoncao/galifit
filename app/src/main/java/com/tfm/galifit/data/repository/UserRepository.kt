package com.tfm.galifit.data.repository

import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.firestore
import com.tfm.galifit.data.model.UserPreferences
import com.tfm.galifit.util.toUserPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

class UserRepository {

    private val auth get() = Firebase.auth
    private val firestore get() = Firebase.firestore

    fun currentUid(): String? = auth.currentUser?.uid

    suspend fun getPreferences(uid: String? = currentUid()): UserPreferences? {
        val userId = uid ?: return null
        return withContext(Dispatchers.IO) {
            firestore.collection("users")
                .document(userId)
                .get()
                .await()
                .toUserPreferences()
        }
    }

    suspend fun savePreferences(uid: String, preferences: Map<String, Any?>) {
        withContext(Dispatchers.IO) {
            firestore.collection("users")
                .document(uid)
                .set(preferences, com.google.firebase.firestore.SetOptions.merge())
                .await()
        }
    }
}
