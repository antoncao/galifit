package com.tfm.galifit

import android.app.AlertDialog
import android.os.Build
import android.os.Bundle
import android.view.View
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import android.view.LayoutInflater
import android.widget.EditText
import android.widget.ImageButton
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import com.google.firebase.auth.auth
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.firestore
import com.google.firebase.Firebase
import com.tfm.galifit.data.EquipmentKeyMapper
import com.tfm.galifit.data.model.EdamamHealthCatalog
import com.tfm.galifit.data.model.UserPreferences
import android.Manifest
import com.tfm.galifit.notifications.SedentaryDataSource
import com.tfm.galifit.notifications.SedentaryNotificationScheduler
import com.tfm.galifit.notifications.SedentaryPermissionOutcome
import com.tfm.galifit.notifications.SedentaryPermissionUi
import com.tfm.galifit.notifications.SedentaryPermissionsHelper
import com.tfm.galifit.domain.usecase.CalculateNutritionTargetsUseCase
import com.tfm.galifit.util.CalorieCalculator
import com.tfm.galifit.util.ObjectiveMapper

class PreferencesActivity : GetNavigationBarActivity() {

    private val calculateNutritionTargets = CalculateNutritionTargetsUseCase()

    private var currentPrefs = UserPreferences()

    private lateinit var tvBasicData: TextView
    private lateinit var tvActivity: TextView
    private lateinit var tvObjective: TextView
    private lateinit var tvDiet: TextView
    private lateinit var tvCuisines: TextView
    private lateinit var tvAvoidedFoods: TextView
    private lateinit var tvHealth: TextView
    private lateinit var tvMeals: TextView
    private lateinit var tvProteinSupplements: TextView

    private lateinit var tvTrainingDays: TextView
    private lateinit var tvTrainingMinutes: TextView
    private lateinit var tvTrainingLocation: TextView
    private lateinit var tvTrainingEquipment: TextView
    private lateinit var tvTrainingLevel: TextView
    private lateinit var tvTrainingSplit: TextView
    private lateinit var tvTrainingObjective: TextView
    private lateinit var tvTrainingLimitations: TextView
    private lateinit var tvSedentaryNotifications: TextView
    private lateinit var tvSedentarySource: TextView
    private lateinit var sedentaryPermissionsHelper: SedentaryPermissionsHelper

    override fun onCreate(savedInstanceState: Bundle?) {
        sedentaryPermissionsHelper = SedentaryPermissionsHelper(this)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_preferences)

        tvBasicData = findViewById(R.id.tvBasicDataValue)
        tvActivity = findViewById(R.id.tvActivityValue)
        tvObjective = findViewById(R.id.tvObjectiveValue)
        tvDiet = findViewById(R.id.tvDietValue)
        tvCuisines = findViewById(R.id.tvCuisinesValue)
        tvAvoidedFoods = findViewById(R.id.tvAvoidedFoodsValue)
        tvHealth = findViewById(R.id.tvHealthValue)
        tvMeals = findViewById(R.id.tvMealsValue)
        tvProteinSupplements = findViewById(R.id.tvProteinSupplementsValue)

        tvTrainingDays = findViewById(R.id.tvTrainingDaysValue)
        tvTrainingMinutes = findViewById(R.id.tvTrainingMinutesValue)
        tvTrainingLocation = findViewById(R.id.tvTrainingLocationValue)
        tvTrainingEquipment = findViewById(R.id.tvTrainingEquipmentValue)
        tvTrainingLevel = findViewById(R.id.tvTrainingLevelValue)
        tvTrainingSplit = findViewById(R.id.tvTrainingSplitValue)
        tvTrainingObjective = findViewById(R.id.tvTrainingObjectiveValue)
        tvTrainingLimitations = findViewById(R.id.tvTrainingLimitationsValue)
        tvSedentaryNotifications = findViewById(R.id.tvSedentaryNotificationsValue)
        tvSedentarySource = findViewById(R.id.tvSedentarySourceValue)

