package com.tfm.galifit.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.RadioGroup
import androidx.fragment.app.Fragment
import com.tfm.galifit.R

class OnboardingBasicDataFragment : Fragment() {

    private var etAge: EditText? = null
    private var etHeight: EditText? = null
    private var etWeight: EditText? = null
    private var rgSex: RadioGroup? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.onboarding_basic_data, container, false)
        etAge = view.findViewById(R.id.etAge)
        etHeight = view.findViewById(R.id.etHeight)
        etWeight = view.findViewById(R.id.etWeight)
        rgSex = view.findViewById(R.id.rgSex)
        return view
    }

    fun getAge(): Int? = etAge?.text?.toString()?.toIntOrNull()

    fun getSex(): String? = when (rgSex?.checkedRadioButtonId) {
        R.id.rbMale -> "Hombre"
        R.id.rbFemale -> "Mujer"
        else -> null
    }

    fun getHeightCm(): Float? = etHeight?.text?.toString()?.toFloatOrNull()

    fun getWeightKg(): Float? = etWeight?.text?.toString()?.toFloatOrNull()

    fun isValid(): Boolean =
        getAge() != null && getSex() != null && getHeightCm() != null && getWeightKg() != null

    override fun onDestroyView() {
        etAge = null; etHeight = null; etWeight = null; rgSex = null
        super.onDestroyView()
    }
}
