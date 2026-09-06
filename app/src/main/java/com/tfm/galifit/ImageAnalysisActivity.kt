package com.tfm.galifit

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.firebase.Firebase
import com.google.firebase.ai.type.content
import com.tfm.galifit.util.ai.GeminiModelProvider
import com.google.firebase.auth.auth
import com.tfm.galifit.data.repository.CustomDishRepository
import com.tfm.galifit.util.GalifitFlowLog
import kotlinx.coroutines.launch
import java.io.File

class ImageAnalysisActivity : GetNavigationBarActivity() {

    private val customDishRepository = CustomDishRepository()

    private lateinit var placeholderContainer: View
    private lateinit var ivFoodImage: ImageView
    private lateinit var btnTakePhoto: MaterialButton
    private lateinit var btnUploadImage: MaterialButton
    private lateinit var progressBar: ProgressBar
    private lateinit var tvError: TextView
    private lateinit var tvFoodName: TextView
    private lateinit var nutritionSection: LinearLayout
    private lateinit var tvCalories: TextView
    private lateinit var tvProteins: TextView
    private lateinit var tvCarbs: TextView
    private lateinit var tvFats: TextView
    private lateinit var btnAddToPlan: MaterialButton
    private lateinit var progressAddToPlan: ProgressBar

    private var currentBitmap: Bitmap? = null
    private var photoUri: Uri? = null

    private var lastFoodName: String? = null
    private var lastCalories: Int = 0
    private var lastProteins: Int = 0
    private var lastCarbs: Int = 0
    private var lastFats: Int = 0

    private val generativeModel by lazy {
        GeminiModelProvider.generativeModel()
    }

