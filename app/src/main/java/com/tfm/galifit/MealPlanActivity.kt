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
import com.google.firebase.auth.auth
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.Source
import com.google.firebase.firestore.firestore
import com.google.firebase.Firebase
import com.google.gson.Gson
import com.tfm.galifit.adapter.DayMealPlanAdapter
import com.tfm.galifit.api.APIService
import com.tfm.galifit.api.ApiClient
import com.tfm.galifit.data.model.CustomDish
import com.tfm.galifit.data.model.DayMealPlan
import com.tfm.galifit.data.model.EdamamPlanRequest
import com.tfm.galifit.data.model.FirebaseMealPlanDay
import com.tfm.galifit.data.model.MealMacrosSummary
import com.tfm.galifit.data.model.FirebaseSavedMealPlan
import com.tfm.galifit.data.model.Meal
import com.tfm.galifit.data.model.UserPreferences
import com.tfm.galifit.data.model.cacheMealPlanThumbnails
import com.tfm.galifit.data.model.defaultEmptyWeekDayMealPlans
import com.tfm.galifit.data.model.enrichMealsWithRecipeDetails
import com.tfm.galifit.data.model.isProteinSupplementMeal
import com.tfm.galifit.data.model.refreshPresignedRecipeImages
import com.tfm.galifit.data.model.toCustomPlannedSummary
import com.tfm.galifit.data.model.toFirebaseSavedMealPlan
import com.tfm.galifit.data.model.toMeal
import com.tfm.galifit.data.model.toUiDayMealPlans
import com.tfm.galifit.data.model.withProteinSupplementsIfNeeded
import com.tfm.galifit.data.repository.UserRepository
import com.tfm.galifit.domain.usecase.BuildEdamamPlanRequestUseCase
import com.tfm.galifit.util.EdamamCredentials
import com.tfm.galifit.util.GalifitFlowLog
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

class MealPlanActivity : GetNavigationBarActivity() {

    private val userRepository = UserRepository()
    private val buildEdamamPlanRequest = BuildEdamamPlanRequestUseCase()

    override fun showChatFab(): Boolean = true

    override fun chatOrigin(): String = ChatAssistantActivity.ORIGIN_MEALS

    private lateinit var recyclerDayMealPlans: RecyclerView
    private lateinit var adapter: DayMealPlanAdapter
    private lateinit var generateButton: Button
    private lateinit var generationOverlay: View
    private lateinit var generationStatus: TextView

    private var activePlanDocId: String? = null

    private var pendingDayIndex: Int = -1

