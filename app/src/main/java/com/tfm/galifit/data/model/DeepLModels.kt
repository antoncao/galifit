package com.tfm.galifit.data.model

import com.google.gson.annotations.SerializedName

data class DeepLTranslateRequest(
    @SerializedName("text") val text: List<String>,
    @SerializedName("target_lang") val targetLang: String = "ES",
    @SerializedName("source_lang") val sourceLang: String? = "EN"
)

data class DeepLTranslateResponse(
    @SerializedName("translations") val translations: List<DeepLTranslation> = emptyList()
)

data class DeepLTranslation(
    @SerializedName("detected_source_language") val detectedSourceLanguage: String? = null,
    @SerializedName("text") val text: String = ""
)
