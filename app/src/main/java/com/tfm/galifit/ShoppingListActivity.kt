package com.tfm.galifit

import android.os.Bundle
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.Source
import com.google.firebase.firestore.firestore
import com.google.firebase.Firebase
import com.tfm.galifit.adapter.ShoppingItem
import com.tfm.galifit.adapter.ShoppingListAdapter
import com.tfm.galifit.api.APIService
import com.tfm.galifit.api.ApiClient
import com.tfm.galifit.data.model.FirebaseSavedMealPlan
import com.tfm.galifit.data.model.ShoppingListEntry
import com.tfm.galifit.data.model.ShoppingListRequest
import com.tfm.galifit.data.model.toSingleLineQuantity
import com.tfm.galifit.util.EdamamCredentials
import com.tfm.galifit.util.GalifitFlowLog
import com.tfm.galifit.util.TranslationService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Calendar
import java.util.TimeZone

class ShoppingListActivity : GetNavigationBarActivity() {

    private lateinit var tvWeekLabel: TextView
    private lateinit var btnChangeWeek: TextView
    private lateinit var btnMarkAll: MaterialButton
    private lateinit var progressBar: ProgressBar
    private lateinit var tvEmptyState: TextView
    private lateinit var rvIngredients: RecyclerView

    private var adapter: ShoppingListAdapter? = null
    private var currentWeekOffset = 0
    private var cachedRecipeUris: List<String> = emptyList()