    private val takePictureLauncher = registerForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        if (success && photoUri != null) {
            val bitmap = uriToBitmap(photoUri!!)
            if (bitmap != null) {
                showImage(bitmap)
                analyzeImage(bitmap)
            }
        }
    }

    private val pickMediaLauncher = registerForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            val bitmap = uriToBitmap(uri)
            if (bitmap != null) {
                showImage(bitmap)
                analyzeImage(bitmap)
            }
        }
    }

    private val cameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) launchCamera()
        else Toast.makeText(this, "Se necesita permiso de cámara", Toast.LENGTH_SHORT).show()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_image_analysis)

        placeholderContainer = findViewById(R.id.placeholderContainer)
        ivFoodImage = findViewById(R.id.ivFoodImage)
        btnTakePhoto = findViewById(R.id.btnTakePhoto)
        btnUploadImage = findViewById(R.id.btnUploadImage)
        progressBar = findViewById(R.id.progressBar)
        tvError = findViewById(R.id.tvError)
        tvFoodName = findViewById(R.id.tvFoodName)
        nutritionSection = findViewById(R.id.nutritionSection)
        tvCalories = findViewById(R.id.tvCalories)
        tvProteins = findViewById(R.id.tvProteins)
        tvCarbs = findViewById(R.id.tvCarbs)
        tvFats = findViewById(R.id.tvFats)
        btnAddToPlan = findViewById(R.id.btnAddToPlan)
        progressAddToPlan = findViewById(R.id.progressAddToPlan)

        btnTakePhoto.setOnClickListener { requestCameraPermissionAndLaunch() }
        btnUploadImage.setOnClickListener {
            pickMediaLauncher.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
            )
        }
        btnAddToPlan.setOnClickListener { showDaySelectionDialog() }

        getNavigationView()
    }

    private fun requestCameraPermissionAndLaunch() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            == PackageManager.PERMISSION_GRANTED
        ) {
            launchCamera()
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    private fun launchCamera() {
        val photoFile = File(cacheDir, "food_photo_${System.currentTimeMillis()}.jpg")
        photoUri = FileProvider.getUriForFile(
            this,
            "${applicationContext.packageName}.fileprovider",
            photoFile
        )
        takePictureLauncher.launch(photoUri!!)
    }

    private fun uriToBitmap(uri: Uri): Bitmap? {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val source = ImageDecoder.createSource(contentResolver, uri)
                ImageDecoder.decodeBitmap(source) { decoder, _, _ ->
                    decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                    decoder.isMutableRequired = true
                }
            } else {
                @Suppress("DEPRECATION")
                MediaStore.Images.Media.getBitmap(contentResolver, uri)
            }
        } catch (e: Exception) {
            showError("Error al cargar la imagen")
            null
        }
    }

    private fun showImage(bitmap: Bitmap) {
        currentBitmap = bitmap
        placeholderContainer.visibility = View.GONE
        ivFoodImage.visibility = View.VISIBLE
        ivFoodImage.setImageBitmap(bitmap)
    }

    private fun analyzeImage(bitmap: Bitmap) {
        tvError.visibility = View.GONE
        tvFoodName.visibility = View.GONE
        nutritionSection.visibility = View.GONE
        btnAddToPlan.visibility = View.GONE
        progressBar.visibility = View.VISIBLE
        btnTakePhoto.isEnabled = false
        btnUploadImage.isEnabled = false

        lifecycleScope.launch {
            val t0 = System.currentTimeMillis()
            try {
                GalifitFlowLog.api("ImageAnalysis: generateContent inicio")
                val prompt = content {
                    image(bitmap)
                    text(
                        "Analiza esta imagen de comida y devuelve SOLO un JSON con este formato exacto, " +
                        "sin markdown ni texto adicional:\n" +
                        "{\"nombre\": \"nombre del plato\", \"calorias\": 0, \"proteinas\": 0, " +
                        "\"carbohidratos\": 0, \"grasas\": 0}\n" +
                        "Los valores nutricionales deben ser estimaciones por porción visible. " +
                        "Calorías en kcal, el resto en gramos (solo el número entero). " +
                        "Si la imagen no contiene comida, devuelve: " +
                        "{\"nombre\": \"No es comida\", \"calorias\": 0, \"proteinas\": 0, " +
                        "\"carbohidratos\": 0, \"grasas\": 0}"
                    )
                }

                val response = generativeModel.generateContent(prompt)
                val text = response.text?.trim() ?: ""
                GalifitFlowLog.api(
                    "ImageAnalysis: generateContent OK en ${System.currentTimeMillis() - t0}ms chars=${text.length}"
                )
                parseAndDisplayResult(text)
            } catch (e: Exception) {
                GalifitFlowLog.warn(
                    "ImageAnalysis: generateContent falló tras ${System.currentTimeMillis() - t0}ms → ${e.message}"
                )
                showError("Error al analizar: ${e.localizedMessage}")
            } finally {
                progressBar.visibility = View.GONE
                btnTakePhoto.isEnabled = true
                btnUploadImage.isEnabled = true
            }
        }
    }

    private fun parseAndDisplayResult(rawJson: String) {
        try {
            val json = rawJson
                .removePrefix("```json")
                .removePrefix("```")
                .removeSuffix("```")
                .trim()

            val obj = org.json.JSONObject(json)
            val nombre = obj.optString("nombre", "Desconocido")
            val calorias = obj.optInt("calorias", 0)
            val proteinas = obj.optInt("proteinas", 0)
            val carbohidratos = obj.optInt("carbohidratos", 0)
            val grasas = obj.optInt("grasas", 0)

            tvFoodName.text = nombre
            tvFoodName.visibility = View.VISIBLE

            tvCalories.text = "$calorias kcal"
            tvProteins.text = "$proteinas g"
            tvCarbs.text = "$carbohidratos g"
            tvFats.text = "$grasas g"
            nutritionSection.visibility = View.VISIBLE

            val isValidFood = nombre.isNotBlank() &&
                !nombre.equals("No es comida", ignoreCase = true) &&
                !nombre.equals("Desconocido", ignoreCase = true)
            if (isValidFood) {
                lastFoodName = nombre
                lastCalories = calorias
                lastProteins = proteinas
                lastCarbs = carbohidratos
                lastFats = grasas
                btnAddToPlan.visibility = View.VISIBLE
            } else {
                btnAddToPlan.visibility = View.GONE
            }
        } catch (e: Exception) {
            showError("No se pudo interpretar la respuesta de la IA")
        }
    }

    private fun showDaySelectionDialog() {
        val name = lastFoodName ?: return
        if (currentBitmap == null) {
            Toast.makeText(this, "No hay imagen para asociar al plato", Toast.LENGTH_SHORT).show()
            return
        }
        val days = CustomDishRepository.WEEK_DAYS.toTypedArray()
        MaterialAlertDialogBuilder(this)
            .setTitle("Añadir \"$name\" a")
            .setItems(days) { _, which ->
                addAnalyzedDishToPlan(which)
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun addAnalyzedDishToPlan(dayIndex: Int) {
        val uid = Firebase.auth.currentUser?.uid
        if (uid == null) {
            Toast.makeText(this, "Sesión no válida", Toast.LENGTH_SHORT).show()
            return
        }
        val name = lastFoodName ?: return
        val bitmap = currentBitmap

        btnAddToPlan.isEnabled = false
        progressAddToPlan.visibility = View.VISIBLE

        lifecycleScope.launch {
            val t0 = System.currentTimeMillis()
            val result = runCatching {
                GalifitFlowLog.firestore("ImageAnalysis: guardar plato analizado inicio día=$dayIndex")
                val dish = customDishRepository.saveDishFromBitmap(
                    uid = uid,
                    name = name,
                    calories = lastCalories,
                    proteins = lastProteins,
                    carbs = lastCarbs,
                    fats = lastFats,
                    bitmap = bitmap
                )
                customDishRepository.addDishToWeeklyPlan(uid, dish, dayIndex)
            }

            progressAddToPlan.visibility = View.GONE
            btnAddToPlan.isEnabled = true

            result.onSuccess {
                GalifitFlowLog.firestore(
                    "ImageAnalysis: guardar plato analizado OK total=${System.currentTimeMillis() - t0}ms"
                )
                val dayName = CustomDishRepository.WEEK_DAYS[dayIndex]
                Toast.makeText(
                    this@ImageAnalysisActivity,
                    "\"$name\" añadido a $dayName",
                    Toast.LENGTH_LONG
                ).show()
                btnAddToPlan.visibility = View.GONE
            }.onFailure {
                GalifitFlowLog.warn(
                    "ImageAnalysis: guardar plato analizado falló tras ${System.currentTimeMillis() - t0}ms → ${it.message}"
                )
                Toast.makeText(
                    this@ImageAnalysisActivity,
                    "No se pudo añadir al plan: ${it.localizedMessage}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun showError(message: String) {
        tvError.text = message
        tvError.visibility = View.VISIBLE
        btnAddToPlan.visibility = View.GONE
    }
}