    private lateinit var createDishLauncher: ActivityResultLauncher<Intent>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.meal_plan)

        recyclerDayMealPlans = findViewById(R.id.rvDays)

        adapter = DayMealPlanAdapter(
            dias = defaultEmptyWeekDayMealPlans().toMutableList(),
            onAddPlato = { dayIndex -> launchCreateDish(dayIndex, null) },
            onEditCustomDish = { dayIndex, meal, _ ->
                meal.customDishId?.let { launchCreateDish(dayIndex, it) }
            },
            onDeleteMeal = { _, meal, _ ->
                if (meal.isCustom) {
                    persistCurrentPlanToFirestore()
                } else {
                    persistCurrentPlanToFirestore()
                }
            }
        )

        recyclerDayMealPlans.layoutManager = LinearLayoutManager(this)
        recyclerDayMealPlans.adapter = adapter

        createDishLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                val data = result.data ?: return@registerForActivityResult
                val dishId = data.getStringExtra(CreateDishActivity.EXTRA_RESULT_DISH_ID)
                    ?: return@registerForActivityResult
                val dayIndex = data.getIntExtra(CreateDishActivity.EXTRA_DAY_INDEX, pendingDayIndex)
                val isEdit = data.getBooleanExtra(CreateDishActivity.EXTRA_RESULT_IS_EDIT, false)
                onCustomDishSaved(dayIndex, dishId, isEdit)
            }
        }

        loadLatestMealPlanFromFirestore()

        generationOverlay = findViewById(R.id.mealGenerationOverlay)
        generationStatus = findViewById(R.id.mealGenerationStatus)

        generateButton = findViewById(R.id.button)
        generateButton.setOnClickListener {
            onGenerateButtonPressed()
        }

        this.getNavigationView().selectedItemId = R.id.navigation_platos
    }

    private fun showGenerationOverlay(message: String) {
        generationStatus.text = message
        generationOverlay.visibility = View.VISIBLE
    }

    private fun hideGenerationOverlay() {
        generationOverlay.visibility = View.GONE
    }

    private fun launchCreateDish(dayIndex: Int, dishId: String?) {
        pendingDayIndex = dayIndex
        val intent = Intent(this, CreateDishActivity::class.java).apply {
            putExtra(CreateDishActivity.EXTRA_DAY_INDEX, dayIndex)
            if (dishId != null) putExtra(CreateDishActivity.EXTRA_DISH_ID, dishId)
        }
        createDishLauncher.launch(intent)
    }

    private fun onCustomDishSaved(dayIndex: Int, dishId: String, isEdit: Boolean) {
        val uid = Firebase.auth.currentUser?.uid ?: return
        Firebase.firestore.collection("users")
            .document(uid)
            .collection("customDishes")
            .document(dishId)
            .get()
            .addOnSuccessListener { doc ->
                val dish = doc.toObject(CustomDish::class.java) ?: return@addOnSuccessListener
                val meal = dish.toMeal()
                if (isEdit) {
                    var found = false
                    val days = adapter.getDays()
                    for (d in days.indices) {
                        val meals = days[d].meals
                        for (m in meals.indices) {
                            if (meals[m].customDishId == dishId) {
                                adapter.updateMealInDay(d, m, meal)
                                found = true
                            }
                        }
                    }
                    if (!found && dayIndex in days.indices) {
                        adapter.addMealToDay(dayIndex, meal)
                    }
                } else {
                    if (dayIndex in adapter.getDays().indices) {
                        adapter.addMealToDay(dayIndex, meal)
                    }
                }
                persistCurrentPlanToFirestore()
            }
            .addOnFailureListener {
                GalifitFlowLog.warn("No se pudo cargar customDish $dishId tras guardar")
            }
    }

    private fun persistCurrentPlanToFirestore() {
        val uid = Firebase.auth.currentUser?.uid ?: return
        val days = adapter.getDays()
        val docToWrite = buildFirebasePlanFromUi(days)

        val mealPlansRef = Firebase.firestore.collection("users")
            .document(uid)
            .collection("mealPlans")

        val docId = activePlanDocId
        if (docId != null) {
            mealPlansRef.document(docId).set(docToWrite)
                .addOnSuccessListener {
                    GalifitFlowLog.firestore("Plan actualizado id=$docId (custom dishes)")
                }
                .addOnFailureListener { e ->
                    GalifitFlowLog.warn("Error actualizando plan: ${e.message}")
                }
        } else {
            mealPlansRef.add(docToWrite)
                .addOnSuccessListener { ref ->
                    activePlanDocId = ref.id
                    GalifitFlowLog.firestore("Plan creado id=${ref.id} (primer custom)")
                }
                .addOnFailureListener { e ->
                    GalifitFlowLog.warn("Error creando plan: ${e.message}")
                }
        }
    }

    private fun buildFirebasePlanFromUi(days: List<DayMealPlan>): FirebaseSavedMealPlan {
        val firebaseDays = days.mapIndexed { index, day ->
            FirebaseMealPlanDay().apply {
                dayIndex = index.toLong()
                dayLabel = day.dayName
                meals = day.meals.map { meal ->
                    if (meal.isCustom) {
                        meal.toCustomPlannedSummary()
                    } else {
                        meal.toEdamamSummary()
                    }
                }
            }
        }
        return FirebaseSavedMealPlan().apply {
            createdAtMillis = System.currentTimeMillis()
            edamamStatus = "OK"
            source = "edamam_meal_planner"
            this.days = firebaseDays
        }
    }

    private fun Meal.toEdamamSummary(): com.tfm.galifit.data.model.FirebasePlannedMealSummary {
        val src = this
        return com.tfm.galifit.data.model.FirebasePlannedMealSummary().apply {
            sectionKey = ""
            name = src.name
            recipeReferenceUrl = src.recipeReferenceUrl ?: ""
            thumbnailImageUrl = src.thumbnailImageUrl
            detailImageUrl = src.detailImageUrl
            customDishId = null
            macros = MealMacrosSummary().apply {
                caloriesKcal = src.calories?.toDouble()
                proteinGrams = src.proteinGrams?.toDouble()
                carbsGrams = src.carbsGrams?.toDouble()
                fatGrams = src.fatGrams?.toDouble()
            }
        }
    }

    private fun onGenerateButtonPressed() {
        if (adapter.hasCustomMeals()) {
            MaterialAlertDialogBuilder(this)
                .setTitle("Tienes platos personalizados")
                .setMessage(
                    "Has añadido platos creados por ti al plan actual. ¿Qué quieres hacer al " +
                        "generar el nuevo plan semanal?"
                )
                .setPositiveButton("Mantener mis platos") { _, _ ->
                    generateWeeklyPlan(keepCustomDishes = true)
                }
                .setNegativeButton("Generar plan limpio") { _, _ ->
                    generateWeeklyPlan(keepCustomDishes = false)
                }
                .setNeutralButton("Cancelar", null)
                .show()
        } else {
            generateWeeklyPlan(keepCustomDishes = false)
        }
    }

    private fun loadLatestMealPlanFromFirestore() {
        val uid = Firebase.auth.currentUser?.uid
        if (uid == null) {
            GalifitFlowLog.warn("Firestore: sin usuario, no se carga mealPlan")
            return
        }

        GalifitFlowLog.firestore(
            "Lectura último plan CACHE: users/$uid/mealPlans orderBy createdAtMillis DESC limit 1"
        )
        val latestPlanQuery = Firebase.firestore.collection("users")
            .document(uid)
            .collection("mealPlans")
            .orderBy("createdAtMillis", Query.Direction.DESCENDING)
            .limit(1)

        latestPlanQuery
            .get(Source.CACHE)
            .addOnSuccessListener { snapshot ->
                val loaded = handleMealPlanDocument(
                    snapshot.documents.firstOrNull(),
                    sourceLabel = "CACHE"
                )
                if (!loaded) {
                    loadLatestMealPlanFromServer(latestPlanQuery)
                }
            }
            .addOnFailureListener { e ->
                GalifitFlowLog.warn("Firestore lectura mealPlan CACHE falló: ${e.message}; probando SERVER")
                loadLatestMealPlanFromServer(latestPlanQuery)
            }
    }

    private fun loadLatestMealPlanFromServer(latestPlanQuery: Query) {
        GalifitFlowLog.firestore("Lectura último plan SERVER: mealPlans orderBy createdAtMillis DESC limit 1")
        latestPlanQuery
            .get(Source.SERVER)
            .addOnSuccessListener { snapshot ->
                val loaded = handleMealPlanDocument(
                    snapshot.documents.firstOrNull(),
                    sourceLabel = "SERVER"
                )
                if (!loaded) {
                    GalifitFlowLog.firestore("No hay plan guardado o documento vacío")
                }
            }
            .addOnFailureListener { e ->
                GalifitFlowLog.warn("Firestore lectura mealPlan SERVER falló: ${e.message}")
                android.util.Log.e("MEAL_PLAN", "Error al leer plan desde Firestore", e)
            }
    }

    private fun handleMealPlanDocument(
        doc: com.google.firebase.firestore.DocumentSnapshot?,
        sourceLabel: String
    ): Boolean {
        val plan = doc?.toObject(FirebaseSavedMealPlan::class.java)
        val uiDays = plan?.toUiDayMealPlans()?.toMutableList()
        GalifitFlowLog.firestore(
            "Plan $sourceLabel docId=${doc?.id} días=${uiDays?.size ?: 0} comidas=${uiDays?.sumOf { it.meals.size } ?: 0}"
        )
        if (uiDays.isNullOrEmpty()) return false

        activePlanDocId = doc?.id
        adapter.replaceDays(uiDays)

        val apiClient = ApiClient().getEdamamMealPlanner()
        val service = apiClient.create(APIService::class.java)
        val authHeader = EdamamCredentials.basicAuthHeader()
        val edamamAccountUser = EdamamCredentials.accountUser
        lifecycleScope.launch {
            GalifitFlowLog.ui("Tras Firestore $sourceLabel: refrescar URLs firmadas + caché miniaturas en disco")
            val refreshed = uiDays.refreshPresignedRecipeImages(
                service,
                authHeader,
                edamamAccountUser
            )
            val cached = withContext(Dispatchers.IO) {
                refreshed.cacheMealPlanThumbnails(this@MealPlanActivity)
            }
            adapter.replaceDays(cached.toMutableList())
        }
        return true
    }

    private fun generateWeeklyPlan(keepCustomDishes: Boolean) {
        val customMealsByDay: Map<Int, List<Meal>> = if (keepCustomDishes) {
            adapter.getDays()
                .mapIndexed { i, d ->
                    i to d.meals.filter { it.isCustom && !it.isProteinSupplementMeal() }
                }
                .toMap()
        } else {
            emptyMap()
        }

        lifecycleScope.launch {
            generateButton.isEnabled = false
            try {
                showGenerationOverlay(getString(R.string.meal_generation_loading_prefs))
                val (body, prefs) = loadMealPlanRequest()

                showGenerationOverlay(getString(R.string.meal_generation_running))
                val document = buildMealPlanDocument(body, prefs, customMealsByDay)

                val cached = withContext(Dispatchers.IO) {
                    document.toUiDayMealPlans().cacheMealPlanThumbnails(this@MealPlanActivity)
                }
                adapter.replaceDays(cached.toMutableList())

                showGenerationOverlay(getString(R.string.meal_generation_saving))
                saveMealPlanToFirestore(document)
            } catch (e: CancellationException) {
                throw e
            } catch (t: Throwable) {
                if (!isFinishing && !isDestroyed) {
                    GalifitFlowLog.warn("generateWeeklyPlan meal: error → ${t.message}")
                    android.util.Log.e("MEAL_PLAN", "Error generando plan de comidas", t)
                    Toast.makeText(
                        this@MealPlanActivity,
                        R.string.meal_generation_failure,
                        Toast.LENGTH_LONG
                    ).show()
                }
            } finally {
                if (!isFinishing) {
                    hideGenerationOverlay()
                    generateButton.isEnabled = true
                }
            }
        }
    }

    private suspend fun loadMealPlanRequest(): Pair<EdamamPlanRequest, UserPreferences?> {
        val uid = userRepository.currentUid()
            ?: return buildEdamamPlanRequest(null) to null
        return try {
            val prefs = userRepository.getPreferences(uid)
            buildEdamamPlanRequest(prefs) to prefs
        } catch (e: Exception) {
            GalifitFlowLog.warn("Error leyendo preferencias: ${e.message}")
            buildEdamamPlanRequest(null) to null
        }
    }

    private suspend fun buildMealPlanDocument(
        body: EdamamPlanRequest,
        prefs: UserPreferences?,
        customMealsByDay: Map<Int, List<Meal>>
    ): FirebaseSavedMealPlan {
        val service = ApiClient().getEdamamMealPlanner().create(APIService::class.java)
        val authHeader = EdamamCredentials.basicAuthHeader()
        val accountUser = EdamamCredentials.accountUser

        val response = withContext(Dispatchers.IO) {
            android.util.Log.d("API_DEBUG", "Cuerpo de la petición: ${Gson().toJson(body)}")
            GalifitFlowLog.api("POST generateWeeklyMealPlan (Edamam meal planner, 7 días)")
            service.generateWeeklyMealPlan(
                EdamamCredentials.appId,
                authHeader,
                accountUser,
                body
            )
        }

        if (!response.isSuccessful) {
            val errorBody = withContext(Dispatchers.IO) { response.errorBody()?.string() }
            GalifitFlowLog.warn("POST planner falló HTTP ${response.code()}: $errorBody")
            android.util.Log.e("API_DEBUG", "Error en la respuesta: $errorBody")
            throw IllegalStateException("Edamam HTTP ${response.code()}")
        }

        val plan = response.body()
            ?: throw IllegalStateException("Respuesta vacía de Edamam")

        val usableDays = plan.selection?.count { it.sections?.isNotEmpty() == true } ?: 0
        android.util.Log.d(
            "API_DEBUG",
            "Edamam status=${plan.status}, díasEnSelection=${plan.selection?.size ?: 0}, díasÚtiles=$usableDays"
        )

        var document = plan.toFirebaseSavedMealPlan()
        document = document.enrichMealsWithRecipeDetails(
            service,
            authHeader,
            accountUser,
            applicationContext
        )

        if (customMealsByDay.isNotEmpty()) {
            document.days.forEach { day ->
                val dayIdx = day.dayIndex.toInt()
                val customs = customMealsByDay[dayIdx].orEmpty()
                if (customs.isNotEmpty()) {
                    day.meals = day.meals + customs.map { it.toCustomPlannedSummary() }
                }
            }
        }

        if (prefs != null) {
            document = document.withProteinSupplementsIfNeeded(prefs)
        }
        return document
    }

    private fun saveMealPlanToFirestore(document: FirebaseSavedMealPlan) {
        val user = Firebase.auth.currentUser
        val uid = user?.uid
        if (uid == null) {
            GalifitFlowLog.warn("Firestore: sin uid, no se guarda mealPlan")
            return
        }

        GalifitFlowLog.firestore("Escritura nuevo documento en users/$uid/mealPlans (add)")
        val db = Firebase.firestore
        db.collection("users")
            .document(uid)
            .collection("mealPlans")
            .add(document)
            .addOnSuccessListener { ref ->
                activePlanDocId = ref.id
                GalifitFlowLog.firestore("Plan guardado OK id=${ref.id}")
                android.util.Log.d("MEAL_PLAN", "Plan de comidas guardado con id=${ref.id}")
            }
            .addOnFailureListener { e ->
                GalifitFlowLog.warn("Firestore escritura mealPlan falló: ${e.message}")
                android.util.Log.e("MEAL_PLAN", "Error al guardar el plan de comidas", e)
            }
    }
}
