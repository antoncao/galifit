package com.tfm.galifit.util.chat

import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.Source
import com.google.firebase.firestore.firestore
import com.tfm.galifit.ChatAssistantActivity
import com.tfm.galifit.data.model.FirebaseSavedExercisePlan
import com.tfm.galifit.data.model.FirebaseSavedMealPlan
import com.tfm.galifit.data.model.UserPreferences
import com.tfm.galifit.util.toUserPreferences
import kotlinx.coroutines.tasks.await

object ChatContextBuilder {

    private const val MAX_CONTEXT_CHARS = 7_500

    data class BuildResult(
        val systemInstruction: String,
        val contextHash: Int
    )

    suspend fun build(origin: String): BuildResult {
        val uid = Firebase.auth.currentUser?.uid
            ?: return BuildResult(emptySystemInstruction(origin, null), 0)

        val userDoc = runCatching {
            Firebase.firestore.collection("users").document(uid).get(Source.CACHE).await()
        }.getOrNull() ?: runCatching {
            Firebase.firestore.collection("users").document(uid).get(Source.SERVER).await()
        }.getOrNull()

        val prefs = userDoc?.toUserPreferences()

        val mealPlan = loadLatestMealPlan(uid)
        val exercisePlan = loadLatestExercisePlan(uid)

        val contextBody = buildString {
            appendLine(originHint(origin))
            appendLine()
            appendLine(formatPreferences(prefs))
            appendLine()
            appendLine(formatMealPlan(mealPlan))
            appendLine()
            appendLine(formatExercisePlan(exercisePlan))
        }.let { truncate(it) }

        val systemInstruction = buildSystemInstruction(contextBody)
        return BuildResult(systemInstruction, systemInstruction.hashCode())
    }

    private suspend fun loadLatestMealPlan(uid: String): FirebaseSavedMealPlan? {
        val query = Firebase.firestore.collection("users")
            .document(uid)
            .collection("mealPlans")
            .orderBy("createdAtMillis", Query.Direction.DESCENDING)
            .limit(1)

        val snapshot = runCatching { query.get(Source.CACHE).await() }.getOrNull()
            ?: runCatching { query.get(Source.SERVER).await() }.getOrNull()
            ?: return null

        val doc = snapshot.documents.firstOrNull() ?: return null
        return doc.toObject(FirebaseSavedMealPlan::class.java)
    }

    private suspend fun loadLatestExercisePlan(uid: String): FirebaseSavedExercisePlan? {
        val query = Firebase.firestore.collection("users")
            .document(uid)
            .collection("exercisePlans")
            .orderBy("createdAtMillis", Query.Direction.DESCENDING)
            .limit(1)

        val snapshot = runCatching { query.get(Source.CACHE).await() }.getOrNull()
            ?: runCatching { query.get(Source.SERVER).await() }.getOrNull()
            ?: return null

        val doc = snapshot.documents.firstOrNull() ?: return null
        return doc.toObject(FirebaseSavedExercisePlan::class.java)
    }

    private fun originHint(origin: String): String = when (origin) {
        ChatAssistantActivity.ORIGIN_HOME ->
            "El usuario abrió el chat desde la pantalla de Inicio (resumen del día)."
        ChatAssistantActivity.ORIGIN_MEALS ->
            "El usuario abrió el chat desde el Plan de comidas."
        ChatAssistantActivity.ORIGIN_EXERCISES ->
            "El usuario abrió el chat desde la Rutina de ejercicios."
        else -> "El usuario abrió el asistente Galifit."
    }

    private fun formatPreferences(prefs: UserPreferences?): String {
        if (prefs == null) return "PREFERENCIAS: no disponibles (usuario sin onboarding completo)."
        return buildString {
            appendLine("PREFERENCIAS DEL USUARIO:")
            appendLine("- Edad: ${prefs.age}, sexo: ${prefs.sex}")
            appendLine("- Altura: ${prefs.heightCm} cm, peso: ${prefs.weightKg} kg")
            appendLine("- Actividad: ${prefs.activityLevel} (x${prefs.activityMultiplier})")
            appendLine("- Objetivo: ${prefs.objective}, meta semanal: ${prefs.weeklyWeightGoalKg} kg")
            appendLine("- Dieta: ${prefs.dietType}, comidas/día: ${prefs.mealsPerDay}")
            appendLine("- Calorías objetivo: ${prefs.targetCalories} kcal (TMB ${prefs.tmb}, TDEE ${prefs.tdee})")
            if (prefs.restrictions.isNotEmpty()) appendLine("- Restricciones: ${prefs.restrictions.joinToString()}")
            if (prefs.avoidedFoods.isNotEmpty()) appendLine("- Alimentos evitados: ${prefs.avoidedFoods.joinToString()}")
            if (prefs.cuisinePreferences.isNotEmpty()) {
                appendLine("- Cocinas preferidas: ${prefs.cuisinePreferences.joinToString()}")
            }
            appendLine("- Entrenamiento: ${prefs.trainingDaysPerWeek} días/sem, ${prefs.trainingSessionMinutes} min/sesión")
            appendLine("- Nivel: ${prefs.trainingLevel}, objetivo: ${prefs.trainingObjective}")
            appendLine("- Lugar: ${prefs.trainingLocation}, split: ${prefs.trainingSplit}")
            if (prefs.availableEquipment.isNotEmpty()) {
                appendLine("- Equipamiento: ${prefs.availableEquipment.joinToString()}")
            }
            if (prefs.trainingLimitations.isNotEmpty()) {
                appendLine("- Limitaciones: ${prefs.trainingLimitations.joinToString()}")
            }
        }
    }

