package com.tfm.galifit.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.tfm.galifit.R

class OnboardingMultiSelectFragment : Fragment() {

    private var checkboxContainer: LinearLayout? = null

    companion object {

        fun newInstance(
            title: String,
            labels: Array<String>,
            keys: Array<String>,
            subtitle: String? = null,
            exclusiveKey: String? = null
        ) = OnboardingMultiSelectFragment().apply {
            arguments = Bundle().apply {
                putString("title", title)
                putStringArray("labels", labels)
                putStringArray("keys", keys)
                if (subtitle != null) putString("subtitle", subtitle)
                if (exclusiveKey != null) putString("exclusiveKey", exclusiveKey)
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.onboarding_multi_select, container, false)
        view.findViewById<TextView>(R.id.tvTitle).text = arguments?.getString("title")

        val tvSub = view.findViewById<TextView>(R.id.tvSubtitle)
        arguments?.getString("subtitle")?.let {
            tvSub.text = it
            tvSub.visibility = View.VISIBLE
        }

        checkboxContainer = view.findViewById(R.id.checkboxContainer)
        val labels = arguments?.getStringArray("labels") ?: emptyArray()
        val keys = arguments?.getStringArray("keys") ?: emptyArray()
        val exclusiveKey = arguments?.getString("exclusiveKey")
        labels.forEachIndexed { idx, label ->
            val cb = CheckBox(requireContext()).apply {
                text = label
                tag = idx
                textSize = 16f
                setPadding(8, 12, 8, 12)
                setOnCheckedChangeListener { _, isChecked ->
                    if (exclusiveKey == null || !isChecked) return@setOnCheckedChangeListener
                    val container = checkboxContainer ?: return@setOnCheckedChangeListener
                    val exclusiveIdx = keys.indexOf(exclusiveKey)
                    if (exclusiveIdx < 0) return@setOnCheckedChangeListener
                    if (idx == exclusiveIdx) {
                        for (i in 0 until container.childCount) {
                            val other = container.getChildAt(i) as? CheckBox ?: continue
                            if (other.tag != idx) other.isChecked = false
                        }
                    } else {
                        (container.getChildAt(exclusiveIdx) as? CheckBox)?.isChecked = false
                    }
                }
            }
            checkboxContainer?.addView(cb)
        }
        return view
    }

    fun getSelectedKeys(): List<String> {
        val container = checkboxContainer ?: return emptyList()
        val keys = arguments?.getStringArray("keys") ?: return emptyList()
        val exclusiveKey = arguments?.getString("exclusiveKey")
        val result = mutableListOf<String>()
        for (i in 0 until container.childCount) {
            val cb = container.getChildAt(i) as? CheckBox ?: continue
            if (cb.isChecked) {
                val idx = cb.tag as? Int ?: continue
                keys.getOrNull(idx)?.let { result.add(it) }
            }
        }
        if (exclusiveKey != null && exclusiveKey in result) return emptyList()
        return result
    }

    override fun onDestroyView() {
        checkboxContainer = null
        super.onDestroyView()
    }
}
