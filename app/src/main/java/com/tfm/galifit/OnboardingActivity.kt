package com.tfm.galifit

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.google.firebase.auth.auth
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.firestore
import com.google.firebase.Firebase
import com.tfm.galifit.adapter.QuestionAdapter
import com.tfm.galifit.data.EquipmentKeyMapper
import com.tfm.galifit.data.model.UserPreferences
import com.tfm.galifit.fragments.OnboardingBasicDataFragment
import com.tfm.galifit.fragments.OnboardingFoodPrefsFragment
import com.tfm.galifit.fragments.OnboardingHealthProfileFragment
import com.tfm.galifit.fragments.OnboardingMultiSelectFragment
import com.tfm.galifit.fragments.OnboardingSingleSelectFragment
import com.tfm.galifit.notifications.SedentaryDataSource
import com.tfm.galifit.notifications.SedentaryNotificationScheduler
import com.tfm.galifit.notifications.SedentaryPermissionOutcome
import com.tfm.galifit.notifications.SedentaryPermissionUi
import com.tfm.galifit.notifications.SedentaryPermissionsHelper
import com.tfm.galifit.domain.usecase.CalculateNutritionTargetsUseCase
import com.tfm.galifit.util.CalorieCalculator
import com.tfm.galifit.util.ObjectiveMapper

class OnboardingActivity : AppCompatActivity() {

    private val calculateNutritionTargets = CalculateNutritionTargetsUseCase()

    private lateinit var viewPager: ViewPager2
    private lateinit var btnNext: Button
    private lateinit var btnBack: Button
    private lateinit var progress: LinearProgressIndicator

    private val IDX_OBJECTIVE = 2
    private val IDX_WEIGHT_GOAL = 3
    private val IDX_DIET = 4
    private val IDX_TRAINING_DAYS = 9
    private val IDX_TRAINING_LOCATION = 11
    private val IDX_TRAINING_EQUIPMENT = 12
    private val IDX_SEDENTARY_NOTIFICATIONS = 16
    private val IDX_SEDENTARY_SOURCE = 17

    private val answers = mutableMapOf<String, String>()

    private lateinit var allFragments: List<Fragment>
    private lateinit var basicDataFragment: OnboardingBasicDataFragment
    private lateinit var activityFragment: OnboardingSingleSelectFragment
    private lateinit var objectiveFragment: OnboardingSingleSelectFragment
    private lateinit var weightGoalFragment: OnboardingSingleSelectFragment
    private lateinit var dietFragment: OnboardingSingleSelectFragment
    private lateinit var healthProfileFragment: OnboardingHealthProfileFragment
    private lateinit var foodPrefsFragment: OnboardingFoodPrefsFragment
    private lateinit var mealsFragment: OnboardingSingleSelectFragment
    private lateinit var proteinSupplementsFragment: OnboardingSingleSelectFragment

    private lateinit var trainingDaysFragment: OnboardingSingleSelectFragment
    private lateinit var trainingMinutesFragment: OnboardingSingleSelectFragment
    private lateinit var trainingLocationFragment: OnboardingSingleSelectFragment
    private lateinit var trainingEquipmentFragment: OnboardingMultiSelectFragment
    private lateinit var trainingLevelFragment: OnboardingSingleSelectFragment
    private lateinit var trainingSplitFragment: OnboardingSingleSelectFragment
    private lateinit var trainingLimitationsFragment: OnboardingMultiSelectFragment
    private lateinit var sedentaryNotificationsFragment: OnboardingSingleSelectFragment
    private lateinit var sedentarySourceFragment: OnboardingSingleSelectFragment
    private lateinit var sedentaryPermissionsHelper: SedentaryPermissionsHelper
    private var awaitingSedentaryPermissions = false

    private fun wantsSedentaryNotifications(): Boolean {
        return (answer("sedentaryNotifications")
            ?: sedentaryNotificationsFragment.getSelectedKey()) == "true"
    }

    private fun objectiveKey(): String? =
        answer("objective") ?: objectiveFragment.getSelectedKey()

    private fun trainingLocationKey(): String? =
        answer("trainingLocation") ?: trainingLocationFragment.getSelectedKey()

