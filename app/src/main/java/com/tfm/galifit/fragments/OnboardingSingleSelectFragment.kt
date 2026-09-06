package com.tfm.galifit.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.tfm.galifit.R

class OnboardingSingleSelectFragment : Fragment() {

    private var radioGroup: RadioGroup? = null
    private var selectedKey: String? = null
    var onSelectionChanged: (() -> Unit)? = null

    companion object {

        fun newInstance(
            title: String,
            labels: Array<String>,
            keys: Array<String>,
            subtitle: String? = null,
            defaultSelectedKey: String? = null
        ) = OnboardingSingleSelectFragment().apply {
            arguments = Bundle().apply {
                putString("title", title)
                putStringArray("labels", labels)
                putStringArray("keys", keys)
                putString("subtitle", subtitle)
                putString("defaultSelectedKey", defaultSelectedKey)
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.onboarding_single_select, container, false)
        view.findViewById<TextView>(R.id.tvTitle).text = arguments?.getString("title")
        val subtitle = arguments?.getString("subtitle")
        view.findViewById<TextView>(R.id.tvSubtitle).apply {
            if (subtitle.isNullOrBlank()) {
                visibility = View.GONE
            } else {
                visibility = View.VISIBLE
                text = subtitle
            }
        }

        if (selectedKey == null) {
            selectedKey = arguments?.getString("defaultSelectedKey")
        }

        radioGroup = view.findViewById(R.id.radioGroup)
        val labels = arguments?.getStringArray("labels") ?: emptyArray()
        labels.forEachIndexed { idx, label ->
            val rb = RadioButton(requireContext()).apply {
                id = View.generateViewId()
                text = label
                tag = idx
                textSize = 16f
                setPadding(8, 16, 8, 16)
            }
            radioGroup?.addView(rb)
        }

        radioGroup?.setOnCheckedChangeListener { _, _ ->
            selectedKey = readKeyFromRadioGroup()
            onSelectionChanged?.invoke()
        }

        applySelectionToRadioGroup()
        return view
    }

    fun setSelectedKey(key: String?) {
        selectedKey = key
        applySelectionToRadioGroup()
    }

    fun replaceOptions(labels: Array<String>, keys: Array<String>, defaultKey: String? = null) {
        arguments?.putStringArray("labels", labels)
        arguments?.putStringArray("keys", keys)
        if (selectedKey != null && selectedKey !in keys) {
            selectedKey = defaultKey?.takeIf { it in keys } ?: keys.firstOrNull()
        }
        val rg = radioGroup
        if (rg != null) {
            rg.removeAllViews()
            labels.forEachIndexed { idx, label ->
                val rb = RadioButton(requireContext()).apply {
                    id = View.generateViewId()
                    text = label
                    tag = idx
                    textSize = 16f
                    setPadding(8, 16, 8, 16)
                }
                rg.addView(rb)
            }
            rg.setOnCheckedChangeListener { _, _ ->
                selectedKey = readKeyFromRadioGroup()
                onSelectionChanged?.invoke()
            }
            applySelectionToRadioGroup()
        }
    }

    fun getSelectedKey(): String? {
        readKeyFromRadioGroup()?.let { selectedKey = it }
        return selectedKey
    }

    fun getSelectedLabel(): String? {
        val key = getSelectedKey() ?: return null
        val keys = arguments?.getStringArray("keys") ?: return null
        val labels = arguments?.getStringArray("labels") ?: return null
        val idx = keys.indexOf(key)
        return labels.getOrNull(idx)
    }

    private fun readKeyFromRadioGroup(): String? {
        val rg = radioGroup ?: return selectedKey
        val checkedId = rg.checkedRadioButtonId
        if (checkedId == View.NO_ID) return selectedKey
        val rb = rg.findViewById<RadioButton>(checkedId)
        val idx = rb?.tag as? Int ?: return selectedKey
        return arguments?.getStringArray("keys")?.getOrNull(idx)
    }

    private fun applySelectionToRadioGroup() {
        val key = selectedKey ?: return
        val keys = arguments?.getStringArray("keys") ?: return
        val idx = keys.indexOf(key)
        if (idx < 0) return
        val rg = radioGroup ?: return
        for (i in 0 until rg.childCount) {
            val rb = rg.getChildAt(i) as? RadioButton ?: continue
            if (rb.tag == idx) {
                rg.check(rb.id)
                return
            }
        }
    }

    override fun onDestroyView() {
        readKeyFromRadioGroup()
        radioGroup = null
        super.onDestroyView()
    }
}
