package com.tfm.galifit

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Rect
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.widget.NestedScrollView
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.google.android.material.button.MaterialButton
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.firestore
import com.google.firebase.storage.storage
import com.tfm.galifit.data.model.CustomDish
import com.tfm.galifit.util.GalifitFlowLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.File

class CreateDishActivity : GetNavigationBarActivity() {

    private lateinit var tvScreenTitle: TextView
    private lateinit var etDishName: EditText
    private lateinit var etCalories: EditText
    private lateinit var etProteins: EditText
    private lateinit var etCarbs: EditText
    private lateinit var etFats: EditText
    private lateinit var ingredientsContainer: LinearLayout
    private lateinit var btnAddIngredient: MaterialButton
    private lateinit var etElaboration: EditText
    private lateinit var btnSaveDish: MaterialButton
    private lateinit var progressSaveDish: ProgressBar
    private lateinit var scrollCreateDish: NestedScrollView
    private lateinit var bottomNavigationView: View

    private lateinit var ivDishImage: ImageView
    private lateinit var btnTakeDishPhoto: MaterialButton
    private lateinit var btnPickDishImage: MaterialButton
    private lateinit var btnRemoveDishImage: MaterialButton

    private var dayIndex: Int = -1
    private var editingDishId: String? = null

    private var pendingImageUri: Uri? = null

    private var cameraTempUri: Uri? = null

    private var existingImageUrl: String? = null

    private var removeExistingImage: Boolean = false

