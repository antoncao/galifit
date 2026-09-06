package com.tfm.galifit

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.auth
import com.google.firebase.firestore.firestore
import com.google.firebase.Firebase
import com.tfm.galifit.util.registerAndRequestStoragePermissionsOnStart

class AuthActivity : AppCompatActivity(){

    private lateinit var auth: FirebaseAuth
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        auth = Firebase.auth

        if (auth.currentUser != null) {
            routeAfterAuth()
            return
        }
        registerAndRequestStoragePermissionsOnStart()
        setContentView(R.layout.activity_auth)
        setup()
    }

    private fun setup(){
        val registerButton = findViewById<TextView>(R.id.register)
        val signInButton = findViewById<Button>(R.id.sign_in_button)
        val username = findViewById<TextInputEditText>(R.id.username)
        val password = findViewById<TextInputEditText>(R.id.password)

        registerButton.setOnClickListener {
            val email = username.text.toString().trim()
            val pass = password.text.toString()
            if (email.isEmpty() || pass.isEmpty()) {
                showAlert("Completa email y contraseña para registrarte")
                return@setOnClickListener
            }
            auth.createUserWithEmailAndPassword(email, pass).addOnCompleteListener {
                if (it.isSuccessful) {
                    routeAfterAuth()
                } else {
                    showAlert(authErrorMessage(it.exception, "No se pudo registrar el usuario"))
                }
            }
        }

        signInButton.setOnClickListener {
            val email = username.text.toString().trim()
            val pass = password.text.toString()
            if (email.isEmpty() || pass.isEmpty()) {
                showAlert("Completa email y contraseña para iniciar sesión")
                return@setOnClickListener
            }
            auth.signInWithEmailAndPassword(email, pass).addOnCompleteListener {
                if (it.isSuccessful) {
                    routeAfterAuth()
                } else {
                    showAlert(
                        authErrorMessage(
                            it.exception,
                            "Se ha producido un error autenticando al usuario"
                        )
                    )
                }
            }
        }
    }

    private fun authErrorMessage(exception: Exception?, fallback: String): String {
        if (exception == null) return fallback

        if (exception is FirebaseAuthException) {
            return when (exception.errorCode) {
                "ERROR_INVALID_CREDENTIAL" -> "Las credenciales de autenticación proporcionadas son incorrectas, están mal formadas o han caducado."
                "ERROR_INVALID_EMAIL" -> "El correo electrónico no es válido."
                "ERROR_WRONG_PASSWORD" -> "La contraseña es incorrecta."
                "ERROR_EMAIL_ALREADY_IN_USE" -> "El correo electrónico introducido ya está en uso."
                else -> exception.localizedMessage ?: fallback
            }
        }

        return "Se ha producido un error autenticando al usuario"
    }

    private fun showAlert(message: String){
        val builder = AlertDialog.Builder(this)
        builder.setTitle("Error")
        builder.setMessage(message)
        builder.setPositiveButton("Aceptar", null)
        val dialog: AlertDialog = builder.create()
        dialog.show()
    }

    private fun routeAfterAuth() {
        val user = auth.currentUser
        val uid = user?.uid
        if (uid.isNullOrBlank()) {
            return
        }

        Firebase.firestore.collection("users").document(uid).get()
            .addOnSuccessListener { doc ->
                val hasPreferences = (doc.get("preferences") as? Map<*, *>) != null
                if (hasPreferences) {
                    goToMainAndClearStack()
                } else {
                    goToWelcomeAndClearStack()
                }
                finish()
            }
            .addOnFailureListener {
                goToMainAndClearStack()
                finish()
            }
    }

    private fun goToMainAndClearStack() {
        startActivity(
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
        )
    }

    private fun goToWelcomeAndClearStack() {
        startActivity(
            Intent(this, OnboardingWelcomeActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
        )
    }
}
