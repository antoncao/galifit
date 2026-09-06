package com.tfm.galifit

import android.content.Context
import android.content.Intent
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.core.widget.NestedScrollView
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.target.Target
import com.google.android.material.button.MaterialButton
import com.google.firebase.Firebase
import com.google.firebase.ai.type.content
import com.google.firebase.auth.auth
import com.google.firebase.firestore.firestore
import com.tfm.galifit.util.ai.GeminiModelProvider
import com.tfm.galifit.adapter.ElaborationAdapter
import com.tfm.galifit.adapter.IngredientsAdapter
import com.tfm.galifit.api.APIService
import com.tfm.galifit.api.ApiClient
import com.tfm.galifit.data.model.CachedRecipeDetailPayload
import com.tfm.galifit.data.model.CustomDish
import com.tfm.galifit.data.model.EdamamRecipeDetail
import com.tfm.galifit.data.model.Meal
import com.tfm.galifit.data.model.buildProteinSupplementDetailPayload
import com.tfm.galifit.data.model.isProteinSupplementMeal
import com.tfm.galifit.data.model.isProteinSupplementName
import com.tfm.galifit.data.model.pickThumbnailAndDetailUrls
import com.tfm.galifit.util.MealImageResolver
import com.tfm.galifit.data.model.toCachedPayload
import com.tfm.galifit.data.model.translatedToSpanish
import com.tfm.galifit.util.EdamamCredentials
import com.tfm.galifit.util.FullyExpandedLinearLayoutManager
import com.tfm.galifit.util.GalifitFlowLog
import com.tfm.galifit.util.RecipeDetailDiskCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

class MealDetailActivity : GetNavigationBarActivity() {

    private lateinit var rvElaboration: RecyclerView
    private lateinit var btnSuggestElaboration: MaterialButton
    private lateinit var progressSuggestElaboration: ProgressBar

    private var currentMealName: String = ""
    private var currentIngredientLines: List<String> = emptyList()
    private var currentSourceWebUrl: String? = null
    private var currentRecipeReferenceUrl: String? = null

    private val generativeModel by lazy {
        GeminiModelProvider.generativeModel()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.meal_detail)

        val rvIngredients = findViewById<RecyclerView>(R.id.rvIngredients)
        rvElaboration = findViewById(R.id.rvElaboration)
        btnSuggestElaboration = findViewById(R.id.btnSuggestElaboration)
        progressSuggestElaboration = findViewById(R.id.progressSuggestElaboration)
        rvIngredients.layoutManager = FullyExpandedLinearLayoutManager(this)
        rvElaboration.layoutManager = LinearLayoutManager(this)
        rvIngredients.isNestedScrollingEnabled = false

        val intentIngredients = intent.getStringArrayListExtra(EXTRA_INGREDIENTS)
            ?: intent.getStringArrayListExtra("ingredients")
            ?: arrayListOf()
        val intentElaboration = intent.getStringArrayListExtra(EXTRA_ELABORATION)
            ?: intent.getStringArrayListExtra("elaboration")
            ?: arrayListOf()
        val titulo = intent.getStringExtra(EXTRA_TITLE) ?: intent.getStringExtra("titulo")
        val detailUrl = intent.getStringExtra(EXTRA_DETAIL_IMAGE_URL)
        val recipeRef = intent.getStringExtra(EXTRA_RECIPE_REFERENCE_URL)?.trim().orEmpty()
        val isProteinSupplement = intent.getBooleanExtra(EXTRA_IS_PROTEIN_SUPPLEMENT, false)
            || isProteinSupplementName(titulo.orEmpty())

        currentMealName = titulo.orEmpty()
        currentIngredientLines = intentIngredients
        currentRecipeReferenceUrl = recipeRef.takeIf { it.isNotBlank() }

        findViewById<TextView>(R.id.mealNameLabel).text = titulo
        bindNutrition(null)

        rvIngredients.adapter = IngredientsAdapter(intentIngredients)
        rvElaboration.adapter = ElaborationAdapter(intentElaboration)
        btnSuggestElaboration.setOnClickListener { suggestElaboration() }

        val imageView = findViewById<ImageView>(R.id.detailMealImage)

