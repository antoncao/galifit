package com.tfm.galifit.data.repository

import android.graphics.Bitmap
import com.google.firebase.Firebase
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.Source
import com.google.firebase.firestore.firestore
import com.google.firebase.storage.storage
import com.tfm.galifit.data.model.CustomDish
import com.tfm.galifit.data.model.FirebaseMealPlanDay
import com.tfm.galifit.data.model.FirebasePlannedMealSummary
import com.tfm.galifit.data.model.FirebaseSavedMealPlan
import com.tfm.galifit.data.model.MealMacrosSummary
import com.tfm.galifit.util.GalifitFlowLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.ByteArrayOutputStream

class CustomDishRepository {

    suspend fun saveDishFromBitmap(
        uid: String,
        name: String,
        calories: Int,
        proteins: Int,
        carbs: Int,
        fats: Int,
        bitmap: Bitmap?
    ): CustomDish = withContext(Dispatchers.IO) {
        val dishesRef = Firebase.firestore.collection("users")
            .document(uid)
            .collection("customDishes")
        val docRef = dishesRef.document()
        val dishId = docRef.id

        val imageUrl = if (bitmap != null) {
            runCatching { uploadBitmap(uid, dishId, bitmap) }
                .onFailure { GalifitFlowLog.warn("CustomDishRepository: subida imagen falló ${it.message}") }
                .getOrNull()
        } else null

        val dish = CustomDish().apply {
            id = dishId
            this.name = name
            this.calories = calories
            this.proteins = proteins
            this.carbs = carbs
            this.fats = fats
            this.imageUrl = imageUrl
            createdAtMillis = System.currentTimeMillis()
        }
        docRef.set(dish).await()
        GalifitFlowLog.firestore("CustomDishRepository: dish creado id=$dishId imagen=${imageUrl != null}")
        dish
    }

    suspend fun addDishToWeeklyPlan(
        uid: String,
        dish: CustomDish,
        dayIndex: Int
    ) = withContext(Dispatchers.IO) {
        val t0 = System.currentTimeMillis()
        val plansRef = Firebase.firestore.collection("users")
            .document(uid)
            .collection("mealPlans")

        val latestQuery = plansRef.orderBy("createdAtMillis", Query.Direction.DESCENDING).limit(1)
        GalifitFlowLog.firestore("CustomDishRepository: añadir dish=${dish.id} día=$dayIndex al último mealPlan")

        val cacheSnapshot = runCatching { latestQuery.get(Source.CACHE).await() }
            .onSuccess {
                GalifitFlowLog.firestore(
                    "CustomDishRepository: mealPlan CACHE docs=${it.size()} en ${System.currentTimeMillis() - t0}ms"
                )
            }
            .onFailure {
                GalifitFlowLog.warn("CustomDishRepository: lectura CACHE falló ${it.message}")
            }
            .getOrNull()

        val snapshot = if (cacheSnapshot != null && !cacheSnapshot.isEmpty) {
            cacheSnapshot
        } else {
            GalifitFlowLog.firestore("CustomDishRepository: CACHE vacío, probando SERVER con timeout")
            withTimeoutOrNull(SERVER_READ_TIMEOUT_MS) {
                latestQuery.get(Source.SERVER).await()
            }?.also {
                GalifitFlowLog.firestore(
                    "CustomDishRepository: mealPlan SERVER docs=${it.size()} en ${System.currentTimeMillis() - t0}ms"
                )
            } ?: cacheSnapshot.also {
                GalifitFlowLog.warn(
                    "CustomDishRepository: SERVER no respondió en ${SERVER_READ_TIMEOUT_MS}ms; se usará plan vacío"
                )
            }
        }

        val doc = snapshot?.documents?.firstOrNull()
        val plan = doc?.toObject(FirebaseSavedMealPlan::class.java) ?: emptyPlan()
        ensureSevenDays(plan)

        val targetDay = plan.days[dayIndex.coerceIn(0, plan.days.size - 1)]
        targetDay.meals = targetDay.meals + dish.toPlannedSummary()

        if (doc != null) {
            withTimeoutOrNull(SERVER_WRITE_TIMEOUT_MS) {
                plansRef.document(doc.id).set(plan).await()
                Unit
            } ?: error("Timeout guardando el plan de comidas")
            GalifitFlowLog.firestore("CustomDishRepository: plan actualizado id=${doc.id} día=$dayIndex")
        } else {
            val newRef = withTimeoutOrNull(SERVER_WRITE_TIMEOUT_MS) {
                plansRef.add(plan).await()
            } ?: error("Timeout creando el plan de comidas")
            GalifitFlowLog.firestore(
                "CustomDishRepository: plan creado id=${newRef.id} día=$dayIndex (no existía plan previo)"
            )
        }
        GalifitFlowLog.firestore(
            "CustomDishRepository: addDishToWeeklyPlan OK total=${System.currentTimeMillis() - t0}ms"
        )
    }

    private suspend fun uploadBitmap(uid: String, dishId: String, bitmap: Bitmap): String {
        val bytes = ByteArrayOutputStream().use { stream ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 85, stream)
            stream.toByteArray()
        }
        val ref = Firebase.storage.reference.child("users/$uid/customDishes/$dishId.jpg")
        ref.putBytes(bytes).await()
        return ref.downloadUrl.await().toString()
    }

    private fun emptyPlan(): FirebaseSavedMealPlan = FirebaseSavedMealPlan().apply {
        createdAtMillis = System.currentTimeMillis()
        source = "user_manual"
        edamamStatus = null
        days = emptyList()
    }

    private fun ensureSevenDays(plan: FirebaseSavedMealPlan) {
        if (plan.days.size >= 7) return
        val existing = plan.days.associateBy { it.dayIndex.toInt() }
        plan.days = (0 until 7).map { idx ->
            existing[idx] ?: FirebaseMealPlanDay().apply {
                dayIndex = idx.toLong()
                dayLabel = WEEK_DAYS[idx]
                meals = emptyList()
            }
        }
    }

    private fun CustomDish.toPlannedSummary(): FirebasePlannedMealSummary {
        val src = this
        val img = src.imageUrl?.takeIf { it.isNotBlank() }
        return FirebasePlannedMealSummary().apply {
            sectionKey = "Custom"
            name = src.name
            recipeReferenceUrl = ""
            assignedRecipeUri = null
            thumbnailImageUrl = img
            detailImageUrl = img
            imageUrl = img
            customDishId = src.id
            macros = MealMacrosSummary().apply {
                caloriesKcal = src.calories.toDouble()
                proteinGrams = src.proteins.toDouble()
                carbsGrams = src.carbs.toDouble()
                fatGrams = src.fats.toDouble()
            }
        }
    }

    companion object {

        val WEEK_DAYS = listOf(
            "Lunes", "Martes", "Miércoles", "Jueves", "Viernes", "Sábado", "Domingo"
        )

        private const val SERVER_READ_TIMEOUT_MS = 8_000L

        private const val SERVER_WRITE_TIMEOUT_MS = 12_000L
    }
}
