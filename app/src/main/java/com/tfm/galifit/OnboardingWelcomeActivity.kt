package com.tfm.galifit

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity

class OnboardingWelcomeActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.onboarding_welcome)

        findViewById<Button>(R.id.btnStartOnboarding).setOnClickListener {
            startActivity(Intent(this, OnboardingActivity::class.java))
        }
    }
}
