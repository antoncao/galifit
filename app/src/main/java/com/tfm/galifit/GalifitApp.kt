package com.tfm.galifit

import android.app.Application
import com.tfm.galifit.util.ai.GeminiModelProvider

class GalifitApp : Application() {

    override fun onCreate() {
        super.onCreate()
        GeminiModelProvider.initialize(this)
    }
}