    private fun formatMealPlan(plan: FirebaseSavedMealPlan?): String {
        if (plan == null || plan.days.isEmpty()) {
            return "PLAN DE COMIDAS: no hay plan semanal guardado."
        }
        return buildString {
            appendLine("PLAN DE COMIDAS (último plan semanal):")
            for (day in plan.days) {
                val label = day.dayLabel?.takeIf { it.isNotBlank() }
                    ?: "Día ${day.dayIndex + 1}"
                appendLine("[$label]")
                if (day.meals.isEmpty()) {
                    appendLine("  (sin comidas)")
                } else {
                    for (meal in day.meals) {
                        val kcal = meal.macros?.caloriesKcal?.toInt()
                        val kcalStr = if (kcal != null && kcal > 0) " ~${kcal} kcal" else ""
                        appendLine("  - ${meal.sectionKey}: ${meal.name}$kcalStr")
                    }
                }
            }
        }
    }

    private fun formatExercisePlan(plan: FirebaseSavedExercisePlan?): String {
        if (plan == null || plan.days.isEmpty()) {
            return "RUTINA DE EJERCICIOS: no hay rutina semanal guardada."
        }
        val summary = plan.planSummary
        return buildString {
            appendLine("RUTINA DE EJERCICIOS (última rutina semanal):")
            appendLine(
                "Resumen: ${summary.daysPerWeek} días/sem, ${summary.objective}, " +
                    "nivel ${summary.level}, ${summary.location}, split ${summary.split}"
            )
            for (day in plan.days) {
                appendLine("[${day.dayName.ifBlank { "Día ${day.dayIndex + 1}" }} — ${day.focus}]")
                if (day.isRest) {
                    appendLine("  (descanso)")
                    continue
                }
                if (day.exercises.isEmpty()) {
                    appendLine("  (sin ejercicios)")
                } else {
                    for (ex in day.exercises) {
                        val reps = when {
                            ex.repsLow > 0 && ex.repsHigh > 0 -> "${ex.repsLow}-${ex.repsHigh} reps"
                            !ex.repsOrDuration.isNullOrBlank() -> ex.repsOrDuration
                            else -> ""
                        }
                        val sets = if (ex.sets > 0) "${ex.sets} series" else ""
                        val rest = if (ex.restSeconds > 0) ", descanso ${ex.restSeconds}s" else ""
                        appendLine("  - ${ex.name}: $sets $reps$rest".trim())
                    }
                }
            }
        }
    }

    private fun buildSystemInstruction(contextBody: String): String = """
        Eres el asistente virtual de Galifit, una app de nutrición y entrenamiento.
        Responde siempre en español, de forma clara, práctica y amable.
        Usa el CONTEXTO_USUARIO para personalizar consejos sobre comidas y ejercicios del plan.
        Tu rol es INFORMATIVO y de ASESORAMIENTO: explica, orienta y responde dudas sobre el plan actual del usuario.
        SÍ puedes proponer alternativas y sugerencias: si el usuario te pide platos, comidas o
        elaboraciones alternativas a las de su plan de hoy (o de cualquier día), ofréceselas de forma
        concreta (nombre del plato, ingredientes o idea de elaboración), teniendo en cuenta sus
        preferencias, dieta, restricciones y objetivo calórico del CONTEXTO_USUARIO. También puedes
        sugerir ejercicios alternativos cuando te lo pidan.
        IMPORTANTE: tú NO aplicas esos cambios. No modificas, regeneras ni sustituyes directamente el
        plan de comidas ni la rutina de ejercicios; solo das las sugerencias como recomendación.
        Cuando propongas una alternativa, recuérdale al usuario que, si quiere aplicarla, debe hacerlo
        él mismo desde las pantallas Plan de comidas, Rutina de ejercicios o Preferencias de la app.
        Puedes proponer platos o ejercicios que no aparezcan en el contexto siempre que sean coherentes
        con las preferencias del usuario; si te falta información para personalizar, dilo.
        No des diagnósticos médicos ni sustituyas a un profesional de la salud.
        Si la consulta es clínica o de riesgo, recomienda acudir a un médico o nutricionista.

        CONTEXTO_USUARIO:
        $contextBody
    """.trimIndent()

    private fun emptySystemInstruction(origin: String, prefs: UserPreferences?): String =
        buildSystemInstruction(
            buildString {
                appendLine(originHint(origin))
                appendLine()
                appendLine(formatPreferences(prefs))
                appendLine()
                appendLine("PLAN DE COMIDAS: no disponible (sin sesión).")
                appendLine()
                appendLine("RUTINA DE EJERCICIOS: no disponible (sin sesión).")
            }
        )

    private fun truncate(text: String): String {
        if (text.length <= MAX_CONTEXT_CHARS) return text
        return text.take(MAX_CONTEXT_CHARS) + "\n…(contexto truncado)"
    }
}