        findViewById<ImageButton>(R.id.btnEditBasicData).setOnClickListener { showBasicDataDialog() }
        findViewById<ImageButton>(R.id.btnEditActivity).setOnClickListener { showSingleSelectDialog(
            "Nivel de actividad física",
            ACTIVITY_LABELS, ACTIVITY_KEYS, currentPrefs.activityLevel
        ) { key -> currentPrefs = currentPrefs.copy(activityLevel = key, activityMultiplier = CalorieCalculator.activityMultiplier(key)); refreshUI() } }
        findViewById<ImageButton>(R.id.btnEditObjective).setOnClickListener { showSingleSelectDialog(
            "Objetivo principal",
            OBJECTIVE_LABELS, OBJECTIVE_KEYS, currentPrefs.objective
        ) { key ->
            currentPrefs = currentPrefs.copy(
                objective = key,
                trainingObjective = ObjectiveMapper.trainingObjective(key)
            )
            refreshUI()
        } }
        findViewById<ImageButton>(R.id.btnEditDiet).setOnClickListener { showSingleSelectDialog(
            "Tipo de dieta",
            DIET_LABELS, DIET_KEYS, currentPrefs.dietType
        ) { key -> currentPrefs = currentPrefs.copy(dietType = key); refreshUI() } }
        findViewById<ImageButton>(R.id.btnEditCuisines).setOnClickListener { showMultiSelectDialog(
            "Cocinas preferidas",
            CUISINE_LABELS, CUISINE_KEYS, currentPrefs.cuisinePreferences
        ) { keys -> currentPrefs = currentPrefs.copy(cuisinePreferences = keys); refreshUI() } }
        findViewById<ImageButton>(R.id.btnEditAvoidedFoods).setOnClickListener { showMultiSelectDialog(
            "Alimentos que evitas",
            AVOID_LABELS, AVOID_KEYS, currentPrefs.avoidedFoods
        ) { keys -> currentPrefs = currentPrefs.copy(avoidedFoods = keys); refreshUI() } }
        findViewById<ImageButton>(R.id.btnEditHealth).setOnClickListener { showHealthDialog() }
        findViewById<ImageButton>(R.id.btnEditMeals).setOnClickListener { showSingleSelectDialog(
            "Comidas al día",
            MEALS_LABELS, MEALS_KEYS, currentPrefs.mealsPerDay.toString()
        ) { key -> currentPrefs = currentPrefs.copy(mealsPerDay = key.toIntOrNull() ?: 3); refreshUI() } }
        findViewById<ImageButton>(R.id.btnEditProteinSupplements).setOnClickListener {
            showSingleSelectDialog(
                "Suplementos de proteína",
                PROTEIN_SUPPLEMENT_LABELS,
                PROTEIN_SUPPLEMENT_KEYS,
                currentPrefs.acceptsProteinSupplements.toString()
            ) { key ->
                currentPrefs = currentPrefs.copy(
                    acceptsProteinSupplements = key.toBooleanStrictOrNull() ?: false
                )
                refreshUI()
            }
        }

