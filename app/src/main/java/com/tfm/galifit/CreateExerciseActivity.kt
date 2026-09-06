package com.tfm.galifit

import android.app.Activity
import android.content.Intent
import android.graphics.Rect
import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.Spinner
import android.widget.TextView
import androidx.core.widget.NestedScrollView
import com.google.android.material.button.MaterialButton
import com.tfm.galifit.data.model.Exercise

class CreateExerciseActivity : GetNavigationBarActivity() {

    private lateinit var tvScreenTitle: TextView
    private lateinit var etExerciseName: EditText
    private lateinit var etSeries: EditText
    private lateinit var etRepsDuration: EditText
    private lateinit var spMeasurementType: Spinner
    private lateinit var etRestSeconds: EditText
    private lateinit var etNotes: EditText
    private lateinit var btnSaveExercise: MaterialButton
    private lateinit var scrollCreateExercise: NestedScrollView
    private lateinit var bottomNavigationView: View

    private val measurementKeys = arrayOf(Exercise.MEASUREMENT_REPS, Exercise.MEASUREMENT_DURATION)

    private var dayIndex: Int = -1
    private var exerciseIndex: Int = -1
    private var wgerExerciseId: Int = -1
    private var muscleGroup: String? = null
    private var equipmentLabel: String? = null
    private var thumbnailImageUrl: String? = null
    private var instructions: String? = null
    private var restSeconds: Int = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_create_exercise)

        tvScreenTitle = findViewById(R.id.tvScreenTitle)
        etExerciseName = findViewById(R.id.etExerciseName)
        etSeries = findViewById(R.id.etSeries)
        etRepsDuration = findViewById(R.id.etRepsDuration)
        spMeasurementType = findViewById(R.id.spMeasurementType)
        etRestSeconds = findViewById(R.id.etRestSeconds)
        etNotes = findViewById(R.id.etNotes)
        btnSaveExercise = findViewById(R.id.btnSaveExercise)
        scrollCreateExercise = findViewById(R.id.scrollCreateExercise)
        bottomNavigationView = findViewById(R.id.bottomNavigationView)

        spMeasurementType.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            arrayOf("Repeticiones", "Duración")
        )

        dayIndex = intent.getIntExtra(EXTRA_DAY_INDEX, -1)
        exerciseIndex = intent.getIntExtra(EXTRA_EXERCISE_INDEX, -1)
        wgerExerciseId = intent.getIntExtra(EXTRA_WGER_EXERCISE_ID, -1)
        muscleGroup = intent.getStringExtra(EXTRA_MUSCLE_GROUP)
        equipmentLabel = intent.getStringExtra(EXTRA_EQUIPMENT_LABEL)
        thumbnailImageUrl = intent.getStringExtra(EXTRA_THUMB_URL)
        instructions = intent.getStringExtra(EXTRA_INSTRUCTIONS)
        restSeconds = intent.getIntExtra(EXTRA_REST_SECONDS, 0)

        val initialName = intent.getStringExtra(EXTRA_NAME).orEmpty()
        val initialSets = intent.getIntExtra(EXTRA_SETS, 0)
        val initialRepsOrDuration = intent.getStringExtra(EXTRA_REPS_OR_DURATION).orEmpty()
        val initialNotes = intent.getStringExtra(EXTRA_USER_NOTES).orEmpty()
        val initialMeasurementType = intent.getStringExtra(EXTRA_MEASUREMENT_TYPE)
            ?: Exercise.MEASUREMENT_REPS

        etExerciseName.setText(initialName)
        if (initialSets > 0) etSeries.setText(initialSets.toString())
        etRepsDuration.setText(initialRepsOrDuration)
        if (restSeconds > 0) etRestSeconds.setText(restSeconds.toString())
        spMeasurementType.setSelection(
            measurementKeys.indexOf(initialMeasurementType).coerceAtLeast(0)
        )
        etNotes.setText(initialNotes)

        val isEditing = exerciseIndex >= 0
        val isFromWger = wgerExerciseId >= 0
        tvScreenTitle.text = if (isEditing) "Editar ejercicio" else "Añadir ejercicio"
        if (isFromWger) {
            etExerciseName.isEnabled = false
            etExerciseName.isFocusable = false
        }

        btnSaveExercise.setOnClickListener { saveExercise() }

        hideBottomNavigationWhileTyping()
        getNavigationView()
    }

    private fun hideBottomNavigationWhileTyping() {
        val root = scrollCreateExercise.rootView
        root.viewTreeObserver.addOnGlobalLayoutListener {
            val visibleFrame = Rect()
            root.getWindowVisibleDisplayFrame(visibleFrame)
            val hiddenHeight = root.height - visibleFrame.height()
            val keyboardVisible = hiddenHeight > root.height * KEYBOARD_HEIGHT_THRESHOLD
            bottomNavigationView.visibility = if (keyboardVisible) View.GONE else View.VISIBLE
        }
    }

    private fun saveExercise() {
        val name = etExerciseName.text.toString().trim()
        if (name.isEmpty()) {
            etExerciseName.error = "Introduce un nombre"
            return
        }

        val sets = etSeries.text.toString().toIntOrNull() ?: 0
        if (sets <= 0) {
            etSeries.error = "Series ≥ 1"
            return
        }

        val repsOrDuration = etRepsDuration.text.toString().trim().ifEmpty { null }
        val userNotes = etNotes.text.toString().trim().ifEmpty { null }
        val editedRestSeconds = etRestSeconds.text.toString().toIntOrNull()?.coerceAtLeast(0)
            ?: restSeconds
        val measurementType = measurementKeys.getOrElse(
            spMeasurementType.selectedItemPosition
        ) { Exercise.MEASUREMENT_REPS }

        val data = Intent().apply {
            putExtra(EXTRA_DAY_INDEX, dayIndex)
            putExtra(EXTRA_EXERCISE_INDEX, exerciseIndex)
            putExtra(EXTRA_NAME, name)
            putExtra(EXTRA_SETS, sets)
            putExtra(EXTRA_REPS_OR_DURATION, repsOrDuration)
            putExtra(EXTRA_MEASUREMENT_TYPE, measurementType)
            putExtra(EXTRA_USER_NOTES, userNotes)
            putExtra(EXTRA_WGER_EXERCISE_ID, wgerExerciseId)
            putExtra(EXTRA_MUSCLE_GROUP, muscleGroup)
            putExtra(EXTRA_EQUIPMENT_LABEL, equipmentLabel)
            putExtra(EXTRA_THUMB_URL, thumbnailImageUrl)
            putExtra(EXTRA_INSTRUCTIONS, instructions)
            putExtra(EXTRA_REST_SECONDS, editedRestSeconds)
        }
        setResult(Activity.RESULT_OK, data)
        finish()
    }

    companion object {

        const val EXTRA_DAY_INDEX = "extra_day_index"

        const val EXTRA_EXERCISE_INDEX = "extra_exercise_index"

        const val EXTRA_NAME = "extra_name"

        const val EXTRA_SETS = "extra_sets"

        const val EXTRA_REPS_OR_DURATION = "extra_reps_or_duration"

        const val EXTRA_MEASUREMENT_TYPE = "extra_measurement_type"

        const val EXTRA_USER_NOTES = "extra_user_notes"

        const val EXTRA_WGER_EXERCISE_ID = "extra_wger_exercise_id"

        const val EXTRA_MUSCLE_GROUP = "extra_muscle_group"

        const val EXTRA_EQUIPMENT_LABEL = "extra_equipment_label"

        const val EXTRA_THUMB_URL = "extra_thumb_url"

        const val EXTRA_INSTRUCTIONS = "extra_instructions"

        const val EXTRA_REST_SECONDS = "extra_rest_seconds"

        private const val KEYBOARD_HEIGHT_THRESHOLD = 0.15f
    }
}
