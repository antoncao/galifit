package com.tfm.galifit

import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AuthActivityInstrumentedTest {

    @Before
    fun signOut() {
        Firebase.auth.signOut()
    }

    @Test
    fun authScreen_showsLoginFormControls() {
        ActivityScenario.launch(AuthActivity::class.java).use {
            onView(withId(R.id.username)).check(matches(isDisplayed()))
            onView(withId(R.id.password)).check(matches(isDisplayed()))
            onView(withText("Iniciar sesión")).check(matches(isDisplayed()))
        }
    }
}
