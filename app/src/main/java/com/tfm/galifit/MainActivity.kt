package com.tfm.galifit

import android.os.Bundle
import android.util.Base64
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.google.android.material.button.MaterialButton
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.firebase.auth.auth
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.Source
import com.google.firebase.firestore.firestore
import com.google.firebase.Firebase
import com.tfm.galifit.adapter.HomeExerciseAdapter
import com.tfm.galifit.adapter.HomeMealAdapter
import com.tfm.galifit.api.APIService
import com.tfm.galifit.api.ApiClient
import com.tfm.galifit.data.model.DayExercisePlan
import com.tfm.galifit.data.model.DayMealPlan
import com.tfm.galifit.data.model.Exercise
import com.tfm.galifit.data.model.FirebaseSavedExercisePlan
import com.tfm.galifit.data.model.FirebaseSavedMealPlan
import com.tfm.galifit.data.model.Meal
import com.tfm.galifit.data.model.cacheMealPlanThumbnails
import com.tfm.galifit.data.model.refreshPresignedRecipeImages
import com.tfm.galifit.data.model.toUiDayExercisePlans
import com.tfm.galifit.data.model.toUiDayMealPlans
import com.tfm.galifit.util.EdamamCredentials
import com.tfm.galifit.util.GalifitFlowLog
import com.tfm.galifit.util.registerAndRequestStoragePermissionsOnStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

class MainActivity : GetNavigationBarActivity(), SwipeRefreshLayout.OnRefreshListener {

    override fun showChatFab(): Boolean = true

    override fun chatOrigin(): String = ChatAssistantActivity.ORIGIN_HOME

    private lateinit var swipeRefreshLayout: SwipeRefreshLayout
    private lateinit var tvDate: TextView
    private lateinit var tvCalorieLabel: TextView
    private lateinit var tvCalorieProgress: TextView
    private lateinit var rvMeals: RecyclerView
    private lateinit var rvExercises: RecyclerView
    private lateinit var progressBarMeals: ProgressBar
    private lateinit var progressBarExercises: ProgressBar
    private lateinit var tvEmptyMeals: TextView
    private lateinit var tvEmptyExercises: TextView

    private var targetCalories = 2000
    private var consumedCalories = 0
    private var totalMealsCount = 0
    private var checkedMealsCount = 0

    private val spainTz: TimeZone = TimeZone.getTimeZone("Europe/Madrid")
    private val dateKeyFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { timeZone = spainTz }
    private val displayDateFormat = SimpleDateFormat("EEEE, d 'de' MMMM", Locale("es", "ES")).apply { timeZone = spainTz }

    private lateinit var selectedDate: Calendar

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        registerAndRequestStoragePermissionsOnStart()
        setContentView(R.layout.inicio_page)

        selectedDate = Calendar.getInstance(spainTz)

        tvDate = findViewById(R.id.tvDate)
        tvCalorieLabel = findViewById(R.id.tvCalorieLabel)
        tvCalorieProgress = findViewById(R.id.tvCalorieProgress)
        rvMeals = findViewById(R.id.rvHomeMeals)
        rvExercises = findViewById(R.id.rvHomeExercises)
        progressBarMeals = findViewById(R.id.progressBarMeals)
        progressBarExercises = findViewById(R.id.progressBarExercises)
        tvEmptyMeals = findViewById(R.id.tvEmptyMeals)
        tvEmptyExercises = findViewById(R.id.tvEmptyExercises)
        swipeRefreshLayout = findViewById(R.id.swipeRefreshLayout)
        swipeRefreshLayout.setOnRefreshListener(this)

        rvMeals.layoutManager = LinearLayoutManager(this)
        rvMeals.isNestedScrollingEnabled = false
        rvExercises.layoutManager = LinearLayoutManager(this)
        rvExercises.isNestedScrollingEnabled = false

        findViewById<MaterialButton>(R.id.calendar_button).setOnClickListener { showDatePicker() }

        this.getNavigationView().selectedItemId = R.id.navigation_inicio

