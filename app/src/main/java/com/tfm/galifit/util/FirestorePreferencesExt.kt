package com.tfm.galifit.util

import com.google.firebase.firestore.DocumentSnapshot
import com.tfm.galifit.data.model.EdamamHealthCatalog
import com.tfm.galifit.data.model.UserPreferences
import com.tfm.galifit.notifications.SedentaryDataSource

fun DocumentSnapshot.toUserPreferences(): UserPreferences? {
    val p = get("preferences") as? Map<*, *> ?: return null
    return EdamamHealthCatalog.normalizeLegacyPaleoPreference(UserPreferences(
        age = (p["age"] as? Number)?.toInt() ?: 0,
        sex = p["sex"] as? String ?: "",
        heightCm = (p["heightCm"] as? Number)?.toFloat() ?: 0f,
        weightKg = (p["weightKg"] as? Number)?.toFloat() ?: 0f,
        activityLevel = p["activityLevel"] as? String ?: "",
        activityMultiplier = (p["activityMultiplier"] as? Number)?.toFloat() ?: 1.2f,
        objective = p["objective"] as? String ?: "",
        weeklyWeightGoalKg = (p["weeklyWeightGoalKg"] as? Number)?.toFloat() ?: 0f,
        dietType = p["dietType"] as? String ?: "none",
        restrictions = (p["restrictions"] as? List<*>)?.filterIsInstance<String>() ?: emptyList(),
        healthConditions = (p["healthConditions"] as? List<*>)?.filterIsInstance<String>() ?: emptyList(),
        healthLabels = (p["healthLabels"] as? List<*>)?.filterIsInstance<String>() ?: emptyList(),
        cuisinePreferences = (p["cuisinePreferences"] as? List<*>)?.filterIsInstance<String>() ?: emptyList(),
        avoidedFoods = (p["avoidedFoods"] as? List<*>)?.filterIsInstance<String>() ?: emptyList(),
        mealsPerDay = (p["mealsPerDay"] as? Number)?.toInt() ?: 3,
        tmb = (p["tmb"] as? Number)?.toFloat() ?: 0f,
        tdee = (p["tdee"] as? Number)?.toFloat() ?: 0f,
        targetCalories = (p["targetCalories"] as? Number)?.toInt() ?: 0,
        targetProteinGrams = (p["targetProteinGrams"] as? Number)?.toInt() ?: 0,
        acceptsProteinSupplements = p["acceptsProteinSupplements"] as? Boolean ?: false,
        completedAtMillis = (p["completedAtMillis"] as? Number)?.toLong() ?: 0,
        trainingDaysPerWeek = (p["trainingDaysPerWeek"] as? Number)?.toInt() ?: 0,
        trainingSessionMinutes = (p["trainingSessionMinutes"] as? Number)?.toInt() ?: 45,
        trainingLocation = p["trainingLocation"] as? String ?: "",
        availableEquipment = (p["availableEquipment"] as? List<*>)?.filterIsInstance<String>() ?: emptyList(),
        trainingLevel = p["trainingLevel"] as? String ?: "",
        trainingObjective = p["trainingObjective"] as? String ?: "",
        trainingSplit = p["trainingSplit"] as? String ?: "",
        trainingLimitations = (p["trainingLimitations"] as? List<*>)?.filterIsInstance<String>() ?: emptyList(),
        trainingCompletedAtMillis = (p["trainingCompletedAtMillis"] as? Number)?.toLong() ?: 0,
        sedentaryNotificationsEnabled = p["sedentaryNotificationsEnabled"] as? Boolean ?: false,
        sedentaryDataSource = SedentaryDataSource.defaultForLegacy(
            enabled = p["sedentaryNotificationsEnabled"] as? Boolean ?: false,
            stored = p["sedentaryDataSource"] as? String
        )
    ))
}
