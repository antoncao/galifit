package com.tfm.galifit

import android.os.Bundle
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class HealthConnectPermissionsRationaleActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = getString(R.string.health_connect_privacy_title)

        val textView = TextView(this).apply {
            setPadding(48, 32, 48, 32)
            text = getString(R.string.health_connect_privacy_rationale)
            textSize = 16f
        }
        setContentView(ScrollView(this).apply { addView(textView) })

        supportActionBar?.setDisplayHomeAsUpEnabled(true)
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}
