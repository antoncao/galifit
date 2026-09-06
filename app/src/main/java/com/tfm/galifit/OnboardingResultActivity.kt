package com.tfm.galifit

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.firestore
import com.tfm.galifit.data.model.UserPreferences
import com.tfm.galifit.util.GalifitFlowLog
import com.tfm.galifit.util.init.InitialPlanService
import com.tfm.galifit.util.toUserPreferences
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class OnboardingResultActivity : AppCompatActivity() {

    private lateinit var startButton: Button
    private lateinit var overlay: View
    private lateinit var statusText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.onboarding_result)

        startButton = findViewById(R.id.onboardingResultStartButton)
        overlay = findViewById(R.id.onboardingGenerationOverlay)
        statusText = findViewById(R.id.onboardingGenerationStatus)

        startButton.setOnClickListener { generateInitialPlansAndContinue() }
    }

    private fun generateInitialPlansAndContinue() {
        startButton.isEnabled = false
        overlay.visibility = View.VISIBLE
        statusText.text = getString(R.string.onboarding_generation_loading_prefs)

        lifecycleScope.launch {
            val prefs = runCatching { loadPreferences() }.getOrNull()
            if (prefs == null) {
                Toast.makeText(
                    this@OnboardingResultActivity,
                    R.string.onboarding_generation_error_prefs,
                    Toast.LENGTH_LONG
                ).show()
                goToMain()
                return@launch
            }

            statusText.text = getString(R.string.onboarding_generation_running)

            val results = coroutineScope {
                listOf(
                    async { InitialPlanService.generateMealPlan(applicationContext, prefs) },
                    async { InitialPlanService.generateExercisePlan(applicationContext, prefs) }
                ).awaitAll()
            }

            val mealOk = results[0].isSuccess
            val exerciseOk = results[1].isSuccess
            GalifitFlowLog.api(
                "InitialPlan terminado mealOk=$mealOk exerciseOk=$exerciseOk"
            )

            val message = when {
                mealOk && exerciseOk -> getString(R.string.onboarding_generation_success)
                mealOk -> getString(R.string.onboarding_generation_partial_meal)
                exerciseOk -> getString(R.string.onboarding_generation_partial_exercise)
                else -> getString(R.string.onboarding_generation_failure)
            }
            Toast.makeText(this@OnboardingResultActivity, message, Toast.LENGTH_LONG).show()
            goToMain()
        }
    }

    private suspend fun loadPreferences(): UserPreferences? {
        val uid = Firebase.auth.currentUser?.uid ?: return null
        val doc = Firebase.firestore.collection("users").document(uid).get().await()
        return doc.toUserPreferences()
    }

    private fun goToMain() {
        startActivity(
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
        )
        finish()
    }
}
