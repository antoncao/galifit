package com.tfm.galifit.util

import android.util.Base64
import com.tfm.galifit.BuildConfig

object EdamamCredentials {

    val appId: String get() = BuildConfig.EDAMAM_APP_ID.trim()

    val appKey: String get() = BuildConfig.EDAMAM_APP_KEY.trim()

    val accountUser: String get() = BuildConfig.EDAMAM_ACCOUNT_USER.trim()

    fun basicAuthHeader(): String {
        val credentials = "$appId:$appKey"
        return "Basic " + Base64.encodeToString(credentials.toByteArray(), Base64.NO_WRAP)
    }

    fun isConfigured(): Boolean =
        appId.isNotBlank() && appKey.isNotBlank() && accountUser.isNotBlank()
}
