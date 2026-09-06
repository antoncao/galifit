package com.tfm.galifit

import android.content.Intent
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import androidx.lifecycle.lifecycleScope
import com.tfm.galifit.notifications.DeviceStepTracker
import com.tfm.galifit.notifications.SedentaryNotificationScheduler
import androidx.drawerlayout.widget.DrawerLayout
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.firebase.auth.auth
import com.google.firebase.Firebase
import com.tfm.galifit.util.chat.GeminiChatSession
import kotlinx.coroutines.launch

abstract class GetNavigationBarActivity : AppCompatActivity() {

    private lateinit var drawerLayout: DrawerLayout

    protected open fun showChatFab(): Boolean = false

    protected open fun chatOrigin(): String = ChatAssistantActivity.ORIGIN_HOME

    override fun setContentView(layoutResID: Int) {
        drawerLayout = DrawerLayout(this).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        val contentWrapper = FrameLayout(this).apply {
            layoutParams = DrawerLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }
        val content = LayoutInflater.from(this).inflate(layoutResID, contentWrapper, false)
        contentWrapper.addView(content)
        drawerLayout.addView(contentWrapper)

        val drawerView = LayoutInflater.from(this)
            .inflate(R.layout.layout_nav_drawer, drawerLayout, false)
        val drawerParams = DrawerLayout.LayoutParams(
            resources.getDimensionPixelSize(R.dimen.drawer_width),
            ViewGroup.LayoutParams.MATCH_PARENT
        )
        drawerParams.gravity = GravityCompat.END
        drawerView.layoutParams = drawerParams
        drawerLayout.addView(drawerView)

        super.setContentView(drawerLayout)
        setupDrawer(drawerView)
        findViewById<BottomNavigationView?>(R.id.bottomNavigationView)?.let {
            attachBottomNavigationListener(it)
            configureBottomNavigation(it)
        }
        if (showChatFab()) {
            setupChatFab(contentWrapper)
        }
    }

    protected open fun configureBottomNavigation(nav: BottomNavigationView) {}

    private fun setupChatFab(parent: FrameLayout) {
        val fab = LayoutInflater.from(this)
            .inflate(R.layout.include_chat_fab, parent, false) as FloatingActionButton
        val marginPx = (16 * resources.displayMetrics.density).toInt()
        val bottomMarginPx = (80 * resources.displayMetrics.density).toInt()
        fab.layoutParams = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.END
            setMargins(marginPx, marginPx, marginPx, bottomMarginPx)
        }
        fab.setOnClickListener {
            startActivity(
                Intent(this, ChatAssistantActivity::class.java).apply {
                    putExtra(ChatAssistantActivity.EXTRA_CHAT_ORIGIN, chatOrigin())
                }
            )
        }
        parent.addView(fab)
    }

    fun getNavigationView(): BottomNavigationView {
        val bottomNavigationView = findViewById<BottomNavigationView>(R.id.bottomNavigationView)
        attachBottomNavigationListener(bottomNavigationView)
        return bottomNavigationView
    }

    private fun attachBottomNavigationListener(bottomNavigationView: BottomNavigationView) {
        bottomNavigationView.setOnNavigationItemSelectedListener { item ->
            when (item.itemId) {
                R.id.navigation_inicio -> {
                    if (this !is MainActivity) {
                        startActivity(Intent(this, MainActivity::class.java))
                    }
                }
                R.id.navigation_platos -> {
                    if (this !is MealPlanActivity) {
                        startActivity(Intent(this, MealPlanActivity::class.java))
                    }
                }
                R.id.navigation_ejercicios -> {
                    if (this !is ExercisePlanActivity) {
                        startActivity(Intent(this, ExercisePlanActivity::class.java))
                    }
                }
                R.id.navigation_menu -> {
                    drawerLayout.openDrawer(GravityCompat.END)
                    return@setOnNavigationItemSelectedListener false
                }
            }
            true
        }
    }

    private fun setupDrawer(drawerView: View) {
        drawerView.findViewById<ImageButton>(R.id.btnCloseDrawer).setOnClickListener {
            drawerLayout.closeDrawer(GravityCompat.END)
        }

        val userEmail = Firebase.auth.currentUser?.email ?: "Usuario"
        drawerView.findViewById<TextView>(R.id.tvDrawerUserName).text = userEmail

        val menuActions = mapOf<Int, () -> Unit>(
            R.id.drawerItemInicio to { navigateFromDrawer(MainActivity::class.java) },
            R.id.drawerItemPlanComidas to { navigateFromDrawer(MealPlanActivity::class.java) },
            R.id.drawerItemEjercicios to { navigateFromDrawer(ExercisePlanActivity::class.java) },
            R.id.drawerItemListaCompra to { navigateFromDrawer(ShoppingListActivity::class.java) },
            R.id.drawerItemAnalisisImagen to { navigateFromDrawer(ImageAnalysisActivity::class.java) },
            R.id.drawerItemPreferencias to { navigateFromDrawer(PreferencesActivity::class.java) }
        )

        for ((viewId, action) in menuActions) {
            drawerView.findViewById<TextView>(viewId).setOnClickListener {
                drawerLayout.closeDrawer(GravityCompat.END)
                action()
            }
        }

        drawerView.findViewById<TextView>(R.id.drawerItemCerrarSesion).setOnClickListener {
            drawerLayout.closeDrawer(GravityCompat.END)
            GeminiChatSession.clear()
            Firebase.auth.signOut()
            startActivity(
                Intent(this, AuthActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                }
            )
            finish()
        }
    }

    override fun onResume() {
        super.onResume()
        if (Firebase.auth.currentUser == null) {
            startActivity(
                Intent(this, AuthActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                }
            )
            finish()
            return
        }
        lifecycleScope.launch {
            SedentaryNotificationScheduler.ensureWorkerFromLocalConfig(applicationContext)
            DeviceStepTracker.sampleIfDeviceSensorEnabled(applicationContext)
        }
    }

    private fun navigateFromDrawer(target: Class<out AppCompatActivity>) {
        if (this::class.java != target) {
            startActivity(Intent(this, target))
        }
    }

    override fun onBackPressed() {
        if (::drawerLayout.isInitialized && drawerLayout.isDrawerOpen(GravityCompat.END)) {
            drawerLayout.closeDrawer(GravityCompat.END)
        } else {
            super.onBackPressed()
        }
    }
}
