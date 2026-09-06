package com.tfm.galifit.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.LinearLayout
import androidx.fragment.app.Fragment
import com.tfm.galifit.R

class OnboardingFoodPrefsFragment : Fragment() {

    private var cuisineContainer: LinearLayout? = null
    private var avoidContainer: LinearLayout? = null

    companion object {

        private val CUISINE_LABELS = arrayOf(
            "Americana", "Asiática", "Británica", "Caribeña", "Centroeuropea",
            "China", "Este de Europa", "Francesa", "Griega", "India",
            "Italiana", "Japonesa", "Coreana", "Kosher", "Mediterránea", "Mexicana",
            "Oriente Medio", "Nórdica", "Sudamericana", "Sudeste Asiático", "Internacional"
        )

        private val CUISINE_KEYS = arrayOf(
            "american", "asian", "british", "caribbean", "central europe",
            "chinese", "eastern europe", "french", "greek", "indian",
            "italian", "japanese", "korean", "kosher", "mediterranean", "mexican",
            "middle eastern", "nordic", "south american", "south east asian", "world"
        )

        private val AVOID_LABELS = arrayOf(
            "Sin cerdo", "Sin carne roja", "Sin pescado",
            "Sin crustáceos", "Sin moluscos", "Sin mostaza",
            "Sin sésamo", "Sin altramuz", "Sin sulfitos"
        )

        private val AVOID_KEYS = arrayOf(
            "PORK_FREE", "RED_MEAT_FREE", "FISH_FREE",
            "CRUSTACEAN_FREE", "MOLLUSK_FREE", "MUSTARD_FREE",
            "SESAME_FREE", "LUPINE_FREE", "SULFITE_FREE"
        )
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.onboarding_food_prefs, container, false)
        cuisineContainer = view.findViewById(R.id.cuisineContainer)
        avoidContainer = view.findViewById(R.id.avoidContainer)

        CUISINE_LABELS.forEachIndexed { idx, label ->
            cuisineContainer?.addView(buildCheckBox(label, idx))
        }
        AVOID_LABELS.forEachIndexed { idx, label ->
            avoidContainer?.addView(buildCheckBox(label, idx))
        }
        return view
    }

    private fun buildCheckBox(label: String, index: Int): CheckBox =
        CheckBox(requireContext()).apply {
            text = label
            tag = index
            textSize = 16f
            setPadding(8, 10, 8, 10)
        }

    fun getSelectedCuisineKeys(): List<String> = extractSelected(cuisineContainer, CUISINE_KEYS)

    fun getSelectedAvoidKeys(): List<String> = extractSelected(avoidContainer, AVOID_KEYS)

    private fun extractSelected(container: LinearLayout?, keys: Array<String>): List<String> {
        container ?: return emptyList()
        val result = mutableListOf<String>()
        for (i in 0 until container.childCount) {
            val cb = container.getChildAt(i) as? CheckBox ?: continue
            if (cb.isChecked) {
                val idx = cb.tag as? Int ?: continue
                keys.getOrNull(idx)?.let { result.add(it) }
            }
        }
        return result
    }

    override fun onDestroyView() {
        cuisineContainer = null; avoidContainer = null
        super.onDestroyView()
    }
}