    private fun answer(key: String): String? = answers[key]

    private fun needsWeightGoal(): Boolean =
        ObjectiveMapper.needsWeeklyWeightGoal(objectiveKey().orEmpty())

    private fun needsEquipmentQuestion(): Boolean {
        return trainingLocationKey() == "home_basic"
    }

    private fun snapshotCurrentStep(position: Int) {
        when (position) {
            1 -> activityFragment.getSelectedKey()?.let { answers["activity"] = it }
            IDX_OBJECTIVE -> objectiveFragment.getSelectedKey()?.let { answers["objective"] = it }
            IDX_WEIGHT_GOAL -> weightGoalFragment.getSelectedKey()?.let { answers["weightGoal"] = it }
            IDX_DIET -> dietFragment.getSelectedKey()?.let { answers["diet"] = it }
            7 -> mealsFragment.getSelectedKey()?.let { answers["meals"] = it }
            8 -> proteinSupplementsFragment.getSelectedKey()?.let { answers["proteinSupplements"] = it }
            IDX_TRAINING_DAYS -> trainingDaysFragment.getSelectedKey()?.let { answers["trainingDays"] = it }
            10 -> trainingMinutesFragment.getSelectedKey()?.let { answers["trainingMinutes"] = it }
            IDX_TRAINING_LOCATION -> trainingLocationFragment.getSelectedKey()?.let {
                answers["trainingLocation"] = it
            }
            13 -> trainingLevelFragment.getSelectedKey()?.let { answers["trainingLevel"] = it }
            14 -> trainingSplitFragment.getSelectedKey()?.let { answers["trainingSplit"] = it }
            IDX_SEDENTARY_NOTIFICATIONS -> sedentaryNotificationsFragment.getSelectedKey()?.let {
                answers["sedentaryNotifications"] = it
            }
            IDX_SEDENTARY_SOURCE -> sedentarySourceFragment.getSelectedKey()?.let {
                answers["sedentarySource"] = it
            }
        }
    }