    private val spainTz = TimeZone.getTimeZone("Europe/Madrid")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_shopping_list)

        tvWeekLabel = findViewById(R.id.tvWeekLabel)
        btnChangeWeek = findViewById(R.id.btnChangeWeek)
        btnMarkAll = findViewById(R.id.btnMarkAll)
        progressBar = findViewById(R.id.progressBar)
        tvEmptyState = findViewById(R.id.tvEmptyState)
        rvIngredients = findViewById(R.id.rvIngredients)

        rvIngredients.layoutManager = LinearLayoutManager(this)
        rvIngredients.isNestedScrollingEnabled = false

        btnChangeWeek.setOnClickListener { showWeekPickerDialog() }
        btnMarkAll.setOnClickListener { toggleMarkAll() }

        this.getNavigationView()

        loadMealPlanAndShoppingList()
    }

    private fun getWeekKey(offset: Int): String {
        val cal = Calendar.getInstance(spainTz)
        cal.add(Calendar.WEEK_OF_YEAR, offset)
        val year = cal.get(Calendar.YEAR)
        val week = cal.get(Calendar.WEEK_OF_YEAR)
        return "$year-W${week.toString().padStart(2, '0')}"
    }

    private fun updateWeekLabel() {
        tvWeekLabel.text = if (currentWeekOffset == 0) "Semana actual" else "Semana siguiente"
    }

    private fun showWeekPickerDialog() {
        val options = arrayOf("Semana actual", "Semana siguiente")
        AlertDialog.Builder(this)
            .setTitle("Seleccionar semana")
            .setItems(options) { _, which ->
                currentWeekOffset = which
                updateWeekLabel()
                if (cachedRecipeUris.isNotEmpty()) {
                    loadShoppingListForWeek(cachedRecipeUris)
                }
            }
            .show()
    }

    private fun toggleMarkAll() {
        val a = adapter ?: return
        if (a.isAllChecked()) {
            a.deselectAll()
            btnMarkAll.text = "Marcar todo"
        } else {
            a.selectAll()
            btnMarkAll.text = "Desmarcar todo"
        }
    }

    private fun loadMealPlanAndShoppingList() {
        val uid = Firebase.auth.currentUser?.uid
        if (uid == null) {
            showEmpty()
            return
        }

        progressBar.visibility = View.VISIBLE
        tvEmptyState.visibility = View.GONE

        GalifitFlowLog.firestore("ShoppingList: lectura último mealPlan desde CACHE")
        val latestPlanQuery = Firebase.firestore.collection("users")
            .document(uid)
            .collection("mealPlans")
            .orderBy("createdAtMillis", Query.Direction.DESCENDING)
            .limit(1)

        latestPlanQuery
            .get(Source.CACHE)
            .addOnSuccessListener { snapshot ->
                val loaded = handleMealPlanDocument(snapshot.documents.firstOrNull(), "CACHE")
                if (!loaded) {
                    loadMealPlanFromServer(latestPlanQuery)
                }
            }
            .addOnFailureListener { e ->
                GalifitFlowLog.warn("ShoppingList: lectura mealPlan CACHE falló: ${e.message}; probando SERVER")
                loadMealPlanFromServer(latestPlanQuery)
            }
    }

    private fun loadMealPlanFromServer(latestPlanQuery: Query) {
        GalifitFlowLog.firestore("ShoppingList: lectura último mealPlan desde SERVER")
        latestPlanQuery
            .get(Source.SERVER)
            .addOnSuccessListener { snapshot ->
                val loaded = handleMealPlanDocument(snapshot.documents.firstOrNull(), "SERVER")
                if (!loaded) {
                    progressBar.visibility = View.GONE
                    showEmpty()
                }
            }
            .addOnFailureListener { e ->
                GalifitFlowLog.warn("ShoppingList: lectura mealPlan SERVER falló: ${e.message}")
                progressBar.visibility = View.GONE
                showEmpty()
            }
    }

    private fun handleMealPlanDocument(doc: DocumentSnapshot?, sourceLabel: String): Boolean {
        val plan = doc?.toObject(FirebaseSavedMealPlan::class.java)
        if (plan == null || plan.days.isEmpty()) {
            GalifitFlowLog.warn("ShoppingList: $sourceLabel sin plan válido docId=${doc?.id}")
            return false
        }

        val recipeUris = extractRecipeUris(plan)
        GalifitFlowLog.firestore(
            "ShoppingList: $sourceLabel docId=${doc.id} días=${plan.days.size} recetas=${recipeUris.size}"
        )
        if (recipeUris.isEmpty()) {
            GalifitFlowLog.warn("ShoppingList: plan sin URIs de recetas")
            return false
        }

        cachedRecipeUris = recipeUris
        loadShoppingListForWeek(recipeUris)
        return true
    }

    private fun extractRecipeUris(plan: FirebaseSavedMealPlan): List<String> {
        return plan.days.flatMap { day ->
            day.meals.mapNotNull { meal ->
                meal.assignedRecipeUri?.trim()?.takeIf { it.isNotBlank() }
                    ?: recipeUriFromReferenceUrl(meal.recipeReferenceUrl)
            }
        }
    }

    private fun recipeUriFromReferenceUrl(referenceUrl: String?): String? {
        val clean = referenceUrl?.trim()?.takeIf { it.isNotBlank() } ?: return null
        val recipeId = clean.substringAfterLast("/").substringBefore("?").trim()
        if (recipeId.isBlank()) return null
        return "http://www.edamam.com/ontologies/edamam.owl#recipe_$recipeId"
    }

    private fun loadShoppingListForWeek(recipeUris: List<String>) {
        val uid = Firebase.auth.currentUser?.uid ?: return
        val weekKey = getWeekKey(currentWeekOffset)

        progressBar.visibility = View.VISIBLE
        rvIngredients.visibility = View.GONE
        tvEmptyState.visibility = View.GONE

        GalifitFlowLog.firestore("ShoppingList: lectura lista $weekKey desde CACHE")
        Firebase.firestore.collection("users")
            .document(uid)
            .collection("shoppingLists")
            .document(weekKey)
            .get(Source.CACHE)
            .addOnSuccessListener { doc ->
                if (showCachedShoppingListIfFresh(doc, recipeUris)) {
                    return@addOnSuccessListener
                }
                fetchFromEdamamApi(recipeUris)
            }
            .addOnFailureListener { e ->
                GalifitFlowLog.warn("ShoppingList: lista CACHE falló: ${e.message}; pidiendo a Edamam")
                fetchFromEdamamApi(recipeUris)
            }
    }

    private fun showCachedShoppingListIfFresh(doc: DocumentSnapshot, recipeUris: List<String>): Boolean {
        if (!doc.exists() || doc.get("ingredients") == null) {
            GalifitFlowLog.firestore("ShoppingList: cache MISS lista compra")
            return false
        }

        val expectedSignature = recipeSignature(recipeUris)
        val cachedSignature = doc.getString("recipeSignature")
        if (cachedSignature != expectedSignature) {
            GalifitFlowLog.firestore(
                "ShoppingList: cache stale lista compra (cached=$cachedSignature expected=$expectedSignature)"
            )
            return false
        }

        @Suppress("UNCHECKED_CAST")
        val ingredientsList = doc.get("ingredients") as? List<Map<String, Any>>
        val checkedIds = (doc.get("checkedFoodIds") as? List<String>)?.toSet() ?: emptySet()

        if (ingredientsList.isNullOrEmpty()) {
            GalifitFlowLog.firestore("ShoppingList: cache sin ingredientes")
            return false
        }

        val items = ingredientsList.map { map ->
            ShoppingItem(
                foodId = map["foodId"] as? String ?: "",
                name = map["food"] as? String ?: "",
                quantityText = map["quantityText"] as? String ?: "",
                checked = (map["foodId"] as? String ?: "") in checkedIds
            )
        }
        GalifitFlowLog.firestore("ShoppingList: cache HIT lista compra ingredientes=${items.size}")
        showIngredients(items)
        return true
    }

    private fun fetchFromEdamamApi(recipeUris: List<String>) {
        val uid = Firebase.auth.currentUser?.uid ?: return

        val entries = recipeUris.map { uri ->
            ShoppingListEntry(
                quantity = 1,
                measure = "http://www.edamam.com/ontologies/edamam.owl#Measure_serving",
                item = uri
            )
        }
        val requestBody = ShoppingListRequest(entries = entries)

        val service = ApiClient().getEdamamMealPlanner().create(APIService::class.java)
        val appId = EdamamCredentials.appId
        val appKey = EdamamCredentials.appKey
        val edamamAccountUser = EdamamCredentials.accountUser

        lifecycleScope.launch {
            try {
                GalifitFlowLog.api("ShoppingList: POST shopping-list/v2 recetas=${entries.size}")
                val response = withTimeoutOrNull(25_000L) {
                    withContext(Dispatchers.IO) {
                        service.getShoppingList(edamamAccountUser, appId, appKey, requestBody)
                    }
                }
                if (response == null) {
                    GalifitFlowLog.warn("ShoppingList: timeout llamando a Edamam shopping-list/v2")
                    progressBar.visibility = View.GONE
                    showEmpty()
                    return@launch
                }

                if (response.isSuccessful) {
                    val body = response.body()
                    if (body != null && body.entries.isNotEmpty()) {
                        val validEntries = body.entries.filter { it.food.isNotBlank() }
                        val translatedFoods = TranslationService.translate(
                            applicationContext,
                            validEntries.map { it.food.trim() }
                        )
                        val items = validEntries
                            .mapIndexed { idx, ingredient ->
                                ShoppingItem(
                                    foodId = ingredient.foodId,
                                    name = translatedFoods[idx].trim().replaceFirstChar { it.uppercase() },
                                    quantityText = ingredient.toSingleLineQuantity()
                                )
                            }.sortedBy { it.name }

                        GalifitFlowLog.api("ShoppingList: Edamam OK ingredientes=${items.size}")
                        cacheShoppingList(uid, items, recipeUris)
                        showIngredients(items)
                    } else {
                        GalifitFlowLog.warn("ShoppingList: Edamam devolvió lista vacía")
                        progressBar.visibility = View.GONE
                        showEmpty()
                    }
                } else {
                    val errorBody = response.errorBody()?.string()
                    GalifitFlowLog.warn("ShoppingList: API error ${response.code()} body=${errorBody?.take(500)}")
                    android.util.Log.e("SHOPPING", "API error ${response.code()}: $errorBody")
                    progressBar.visibility = View.GONE
                    showEmpty()
                }
            } catch (e: Exception) {
                GalifitFlowLog.warn("ShoppingList: API call failed: ${e.message}")
                android.util.Log.e("SHOPPING", "API call failed", e)
                progressBar.visibility = View.GONE
                showEmpty()
            }
        }
    }

    private fun showIngredients(items: List<ShoppingItem>) {
        progressBar.visibility = View.GONE
        tvEmptyState.visibility = View.GONE
        rvIngredients.visibility = View.VISIBLE

        val uid = Firebase.auth.currentUser?.uid

        adapter = ShoppingListAdapter(items.toMutableList()) { checkedIds ->
            btnMarkAll.text = if (adapter?.isAllChecked() == true) "Desmarcar todo" else "Marcar todo"
            if (uid != null) saveCheckedState(uid, checkedIds)
        }
        rvIngredients.adapter = adapter
        btnMarkAll.text = if (adapter?.isAllChecked() == true) "Desmarcar todo" else "Marcar todo"
    }

    private fun showEmpty() {
        progressBar.visibility = View.GONE
        tvEmptyState.visibility = View.VISIBLE
        rvIngredients.visibility = View.GONE
    }

    private fun cacheShoppingList(uid: String, items: List<ShoppingItem>, recipeUris: List<String>) {
        val weekKey = getWeekKey(currentWeekOffset)
        val ingredientsMaps = items.map { item ->
            hashMapOf(
                "foodId" to item.foodId,
                "food" to item.name,
                "quantityText" to item.quantityText
            )
        }
        val data = hashMapOf(
            "ingredients" to ingredientsMaps,
            "checkedFoodIds" to emptyList<String>(),
            "recipeSignature" to recipeSignature(recipeUris),
            "updatedAtMillis" to System.currentTimeMillis()
        )

        Firebase.firestore.collection("users")
            .document(uid)
            .collection("shoppingLists")
            .document(weekKey)
            .set(data)
    }

    private fun recipeSignature(recipeUris: List<String>): String =
        recipeUris.map { it.trim() }.filter { it.isNotEmpty() }.sorted().joinToString("|")

    private fun saveCheckedState(uid: String, checkedIds: Set<String>) {
        val weekKey = getWeekKey(currentWeekOffset)

        Firebase.firestore.collection("users")
            .document(uid)
            .collection("shoppingLists")
            .document(weekKey)
            .update(
                "checkedFoodIds", checkedIds.toList(),
                "updatedAtMillis", System.currentTimeMillis()
            )
    }
}
