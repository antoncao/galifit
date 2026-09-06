package com.tfm.galifit.util

import android.util.Log

object GalifitFlowLog {

    const val TAG = "GALIFIT_FLOW"

    fun api(message: String) {
        Log.i(TAG, "[API] $message")
    }

    fun disk(message: String) {
        Log.i(TAG, "[DISCO] $message")
    }

    fun firestore(message: String) {
        Log.i(TAG, "[FIRESTORE] $message")
    }

    fun ui(message: String) {
        Log.i(TAG, "[UI] $message")
    }

    fun warn(message: String) {
        Log.w(TAG, message)
    }

    fun debug(message: String) {
        Log.d(TAG, message)
    }

    fun sedentary(message: String) {
        Log.i(TAG, "[SEDENTARISMO] $message")
    }
}
