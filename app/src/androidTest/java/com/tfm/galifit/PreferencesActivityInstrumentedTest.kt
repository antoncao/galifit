package com.tfm.galifit

import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.scrollTo
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PreferencesActivityInstrumentedTest {

    @Test
    fun preferencesScreen_showsHealthSectionTitle() {
        assumeTrue(
            "Requiere una sesión de Firebase activa en el dispositivo",
            Firebase.auth.currentUser != null
        )

        ActivityScenario.launch(PreferencesActivity::class.java).use {
            onView(withId(R.id.tvHealthValue)).perform(scrollTo())

            onView(withText("Condiciones de salud (Edamam)"))
                .check(matches(isDisplayed()))
            onView(withId(R.id.tvHealthValue))
                .check(matches(isDisplayed()))
        }
    }
}
