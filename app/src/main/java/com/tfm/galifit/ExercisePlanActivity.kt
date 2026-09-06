package com.tfm.galifit

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.QuerySnapshot
import com.google.firebase.firestore.Source
import com.google.firebase.firestore.firestore
import com.tfm.galifit.adapter.DayExercisePlanAdapter
import com.tfm.galifit.data.catalog.ExerciseCatalogProviderFactory
import com.tfm.galifit.data.model.DayExercisePlan
import com.tfm.galifit.data.model.Exercise
import com.tfm.galifit.data.model.FirebaseDayExercisePlan
import com.tfm.galifit.data.model.FirebaseSavedExercisePlan
import com.tfm.galifit.data.model.PlanSummary
import com.tfm.galifit.data.model.UserPreferences
import com.tfm.galifit.data.model.defaultEmptyWeekDayExercisePlans
import com.tfm.galifit.data.model.toFirebase
import com.tfm.galifit.data.model.toUiDayExercisePlans
import com.tfm.galifit.util.GalifitFlowLog
import com.tfm.galifit.util.routine.RoutineGeneratorFactory
import com.tfm.galifit.util.toUserPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ExercisePlanActivity : GetNavigationBarActivity() {

    override fun showChatFab(): Boolean = true

    override fun chatOrigin(): String = ChatAssistantActivity.ORIGIN_EXERCISES

    private lateinit var recyclerDayExercisePlans: RecyclerView
    private lateinit var adapter: DayExercisePlanAdapter
    private lateinit var generateButton: Button
    private lateinit var generationOverlay: View
    private lateinit var generationStatus: TextView

    private var activeExercisePlanDocId: String? = null

    private var activePlanSummary: PlanSummary = PlanSummary()
    private var activePlanSource: String = "wger_local_generator"

    private var pendingDayIndex: Int = -1
    private var pendingExerciseIndex: Int = -1

    private lateinit var createExerciseLauncher: ActivityResultLauncher<Intent>

    private val catalogProvider by lazy { ExerciseCatalogProviderFactory.create(applicationContext) }
    private val generator by lazy { RoutineGeneratorFactory.create() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.exercise_plan)

        recyclerDayExercisePlans = findViewById(R.id.rvDaysExercises)
        adapter = DayExercisePlanAdapter(
            dias = defaultEmptyWeekDayExercisePlans().toMutableList(),
            onAddExercise = { dayIndex -> launchCreateExercise(dayIndex, -1, null) },
            onEditExercise = { dayIndex, exercise, exerciseIndex ->
                launchCreateExercise(dayIndex, exerciseIndex, exercise)
            },
            onDeleteExercise = { _, _, _ ->
                persistCurrentPlanToFirestore()
            }
        )
        recyclerDayExercisePlans.layoutManager = LinearLayoutManager(this)
        recyclerDayExercisePlans.adapter = adapter

        generationOverlay = findViewById(R.id.exerciseGenerationOverlay)
        generationStatus = findViewById(R.id.exerciseGenerationStatus)

        createExerciseLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                val data = result.data ?: return@registerForActivityResult
                handleCreateExerciseResult(data)
            }
        }

        loadLatestExercisePlanFromFirestore()

        generateButton = findViewById(R.id.button)
        generateButton.setOnClickListener { onGenerateButtonPressed() }

        this.getNavigationView().selectedItemId = R.id.navigation_ejercicios
    }

    private fun showGenerationOverlay(message: String) {
        generationStatus.text = message
        generationOverlay.visibility = View.VISIBLE
    }

    private fun hideGenerationOverlay() {
        generationOverlay.visibility = View.GONE
    }

    private fun launchCreateExercise(dayIndex: Int, exerciseIndex: Int, exercise: Exercise?) {
        pendingDayIndex = dayIndex
        pendingExerciseIndex = exerciseIndex
        val intent = Intent(this, CreateExerciseActivity::class.java).apply {
            putExtra(CreateExerciseActivity.EXTRA_DAY_INDEX, dayIndex)
            putExtra(CreateExerciseActivity.EXTRA_EXERCISE_INDEX, exerciseIndex)
            if (exercise != null) {
                putExtra(CreateExerciseActivity.EXTRA_NAME, exercise.name)
                putExtra(CreateExerciseActivity.EXTRA_SETS, exercise.sets)
                putExtra(
                    CreateExerciseActivity.EXTRA_REPS_OR_DURATION,
                    exercise.repsOrDuration
                        ?: exercise.takeIf { it.repsHigh > 0 }?.let {
                            if (it.repsLow == it.repsHigh) "${it.repsHigh}"
                            else "${it.repsLow}-${it.repsHigh}"
                        }
                )
                putExtra(CreateExerciseActivity.EXTRA_MEASUREMENT_TYPE, exercise.measurementType)
                putExtra(CreateExerciseActivity.EXTRA_USER_NOTES, exercise.userNotes)
                putExtra(
                    CreateExerciseActivity.EXTRA_WGER_EXERCISE_ID,
                    exercise.wgerExerciseId ?: -1
                )
                putExtra(CreateExerciseActivity.EXTRA_MUSCLE_GROUP, exercise.muscleGroup)
                putExtra(CreateExerciseActivity.EXTRA_EQUIPMENT_LABEL, exercise.equipmentLabel)
                putExtra(CreateExerciseActivity.EXTRA_THUMB_URL, exercise.thumbnailImageUrl)
                putExtra(CreateExerciseActivity.EXTRA_INSTRUCTIONS, exercise.instructions)
                putExtra(CreateExerciseActivity.EXTRA_REST_SECONDS, exercise.restSeconds)
            }
        }
        createExerciseLauncher.launch(intent)
    }

    private fun handleCreateExerciseResult(data: Intent) {
        val dayIndex = data.getIntExtra(
            CreateExerciseActivity.EXTRA_DAY_INDEX,
            pendingDayIndex
        )
        val exerciseIndex = data.getIntExtra(
            CreateExerciseActivity.EXTRA_EXERCISE_INDEX,
            pendingExerciseIndex
        )
        val name = data.getStringExtra(CreateExerciseActivity.EXTRA_NAME).orEmpty()
        val sets = data.getIntExtra(CreateExerciseActivity.EXTRA_SETS, 0)
        val repsOrDuration = data.getStringExtra(CreateExerciseActivity.EXTRA_REPS_OR_DURATION)
        val measurementType = data.getStringExtra(CreateExerciseActivity.EXTRA_MEASUREMENT_TYPE)
            ?: Exercise.MEASUREMENT_REPS
        val userNotes = data.getStringExtra(CreateExerciseActivity.EXTRA_USER_NOTES)
        val wgerExerciseIdRaw = data.getIntExtra(CreateExerciseActivity.EXTRA_WGER_EXERCISE_ID, -1)
        val muscleGroup = data.getStringExtra(CreateExerciseActivity.EXTRA_MUSCLE_GROUP)
        val equipmentLabel = data.getStringExtra(CreateExerciseActivity.EXTRA_EQUIPMENT_LABEL)
        val thumbUrl = data.getStringExtra(CreateExerciseActivity.EXTRA_THUMB_URL)
        val instructions = data.getStringExtra(CreateExerciseActivity.EXTRA_INSTRUCTIONS)
        val restSeconds = data.getIntExtra(CreateExerciseActivity.EXTRA_REST_SECONDS, 0)

        val original: Exercise? = adapter.getDays()
            .getOrNull(dayIndex)
            ?.exercises
            ?.getOrNull(exerciseIndex)

        val cleanReps = repsOrDuration?.takeIf { it.isNotBlank() }
        val updated = Exercise(
            name = name,
            details = buildExerciseDetails(sets, cleanReps, measurementType, restSeconds),
            thumbnailImageUrl = thumbUrl?.takeIf { it.isNotBlank() },
            wgerExerciseId = wgerExerciseIdRaw.takeIf { it >= 0 },
            muscleGroup = muscleGroup?.takeIf { it.isNotBlank() },
            equipmentLabel = equipmentLabel?.takeIf { it.isNotBlank() },
            sets = sets,
            repsLow = original?.repsLow ?: 0,
            repsHigh = original?.repsHigh ?: 0,
            restSeconds = restSeconds,
            instructions = instructions?.takeIf { it.isNotBlank() },
            repsOrDuration = cleanReps,
            measurementType = measurementType,
            userNotes = userNotes?.takeIf { it.isNotBlank() }
        )

        if (exerciseIndex < 0) {
            adapter.addExerciseToDay(dayIndex, updated)
        } else {
            adapter.updateExerciseInDay(dayIndex, exerciseIndex, updated)
        }
        persistCurrentPlanToFirestore()
    }

    private fun buildExerciseDetails(
        sets: Int,
        repsOrDuration: String?,
        measurementType: String,
        restSeconds: Int
    ): String {
        val parts = mutableListOf<String>()
        val volume = buildString {
            if (sets > 0) append("$sets series")
            if (!repsOrDuration.isNullOrBlank()) {
                if (isNotEmpty()) append(" x ")
                append(repsOrDuration)
                if (measurementType == Exercise.MEASUREMENT_REPS) append(" reps")
            }
        }
        if (volume.isNotBlank()) parts.add(volume)
        if (restSeconds > 0) parts.add("${restSeconds}s descanso")
        return parts.joinToString(" · ")
    }

    private fun persistCurrentPlanToFirestore() {
        val uid = Firebase.auth.currentUser?.uid ?: return
        val docToWrite = buildFirebasePlanFromUi(adapter.getDays())

        val plansRef = Firebase.firestore.collection("users")
            .document(uid)
            .collection("exercisePlans")

        val docId = activeExercisePlanDocId
        if (docId != null) {
            plansRef.document(docId).set(docToWrite)
                .addOnSuccessListener {
                    GalifitFlowLog.firestore("Plan ejercicios actualizado id=$docId")
                }
                .addOnFailureListener { e ->
                    GalifitFlowLog.warn("Error actualizando plan ejercicios: ${e.message}")
                }
        } else {
            plansRef.add(docToWrite)
                .addOnSuccessListener { ref ->
                    activeExercisePlanDocId = ref.id
                    GalifitFlowLog.firestore("Plan ejercicios creado id=${ref.id} (primer cambio manual)")
                }
                .addOnFailureListener { e ->
                    GalifitFlowLog.warn("Error creando plan ejercicios: ${e.message}")
                }
        }
    }

    private fun buildFirebasePlanFromUi(days: List<DayExercisePlan>): FirebaseSavedExercisePlan {
        val firebaseDays = days.mapIndexed { index, day ->
            FirebaseDayExercisePlan().apply {
                dayIndex = index.toLong()
                dayName = day.dayName
                focus = day.focus
                isRest = day.isRest
                exercises = day.exercises.map { it.toFirebase() }
            }
        }
        return FirebaseSavedExercisePlan().apply {
            createdAtMillis = System.currentTimeMillis()
            source = activePlanSource
            planSummary = activePlanSummary
            this.days = firebaseDays
        }
    }

    private fun loadLatestExercisePlanFromFirestore() {
        val uid = Firebase.auth.currentUser?.uid
        if (uid == null) {
            GalifitFlowLog.warn("Firestore: sin usuario, no se carga exercisePlan")
            return
        }

        val query = Firebase.firestore.collection("users")
            .document(uid)
            .collection("exercisePlans")
            .orderBy("createdAtMillis", Query.Direction.DESCENDING)
            .limit(1)

        GalifitFlowLog.firestore(
            "Lectura último plan CACHE: users/$uid/exercisePlans orderBy createdAtMillis DESC limit 1"
        )

        val tCache0 = System.currentTimeMillis()
        query.get(Source.CACHE)
            .addOnSuccessListener { snapshot ->
                GalifitFlowLog.firestore(
                    "⏱ ExercisePlan CACHE success en ${System.currentTimeMillis() - tCache0}ms " +
                        "docs=${snapshot.size()} fromCache=${snapshot.metadata.isFromCache}"
                )
                val loaded = applyExercisePlanSnapshot(snapshot, sourceLabel = "CACHE")
                if (!loaded) {
                    loadLatestExercisePlanFromServer(query)
                }
            }
            .addOnFailureListener { e ->
                GalifitFlowLog.warn(
                    "⏱ ExercisePlan CACHE fallo tras ${System.currentTimeMillis() - tCache0}ms → ${e.message}; probando SERVER"
                )
                loadLatestExercisePlanFromServer(query)
            }
    }

    private fun loadLatestExercisePlanFromServer(query: Query) {
        GalifitFlowLog.firestore("Lectura último plan SERVER: exercisePlans orderBy createdAtMillis DESC limit 1")
        val tServer0 = System.currentTimeMillis()
        query.get(Source.SERVER)
            .addOnSuccessListener { snapshot ->
                GalifitFlowLog.firestore(
                    "⏱ ExercisePlan SERVER success en ${System.currentTimeMillis() - tServer0}ms " +
                        "docs=${snapshot.size()} fromCache=${snapshot.metadata.isFromCache}"
                )
                val loaded = applyExercisePlanSnapshot(snapshot, sourceLabel = "SERVER")
                if (!loaded) {
                    GalifitFlowLog.firestore("No hay plan de ejercicios guardado o documento vacío")
                }
            }
            .addOnFailureListener { e ->
                GalifitFlowLog.warn(
                    "⏱ ExercisePlan SERVER fallo tras ${System.currentTimeMillis() - tServer0}ms → ${e.message}"
                )
                android.util.Log.e("EXERCISE_PLAN", "Error al leer plan desde Firestore", e)
            }
    }

    private fun applyExercisePlanSnapshot(snapshot: QuerySnapshot, sourceLabel: String): Boolean {
        val tMap0 = System.currentTimeMillis()
        val doc = snapshot.documents.firstOrNull()
        val plan = doc?.toObject(FirebaseSavedExercisePlan::class.java)
        val uiDays = plan?.toUiDayExercisePlans()
        if (plan == null || uiDays.isNullOrEmpty()) {
            GalifitFlowLog.firestore(
                "ExercisePlan $sourceLabel vacío docs=${snapshot.size()} map=${System.currentTimeMillis() - tMap0}ms"
            )
            return false
        }
        activeExercisePlanDocId = doc.id
        activePlanSummary = plan.planSummary
        activePlanSource = plan.source
        GalifitFlowLog.firestore(
            "ExercisePlan $sourceLabel leído OK docId=${doc.id} días=${uiDays.size} " +
                "ejercicios=${uiDays.sumOf { it.exercises.size }} map=${System.currentTimeMillis() - tMap0}ms"
        )
        adapter.replaceDays(uiDays)
        return true
    }

    private fun onGenerateButtonPressed() {
        val uid = Firebase.auth.currentUser?.uid
        if (uid == null) {
            Toast.makeText(this, "Inicia sesión para generar tu rutina", Toast.LENGTH_SHORT).show()
            return
        }

        Firebase.firestore.collection("users").document(uid).get()
            .addOnSuccessListener { doc ->
                val prefs = doc.toUserPreferences()
                if (prefs == null || prefs.trainingDaysPerWeek <= 0) {
                    promptCompleteTrainingPreferences()
                } else {
                    executeWeeklyPlan(prefs)
                }
            }
            .addOnFailureListener {
                GalifitFlowLog.warn("Error leyendo preferencias: ${it.message}")
                Toast.makeText(this, "No se pudieron cargar tus preferencias", Toast.LENGTH_SHORT).show()
            }
    }

    private fun promptCompleteTrainingPreferences() {
        MaterialAlertDialogBuilder(this)
            .setTitle("Completa tu perfil de entrenamiento")
            .setMessage(
                "Necesitamos algunos datos sobre tu entrenamiento para generar la rutina " +
                    "(días por semana, equipamiento disponible, objetivo, etc.)."
            )
            .setPositiveButton("Editar preferencias") { _, _ ->
                startActivity(android.content.Intent(this, PreferencesActivity::class.java))
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun executeWeeklyPlan(prefs: UserPreferences) {
        generateButton.isEnabled = false
        showGenerationOverlay("Preparando catálogo de ejercicios…")
        lifecycleScope.launch {
            val tTotal0 = System.currentTimeMillis()
            try {
                GalifitFlowLog.api("⏱ executeWeeklyPlan: inicio")

                val tCat0 = System.currentTimeMillis()
                val catalog = withContext(Dispatchers.IO) { catalogProvider.loadCatalog() }
                GalifitFlowLog.api(
                    "⏱ executeWeeklyPlan: loadCatalog=${System.currentTimeMillis() - tCat0}ms " +
                        "(${catalog.exercises.size} ejercicios)"
                )

                showGenerationOverlay("Generando rutina…")
                val tGen0 = System.currentTimeMillis()
                val plan = withContext(Dispatchers.Default) {
                    generator.generate(prefs, catalog)
                }
                GalifitFlowLog.api(
                    "⏱ executeWeeklyPlan: generator.generate=${System.currentTimeMillis() - tGen0}ms"
                )

                val tUi0 = System.currentTimeMillis()
                activePlanSummary = plan.planSummary
                activePlanSource = plan.source
                adapter.replaceDays(plan.toUiDayExercisePlans())
                GalifitFlowLog.api(
                    "⏱ executeWeeklyPlan: adapter.replaceDays=${System.currentTimeMillis() - tUi0}ms"
                )

                saveExercisePlanToFirestore(plan)
                GalifitFlowLog.api(
                    "⏱ executeWeeklyPlan: FIN total=${System.currentTimeMillis() - tTotal0}ms"
                )
            } catch (t: Throwable) {
                GalifitFlowLog.warn(
                    "⏱ executeWeeklyPlan: FALLO tras ${System.currentTimeMillis() - tTotal0}ms → ${t.message}"
                )
                android.util.Log.e("EXERCISE_PLAN", "Error generando plan", t)
                Toast.makeText(
                    this@ExercisePlanActivity,
                    "No se pudo generar la rutina. Revisa tu conexión.",
                    Toast.LENGTH_LONG
                ).show()
            } finally {
                hideGenerationOverlay()
                generateButton.isEnabled = true
            }
        }
    }

    private fun saveExercisePlanToFirestore(document: FirebaseSavedExercisePlan) {
        val uid = Firebase.auth.currentUser?.uid
        if (uid == null) {
            GalifitFlowLog.warn("Firestore: sin uid, no se guarda exercisePlan")
            return
        }

        GalifitFlowLog.firestore("Escritura nuevo documento en users/$uid/exercisePlans (add)")
        val tSave0 = System.currentTimeMillis()
        Firebase.firestore.collection("users")
            .document(uid)
            .collection("exercisePlans")
            .add(document)
            .addOnSuccessListener { ref ->
                activeExercisePlanDocId = ref.id
                GalifitFlowLog.firestore(
                    "⏱ saveExercisePlan OK id=${ref.id} en ${System.currentTimeMillis() - tSave0}ms"
                )
            }
            .addOnFailureListener { e ->
                GalifitFlowLog.warn(
                    "⏱ saveExercisePlan FALLO tras ${System.currentTimeMillis() - tSave0}ms → ${e.message}"
                )
                android.util.Log.e("EXERCISE_PLAN", "Error al guardar el plan de ejercicios", e)
            }
    }
}