    private val takePictureLauncher = registerForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        if (success && cameraTempUri != null) {
            pendingImageUri = cameraTempUri
            removeExistingImage = false
            bindImagePreview()
        }
    }

    private val pickMediaLauncher = registerForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            pendingImageUri = uri
            removeExistingImage = false
            bindImagePreview()
        }
    }

    private val cameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) launchCamera()
        else Toast.makeText(this, "Permiso de cámara denegado", Toast.LENGTH_SHORT).show()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_create_dish)

        tvScreenTitle = findViewById(R.id.tvScreenTitle)
        etDishName = findViewById(R.id.etDishName)
        etCalories = findViewById(R.id.etCalories)
        etProteins = findViewById(R.id.etProteins)
        etCarbs = findViewById(R.id.etCarbs)
        etFats = findViewById(R.id.etFats)
        ingredientsContainer = findViewById(R.id.ingredientsContainer)
        btnAddIngredient = findViewById(R.id.btnAddIngredient)
        etElaboration = findViewById(R.id.etElaboration)
        btnSaveDish = findViewById(R.id.btnSaveDish)
        progressSaveDish = findViewById(R.id.progressSaveDish)
        scrollCreateDish = findViewById(R.id.scrollCreateDish)
        bottomNavigationView = findViewById(R.id.bottomNavigationView)

        ivDishImage = findViewById(R.id.ivDishImage)
        btnTakeDishPhoto = findViewById(R.id.btnTakeDishPhoto)
        btnPickDishImage = findViewById(R.id.btnPickDishImage)
        btnRemoveDishImage = findViewById(R.id.btnRemoveDishImage)

        dayIndex = intent.getIntExtra(EXTRA_DAY_INDEX, -1)
        editingDishId = intent.getStringExtra(EXTRA_DISH_ID)

        btnAddIngredient.setOnClickListener { addIngredientRow("", focusAfterAdding = true) }
        btnSaveDish.setOnClickListener { saveDish() }

        btnTakeDishPhoto.setOnClickListener { requestCameraPermissionAndLaunch() }
        btnPickDishImage.setOnClickListener {
            pickMediaLauncher.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
            )
        }
        btnRemoveDishImage.setOnClickListener {
            pendingImageUri = null
            removeExistingImage = true
            bindImagePreview()
        }

        if (editingDishId.isNullOrBlank()) {
            tvScreenTitle.text = "Crear plato"
            addIngredientRow("")
            addIngredientRow("")
            addIngredientRow("")
        } else {
            tvScreenTitle.text = "Editar plato"
            loadExistingDish(editingDishId!!)
        }

        bindImagePreview()
        hideBottomNavigationWhileTyping()
        getNavigationView()
    }

    private fun hideBottomNavigationWhileTyping() {
        val root = scrollCreateDish.rootView
        root.viewTreeObserver.addOnGlobalLayoutListener {
            val visibleFrame = Rect()
            root.getWindowVisibleDisplayFrame(visibleFrame)
            val hiddenHeight = root.height - visibleFrame.height()
            val keyboardVisible = hiddenHeight > root.height * KEYBOARD_HEIGHT_THRESHOLD
            bottomNavigationView.visibility = if (keyboardVisible) View.GONE else View.VISIBLE
        }
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
        val photoFile = File(cacheDir, "dish_photo_${System.currentTimeMillis()}.jpg")
        cameraTempUri = FileProvider.getUriForFile(
            this,
            "${applicationContext.packageName}.fileprovider",
            photoFile
        )
        takePictureLauncher.launch(cameraTempUri!!)
    }

    private fun bindImagePreview() {
        val pending = pendingImageUri
        val showExisting = !removeExistingImage && !existingImageUrl.isNullOrBlank()
        when {
            pending != null -> {
                Glide.with(this)
                    .load(pending)
                    .centerCrop()
                    .placeholder(R.drawable.bg_image_placeholder)
                    .into(ivDishImage)
                btnRemoveDishImage.visibility = View.VISIBLE
            }
            showExisting -> {
                Glide.with(this)
                    .load(existingImageUrl)
                    .centerCrop()
                    .placeholder(R.drawable.bg_image_placeholder)
                    .into(ivDishImage)
                btnRemoveDishImage.visibility = View.VISIBLE
            }
            else -> {
                Glide.with(this).clear(ivDishImage)
                ivDishImage.setImageDrawable(null)
                ivDishImage.setBackgroundResource(R.drawable.bg_image_placeholder)
                btnRemoveDishImage.visibility = View.GONE
            }
        }
    }

    private fun addIngredientRow(initialValue: String, focusAfterAdding: Boolean = false) {
        val row = LayoutInflater.from(this)
            .inflate(R.layout.item_ingredient_input, ingredientsContainer, false)
        val et = row.findViewById<EditText>(R.id.etIngredient)
        val btnRemove = row.findViewById<ImageButton>(R.id.btnRemoveIngredient)
        et.setText(initialValue)
        btnRemove.setOnClickListener {
            ingredientsContainer.removeView(row)
        }
        ingredientsContainer.addView(row)
        if (focusAfterAdding) {
            et.post {
                et.requestFocus()
                getSystemService(InputMethodManager::class.java)
                    ?.showSoftInput(et, InputMethodManager.SHOW_IMPLICIT)
            }
        }
    }

    private fun collectIngredients(): List<String> {
        val list = mutableListOf<String>()
        for (i in 0 until ingredientsContainer.childCount) {
            val row = ingredientsContainer.getChildAt(i)
            val et = row.findViewById<EditText>(R.id.etIngredient)
            val text = et.text.toString().trim()
            if (text.isNotEmpty()) list.add(text)
        }
        return list
    }

    private fun collectElaborationSteps(): List<String> =
        etElaboration.text.toString()
            .split('\n')
            .map { line -> line.trim().replace(STEP_NUMBER_PREFIX, "").trim() }
            .filter { it.isNotEmpty() }

    private fun loadExistingDish(dishId: String) {
        val uid = Firebase.auth.currentUser?.uid ?: return
        progressSaveDish.visibility = View.VISIBLE
        Firebase.firestore.collection("users")
            .document(uid)
            .collection("customDishes")
            .document(dishId)
            .get()
            .addOnSuccessListener { doc ->
                progressSaveDish.visibility = View.GONE
                val dish = doc.toObject(CustomDish::class.java) ?: return@addOnSuccessListener
                etDishName.setText(dish.name)
                etCalories.setText(dish.calories.toString())
                etProteins.setText(dish.proteins.toString())
                etCarbs.setText(dish.carbs.toString())
                etFats.setText(dish.fats.toString())
                ingredientsContainer.removeAllViews()
                if (dish.ingredients.isEmpty()) {
                    addIngredientRow("")
                } else {
                    dish.ingredients.forEach { addIngredientRow(it) }
                }
                etElaboration.setText(dish.elaboration.joinToString(separator = "\n"))
                existingImageUrl = dish.imageUrl?.takeIf { it.isNotBlank() }
                bindImagePreview()
            }
            .addOnFailureListener {
                progressSaveDish.visibility = View.GONE
                Toast.makeText(this, "Error al cargar el plato", Toast.LENGTH_SHORT).show()
            }
    }

    private fun saveDish() {
        val name = etDishName.text.toString().trim()
        if (name.isEmpty()) {
            etDishName.error = "Introduce un nombre"
            return
        }

        val uid = Firebase.auth.currentUser?.uid
        if (uid == null) {
            Toast.makeText(this, "Sesión no válida", Toast.LENGTH_SHORT).show()
            return
        }

        val dishesRef = Firebase.firestore.collection("users")
            .document(uid)
            .collection("customDishes")

        val docRef = if (editingDishId.isNullOrBlank()) dishesRef.document()
        else dishesRef.document(editingDishId!!)

        progressSaveDish.visibility = View.VISIBLE
        btnSaveDish.isEnabled = false

        lifecycleScope.launch {
            val resolvedImageUrl = try {
                resolveFinalImageUrl(uid, docRef.id)
            } catch (e: Exception) {
                GalifitFlowLog.warn("CreateDish: upload imagen falló ${e.message}")
                Toast.makeText(
                    this@CreateDishActivity,
                    "No se pudo subir la imagen. Se guarda el plato sin foto.",
                    Toast.LENGTH_LONG
                ).show()
                if (removeExistingImage) null else existingImageUrl
            }

            val dish = CustomDish().apply {
                id = docRef.id
                this.name = name
                calories = etCalories.text.toString().toIntOrNull() ?: 0
                proteins = etProteins.text.toString().toIntOrNull() ?: 0
                carbs = etCarbs.text.toString().toIntOrNull() ?: 0
                fats = etFats.text.toString().toIntOrNull() ?: 0
                ingredients = collectIngredients()
                elaboration = collectElaborationSteps()
                imageUrl = resolvedImageUrl
                createdAtMillis = System.currentTimeMillis()
            }

            docRef.set(dish)
                .addOnSuccessListener {
                    progressSaveDish.visibility = View.GONE
                    btnSaveDish.isEnabled = true
                    val resultIntent = Intent().apply {
                        putExtra(EXTRA_RESULT_DISH_ID, docRef.id)
                        putExtra(EXTRA_DAY_INDEX, dayIndex)
                        putExtra(EXTRA_RESULT_IS_EDIT, !editingDishId.isNullOrBlank())
                    }
                    setResult(Activity.RESULT_OK, resultIntent)
                    finish()
                }
                .addOnFailureListener { e ->
                    progressSaveDish.visibility = View.GONE
                    btnSaveDish.isEnabled = true
                    Toast.makeText(
                        this@CreateDishActivity,
                        "Error al guardar: ${e.localizedMessage}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
        }
    }

    private suspend fun resolveFinalImageUrl(uid: String, dishId: String): String? {
        val pending = pendingImageUri
        if (pending != null) {
            val newUrl = uploadDishImage(uid, dishId, pending)
            return newUrl
        }
        if (removeExistingImage && !existingImageUrl.isNullOrBlank()) {
            runCatching { deleteDishImage(uid, dishId) }
            return null
        }
        return existingImageUrl
    }

    private suspend fun uploadDishImage(uid: String, dishId: String, uri: Uri): String =
        withContext(Dispatchers.IO) {
            GalifitFlowLog.api("CreateDish: subiendo imagen a Storage dishId=$dishId")
            val ref = Firebase.storage.reference
                .child("users/$uid/customDishes/$dishId.jpg")
            ref.putFile(uri).await()
            ref.downloadUrl.await().toString()
        }

    private suspend fun deleteDishImage(uid: String, dishId: String) =
        withContext(Dispatchers.IO) {
            GalifitFlowLog.api("CreateDish: borrando imagen Storage dishId=$dishId")
            Firebase.storage.reference
                .child("users/$uid/customDishes/$dishId.jpg")
                .delete()
                .await()
        }

    companion object {

        const val EXTRA_DAY_INDEX = "extra_day_index"

        const val EXTRA_DISH_ID = "extra_dish_id"

        const val EXTRA_RESULT_DISH_ID = "result_dish_id"

        const val EXTRA_RESULT_IS_EDIT = "result_is_edit"

        private val STEP_NUMBER_PREFIX = Regex("^(\\d+[.)]|-|•)\\s*")

        private const val KEYBOARD_HEIGHT_THRESHOLD = 0.15f
    }
}