        if (isProteinSupplement) {
            btnSuggestElaboration.visibility = View.GONE
            progressSuggestElaboration.visibility = View.GONE
            showProteinSupplementDetail(
                imageView = imageView,
                titulo = titulo,
                detailUrl = detailUrl,
                intentIngredients = intentIngredients,
                intentElaboration = intentElaboration,
                rvIngredients = rvIngredients,
                rvElaboration = rvElaboration
            )
            return
        }

        if (recipeRef.isEmpty()) {
            GalifitFlowLog.ui("Detalle comida: sin recipeReferenceUrl → datos solo desde intent")
            loadDetailImageSync(imageView, detailUrl, null, null)
            loadCustomDishDetails(rvIngredients)
            return
        }

        lifecycleScope.launch {
            val fromIntent = detailUrl?.trim()?.takeIf { it.isNotEmpty() }
            if (fromIntent != null) {
                withContext(Dispatchers.Main) {
                    GalifitFlowLog.ui("Imagen detalle: precarga desde intent")
                    Glide.with(this@MealDetailActivity)
                        .load(fromIntent)
                        .fitCenter()
                        .placeholder(R.drawable.bg_image_placeholder)
                        .error(R.drawable.bg_image_placeholder)
                        .into(imageView)
                }
            }

            val cached = withContext(Dispatchers.IO) {
                RecipeDetailDiskCache.read(this@MealDetailActivity, recipeRef)
            }
            var payload: CachedRecipeDetailPayload? = cached
            if (payload != null) {
                GalifitFlowLog.disk(
                    "Detalle receta desde disco: ${payload.ingredientLines.size} ingredientes"
                )
            } else {
                GalifitFlowLog.api("Detalle receta: sin caché → GET completo Edamam")
                val recipe = withContext(Dispatchers.IO) { fetchRecipeDetail(recipeRef) }
                if (recipe != null) {
                    payload = recipe.toCachedPayload(recipeRef)
                        .translatedToSpanish(this@MealDetailActivity)
                    RecipeDetailDiskCache.write(this@MealDetailActivity, recipeRef, payload)
                } else {
                    GalifitFlowLog.warn("Detalle receta: respuesta vacía o error HTTP")
                }
            }

            if (payload != null) {
                applyPayloadToUi(payload, titulo, rvIngredients, rvElaboration)
                bindNutrition(payload)
            }

            loadDetailImageWithRefresh(
                imageView = imageView,
                primaryUrl = payload?.detailImageUrl ?: fromIntent,
                recipeRef = recipeRef
            )
        }
    }

    private fun loadCustomDishDetails(rvIngredients: RecyclerView) {
        val dishId = intent.getStringExtra(EXTRA_CUSTOM_DISH_ID)?.trim().orEmpty()
        if (dishId.isEmpty()) return
        val uid = Firebase.auth.currentUser?.uid ?: return

        Firebase.firestore.collection("users")
            .document(uid)
            .collection("customDishes")
            .document(dishId)
            .get()
            .addOnSuccessListener { doc ->
                val dish = doc.toObject(CustomDish::class.java) ?: return@addOnSuccessListener
                GalifitFlowLog.firestore(
                    "Detalle comida: plato propio $dishId con ${dish.ingredients.size} " +
                        "ingredientes y ${dish.elaboration.size} pasos"
                )
                if (dish.ingredients.isNotEmpty()) {
                    currentIngredientLines = dish.ingredients
                    rvIngredients.adapter = IngredientsAdapter(dish.ingredients)
                    rvIngredients.requestLayout()
                }
                if (dish.elaboration.isNotEmpty()) {
                    rvElaboration.adapter = ElaborationAdapter(dish.elaboration)
                }
                bindCustomDishNutrition(dish)
            }
            .addOnFailureListener { e ->
                GalifitFlowLog.warn("Detalle comida: no se pudo leer el plato propio: ${e.message}")
            }
    }

    private fun bindCustomDishNutrition(dish: CustomDish) {
        findViewById<TextView>(R.id.textCalories).text = "${dish.calories} kcal"
        findViewById<TextView>(R.id.textProtein).text = "${dish.proteins} g"
        findViewById<TextView>(R.id.textCarbs).text = "${dish.carbs} g"
        findViewById<TextView>(R.id.textFat).text = "${dish.fats} g"
    }

    private fun showProteinSupplementDetail(
        imageView: ImageView,
        titulo: String?,
        detailUrl: String?,
        intentIngredients: List<String>,
        intentElaboration: List<String>,
        rvIngredients: RecyclerView,
        rvElaboration: RecyclerView
    ) {
        val proteinGrams = intent.getIntExtra(EXTRA_PROTEIN_GRAMS, 0)
        val calories = intent.getIntExtra(EXTRA_CALORIES, 0).takeIf { it > 0 }
            ?: (proteinGrams * 4)
        val payload = buildProteinSupplementDetailPayload(
            proteinGrams = proteinGrams.coerceAtLeast(0),
            calories = calories
        ).let { base ->
            val ingredients = intentIngredients.takeIf { it.isNotEmpty() } ?: base.ingredientLines
            val elaboration = intentElaboration.takeIf { it.isNotEmpty() } ?: base.elaborationSteps
            base.copy(ingredientLines = ingredients, elaborationSteps = elaboration)
        }

        applyPayloadToUi(payload, titulo, rvIngredients, rvElaboration)
        bindNutrition(payload)

        val loadModel = MealImageResolver.resolveDetailLoadModel(this, detailUrl)
            ?: MealImageResolver.resolveDetailLoadModel(this, payload.detailImageUrl)
            ?: R.drawable.scoop_proteina
        Glide.with(this)
            .load(loadModel)
            .fitCenter()
            .placeholder(R.drawable.bg_image_placeholder)
            .error(R.drawable.scoop_proteina)
            .into(imageView)

        GalifitFlowLog.ui("Detalle comida: suplemento proteico (${proteinGrams}g)")
    }

    private fun applyPayloadToUi(
        payload: CachedRecipeDetailPayload,
        tituloFallback: String?,
        rvIngredients: RecyclerView,
        rvElaboration: RecyclerView
    ) {
        val name = payload.title?.takeIf { it.isNotBlank() } ?: tituloFallback
        currentMealName = name.orEmpty()
        currentIngredientLines = payload.ingredientLines
        currentSourceWebUrl = payload.sourceWebUrl?.takeIf { it.isNotBlank() }
        currentRecipeReferenceUrl = payload.recipeReferenceUrl.takeIf { it.isNotBlank() }
        findViewById<TextView>(R.id.mealNameLabel).text = name
        rvIngredients.adapter = IngredientsAdapter(payload.ingredientLines)
        rvElaboration.adapter = ElaborationAdapter(payload.elaborationSteps)
        rvIngredients.requestLayout()
    }

    private fun bindNutrition(payload: CachedRecipeDetailPayload?) {
        findViewById<TextView>(R.id.textCalories).text = formatKcal(payload?.caloriesPerServing)
        findViewById<TextView>(R.id.textProtein).text = formatGrams(payload?.proteinPerServing)
        findViewById<TextView>(R.id.textCarbs).text = formatGrams(payload?.carbsPerServing)
        findViewById<TextView>(R.id.textFat).text = formatGrams(payload?.fatPerServing)
        findViewById<TextView>(R.id.textFiber).text = formatGrams(payload?.fiberPerServing)
    }

    private fun formatKcal(value: Double?): String =
        value?.let { "${"%.0f".format(it)} kcal" } ?: "—"

    private fun formatGrams(value: Double?): String =
        value?.let { "${"%.1f".format(it)} g" } ?: "—"

    private fun suggestElaboration() {
        val mealName = currentMealName.trim()
        if (mealName.isBlank()) {
            showElaborationSuggestionError()
            return
        }

        btnSuggestElaboration.isEnabled = false
        progressSuggestElaboration.visibility = View.VISIBLE

        lifecycleScope.launch {
            val result = runCatching {
                GalifitFlowLog.api("Sugerir elaboración: generateContent inicio (plato='$mealName')")
                val response = generativeModel.generateContent(
                    content { text(buildElaborationPrompt(mealName)) }
                )
                val raw = response.text.orEmpty()
                GalifitFlowLog.api(
                    "Sugerir elaboración: respuesta cruda (${raw.length} chars) → " +
                        raw.take(500)
                )
                parseElaborationSuggestion(raw)
            }

            progressSuggestElaboration.visibility = View.GONE
            btnSuggestElaboration.isEnabled = true

            result.onSuccess { elaboration ->
                rvElaboration.adapter = ElaborationAdapter(splitElaborationIntoSteps(elaboration))
                scrollToElaboration()
            }.onFailure { error ->
                GalifitFlowLog.warn(
                    "Sugerir elaboración falló → ${error.javaClass.name}: ${error.message}"
                )
                showElaborationSuggestionError()
            }
        }
    }

    private fun scrollToElaboration() {
        val scroll = findViewById<NestedScrollView>(R.id.scrollMealDetail) ?: return
        scroll.post {
            var offset = 0
            var view: View? = findViewById(R.id.elaboracionLabel)
            while (view != null && view !== scroll) {
                offset += view.top
                view = view.parent as? View
            }
            scroll.smoothScrollTo(0, offset)
        }
    }

    private fun buildElaborationPrompt(mealName: String): String {
        val ingredients = currentIngredientLines
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .joinToString(separator = "\n") { "- $it" }
            .ifBlank { "- No hay ingredientes detallados disponibles." }

        val source = currentSourceWebUrl
            ?: currentRecipeReferenceUrl
            ?: "No hay enlace disponible."

        return """
            Sugiere una elaboración clara y plausible para esta receta.
            Usa el nombre del plato, los ingredientes y, si el enlace aporta contexto, úsalo solo como guía.
            No incluyas explicaciones ni texto fuera del JSON, ni bloques de código markdown.
            Devuelve SOLO un objeto JSON válido con esta forma exacta:
            {"elaboracion":"paso 1\npaso 2\npaso 3"}
            La clave "elaboracion" debe contener únicamente los pasos de elaboración en español,
            separados por saltos de línea (\n).

            Nombre del plato: $mealName
            Ingredientes:
            $ingredients
            Enlace de referencia: $source
        """.trimIndent()
    }

    private fun parseElaborationSuggestion(raw: String): String {
        val sanitized = raw
            .trim()
            .removePrefix("```json")
            .removePrefix("```")
            .removeSuffix("```")
            .trim()

        val jsonCandidate = extractJsonObject(sanitized)
        if (jsonCandidate != null) {
            val elaboration = runCatching {
                JSONObject(jsonCandidate).optString("elaboracion").trim()
            }.getOrDefault("")
            if (elaboration.isNotBlank()) {
                return elaboration
            }
        }

        val plain = sanitized.trim()
        if (plain.isNotBlank()) {
            return plain
        }

        throw IllegalArgumentException("Respuesta vacía de Gemini")
    }

    private fun extractJsonObject(text: String): String? {
        val start = text.indexOf('{')
        val end = text.lastIndexOf('}')
        if (start == -1 || end == -1 || end <= start) return null
        return text.substring(start, end + 1)
    }

    private fun splitElaborationIntoSteps(elaboration: String): List<String> =
        elaboration
            .split('\n')
            .map { line ->
                line.trim()
                    .replace(Regex("^\\d+[.)]\\s*"), "")
                    .removePrefix("-")
                    .trim()
            }
            .filter { it.isNotEmpty() }
            .ifEmpty { listOf(elaboration.trim()) }

    private fun showElaborationSuggestionError() {
        Toast.makeText(
            this,
            "No fue posible sugerir una elaboración para esta receta.",
            Toast.LENGTH_LONG
        ).show()
    }

    private fun loadDetailImageSync(
        imageView: ImageView,
        intentDetailUrl: String?,
        recipeRef: String?,
        fallbackDetailUrl: String?
    ) {
        val resolvedLocal = intentDetailUrl?.let { MealImageResolver.resolveDetailLoadModel(this, it) }
            ?: fallbackDetailUrl?.let { MealImageResolver.resolveDetailLoadModel(this, it) }
        val u = when (resolvedLocal) {
            is Int -> resolvedLocal
            is String -> resolvedLocal.trim().takeIf { it.isNotEmpty() }
            else -> null
        }
        if (u != null) {
            GalifitFlowLog.ui("Imagen detalle: URL estática (intent/caché)")
            Glide.with(this)
                .load(u)
                .fitCenter()
                .placeholder(R.drawable.bg_image_placeholder)
                .error(R.drawable.bg_image_placeholder)
                .into(imageView)
            return
        }
        if (!recipeRef.isNullOrBlank()) {
            lifecycleScope.launch {
                loadDetailImage(imageView, null, recipeRef, null)
            }
            return
        }
        GalifitFlowLog.ui("Imagen detalle: placeholder (sin URLs)")
        Glide.with(this).clear(imageView)
        imageView.setImageResource(R.drawable.outline_meal_icon_galifit_24)
    }

    private fun loadDetailImageWithRefresh(
        imageView: ImageView,
        primaryUrl: String?,
        recipeRef: String
    ) {
        val model = primaryUrl
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?.let { MealImageResolver.resolveDetailLoadModel(this, it) }

        if (model == null) {
            lifecycleScope.launch { loadFreshDetailImage(imageView, recipeRef) }
            return
        }

        Glide.with(this)
            .load(model)
            .fitCenter()
            .placeholder(R.drawable.bg_image_placeholder)
            .error(R.drawable.bg_image_placeholder)
            .listener(object : RequestListener<Drawable> {
                override fun onLoadFailed(
                    e: GlideException?,
                    m: Any?,
                    target: Target<Drawable>,
                    isFirstResource: Boolean
                ): Boolean {
                    GalifitFlowLog.warn(
                        "Imagen detalle: carga falló (posible URL caducada), renovando desde Edamam"
                    )
                    lifecycleScope.launch { loadFreshDetailImage(imageView, recipeRef) }
                    return false
                }

                override fun onResourceReady(
                    resource: Drawable,
                    m: Any,
                    target: Target<Drawable>?,
                    dataSource: DataSource,
                    isFirstResource: Boolean
                ): Boolean = false
            })
            .into(imageView)
    }

    private suspend fun loadFreshDetailImage(imageView: ImageView, recipeRef: String) {
        if (recipeRef.isBlank()) {
            withContext(Dispatchers.Main) {
                Glide.with(imageView.context).clear(imageView)
                imageView.setImageResource(R.drawable.outline_meal_icon_galifit_24)
            }
            return
        }
        val fresh = withContext(Dispatchers.IO) { fetchDetailImageUrl(recipeRef) }
        withContext(Dispatchers.Main) {
            if (!fresh.isNullOrBlank()) {
                GalifitFlowLog.ui("Imagen detalle: cargada con URL renovada de Edamam")
                Glide.with(imageView.context)
                    .load(fresh)
                    .fitCenter()
                    .placeholder(R.drawable.bg_image_placeholder)
                    .error(R.drawable.bg_image_placeholder)
                    .into(imageView)
            } else {
                Glide.with(imageView.context).clear(imageView)
                imageView.setImageResource(R.drawable.outline_meal_icon_galifit_24)
            }
        }
    }

    private suspend fun loadDetailImage(
        imageView: ImageView,
        intentDetailUrl: String?,
        recipeRef: String,
        fallbackDetailUrl: String?
    ) {
        val fromIntent = intentDetailUrl?.trim()?.takeIf { it.isNotEmpty() }
        when {
            fromIntent != null -> {
                GalifitFlowLog.ui("Imagen detalle: URL del intent")
                withContext(Dispatchers.Main) {
                    Glide.with(imageView.context)
                        .load(fromIntent)
                        .fitCenter()
                        .placeholder(R.drawable.bg_image_placeholder)
                        .error(R.drawable.bg_image_placeholder)
                        .into(imageView)
                }
            }
            !fallbackDetailUrl.isNullOrBlank() -> {
                GalifitFlowLog.ui("Imagen detalle: URL desde caché/última respuesta API")
                withContext(Dispatchers.Main) {
                    Glide.with(imageView.context)
                        .load(fallbackDetailUrl)
                        .fitCenter()
                        .placeholder(R.drawable.bg_image_placeholder)
                        .error(R.drawable.bg_image_placeholder)
                        .into(imageView)
                }
            }
            else -> {
                GalifitFlowLog.ui("Imagen detalle: GET receta solo para URL de imagen")
                val fresh = withContext(Dispatchers.IO) { fetchDetailImageUrl(recipeRef) }
                withContext(Dispatchers.Main) {
                    if (!fresh.isNullOrBlank()) {
                        Glide.with(imageView.context)
                            .load(fresh)
                            .fitCenter()
                            .placeholder(R.drawable.bg_image_placeholder)
                            .error(R.drawable.bg_image_placeholder)
                            .into(imageView)
                    } else {
                        Glide.with(imageView.context).clear(imageView)
                        imageView.setImageResource(R.drawable.outline_meal_icon_galifit_24)
                    }
                }
            }
        }
    }

    private suspend fun fetchRecipeDetail(recipeReferenceUrl: String): EdamamRecipeDetail? {
        return try {
            val service = ApiClient().getEdamamMealPlanner().create(APIService::class.java)
            val authHeader = EdamamCredentials.basicAuthHeader()
            val edamamAccountUser = EdamamCredentials.accountUser
            GalifitFlowLog.api(
                "GET receta (detalle completo): …${recipeReferenceUrl.trim().takeLast(48)}"
            )
            val response = service.getRecipeByUrl(
                url = recipeReferenceUrl.trim(),
                authHeader = authHeader,
                edamamAccountUser = edamamAccountUser
            )
            if (!response.isSuccessful) {
                GalifitFlowLog.warn("GET receta detalle falló HTTP ${response.code()}")
                return null
            }
            response.body()?.recipe
        } catch (e: Exception) {
            GalifitFlowLog.warn("GET receta detalle excepción: ${e.message}")
            null
        }
    }

    private suspend fun fetchDetailImageUrl(recipeReferenceUrl: String): String? {
        return try {
            val service = ApiClient().getEdamamMealPlanner().create(APIService::class.java)
            val authHeader = EdamamCredentials.basicAuthHeader()
            val edamamAccountUser = EdamamCredentials.accountUser
            GalifitFlowLog.api(
                "GET receta (solo imagen detalle): …${recipeReferenceUrl.trim().takeLast(48)}"
            )
            val response = service.getRecipeByUrl(
                url = recipeReferenceUrl.trim(),
                authHeader = authHeader,
                edamamAccountUser = edamamAccountUser
            )
            if (response.isSuccessful) {
                response.body()?.recipe?.pickThumbnailAndDetailUrls()?.detailImageUrl
            } else {
                GalifitFlowLog.warn("GET receta (imagen) HTTP ${response.code()}")
                null
            }
        } catch (e: Exception) {
            GalifitFlowLog.warn("GET receta (imagen) excepción: ${e.message}")
            null
        }
    }

    companion object {

        const val EXTRA_TITLE = "titulo"

        const val EXTRA_DETAIL_IMAGE_URL = "detailImageUrl"

        const val EXTRA_RECIPE_REFERENCE_URL = "recipeReferenceUrl"

        const val EXTRA_IS_PROTEIN_SUPPLEMENT = "isProteinSupplement"

        const val EXTRA_CALORIES = "calories"

        const val EXTRA_PROTEIN_GRAMS = "proteinGrams"

        const val EXTRA_INGREDIENTS = "ingredients"

        const val EXTRA_ELABORATION = "elaboration"

        const val EXTRA_CUSTOM_DISH_ID = "customDishId"

        fun intentFor(context: Context, meal: Meal): Intent {
            return Intent(context, MealDetailActivity::class.java).apply {
                putExtra(EXTRA_TITLE, meal.name)
                putExtra(EXTRA_DETAIL_IMAGE_URL, meal.detailImageUrl)
                putExtra(EXTRA_RECIPE_REFERENCE_URL, meal.recipeReferenceUrl)
                putExtra(EXTRA_CUSTOM_DISH_ID, meal.customDishId)
                if (meal.ingredients.isNotEmpty()) {
                    putStringArrayListExtra(EXTRA_INGREDIENTS, ArrayList(meal.ingredients))
                }
                if (meal.elaboration.isNotEmpty()) {
                    putStringArrayListExtra(EXTRA_ELABORATION, ArrayList(meal.elaboration))
                }
                if (meal.isProteinSupplementMeal()) {
                    putExtra(EXTRA_IS_PROTEIN_SUPPLEMENT, true)
                    putExtra(EXTRA_CALORIES, meal.calories ?: 0)
                    putExtra(EXTRA_PROTEIN_GRAMS, meal.proteinGrams ?: 0)
                }
            }
        }
    }
}
