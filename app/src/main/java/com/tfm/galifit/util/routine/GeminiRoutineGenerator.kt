package com.tfm.galifit.util.routine

import com.google.firebase.ai.type.content
import com.tfm.galifit.data.catalog.BodyRegion
import com.tfm.galifit.data.catalog.CatalogExercise
import com.tfm.galifit.data.catalog.ExerciseCatalog
import com.tfm.galifit.data.model.FirebaseDayExercisePlan
import com.tfm.galifit.data.model.FirebaseSavedExercise
import com.tfm.galifit.data.model.FirebaseSavedExercisePlan
import com.tfm.galifit.data.model.PlanSummary
import com.tfm.galifit.data.model.UserPreferences
import com.tfm.galifit.util.GalifitFlowLog
import com.tfm.galifit.util.ai.GeminiModelProvider
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.abs

class GeminiRoutineGenerator(
    private val fallback: ExerciseRoutineGenerator = LocalRoutineGenerator()
) : ExerciseRoutineGenerator {

    private val generativeModel by lazy {
        GeminiModelProvider.generativeModel()
    }

    override suspend fun generate(
        prefs: UserPreferences,
        catalog: ExerciseCatalog
    ): FirebaseSavedExercisePlan {
        val t0 = System.currentTimeMillis()
        return try {
            val candidates = buildCandidates(prefs, catalog)
            GalifitFlowLog.api(
                "⏱ GeminiGen: candidatos=${candidates.size} catálogo=${catalog.exercises.size} fuente=${catalog.source}"
            )

            val response = generativeModel.generateContent(
                content {
                    text(buildPrompt(prefs, candidates))
                }
            )
            val raw = response.text.orEmpty()
            GalifitFlowLog.api(
                "⏱ GeminiGen: respuesta recibida en ${System.currentTimeMillis() - t0}ms chars=${raw.length}"
            )
            logGeminiRawResponse(raw)

            parsePlan(raw, prefs, catalog, candidates.associateBy { it.id }).also {
                GalifitFlowLog.api(
                    "⏱ GeminiGen: plan parseado OK total=${System.currentTimeMillis() - t0}ms"
                )
            }
        } catch (t: Throwable) {
            GalifitFlowLog.warn(
                "GeminiGen: fallo (${t.message}), usando generador local como fallback"
            )
            fallback.generate(prefs, catalog).also {
                it.source = "local_fallback_after_gemini_error"
            }
        }
    }

    private fun logGeminiRawResponse(raw: String) {
        val sanitized = raw
            .replace("\r", "\\r")
            .replace("\n", "\\n")
        if (sanitized.isBlank()) {
            GalifitFlowLog.api("GeminiGen RAW: <respuesta vacía>")
            return
        }

        val chunks = sanitized.chunked(LOG_CHUNK_SIZE)
        GalifitFlowLog.api("GeminiGen RAW inicio chunks=${chunks.size} chars=${raw.length}")
        chunks.forEachIndexed { index, chunk ->
            GalifitFlowLog.api("GeminiGen RAW ${index + 1}/${chunks.size}: $chunk")
        }
        GalifitFlowLog.api("GeminiGen RAW fin")
    }

    private fun buildCandidates(
        prefs: UserPreferences,
        catalog: ExerciseCatalog
    ): List<CatalogExercise> {
        val userKeys = prefs.availableEquipment.toSet()
        val allowMachines = prefs.trainingLocation.equals("gym", true)

        val filtered = catalog.exercises
            .filter { it.name.isNotBlank() }
            .filter { it.isAvailableFor(userKeys, allowMachines) }

        val pool = filtered.ifEmpty { catalog.exercises.filter { it.name.isNotBlank() } }
        val seed = abs((prefs.trainingObjective + prefs.trainingSplit + prefs.trainingDaysPerWeek).hashCode())
        val stableComparator = compareBy<CatalogExercise> {
            abs((it.id.hashCode() * 31 + seed) % 997)
        }.thenBy { it.name }

        val byRegion = BodyRegion.entries
            .associateWith { region ->
                pool.filter { it.region == region }.sortedWith(stableComparator).toMutableList()
            }

        val balanced = mutableListOf<CatalogExercise>()
        while (balanced.size < MAX_CANDIDATES && byRegion.values.any { it.isNotEmpty() }) {
            BodyRegion.entries.forEach { region ->
                val next = byRegion[region]?.removeFirstOrNull()
                if (next != null && balanced.size < MAX_CANDIDATES) {
                    balanced += next
                }
            }
        }
        return balanced
    }

    private fun buildPrompt(
        prefs: UserPreferences,
        candidates: List<CatalogExercise>
    ): String {
        val candidateJson = JSONArray().apply {
            candidates.forEach { ex ->
                put(JSONObject().apply {
                    put("id", ex.id)
                    put("name", ex.name)
                    put("region", ex.region.name)
                    put("muscle", ex.muscleGroup.orEmpty())
                    put("equipment", ex.equipmentLabel)
                })
            }
        }

        return """
            Eres un entrenador personal especializado en fuerza e hipertrofia para usuarios principiantes e intermedios.
            Genera una rutina semanal segura, variada y realista usando SOLO ejercicios del catálogo incluido.
            Devuelve SOLO JSON válido, sin markdown, sin comentarios, sin texto adicional y siempre con objeto raíz {"days":[...]}.

            Preferencias del usuario:
            - dias_por_semana: ${prefs.trainingDaysPerWeek.coerceIn(1, 7)}
            - duracion_minutos: ${prefs.trainingSessionMinutes}
            - lugar: ${prefs.trainingLocation}
            - equipamiento: ${prefs.availableEquipment.joinToString(", ").ifBlank { "peso corporal" }}
            - nivel: ${prefs.trainingLevel}
            - objetivo: ${prefs.trainingObjective}
            - split_preferido: ${prefs.trainingSplit}
            - limitaciones: ${prefs.trainingLimitations.joinToString(", ").ifBlank { "ninguna" }}

            Reglas obligatorias de estructura:
            - Crea exactamente 7 días: Lunes, Martes, Miércoles, Jueves, Viernes, Sábado, Domingo.
            - Marca como descanso los días que no entrenan.
            - Usa aproximadamente ${prefs.trainingDaysPerWeek.coerceIn(1, 7)} días de entrenamiento.
            - Cada día de entrenamiento debe tener entre 4 y 6 ejercicios, salvo si la duración es 30 minutos o menos, donde puede tener 3 o 4.
            - El campo "exerciseId" de cada ejercicio DEBE ser un "id" presente en el catálogo (cópialo tal cual, es un texto).
            - No devuelvas name, muscleGroup, equipmentLabel ni instructions dentro de cada ejercicio: la app los reconstruye desde el catálogo.
            - No devuelvas details: la app lo compone a partir de sets/reps/restSeconds.
            - No inventes ejercicios ni ids.
            - No devuelvas un array raíz. El JSON raíz debe ser siempre: {"days":[...]}.

            Reglas obligatorias de calidad:
            - Evita repetir el mismo "exerciseId" en la semana. Solo puedes repetirlo si no hay alternativas suficientes.
            - En rutinas full_body, cada día de entrenamiento debe combinar al menos 3 zonas (region): LEGS, CHEST/SHOULDERS/ARMS (empuje), BACK (tirón), ABS (core).
            - En rutinas por split, respeta el foco del día, pero añade 1 ejercicio accesorio o core si encaja con la duración.
            - El campo "focus" debe ser una etiqueta corta de entrenamiento ("Cuerpo completo", "Empuje", "Tirón", "Pierna", "Tren superior", "Tren inferior", etc.).
            - No añadas explicaciones, paréntesis ni notas de catálogo limitado dentro de "focus".
            - No generes todos los días con los mismos ejercicios ni con el mismo orden.
            - Si el usuario no tiene equipamiento, prioriza ejercicios de peso corporal.
            - Ajusta series/repeticiones/descanso al objetivo y nivel.
            - Si hay limitaciones físicas, evita movimientos obvios de riesgo.
            - Las instrucciones deben ser breves, claras y en español.
            - Si el catálogo no tiene suficiente variedad para una zona, usa la mejor alternativa disponible sin inventar ids.

            Formato exacto:
            {
              "days": [
                {
                  "dayIndex": 0,
                  "dayName": "Lunes",
                  "focus": "Cuerpo completo",
                  "isRest": false,
                  "exercises": [
                    {
                      "exerciseId": "id-del-catalogo",
                      "sets": 3,
                      "repsLow": 8,
                      "repsHigh": 12,
                      "restSeconds": 75
                    }
                  ]
                }
              ]
            }

            Catálogo disponible:
            ${candidateJson}
        """.trimIndent()
    }

    private fun parsePlan(
        raw: String,
        prefs: UserPreferences,
        catalog: ExerciseCatalog,
        candidatesById: Map<String, CatalogExercise>
    ): FirebaseSavedExercisePlan {
        val json = raw
            .removePrefix("```json")
            .removePrefix("```")
            .removeSuffix("```")
            .trim()
        val root = JSONObject(json)
        val daysJson = root.getJSONArray("days")
        require(daysJson.length() == 7) { "Gemini no devolvió 7 días" }

        val days = mutableListOf<FirebaseDayExercisePlan>()
        for (i in 0 until daysJson.length()) {
            val dayObj = daysJson.getJSONObject(i)
            val isRest = dayObj.optBoolean("isRest", false)
            val exercises = parseExercises(dayObj.optJSONArray("exercises"), candidatesById)

            days += FirebaseDayExercisePlan().apply {
                dayIndex = dayObj.optLong("dayIndex", i.toLong())
                dayName = dayObj.optString("dayName", WEEK_DAYS.getOrElse(i) { "Día ${i + 1}" })
                focus = sanitizeFocus(dayObj.optString("focus", if (isRest) "Descanso" else "Entrenamiento"))
                this.isRest = isRest || exercises.isEmpty()
                this.exercises = if (this.isRest) emptyList() else exercises
            }
        }

        require(days.any { !it.isRest && it.exercises.isNotEmpty() }) {
            "Gemini no devolvió ejercicios válidos del catálogo"
        }

        return FirebaseSavedExercisePlan().apply {
            createdAtMillis = System.currentTimeMillis()
            source = "gemini_${catalog.source}_generator"
            planSummary = PlanSummary().apply {
                daysPerWeek = prefs.trainingDaysPerWeek.coerceIn(1, 7)
                split = prefs.trainingSplit
                objective = prefs.trainingObjective
                level = prefs.trainingLevel
                location = prefs.trainingLocation
            }
            this.days = days
        }
    }

    private fun parseExercises(
        array: JSONArray?,
        candidatesById: Map<String, CatalogExercise>
    ): List<FirebaseSavedExercise> {
        if (array == null) return emptyList()

        val out = mutableListOf<FirebaseSavedExercise>()
        for (i in 0 until array.length()) {
            val obj = array.optJSONObject(i) ?: continue
            val id = obj.optString("exerciseId", "").trim()
            val catalogExercise = candidatesById[id] ?: continue
            val sets = obj.optInt("sets", 3).coerceIn(1, 6)
            val repsLow = obj.optInt("repsLow", 8).coerceIn(1, 30)
            val repsHigh = obj.optInt("repsHigh", repsLow.coerceAtLeast(12)).coerceIn(repsLow, 40)
            val restSeconds = obj.optInt("restSeconds", 75).coerceIn(15, 240)

            out += FirebaseSavedExercise().apply {
                name = catalogExercise.name
                details = "$sets series x $repsLow-$repsHigh reps · ${restSeconds}s descanso"

                thumbnailImageUrl = catalogExercise.gifUrl
                catalogExerciseId = catalogExercise.id
                wgerExerciseId = catalogExercise.id.toLongOrNull()
                muscleGroup = catalogExercise.muscleGroup
                equipmentLabel = catalogExercise.equipmentLabel
                this.sets = sets.toLong()
                this.repsLow = repsLow.toLong()
                this.repsHigh = repsHigh.toLong()
                this.restSeconds = restSeconds.toLong()
                instructions = catalogExercise.instructions
            }
        }
        return out.take(6)
    }

    private fun sanitizeFocus(raw: String): String {
        val cleaned = raw
            .replace(Regex("\\s*\\([^)]*\\)"), "")
            .replace(Regex("\\s+"), " ")
            .trim()
        return cleaned.ifBlank { "Entrenamiento" }
    }

    private companion object {
        private const val MAX_CANDIDATES = 40
        private const val LOG_CHUNK_SIZE = 2500
        private val WEEK_DAYS = listOf(
            "Lunes", "Martes", "Miércoles", "Jueves", "Viernes", "Sábado", "Domingo"
        )
    }
}
