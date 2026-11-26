package com.roinur.caffeinetracker.app

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import com.roinur.caffeinetracker.core.ui.components.warmDrinkThumbnails
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val vm: TrackerViewModel by viewModels()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Configuration changes retain the ViewModel/database; do not delete its media DB again.
        val media = com.roinur.caffeinetracker.core.diagnostics.GitHubMediaSession
        if(intent.component?.className != media.LAUNCHER_COMPONENT_CLASS) media.deactivate()
        else if(savedInstanceState == null || !media.active) media.activateIfRequested(applicationContext, intent)
        configureWindow()
        lifecycleScope.launch(Dispatchers.Default) { warmDrinkThumbnails(applicationContext.resources) }
        setContent { CaffeineTrackerApp(vm) }
        if (savedInstanceState == null) receive(intent)
    }
    private fun configureWindow() {
        window.attributes = window.attributes.apply { preferredRefreshRate = 120f }
        WindowCompat.setDecorFitsSystemWindows(window, false)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }
    }
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if(intent.component?.className != this.intent.component?.className) {
            // A widget may open the normal app while an isolated media activity is on top.
            // Recreate its ViewModel with the correct database rather than reusing that session.
            startActivity(Intent(intent).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
            return
        }
        setIntent(intent)
        receive(intent)
    }
    @Suppress("DEPRECATION")
    private fun receive(intent: Intent) {
        val uri = when (intent.action) {
            Intent.ACTION_SEND -> intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
            Intent.ACTION_VIEW -> intent.data
            else -> null
        }
        if (uri?.scheme == "content") vm.readImport(uri)
    }
}