        loadForSelectedDate()
        loadExercises()
    }

    private fun showDatePicker() {
        val utcCal = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
        utcCal.set(
            selectedDate.get(Calendar.YEAR),
            selectedDate.get(Calendar.MONTH),
            selectedDate.get(Calendar.DAY_OF_MONTH),
            0, 0, 0
        )
        utcCal.set(Calendar.MILLISECOND, 0)

        val picker = MaterialDatePicker.Builder.datePicker()
            .setTitleText("Selecciona una fecha")
            .setSelection(utcCal.timeInMillis)
            .build()

        picker.addOnPositiveButtonClickListener { utcMillis ->
            val pickedUtc = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
            pickedUtc.timeInMillis = utcMillis
            val cal = Calendar.getInstance(spainTz)
            cal.set(
                pickedUtc.get(Calendar.YEAR),
                pickedUtc.get(Calendar.MONTH),
                pickedUtc.get(Calendar.DAY_OF_MONTH),
                12, 0, 0
            )
            selectedDate = cal
            consumedCalories = 0
            loadForSelectedDate()
        }

        picker.show(supportFragmentManager, "datePicker")
    }

    private fun loadForSelectedDate() {
        updateDateDisplay()
        loadTargetCaloriesAndMeals()
        loadExercises()
    }

    private fun isSelectedDateToday(): Boolean {
        val todayKey = dateKeyFormat.format(Date())
        val selectedKey = dateKeyFormat.format(selectedDate.time)
        return todayKey == selectedKey
    }

    private fun updateDateDisplay() {
        tvDate.text = displayDateFormat.format(selectedDate.time).replaceFirstChar { it.uppercase() }
        tvCalorieLabel.text = if (isSelectedDateToday()) {
            "Meta de calorías hoy:"
        } else {
            val shortDate = SimpleDateFormat("d MMM", Locale("es", "ES")).apply { timeZone = spainTz }
                .format(selectedDate.time)
            "Meta de calorías ($shortDate):"
        }
    }

    private fun updateCalorieCard() {
        tvCalorieProgress.text = if (isSelectedDateToday() && isDayCompleted()) {
            getString(R.string.home_kcal_completed)
        } else {
            "$consumedCalories / $targetCalories kcal"
        }
    }

    private fun isDayCompleted(): Boolean {
        if (totalMealsCount <= 0 || checkedMealsCount <= 0) return false
        if (checkedMealsCount >= totalMealsCount) return true
        if (targetCalories <= 0) return false
        return consumedCalories >= targetCalories * CALORIE_GOAL_TOLERANCE
    }

    private fun loadTargetCaloriesAndMeals() {
        val uid = Firebase.auth.currentUser?.uid
        if (uid == null) {
            showNoMealPlanMessage()
            return
        }

        Firebase.firestore.collection("users").document(uid).get()
            .addOnSuccessListener { doc ->
                val prefs = doc.get("preferences") as? Map<*, *>
                targetCalories = (prefs?.get("targetCalories") as? Number)?.toInt() ?: 2000
                if (targetCalories <= 0) targetCalories = 2000
                updateCalorieCard()
            }

        loadMealsForDate(uid)
    }

    private fun loadMealsForDate(uid: String) {
        progressBarMeals.visibility = View.VISIBLE
        tvEmptyMeals.visibility = View.GONE
        rvMeals.visibility = View.VISIBLE

        GalifitFlowLog.firestore(
            "Home meals: lectura último plan desde CACHE users/$uid/mealPlans"
        )
        val latestMealPlanQuery = Firebase.firestore.collection("users")
            .document(uid)
            .collection("mealPlans")
            .orderBy("createdAtMillis", Query.Direction.DESCENDING)
            .limit(1)

        latestMealPlanQuery
            .get(Source.CACHE)
            .addOnSuccessListener { snapshot ->
                val loaded = handleMealPlanSnapshot(uid, snapshot.documents.firstOrNull(), sourceLabel = "CACHE")
                if (!loaded) {
                    loadMealsForDateFromServer(uid, latestMealPlanQuery)
                }
            }
            .addOnFailureListener { e ->
                GalifitFlowLog.warn("Home meals: CACHE falló: ${e.message}; probando SERVER")
                loadMealsForDateFromServer(uid, latestMealPlanQuery)
            }
    }

    private fun loadMealsForDateFromServer(
        uid: String,
        latestMealPlanQuery: com.google.firebase.firestore.Query
    ) {
        GalifitFlowLog.firestore("Home meals: lectura último plan desde SERVER")
        latestMealPlanQuery
            .get(Source.SERVER)
            .addOnSuccessListener { snapshot ->
                val loaded = handleMealPlanSnapshot(uid, snapshot.documents.firstOrNull(), sourceLabel = "SERVER")
                if (!loaded) {
                    progressBarMeals.visibility = View.GONE
                    showNoMealPlanMessage()
                }
            }
            .addOnFailureListener { e ->
                GalifitFlowLog.warn("Home meals: SERVER falló: ${e.message}")
                progressBarMeals.visibility = View.GONE
                showNoMealPlanMessage()
            }
    }

    private fun handleMealPlanSnapshot(
        uid: String,
        doc: com.google.firebase.firestore.DocumentSnapshot?,
        sourceLabel: String
    ): Boolean {
        val plan = doc?.toObject(FirebaseSavedMealPlan::class.java)
        val uiDays = plan?.toUiDayMealPlans()
        GalifitFlowLog.firestore(
            "Home meals: $sourceLabel docId=${doc?.id} días=${uiDays?.size ?: 0} comidas=${uiDays?.sumOf { it.meals.size } ?: 0}"
        )
        if (uiDays.isNullOrEmpty()) return false

        resolveImagesAndShow(uid, uiDays)
        return true
    }

    private fun resolveImagesAndShow(uid: String, uiDays: List<DayMealPlan>) {
        val dayIndex = getDayIndexForDate(selectedDate)
        val selectedDay = uiDays.getOrNull(dayIndex) ?: uiDays.firstOrNull()
        if (selectedDay == null || selectedDay.meals.isEmpty()) {
            progressBarMeals.visibility = View.GONE
            showNoMealPlanMessage()
            return
        }
        val daysToRender = listOf(selectedDay)

        lifecycleScope.launch {
            try {
                val cached = withTimeoutOrNull(12_000L) {
                    withContext(Dispatchers.IO) {
                        daysToRender.cacheMealPlanThumbnails(this@MainActivity)
                    }
                } ?: run {
                    GalifitFlowLog.warn(
                        "Home meals: timeout cacheando miniaturas iniciales; se continúa sin bloquear UI"
                    )
                    daysToRender
                }

                val needsRefresh = cached.any { day ->
                    day.meals.any {
                        !it.recipeReferenceUrl.isNullOrBlank() &&
                            (it.thumbnailLocalPath.isNullOrBlank() || it.calories == null)
                    }
                }

                val finalDays = if (needsRefresh) {
                    val refreshed = withTimeoutOrNull(15_000L) {
                        val service = ApiClient().getEdamamMealPlanner().create(APIService::class.java)
                        val authHeader = EdamamCredentials.basicAuthHeader()
                        val refreshedDays = cached.refreshPresignedRecipeImages(
                            service,
                            authHeader,
                            EdamamCredentials.accountUser
                        )
                        withContext(Dispatchers.IO) {
                            refreshedDays.cacheMealPlanThumbnails(this@MainActivity)
                        }
                    }
                    if (refreshed == null) {
                        GalifitFlowLog.warn(
                            "Home meals: timeout refrescando imágenes/macros; se usa caché local"
                        )
                        cached
                    } else {
                        refreshed
                    }
                } else {
                    cached
                }

                val dayPlan = finalDays.firstOrNull() ?: selectedDay
                loadHistoryAndSetupAdapter(uid, dayPlan.meals)
            } catch (e: Exception) {
                GalifitFlowLog.warn("Home meals: error resolviendo imágenes/macros: ${e.message}")
                progressBarMeals.visibility = View.GONE
                showNoMealPlanMessage()
            }
        }
    }

    private fun loadHistoryAndSetupAdapter(uid: String, meals: List<Meal>) {
        val dateKey = dateKeyFormat.format(selectedDate.time)

        Firebase.firestore.collection("users")
            .document(uid)
            .collection("mealHistory")
            .document(dateKey)
            .get()
            .addOnSuccessListener { doc ->
                progressBarMeals.visibility = View.GONE
                @Suppress("UNCHECKED_CAST")
                val checkedNames = (doc.get("checkedMeals") as? List<String>)?.toSet() ?: emptySet()
                val savedConsumed = (doc.get("consumedCalories") as? Number)?.toInt()

                setupMealsAdapter(meals, checkedNames)

                if (savedConsumed != null) {
                    consumedCalories = savedConsumed
                    updateCalorieCard()
                }
            }
            .addOnFailureListener {
                progressBarMeals.visibility = View.GONE
                setupMealsAdapter(meals, emptySet())
            }
    }

    private fun getDayIndexForDate(cal: Calendar): Int {
        return when (cal.get(Calendar.DAY_OF_WEEK)) {
            Calendar.MONDAY -> 0
            Calendar.TUESDAY -> 1
            Calendar.WEDNESDAY -> 2
            Calendar.THURSDAY -> 3
            Calendar.FRIDAY -> 4
            Calendar.SATURDAY -> 5
            Calendar.SUNDAY -> 6
            else -> 0
        }
    }

    private fun showNoMealPlanMessage() {
        rvMeals.adapter = null
        rvMeals.visibility = View.GONE
        tvEmptyMeals.visibility = View.VISIBLE
        consumedCalories = 0
        totalMealsCount = 0
        checkedMealsCount = 0
        updateCalorieCard()
    }

    private fun setupMealsAdapter(meals: List<Meal>, initialChecked: Set<String>) {
        val uid = Firebase.auth.currentUser?.uid
        GalifitFlowLog.ui(
            "Home meals: ${meals.size} comidas, conKcal=${meals.count { it.calories != null }}"
        )

        tvEmptyMeals.visibility = View.GONE
        rvMeals.visibility = View.VISIBLE

        val adapter = HomeMealAdapter(meals, initialChecked) { checkedNames, checkedCals ->
            consumedCalories = checkedCals
            checkedMealsCount = checkedNames.size
            updateCalorieCard()
            if (uid != null) saveMealHistory(uid, checkedNames, checkedCals)
        }

        rvMeals.adapter = adapter
        totalMealsCount = meals.size
        checkedMealsCount = adapter.getCheckedNames().size
        consumedCalories = adapter.computeCheckedCalories()
        updateCalorieCard()
    }

    private fun saveMealHistory(uid: String, checkedNames: Set<String>, consumedCals: Int) {
        val dateKey = dateKeyFormat.format(selectedDate.time)
        val data = hashMapOf(
            "checkedMeals" to checkedNames.toList(),
            "consumedCalories" to consumedCals,
            "targetCalories" to targetCalories,
            "date" to dateKey,
            "updatedAtMillis" to System.currentTimeMillis()
        )

        Firebase.firestore.collection("users")
            .document(uid)
            .collection("mealHistory")
            .document(dateKey)
            .set(data)
    }

    private fun loadExercises() {
        val uid = Firebase.auth.currentUser?.uid
        if (uid == null) {
            showNoExercisePlanMessage()
            return
        }

        progressBarExercises.visibility = View.VISIBLE
        tvEmptyExercises.visibility = View.GONE
        rvExercises.visibility = View.VISIBLE

        val latestExercisePlanQuery = Firebase.firestore.collection("users")
            .document(uid)
            .collection("exercisePlans")
            .orderBy("createdAtMillis", Query.Direction.DESCENDING)
            .limit(1)

        GalifitFlowLog.firestore(
            "Home exercises: lectura último plan CACHE users/$uid/exercisePlans"
        )
        latestExercisePlanQuery
            .get(Source.CACHE)
            .addOnSuccessListener { snapshot ->
                val loaded = handleExercisePlanSnapshot(snapshot.documents.firstOrNull(), "CACHE")
                if (!loaded) {
                    loadExercisesFromServer(latestExercisePlanQuery)
                }
            }
            .addOnFailureListener { e ->
                GalifitFlowLog.warn("Home exercises: CACHE falló: ${e.message}; probando SERVER")
                loadExercisesFromServer(latestExercisePlanQuery)
            }
    }

    private fun loadExercisesFromServer(query: com.google.firebase.firestore.Query) {
        GalifitFlowLog.firestore("Home exercises: lectura último plan SERVER")
        query.get(Source.SERVER)
            .addOnSuccessListener { snapshot ->
                val loaded = handleExercisePlanSnapshot(snapshot.documents.firstOrNull(), "SERVER")
                if (!loaded) {
                    progressBarExercises.visibility = View.GONE
                    showNoExercisePlanMessage()
                }
            }
            .addOnFailureListener { e ->
                GalifitFlowLog.warn("Home exercises: SERVER falló: ${e.message}")
                progressBarExercises.visibility = View.GONE
                showNoExercisePlanMessage()
            }
    }

    private fun handleExercisePlanSnapshot(
        doc: com.google.firebase.firestore.DocumentSnapshot?,
        sourceLabel: String
    ): Boolean {
        val plan = doc?.toObject(FirebaseSavedExercisePlan::class.java)
        val uiDays = plan?.toUiDayExercisePlans()
        GalifitFlowLog.firestore(
            "Home exercises: $sourceLabel docId=${doc?.id} días=${uiDays?.size ?: 0}"
        )
        if (uiDays.isNullOrEmpty()) return false

        val dayIndex = getDayIndexForDate(selectedDate)
        val selectedDay = uiDays.getOrNull(dayIndex) ?: uiDays.firstOrNull()
        progressBarExercises.visibility = View.GONE

        if (selectedDay == null) {
            showNoExercisePlanMessage()
            return true
        }
        if (selectedDay.isRest) {
            showRestDayMessage()
            return true
        }
        if (selectedDay.exercises.isEmpty()) {
            showNoExercisePlanMessage()
            return true
        }
        val uid = Firebase.auth.currentUser?.uid
        if (uid != null) {
            loadExerciseHistoryAndSetupAdapter(uid, selectedDay)
        } else {
            setupExercisesAdapter(selectedDay, emptySet())
        }
        return true
    }

    private fun loadExerciseHistoryAndSetupAdapter(uid: String, day: DayExercisePlan) {
        val dateKey = dateKeyFormat.format(selectedDate.time)
        Firebase.firestore.collection("users")
            .document(uid)
            .collection("exerciseHistory")
            .document(dateKey)
            .get()
            .addOnSuccessListener { doc ->
                @Suppress("UNCHECKED_CAST")
                val checkedNames = (doc.get("checkedExercises") as? List<String>)?.toSet() ?: emptySet()
                setupExercisesAdapter(day, checkedNames)
            }
            .addOnFailureListener {
                setupExercisesAdapter(day, emptySet())
            }
    }

    private fun setupExercisesAdapter(day: DayExercisePlan, initialChecked: Set<String>) {
        val uid = Firebase.auth.currentUser?.uid
        tvEmptyExercises.visibility = View.GONE
        rvExercises.visibility = View.VISIBLE
        val uiExercises = day.exercises.map { it.withFormattedDetails() }
        rvExercises.adapter = HomeExerciseAdapter(uiExercises, initialChecked) { checkedNames ->
            if (uid != null) saveExerciseHistory(uid, checkedNames)
        }
    }

    private fun saveExerciseHistory(uid: String, checkedNames: Set<String>) {
        val dateKey = dateKeyFormat.format(selectedDate.time)
        val data = hashMapOf(
            "checkedExercises" to checkedNames.toList(),
            "date" to dateKey,
            "updatedAtMillis" to System.currentTimeMillis()
        )
        Firebase.firestore.collection("users")
            .document(uid)
            .collection("exerciseHistory")
            .document(dateKey)
            .set(data)
    }

    private fun Exercise.withFormattedDetails(): Exercise {
        if (details.isNotBlank()) return this
        val parts = mutableListOf<String>()
        if (sets > 0) parts.add("$sets series")
        val repsText = when {
            !repsOrDuration.isNullOrBlank() -> repsOrDuration
            repsLow > 0 && repsHigh > 0 && repsLow != repsHigh -> "$repsLow-$repsHigh reps"
            repsHigh > 0 -> "$repsHigh reps"
            else -> null
        }
        if (repsText != null) parts.add(repsText)
        if (restSeconds > 0) parts.add("descanso ${restSeconds}s")
        return copy(details = parts.joinToString(" · "))
    }

    private fun showNoExercisePlanMessage() {
        rvExercises.adapter = null
        rvExercises.visibility = View.GONE
        tvEmptyExercises.text = getString(R.string.home_no_exercise_plan)
        tvEmptyExercises.visibility = View.VISIBLE
    }

    private fun showRestDayMessage() {
        rvExercises.adapter = null
        rvExercises.visibility = View.GONE
        tvEmptyExercises.text = getString(R.string.home_rest_day)
        tvEmptyExercises.visibility = View.VISIBLE
    }

    override fun onRefresh() {
        consumedCalories = 0
        selectedDate = Calendar.getInstance(spainTz)
        loadForSelectedDate()
        loadExercises()
        swipeRefreshLayout.isRefreshing = false
    }

    private companion object {

        const val CALORIE_GOAL_TOLERANCE = 0.95f
    }
}
