package com.tfm.galifit.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.LinearLayout
import androidx.fragment.app.Fragment
import com.tfm.galifit.R
import com.tfm.galifit.data.model.EdamamHealthCatalog

class OnboardingHealthProfileFragment : Fragment() {

    private var conditionsContainer: LinearLayout? = null
    private var allergiesContainer: LinearLayout? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.onboarding_health_profile, container, false)
        conditionsContainer = view.findViewById(R.id.conditionsContainer)
        allergiesContainer = view.findViewById(R.id.allergiesContainer)

        EdamamHealthCatalog.conditions.forEach { condition ->
            conditionsContainer?.addView(
                buildCheckBox(condition.displayLabel(), condition.key)
            )
        }

        EdamamHealthCatalog.allergyRestrictionLabels.forEach { label ->
            allergiesContainer?.addView(
                buildCheckBox(EdamamHealthCatalog.labelDisplayName(label), label)
            )
        }

        return view
    }

    private fun buildCheckBox(text: String, tagValue: String): CheckBox =
        CheckBox(requireContext()).apply {
            this.text = text
            tag = tagValue
            textSize = 16f
            setPadding(8, 10, 8, 10)
        }

    fun getSelectedConditionKeys(): List<String> =
        extractChecked(conditionsContainer)

    fun getSelectedRestrictionKeys(): List<String> =
        extractChecked(allergiesContainer)

    private fun extractChecked(container: LinearLayout?): List<String> {
        container ?: return emptyList()
        val result = mutableListOf<String>()
        for (i in 0 until container.childCount) {
            val cb = container.getChildAt(i) as? CheckBox ?: continue
            if (cb.isChecked) {
                val tagVal = cb.tag as? String ?: continue
                result.add(tagVal)
            }
        }
        return result
    }

    override fun onDestroyView() {
        conditionsContainer = null
        allergiesContainer = null
        super.onDestroyView()
    }
}