        findViewById<ImageButton>(R.id.btnEditTrainingDays).setOnClickListener { showSingleSelectDialog(
            "Días por semana",
            TRAINING_DAYS_LABELS, TRAINING_DAYS_KEYS,
            currentPrefs.trainingDaysPerWeek.takeIf { it > 0 }?.toString() ?: ""
        ) { key ->
            val now = System.currentTimeMillis()
            currentPrefs = currentPrefs.copy(
                trainingDaysPerWeek = key.toIntOrNull() ?: 0,
                trainingCompletedAtMillis = now
            )
            refreshUI()
        } }
        findViewById<ImageButton>(R.id.btnEditTrainingMinutes).setOnClickListener { showSingleSelectDialog(
            "Duración por sesión",
            TRAINING_MINUTES_LABELS, TRAINING_MINUTES_KEYS,
            currentPrefs.trainingSessionMinutes.toString()
        ) { key -> currentPrefs = currentPrefs.copy(trainingSessionMinutes = key.toIntOrNull() ?: 45); refreshUI() } }
        findViewById<ImageButton>(R.id.btnEditTrainingLocation).setOnClickListener { showSingleSelectDialog(
            "Lugar de entrenamiento",
            TRAINING_LOCATION_LABELS, TRAINING_LOCATION_KEYS, currentPrefs.trainingLocation
        ) { key ->
            val equipment = when (key) {
                "home_none", "outdoor" -> emptyList()
                "gym" -> EquipmentKeyMapper.SELECTABLE_EQUIPMENT_KEYS
                else -> currentPrefs.availableEquipment
            }
            currentPrefs = currentPrefs.copy(trainingLocation = key, availableEquipment = equipment)
            refreshUI()
        } }
        findViewById<ImageButton>(R.id.btnEditTrainingEquipment).setOnClickListener {
            if (currentPrefs.trainingLocation == "home_none" ||
                currentPrefs.trainingLocation == "outdoor" ||
                currentPrefs.trainingLocation == "gym"
            ) {
                Toast.makeText(this, "Cambia primero el lugar de entrenamiento", Toast.LENGTH_SHORT).show()
            } else {
                showMultiSelectDialog(
                    "Equipamiento disponible",
                    TRAINING_EQUIPMENT_LABELS, TRAINING_EQUIPMENT_KEYS, currentPrefs.availableEquipment
                ) { keys -> currentPrefs = currentPrefs.copy(availableEquipment = keys); refreshUI() }
            }
        }
        findViewById<ImageButton>(R.id.btnEditTrainingLevel).setOnClickListener { showSingleSelectDialog(
            "Nivel de experiencia",
            TRAINING_LEVEL_LABELS, TRAINING_LEVEL_KEYS, currentPrefs.trainingLevel
        ) { key -> currentPrefs = currentPrefs.copy(trainingLevel = key); refreshUI() } }
        findViewById<ImageButton>(R.id.btnEditTrainingSplit).setOnClickListener { showSingleSelectDialog(
            "Tipo de rutina",
            TRAINING_SPLIT_LABELS, TRAINING_SPLIT_KEYS, currentPrefs.trainingSplit
        ) { key -> currentPrefs = currentPrefs.copy(trainingSplit = key); refreshUI() } }
        findViewById<ImageButton>(R.id.btnEditTrainingObjective).setOnClickListener {
            Toast.makeText(
                this,
                "Se ajusta automáticamente según tu objetivo principal",
                Toast.LENGTH_SHORT
            ).show()
        }
        findViewById<ImageButton>(R.id.btnEditTrainingLimitations).setOnClickListener { showMultiSelectDialog(
            "Limitaciones / lesiones",
            TRAINING_LIMITATIONS_LABELS, TRAINING_LIMITATIONS_KEYS, currentPrefs.trainingLimitations
        ) { keys -> currentPrefs = currentPrefs.copy(trainingLimitations = keys); refreshUI() } }

        findViewById<ImageButton>(R.id.btnEditSedentaryNotifications).setOnClickListener {
            showSingleSelectDialog(
                "Notificaciones de sedentarismo",
                SEDENTARY_LABELS,
                SEDENTARY_KEYS,
                currentPrefs.sedentaryNotificationsEnabled.toString()
            ) { key ->
                val enabled = key.toBooleanStrictOrNull() ?: false
                if (!enabled) {
                    currentPrefs = currentPrefs.copy(
                        sedentaryNotificationsEnabled = false,
                        sedentaryDataSource = ""
                    )
                    SedentaryNotificationScheduler.applyPreference(
                        applicationContext,
                        enabled = false,
                        dataSource = ""
                    )
                    refreshUI()
                } else {
                    showSedentarySourceDialog()
                }
            }
        }

        findViewById<ImageButton>(R.id.btnEditSedentarySource).setOnClickListener {
            if (!currentPrefs.sedentaryNotificationsEnabled) {
                Toast.makeText(
                    this,
                    "Activa primero las notificaciones de sedentarismo",
                    Toast.LENGTH_SHORT
                ).show()
                return@setOnClickListener
            }
            showSedentarySourceDialog(requestPermissionsAfter = true)
        }

        findViewById<android.widget.Button>(R.id.btnTestSedentaryNotification).setOnClickListener {
            testSedentaryNotificationNow()
        }

        findViewById<android.widget.Button>(R.id.btnSavePreferences).setOnClickListener { savePreferences() }