    private fun validateCurrentStep(position: Int): Boolean {
        snapshotCurrentStep(position)
        return when (position) {
            1 -> {
                if (answer("activity") == null) {
                    Toast.makeText(this, "Selecciona tu nivel de actividad", Toast.LENGTH_SHORT).show()
                    false
                } else true
            }
            IDX_OBJECTIVE -> {
                if (objectiveKey() == null) {
                    Toast.makeText(this, "Selecciona tu objetivo principal", Toast.LENGTH_SHORT).show()
                    false
                } else true
            }
            IDX_WEIGHT_GOAL -> {
                if (needsWeightGoal() && answer("weightGoal") == null) {
                    Toast.makeText(this, "Selecciona tu meta de peso semanal", Toast.LENGTH_SHORT).show()
                    false
                } else true
            }
            IDX_DIET -> {
                if (answer("diet") == null) {
                    Toast.makeText(this, "Selecciona un tipo de dieta", Toast.LENGTH_SHORT).show()
                    false
                } else true
            }
            IDX_TRAINING_DAYS -> {
                if (answer("trainingDays") == null) {
                    Toast.makeText(this, "Selecciona cuántos días quieres entrenar", Toast.LENGTH_SHORT).show()
                    false
                } else true
            }
            else -> true
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        sedentaryPermissionsHelper = SedentaryPermissionsHelper(this)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.onboarding)

        viewPager = findViewById(R.id.viewPager)
        btnNext = findViewById(R.id.onboardingNextButton)
        btnBack = findViewById(R.id.onboardingBackButton)
        progress = findViewById(R.id.onboardingProgress)

        buildFragments()

        viewPager.adapter = QuestionAdapter(this, allFragments)
        viewPager.offscreenPageLimit = allFragments.lastIndex
        viewPager.isUserInputEnabled = false

        viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                if (position == IDX_WEIGHT_GOAL) {
                    configureWeightGoalOptions()
                }
                updateUI(position)
            }
        })

        btnNext.setOnClickListener { onNextClicked() }
        btnBack.setOnClickListener { onBackClicked() }

        updateUI(0)
    }

    private fun buildFragments() {
        basicDataFragment = OnboardingBasicDataFragment()

        activityFragment = OnboardingSingleSelectFragment.newInstance(
            title = "¿Cuál es tu nivel de actividad física?",
            labels = arrayOf(
                "Sedentario (casi sin actividad física en el día a día)",
                "Actividad ligera (1–2 días de ejercicio a la semana o caminar con frecuencia)",
                "Moderado (3–4 días de ejercicio a la semana)",
                "Alto (5–6 días de ejercicio a la semana)",
                "Muy alto (entrenamiento casi diario o trabajo físico intenso)"
            ),
            keys = arrayOf("sedentary", "light", "moderate", "high", "very_high")
        )

        objectiveFragment = OnboardingSingleSelectFragment.newInstance(
            title = "¿Cuál es tu objetivo principal?",
            labels = ObjectiveMapper.BODY_OBJECTIVE_LABELS,
            keys = ObjectiveMapper.BODY_OBJECTIVE_KEYS
        )

        weightGoalFragment = OnboardingSingleSelectFragment.newInstance(
            title = "¿Cuántos kilos quieres perder/ganar por semana?",
            labels = arrayOf("0,25 kg", "0,5 kg", "0,75 kg", "1 kg"),
            keys = arrayOf("0.25", "0.5", "0.75", "1.0")
        )

        dietFragment = OnboardingSingleSelectFragment.newInstance(
            title = "¿Qué tipo de dieta quieres seguir?",
            labels = arrayOf(
                "Sin dieta específica",
                "Vegetariana",
                "Vegana",
                "Pescetariana",
                "Baja en carbohidratos",
                "Alta en proteínas",
                "Baja en grasas",
                "Mediterránea",
                "Keto",
                "Paleo"
            ),
            keys = arrayOf(
                "none", "vegetarian", "vegan", "pescatarian",
                "low_carb", "high_protein", "low_fat", "mediterranean", "keto", "paleo"
            )
        )

        healthProfileFragment = OnboardingHealthProfileFragment()

        foodPrefsFragment = OnboardingFoodPrefsFragment()

        mealsFragment = OnboardingSingleSelectFragment.newInstance(
            title = "¿Cuántas comidas realizas al día?",
            labels = arrayOf(
                "2 (comida, cena)",
                "3 (desayuno, comida, cena)",
                "4 (desayuno, comida, merienda, cena)",
                "5 (desayuno, media mañana, comida, merienda, cena)"
            ),
            keys = arrayOf("2", "3", "4", "5"),
            defaultSelectedKey = "3"
        )

        proteinSupplementsFragment = OnboardingSingleSelectFragment.newInstance(
            title = "Si tu plan nutricional no alcanza tus necesidades de proteína únicamente con alimentos, ¿aceptarías incluir suplementos de proteína?",
            labels = arrayOf(
                "Sí, pero solo si es necesario. Prefiero obtener toda la proteína de los alimentos.",
                "No, no quiero utilizar suplementos."
            ),
            keys = arrayOf("true", "false")
        )

        trainingDaysFragment = OnboardingSingleSelectFragment.newInstance(
            title = "¿Cuántos días a la semana quieres entrenar?",
            labels = arrayOf("2 días", "3 días", "4 días", "5 días", "6 días"),
            keys = arrayOf("2", "3", "4", "5", "6"),
            defaultSelectedKey = "3"
        )

        trainingMinutesFragment = OnboardingSingleSelectFragment.newInstance(
            title = "¿Cuánto tiempo tienes para cada sesión?",
            labels = arrayOf("30 minutos", "45 minutos", "60 minutos", "75+ minutos"),
            keys = arrayOf("30", "45", "60", "75")
        )

        trainingLocationFragment = OnboardingSingleSelectFragment.newInstance(
            title = "¿Dónde sueles entrenar?",
            labels = arrayOf(
                "En casa, sin equipamiento",
                "En casa, con material básico",
                "En un gimnasio completo",
                "Al aire libre"
            ),
            keys = arrayOf("home_none", "home_basic", "gym", "outdoor")
        )

        trainingEquipmentFragment = OnboardingMultiSelectFragment.newInstance(
            title = "¿Qué equipamiento tienes disponible?",
            labels = arrayOf(
                "Mancuernas", "Barra", "Banco", "Kettlebell",
                "Bandas elásticas", "Fitball", "Barra de dominadas", "Esterilla"
            ),
            keys = arrayOf(
                "dumbbell", "barbell", "bench", "kettlebell",
                "bands", "swiss_ball", "pull_up_bar", "mat"
            ),
            subtitle = "Marca todo lo que tengas a mano para que la rutina aproveche tu material."
        )

        trainingLevelFragment = OnboardingSingleSelectFragment.newInstance(
            title = "¿Cuál es tu nivel de experiencia?",
            labels = arrayOf("Principiante", "Intermedio", "Avanzado"),
            keys = arrayOf("beginner", "intermediate", "advanced")
        )

        trainingSplitFragment = OnboardingSingleSelectFragment.newInstance(
            title = "¿Cómo prefieres organizar la semana?",
            labels = arrayOf(
                "Cuerpo completo cada día",
                "Tren superior / Tren inferior",
                "Empuje / Tirón / Pierna",
                "Un grupo muscular por día",
                "Decide por mí"
            ),
            keys = arrayOf("full_body", "upper_lower", "push_pull_legs", "bro_split", "auto"),
            defaultSelectedKey = "auto"
        )

        trainingLimitationsFragment = OnboardingMultiSelectFragment.newInstance(
            title = "¿Tienes alguna limitación o lesión?",
            labels = arrayOf(
                "Ninguna / sin molestias activas",
                "Espalda", "Rodilla", "Hombro", "Muñeca", "Cuello", "Tobillo"
            ),
            keys = arrayOf("none", "back", "knee", "shoulder", "wrist", "neck", "ankle"),
            subtitle = "Marca «Ninguna» si no tienes molestias. Si tienes una lesión activa, selecciona la zona afectada.",
            exclusiveKey = "none"
        )

        sedentaryNotificationsFragment = OnboardingSingleSelectFragment.newInstance(
            title = "¿Quieres recibir notificaciones de sedentarismo?",
            labels = arrayOf("Sí, avísame si llevo mucho rato sin moverme", "No, gracias"),
            keys = arrayOf("true", "false")
        )

        sedentarySourceFragment = OnboardingSingleSelectFragment.newInstance(
            title = "¿De dónde quieres obtener los pasos?",
            labels = arrayOf(
                "Apps y dispositivos (Health Connect)",
                "Solo este móvil (sensor)"
            ),
            keys = arrayOf(
                SedentaryDataSource.HEALTH_CONNECT,
                SedentaryDataSource.DEVICE_SENSOR
            ),
            subtitle = "Health Connect es útil si usas reloj u otras apps, pero debes configurarlo. El sensor solo cuenta pasos llevando el teléfono, sin apps externas."
        )

        allFragments = listOf(
            basicDataFragment,
            activityFragment,
            objectiveFragment,
            weightGoalFragment,
            dietFragment,
            healthProfileFragment,
            foodPrefsFragment,
            mealsFragment,
            proteinSupplementsFragment,
            trainingDaysFragment,
            trainingMinutesFragment,
            trainingLocationFragment,
            trainingEquipmentFragment,
            trainingLevelFragment,
            trainingSplitFragment,
            trainingLimitationsFragment,
            sedentaryNotificationsFragment,
            sedentarySourceFragment
        )

        sedentaryNotificationsFragment.onSelectionChanged = {
            snapshotCurrentStep(IDX_SEDENTARY_NOTIFICATIONS)
            if (::viewPager.isInitialized) {
                updateUI(viewPager.currentItem)
            }
        }
    }

    private fun configureWeightGoalOptions() {
        if (ObjectiveMapper.isMuscleGain(objectiveKey().orEmpty())) {
            weightGoalFragment.replaceOptions(
                labels = arrayOf("0,25 kg", "0,5 kg"),
                keys = arrayOf("0.25", "0.5"),
                defaultKey = "0.25"
            )
        } else {
            weightGoalFragment.replaceOptions(
                labels = arrayOf("0,25 kg", "0,5 kg", "0,75 kg", "1 kg"),
                keys = arrayOf("0.25", "0.5", "0.75", "1.0"),
                defaultKey = "0.5"
            )
        }
    }

    private fun updateUI(position: Int) {
        btnBack.visibility = if (position > 0) View.VISIBLE else View.GONE
        btnNext.text = if (isLastOnboardingStep(position)) "Finalizar" else "Siguiente"

        var effectiveTotal = allFragments.size
        if (!needsWeightGoal()) effectiveTotal -= 1
        if (!needsEquipmentQuestion()) effectiveTotal -= 1
        if (!wantsSedentaryNotifications()) effectiveTotal -= 1

        var effectivePos = position
        if (!needsWeightGoal() && position > IDX_OBJECTIVE) effectivePos -= 1
        if (!needsEquipmentQuestion() && position > IDX_TRAINING_LOCATION) effectivePos -= 1
        if (!wantsSedentaryNotifications() && position > IDX_SEDENTARY_NOTIFICATIONS) {
            effectivePos -= 1
        }

        progress.progress = (effectivePos * 100) / effectiveTotal.coerceAtLeast(1)
    }

    private fun isLastOnboardingStep(position: Int): Boolean {
        if (position == allFragments.lastIndex) return true
        if (position == IDX_SEDENTARY_NOTIFICATIONS && !wantsSedentaryNotifications()) return true
        return false
    }

    private fun onNextClicked() {
        if (awaitingSedentaryPermissions) return
        val current = viewPager.currentItem

        if (current == 0 && !basicDataFragment.isValid()) {
            Toast.makeText(this, "Completa todos los campos", Toast.LENGTH_SHORT).show()
            return
        }

        if (!validateCurrentStep(current)) return

        if (current == IDX_OBJECTIVE && !needsWeightGoal()) {
            viewPager.currentItem = IDX_WEIGHT_GOAL + 1
            return
        }

        if (current == IDX_TRAINING_LOCATION && !needsEquipmentQuestion()) {
            viewPager.currentItem = IDX_TRAINING_EQUIPMENT + 1
            return
        }

        if (current == IDX_SEDENTARY_NOTIFICATIONS && !wantsSedentaryNotifications()) {
            finishOnboarding()
            return
        }

        if (current == IDX_SEDENTARY_SOURCE && sedentarySourceFragment.getSelectedKey() == null) {
            Toast.makeText(this, "Selecciona de dónde obtener los pasos", Toast.LENGTH_SHORT).show()
            return
        }

        if (current < allFragments.lastIndex) {
            viewPager.currentItem = current + 1
        } else {
            finishOnboarding()
        }
    }

    private fun onBackClicked() {
        if (awaitingSedentaryPermissions) return
        val current = viewPager.currentItem
        snapshotCurrentStep(current)
        if (current == IDX_DIET && !needsWeightGoal()) {
            viewPager.currentItem = IDX_OBJECTIVE
            return
        }
        if (current == IDX_TRAINING_EQUIPMENT + 1 && !needsEquipmentQuestion()) {
            viewPager.currentItem = IDX_TRAINING_LOCATION
            return
        }
        if (current == IDX_SEDENTARY_SOURCE && !wantsSedentaryNotifications()) {
            viewPager.currentItem = IDX_SEDENTARY_NOTIFICATIONS
            return
        }
        if (current > 0) viewPager.currentItem = current - 1
    }

    private fun finishOnboarding() {
        if (awaitingSedentaryPermissions) return
        snapshotCurrentStep(viewPager.currentItem)

        val age = basicDataFragment.getAge() ?: 0
        val sex = basicDataFragment.getSex() ?: ""
        val heightCm = basicDataFragment.getHeightCm() ?: 0f
        val weightKg = basicDataFragment.getWeightKg() ?: 0f
        val activityLevel = answer("activity") ?: activityFragment.getSelectedKey() ?: "sedentary"
        val multiplier = CalorieCalculator.activityMultiplier(activityLevel)
        val bodyObjective = objectiveKey() ?: "maintain"
        val objective = ObjectiveMapper.nutritionObjective(bodyObjective)
        val weeklyGoal = if (needsWeightGoal()) {
            (answer("weightGoal") ?: weightGoalFragment.getSelectedKey())?.toFloatOrNull()?.let { goal ->
                if (ObjectiveMapper.isMuscleGain(bodyObjective)) goal.coerceAtMost(0.5f) else goal
            } ?: 0f
        } else {
            0f
        }
        val dietType = answer("diet") ?: dietFragment.getSelectedKey() ?: "none"

        val trainingDays = (answer("trainingDays") ?: trainingDaysFragment.getSelectedKey())
            ?.toIntOrNull() ?: 3
        val trainingMinutes = (answer("trainingMinutes") ?: trainingMinutesFragment.getSelectedKey())
            ?.toIntOrNull() ?: 45
        val trainingLocation = trainingLocationKey() ?: ""
        val trainingEquipment = when {
            trainingLocation == "gym" -> EquipmentKeyMapper.SELECTABLE_EQUIPMENT_KEYS
            needsEquipmentQuestion() -> trainingEquipmentFragment.getSelectedKeys()
            else -> emptyList()
        }
        val trainingLevel = answer("trainingLevel") ?: trainingLevelFragment.getSelectedKey() ?: ""
        val trainingSplit = answer("trainingSplit") ?: trainingSplitFragment.getSelectedKey() ?: "auto"
        val trainingObjective = ObjectiveMapper.trainingObjective(bodyObjective)
        val trainingLimitations = trainingLimitationsFragment.getSelectedKeys()
        val sedentaryEnabled = (answer("sedentaryNotifications")
            ?: sedentaryNotificationsFragment.getSelectedKey())
            ?.toBooleanStrictOrNull() ?: false
        val sedentarySource = if (sedentaryEnabled) {
            answer("sedentarySource") ?: sedentarySourceFragment.getSelectedKey()
                ?: SedentaryDataSource.DEVICE_SENSOR
        } else {
            ""
        }
        val nutritionTargets = calculateNutritionTargets(
            sex = sex,
            weightKg = weightKg,
            heightCm = heightCm,
            age = age,
            activityMultiplier = multiplier,
            objective = objective,
            weeklyWeightGoalKg = weeklyGoal,
            dietType = dietType,
            trainingDaysPerWeek = trainingDays
        )
        val tmb = nutritionTargets.tmb
        val tdee = nutritionTargets.tdee
        val targetCal = nutritionTargets.targetCalories
        val targetProtein = nutritionTargets.targetProteinGrams
        val acceptsProteinSupplements = (answer("proteinSupplements")
            ?: proteinSupplementsFragment.getSelectedKey())
            ?.toBooleanStrictOrNull() ?: false

        val prefs = UserPreferences(
            age = age,
            sex = sex,
            heightCm = heightCm,
            weightKg = weightKg,
            activityLevel = activityLevel,
            activityMultiplier = multiplier,
            objective = objective,
            weeklyWeightGoalKg = weeklyGoal,
            dietType = dietType,
            restrictions = healthProfileFragment.getSelectedRestrictionKeys(),
            healthConditions = healthProfileFragment.getSelectedConditionKeys(),
            healthLabels = emptyList(),
            cuisinePreferences = foodPrefsFragment.getSelectedCuisineKeys(),
            avoidedFoods = foodPrefsFragment.getSelectedAvoidKeys(),
            mealsPerDay = (answer("meals") ?: mealsFragment.getSelectedKey())?.toIntOrNull() ?: 3,
            tmb = tmb,
            tdee = tdee,
            targetCalories = targetCal,
            targetProteinGrams = targetProtein,
            acceptsProteinSupplements = acceptsProteinSupplements,
            completedAtMillis = System.currentTimeMillis(),
            trainingDaysPerWeek = trainingDays,
            trainingSessionMinutes = trainingMinutes,
            trainingLocation = trainingLocation,
            availableEquipment = trainingEquipment,
            trainingLevel = trainingLevel,
            trainingObjective = trainingObjective,
            trainingSplit = trainingSplit,
            trainingLimitations = trainingLimitations,
            trainingCompletedAtMillis = if (trainingDays > 0) System.currentTimeMillis() else 0,
            sedentaryNotificationsEnabled = sedentaryEnabled,
            sedentaryDataSource = sedentarySource
        )

        if (sedentaryEnabled) {
            requestSedentaryPermissionsThenSave(prefs)
        } else {
            saveToFirestore(prefs)
        }
    }

    private fun requestSedentaryPermissionsThenSave(prefs: UserPreferences) {
        awaitingSedentaryPermissions = true
        setOnboardingNavEnabled(false)
        sedentaryPermissionsHelper.requestForDataSource(prefs.sedentaryDataSource) { outcome ->
            handleSedentaryPermissionOutcome(prefs, outcome)
        }
    }

    private fun handleSedentaryPermissionOutcome(
        prefs: UserPreferences,
        outcome: SedentaryPermissionOutcome
    ) {
        if (outcome is SedentaryPermissionOutcome.Granted) {
            saveToFirestore(prefs.copy(sedentaryNotificationsEnabled = true))
            return
        }
        awaitingSedentaryPermissions = false
        setOnboardingNavEnabled(true)
        SedentaryPermissionUi.showOutcome(
            activity = this,
            outcome = outcome,
            onRetry = {
                requestSedentaryPermissionsThenSave(prefs)
            },
            onClose = {
                awaitingSedentaryPermissions = true
                setOnboardingNavEnabled(false)
                saveToFirestore(
                    prefs.copy(
                        sedentaryNotificationsEnabled = false,
                        sedentaryDataSource = ""
                    )
                )
            },
            closeButtonLabelRes = R.string.sedentary_permission_dialog_continue_without
        )
    }

    private fun setOnboardingNavEnabled(enabled: Boolean) {
        btnNext.isEnabled = enabled
        btnBack.isEnabled = enabled
    }

    private fun saveToFirestore(prefs: UserPreferences) {
        val uid = Firebase.auth.currentUser?.uid
        if (uid == null) {
            goToResult()
            return
        }

        val data = hashMapOf(
            "preferences" to hashMapOf(
                "age" to prefs.age,
                "sex" to prefs.sex,
                "heightCm" to prefs.heightCm,
                "weightKg" to prefs.weightKg,
                "activityLevel" to prefs.activityLevel,
                "activityMultiplier" to prefs.activityMultiplier,
                "objective" to prefs.objective,
                "weeklyWeightGoalKg" to prefs.weeklyWeightGoalKg,
                "dietType" to prefs.dietType,
                "restrictions" to prefs.restrictions,
                "healthConditions" to prefs.healthConditions,
                "healthLabels" to prefs.healthLabels,
                "cuisinePreferences" to prefs.cuisinePreferences,
                "avoidedFoods" to prefs.avoidedFoods,
                "mealsPerDay" to prefs.mealsPerDay,
                "tmb" to prefs.tmb,
                "tdee" to prefs.tdee,
                "targetCalories" to prefs.targetCalories,
                "targetProteinGrams" to prefs.targetProteinGrams,
                "acceptsProteinSupplements" to prefs.acceptsProteinSupplements,
                "completedAtMillis" to prefs.completedAtMillis,
                "trainingDaysPerWeek" to prefs.trainingDaysPerWeek,
                "trainingSessionMinutes" to prefs.trainingSessionMinutes,
                "trainingLocation" to prefs.trainingLocation,
                "availableEquipment" to prefs.availableEquipment,
                "trainingLevel" to prefs.trainingLevel,
                "trainingObjective" to prefs.trainingObjective,
                "trainingSplit" to prefs.trainingSplit,
                "trainingLimitations" to prefs.trainingLimitations,
                "trainingCompletedAtMillis" to prefs.trainingCompletedAtMillis,
                "sedentaryNotificationsEnabled" to prefs.sedentaryNotificationsEnabled,
                "sedentaryDataSource" to prefs.sedentaryDataSource
            )
        )

        Firebase.firestore
            .collection("users")
            .document(uid)
            .set(data, SetOptions.merge())
            .addOnCompleteListener {
                SedentaryNotificationScheduler.applyPreference(
                    applicationContext,
                    prefs.sedentaryNotificationsEnabled,
                    prefs.sedentaryDataSource
                )
                goToResult()
            }
    }

    private fun goToResult() {
        startActivity(Intent(this, OnboardingResultActivity::class.java))
        finish()
    }
}
