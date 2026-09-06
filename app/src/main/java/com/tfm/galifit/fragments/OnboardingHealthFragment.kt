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

class OnboardingHealthFragment : Fragment() {

    private var conditionsContainer: LinearLayout? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.onboarding_health, container, false)
        conditionsContainer = view.findViewById(R.id.conditionsContainer)

        EdamamHealthCatalog.conditions.forEachIndexed { index, condition ->
            conditionsContainer?.addView(
                buildCheckBox(condition.displayLabel(), index, condition.key)
            )
        }

        return view
    }

    private fun buildCheckBox(text: String, index: Int, tagValue: String): CheckBox =
        CheckBox(requireContext()).apply {
            this.text = text
            tag = tagValue
            textSize = 16f
            setPadding(8, 10, 8, 10)
        }

    fun getSelectedConditionKeys(): List<String> =
        extractChecked(conditionsContainer)

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

    fun setSelections(conditionKeys: List<String>) {
        applySelection(conditionsContainer, conditionKeys.toSet())
    }

    private fun applySelection(container: LinearLayout?, selected: Set<String>) {
        container ?: return
        for (i in 0 until container.childCount) {
            val cb = container.getChildAt(i) as? CheckBox ?: continue
            val tagVal = cb.tag as? String ?: continue
            cb.isChecked = tagVal in selected
        }
    }

    override fun onDestroyView() {
        conditionsContainer = null
        super.onDestroyView()
    }
}