        loadFromFirestore()
    }

    private fun loadFromFirestore() {
        val uid = Firebase.auth.currentUser?.uid ?: return
        Firebase.firestore.collection("users").document(uid).get()
            .addOnSuccessListener { doc ->
                val p = doc.get("preferences") as? Map<*, *> ?: return@addOnSuccessListener
                currentPrefs = EdamamHealthCatalog.normalizeLegacyPaleoPreference(UserPreferences(
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
                    acceptsProteinSupplements = (p["acceptsProteinSupplements"] as? Boolean) ?: false,
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
                    sedentaryNotificationsEnabled = (p["sedentaryNotificationsEnabled"] as? Boolean) ?: false,
                    sedentaryDataSource = SedentaryDataSource.defaultForLegacy(
                        enabled = (p["sedentaryNotificationsEnabled"] as? Boolean) ?: false,
                        stored = p["sedentaryDataSource"] as? String
                    )
                ))
                refreshUI()
                SedentaryNotificationScheduler.applyPreference(
                    applicationContext,
                    currentPrefs.sedentaryNotificationsEnabled,
                    currentPrefs.sedentaryDataSource
                )
            }
    }

    private fun refreshUI() {
        val p = currentPrefs
        tvBasicData.text = if (p.age > 0)
            "${p.age} años, ${p.sex}, ${p.heightCm.toInt()}cm, ${p.weightKg.toInt()}kg"
        else "Sin definir"

        tvActivity.text = labelForKey(ACTIVITY_KEYS, ACTIVITY_LABELS, p.activityLevel, "Sin definir")
        tvObjective.text = labelForKey(
            OBJECTIVE_KEYS,
            OBJECTIVE_LABELS,
            ObjectiveMapper.nutritionObjective(p.objective),
            "Sin definir"
        )
        tvDiet.text = labelForKey(DIET_KEYS, DIET_LABELS, p.dietType, "Sin definir")
        tvCuisines.text = if (p.cuisinePreferences.isNotEmpty())
            p.cuisinePreferences.joinToString(", ") { labelForKey(CUISINE_KEYS, CUISINE_LABELS, it, it) }
        else "Todas"
        tvAvoidedFoods.text = p.avoidedFoods
            .filter { it != "ALCOHOL_FREE" }
            .let { foods ->
                if (foods.isNotEmpty())
                    foods.joinToString(", ") { labelForKey(AVOID_KEYS, AVOID_LABELS, it, it) }
                else "Ninguno"
            }
        tvHealth.text = formatHealthSummary(p)
        tvMeals.text = "${p.mealsPerDay} comidas"
        tvProteinSupplements.text = if (p.acceptsProteinSupplements) {
            "Sí, solo si falta proteína"
        } else {
            "No"
        }

        tvTrainingDays.text = if (p.trainingDaysPerWeek > 0) "${p.trainingDaysPerWeek} días/semana" else "Sin definir"
        tvTrainingMinutes.text = "${p.trainingSessionMinutes} min/sesión"
        tvTrainingLocation.text = labelForKey(TRAINING_LOCATION_KEYS, TRAINING_LOCATION_LABELS, p.trainingLocation, "Sin definir")
        tvTrainingEquipment.text = if (p.availableEquipment.isNotEmpty())
            p.availableEquipment.map { labelForKey(TRAINING_EQUIPMENT_KEYS, TRAINING_EQUIPMENT_LABELS, it, it) }.joinToString(", ")
        else "Ninguno"
        tvTrainingLevel.text = labelForKey(TRAINING_LEVEL_KEYS, TRAINING_LEVEL_LABELS, p.trainingLevel, "Sin definir")
        tvTrainingSplit.text = labelForKey(TRAINING_SPLIT_KEYS, TRAINING_SPLIT_LABELS, p.trainingSplit, "Sin definir")
        val derivedTrainingObjective = ObjectiveMapper.trainingObjective(p.objective)
        tvTrainingObjective.text = labelForKey(
            TRAINING_OBJECTIVE_KEYS,
            TRAINING_OBJECTIVE_LABELS,
            derivedTrainingObjective,
            "Sin definir"
        )
        tvTrainingLimitations.text = if (p.trainingLimitations.isNotEmpty())
            p.trainingLimitations.map { labelForKey(TRAINING_LIMITATIONS_KEYS, TRAINING_LIMITATIONS_LABELS, it, it) }.joinToString(", ")
        else "Ninguna"
        tvSedentaryNotifications.text = if (p.sedentaryNotificationsEnabled) "Activadas" else "Desactivadas"
        findViewById<View>(R.id.btnTestSedentaryNotification).visibility = View.GONE
        tvSedentarySource.text = when {
            !p.sedentaryNotificationsEnabled -> "No aplica"
            p.sedentaryDataSource == SedentaryDataSource.HEALTH_CONNECT ->
                labelForKey(SEDENTARY_SOURCE_KEYS, SEDENTARY_SOURCE_LABELS, p.sedentaryDataSource, "Sin definir")
            p.sedentaryDataSource == SedentaryDataSource.DEVICE_SENSOR ->
                labelForKey(SEDENTARY_SOURCE_KEYS, SEDENTARY_SOURCE_LABELS, p.sedentaryDataSource, "Sin definir")
            else -> "Sin definir"
        }
    }

    private fun showSedentarySourceDialog(requestPermissionsAfter: Boolean = true) {
        showSingleSelectDialog(
            "Fuente de pasos para sedentarismo",
            SEDENTARY_SOURCE_LABELS,
            SEDENTARY_SOURCE_KEYS,
            currentPrefs.sedentaryDataSource.ifBlank { SedentaryDataSource.DEVICE_SENSOR }
        ) { source ->
            currentPrefs = currentPrefs.copy(
                sedentaryNotificationsEnabled = true,
                sedentaryDataSource = source
            )
            SedentaryNotificationScheduler.applyPreference(
                applicationContext,
                enabled = true,
                dataSource = source
            )
            refreshUI()
            if (requestPermissionsAfter) {
                requestSedentaryPermissions()
            }
        }
    }

    private fun showBasicDataDialog() {
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_basic_data, null)
        val etAge = view.findViewById<EditText>(R.id.etDialogAge)
        val etHeight = view.findViewById<EditText>(R.id.etDialogHeight)
        val etWeight = view.findViewById<EditText>(R.id.etDialogWeight)
        val rgSex = view.findViewById<RadioGroup>(R.id.rgDialogSex)

        etAge.setText(if (currentPrefs.age > 0) currentPrefs.age.toString() else "")
        etHeight.setText(if (currentPrefs.heightCm > 0) currentPrefs.heightCm.toInt().toString() else "")
        etWeight.setText(if (currentPrefs.weightKg > 0) currentPrefs.weightKg.toInt().toString() else "")
        if (currentPrefs.sex == "Hombre") rgSex.check(R.id.rbDialogMale)
        else if (currentPrefs.sex == "Mujer") rgSex.check(R.id.rbDialogFemale)

        AlertDialog.Builder(this)
            .setTitle("Datos básicos")
            .setView(view)
            .setPositiveButton("Aceptar") { _, _ ->
                val age = etAge.text.toString().toIntOrNull() ?: return@setPositiveButton
                val height = etHeight.text.toString().toFloatOrNull() ?: return@setPositiveButton
                val weight = etWeight.text.toString().toFloatOrNull() ?: return@setPositiveButton
                val sex = when (rgSex.checkedRadioButtonId) {
                    R.id.rbDialogMale -> "Hombre"
                    R.id.rbDialogFemale -> "Mujer"
                    else -> return@setPositiveButton
                }
                currentPrefs = currentPrefs.copy(age = age, sex = sex, heightCm = height, weightKg = weight)
                refreshUI()
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun showSingleSelectDialog(
        title: String,
        labels: Array<String>,
        keys: Array<String>,
        currentKey: String,
        onSelected: (String) -> Unit
    ) {
        val checkedIndex = keys.indexOf(currentKey).coerceAtLeast(0)
        var selected = checkedIndex
        AlertDialog.Builder(this)
            .setTitle(title)
            .setSingleChoiceItems(labels, checkedIndex) { _, which -> selected = which }
            .setPositiveButton("Aceptar") { _, _ -> onSelected(keys[selected]) }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun formatHealthSummary(p: UserPreferences): String {
        val parts = mutableListOf<String>()
        p.restrictions.forEach { label ->
            parts.add(EdamamHealthCatalog.labelDisplayName(label))
        }
        p.healthConditions.forEach { key ->
            EdamamHealthCatalog.conditions.find { it.key == key }?.let { parts.add(it.displayName) }
        }
        p.healthLabels.forEach { label ->
            parts.add(EdamamHealthCatalog.labelDisplayName(label))
        }
        return if (parts.isEmpty()) "Ninguna" else parts.distinct().joinToString(", ")
    }

    private fun showHealthDialog() {
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_health_prefs, null)
        val conditionsContainer = view.findViewById<android.widget.LinearLayout>(R.id.dialogConditionsContainer)
        val allergiesContainer = view.findViewById<android.widget.LinearLayout>(R.id.dialogAllergiesContainer)

        EdamamHealthCatalog.conditions.forEach { condition ->
            val cb = android.widget.CheckBox(this).apply {
                text = condition.displayLabel()
                tag = condition.key
                isChecked = condition.key in currentPrefs.healthConditions
                setPadding(8, 8, 8, 8)
            }
            conditionsContainer.addView(cb)
        }
        EdamamHealthCatalog.allergyRestrictionLabels.forEach { label ->
            val cb = android.widget.CheckBox(this).apply {
                text = EdamamHealthCatalog.labelDisplayName(label)
                tag = label
                isChecked = label in currentPrefs.restrictions
                setPadding(8, 8, 8, 8)
            }
            allergiesContainer.addView(cb)
        }

        AlertDialog.Builder(this)
            .setTitle("Salud y alergias alimentarias")
            .setView(view)
            .setPositiveButton("Aceptar") { _, _ ->
                val conditions = mutableListOf<String>()
                for (i in 0 until conditionsContainer.childCount) {
                    val cb = conditionsContainer.getChildAt(i) as? android.widget.CheckBox ?: continue
                    if (cb.isChecked) {
                        (cb.tag as? String)?.let { conditions.add(it) }
                    }
                }
                val restrictions = mutableListOf<String>()
                for (i in 0 until allergiesContainer.childCount) {
                    val cb = allergiesContainer.getChildAt(i) as? android.widget.CheckBox ?: continue
                    if (cb.isChecked) {
                        (cb.tag as? String)?.let { restrictions.add(it) }
                    }
                }
                currentPrefs = currentPrefs.copy(
                    healthConditions = conditions,
                    restrictions = restrictions
                )
                refreshUI()
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun showMultiSelectDialog(
        title: String,
        labels: Array<String>,
        keys: Array<String>,
        currentKeys: List<String>,
        onSelected: (List<String>) -> Unit
    ) {
        val checked = BooleanArray(keys.size) { keys[it] in currentKeys }
        AlertDialog.Builder(this)
            .setTitle(title)
            .setMultiChoiceItems(labels, checked) { _, which, isChecked -> checked[which] = isChecked }
            .setPositiveButton("Aceptar") { _, _ ->
                onSelected(keys.filterIndexed { i, _ -> checked[i] })
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun testSedentaryNotificationNow() {
        if (!currentPrefs.sedentaryNotificationsEnabled) {
            Toast.makeText(
                this,
                "Activa primero las notificaciones de sedentarismo",
                Toast.LENGTH_SHORT
            ).show()
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                SedentaryNotificationScheduler.requestPermissionsIfNeeded(this)
                Toast.makeText(
                    this,
                    "Concede el permiso de notificaciones y vuelve a probar",
                    Toast.LENGTH_SHORT
                ).show()
                return
            }
        }

        SedentaryNotificationScheduler.syncWorkerConfig(
            applicationContext,
            enabled = true,
            dataSource = currentPrefs.sedentaryDataSource
        )
        SedentaryNotificationScheduler.runTestNow(applicationContext)
        Toast.makeText(
            this,
            "Comprobación lanzada. Revisa la notificación en unos segundos.",
            Toast.LENGTH_SHORT
        ).show()
    }

    private fun requestSedentaryPermissions() {
        val source = currentPrefs.sedentaryDataSource
        if (!SedentaryDataSource.isValid(source)) return

        sedentaryPermissionsHelper.requestForDataSource(source) { outcome ->
            if (outcome !is SedentaryPermissionOutcome.Granted) {
                currentPrefs = currentPrefs.copy(sedentaryNotificationsEnabled = false)
                SedentaryNotificationScheduler.applyPreference(
                    applicationContext,
                    enabled = false,
                    dataSource = ""
                )
                refreshUI()
                SedentaryPermissionUi.showOutcome(this, outcome) {
                    currentPrefs = currentPrefs.copy(sedentaryNotificationsEnabled = true)
                    refreshUI()
                    requestSedentaryPermissions()
                }
            }
        }
    }

    private fun savePreferences() {
        val uid = Firebase.auth.currentUser?.uid
        if (uid == null) {
            Toast.makeText(this, "Inicia sesión para guardar", Toast.LENGTH_SHORT).show()
            return
        }

        val p = currentPrefs
        val targets = calculateNutritionTargets(p)
        currentPrefs = p.copy(
            tmb = targets.tmb,
            tdee = targets.tdee,
            targetCalories = targets.targetCalories,
            targetProteinGrams = targets.targetProteinGrams,
            trainingObjective = ObjectiveMapper.trainingObjective(p.objective),
            completedAtMillis = System.currentTimeMillis()
        )

        val data = hashMapOf(
            "preferences" to hashMapOf(
                "age" to currentPrefs.age,
                "sex" to currentPrefs.sex,
                "heightCm" to currentPrefs.heightCm,
                "weightKg" to currentPrefs.weightKg,
                "activityLevel" to currentPrefs.activityLevel,
                "activityMultiplier" to currentPrefs.activityMultiplier,
                "objective" to currentPrefs.objective,
                "weeklyWeightGoalKg" to currentPrefs.weeklyWeightGoalKg,
                "dietType" to currentPrefs.dietType,
                "restrictions" to currentPrefs.restrictions,
                "healthConditions" to currentPrefs.healthConditions,
                "healthLabels" to currentPrefs.healthLabels,
                "cuisinePreferences" to currentPrefs.cuisinePreferences,
                "avoidedFoods" to currentPrefs.avoidedFoods,
                "mealsPerDay" to currentPrefs.mealsPerDay,
                "tmb" to currentPrefs.tmb,
                "tdee" to currentPrefs.tdee,
                "targetCalories" to currentPrefs.targetCalories,
                "targetProteinGrams" to currentPrefs.targetProteinGrams,
                "acceptsProteinSupplements" to currentPrefs.acceptsProteinSupplements,
                "completedAtMillis" to currentPrefs.completedAtMillis,
                "trainingDaysPerWeek" to currentPrefs.trainingDaysPerWeek,
                "trainingSessionMinutes" to currentPrefs.trainingSessionMinutes,
                "trainingLocation" to currentPrefs.trainingLocation,
                "availableEquipment" to currentPrefs.availableEquipment,
                "trainingLevel" to currentPrefs.trainingLevel,
                "trainingObjective" to currentPrefs.trainingObjective,
                "trainingSplit" to currentPrefs.trainingSplit,
                "trainingLimitations" to currentPrefs.trainingLimitations,
                "trainingCompletedAtMillis" to currentPrefs.trainingCompletedAtMillis,
                "sedentaryNotificationsEnabled" to currentPrefs.sedentaryNotificationsEnabled,
                "sedentaryDataSource" to currentPrefs.sedentaryDataSource
            )
        )

        Firebase.firestore.collection("users").document(uid)
            .set(data, SetOptions.merge())
            .addOnSuccessListener {
                SedentaryNotificationScheduler.applyPreference(
                    applicationContext,
                    currentPrefs.sedentaryNotificationsEnabled,
                    currentPrefs.sedentaryDataSource
                )
                Toast.makeText(this, "Preferencias guardadas", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener {
                Toast.makeText(this, "Error al guardar", Toast.LENGTH_SHORT).show()
            }
    }

    private fun labelForKey(keys: Array<String>, labels: Array<String>, key: String, fallback: String): String {
        val idx = keys.indexOf(key)
        return if (idx >= 0) labels[idx] else fallback
    }

    companion object {

        val ACTIVITY_LABELS = arrayOf(
            "Sedentario (casi sin actividad física en el día a día)",
            "Actividad ligera (1–2 días de ejercicio a la semana o caminar con frecuencia)",
            "Moderado (3–4 días de ejercicio a la semana)",
            "Alto (5–6 días de ejercicio a la semana)",
            "Muy alto (entrenamiento casi diario o trabajo físico intenso)"
        )
        val ACTIVITY_KEYS = arrayOf("sedentary", "light", "moderate", "high", "very_high")

        val OBJECTIVE_LABELS = ObjectiveMapper.BODY_OBJECTIVE_LABELS
        val OBJECTIVE_KEYS = ObjectiveMapper.BODY_OBJECTIVE_KEYS

        val DIET_LABELS = arrayOf(
            "Sin dieta específica", "Vegetariana", "Vegana", "Pescetariana",
            "Baja en carbohidratos", "Alta en proteínas", "Baja en grasas", "Mediterránea", "Keto",
            "Paleo"
        )
        val DIET_KEYS = arrayOf(
            "none", "vegetarian", "vegan", "pescatarian",
            "low_carb", "high_protein", "low_fat", "mediterranean", "keto", "paleo"
        )

        val CUISINE_LABELS = arrayOf(
            "Americana", "Asiática", "Británica", "Caribeña", "Centroeuropea",
            "China", "Este de Europa", "Francesa", "Griega", "India",
            "Italiana", "Japonesa", "Coreana", "Kosher", "Mediterránea", "Mexicana",
            "Oriente Medio", "Nórdica", "Sudamericana", "Sudeste Asiático", "Internacional"
        )

        val CUISINE_KEYS = arrayOf(
            "american", "asian", "british", "caribbean", "central europe",
            "chinese", "eastern europe", "french", "greek", "indian",
            "italian", "japanese", "korean", "kosher", "mediterranean", "mexican",
            "middle eastern", "nordic", "south american", "south east asian", "world"
        )

        val AVOID_LABELS = arrayOf(
            "Sin cerdo", "Sin carne roja", "Sin pescado",
            "Sin crustáceos", "Sin moluscos", "Sin mostaza",
            "Sin sésamo", "Sin altramuz", "Sin sulfitos"
        )

        val AVOID_KEYS = arrayOf(
            "PORK_FREE", "RED_MEAT_FREE", "FISH_FREE",
            "CRUSTACEAN_FREE", "MOLLUSK_FREE", "MUSTARD_FREE",
            "SESAME_FREE", "LUPINE_FREE", "SULFITE_FREE"
        )

        val RESTRICTION_LABELS = EdamamHealthCatalog.allergyRestrictionLabels
            .map { EdamamHealthCatalog.labelDisplayName(it) }
            .toTypedArray()
        val RESTRICTION_KEYS = EdamamHealthCatalog.allergyRestrictionLabels.toTypedArray()

        val MEALS_LABELS = arrayOf(
            "2 (comida, cena)", "3 (desayuno, comida, cena)",
            "4 (desayuno, comida, merienda, cena)", "5 (desayuno, media mañana, comida, merienda, cena)"
        )
        val MEALS_KEYS = arrayOf("2", "3", "4", "5")

        val PROTEIN_SUPPLEMENT_LABELS = arrayOf(
            "Sí, pero solo si es necesario. Prefiero obtener toda la proteína de los alimentos.",
            "No, no quiero utilizar suplementos."
        )
        val PROTEIN_SUPPLEMENT_KEYS = arrayOf("true", "false")

        val TRAINING_DAYS_LABELS = arrayOf("2 días", "3 días", "4 días", "5 días", "6 días")
        val TRAINING_DAYS_KEYS = arrayOf("2", "3", "4", "5", "6")

        val TRAINING_MINUTES_LABELS = arrayOf("30 minutos", "45 minutos", "60 minutos", "75+ minutos")
        val TRAINING_MINUTES_KEYS = arrayOf("30", "45", "60", "75")

        val TRAINING_LOCATION_LABELS = arrayOf(
            "En casa, sin equipamiento", "En casa con material básico",
            "Gimnasio completo", "Aire libre"
        )
        val TRAINING_LOCATION_KEYS = arrayOf("home_none", "home_basic", "gym", "outdoor")

        val TRAINING_EQUIPMENT_LABELS = arrayOf(
            "Mancuernas", "Barra", "Banco", "Kettlebell",
            "Bandas elásticas", "Fitball", "Barra de dominadas", "Esterilla"
        )
        val TRAINING_EQUIPMENT_KEYS = arrayOf(
            "dumbbell", "barbell", "bench", "kettlebell",
            "bands", "swiss_ball", "pull_up_bar", "mat"
        )

        val TRAINING_LEVEL_LABELS = arrayOf("Principiante", "Intermedio", "Avanzado")
        val TRAINING_LEVEL_KEYS = arrayOf("beginner", "intermediate", "advanced")

        val TRAINING_SPLIT_LABELS = arrayOf(
            "Cuerpo completo cada día",
            "Tren superior / Tren inferior",
            "Empuje / Tirón / Pierna",
            "Un grupo muscular por día",
            "Decide por mí"
        )
        val TRAINING_SPLIT_KEYS = arrayOf("full_body", "upper_lower", "push_pull_legs", "bro_split", "auto")

        val TRAINING_OBJECTIVE_LABELS = arrayOf(
            "Perder grasa",
            "Ganar masa muscular",
            "Movilidad / salud general"
        )
        val TRAINING_OBJECTIVE_KEYS = arrayOf("fat_loss", "hypertrophy", "mobility")

        val TRAINING_LIMITATIONS_LABELS = arrayOf(
            "Ninguna / sin molestias activas",
            "Espalda", "Rodilla", "Hombro", "Muñeca", "Cuello", "Tobillo"
        )
        val TRAINING_LIMITATIONS_KEYS = arrayOf("none", "back", "knee", "shoulder", "wrist", "neck", "ankle")

        val SEDENTARY_LABELS = arrayOf("Activadas", "Desactivadas")
        val SEDENTARY_KEYS = arrayOf("true", "false")

        val SEDENTARY_SOURCE_LABELS = arrayOf(
            "Apps y dispositivos (Health Connect)",
            "Solo este móvil (sensor)"
        )
        val SEDENTARY_SOURCE_KEYS = arrayOf(
            SedentaryDataSource.HEALTH_CONNECT,
            SedentaryDataSource.DEVICE_SENSOR
        )
    }
}
