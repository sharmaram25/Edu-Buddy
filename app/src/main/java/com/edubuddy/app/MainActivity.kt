package com.edubuddy.app

import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.edubuddy.app.data.prefs.Settings
import com.edubuddy.app.data.prefs.ThemeMode
import com.edubuddy.app.ui.feature.lock.LockScreen
import com.edubuddy.app.ui.navigation.AppRoot
import com.edubuddy.app.ui.theme.EduBuddyTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MainActivity : FragmentActivity() {

    private val pendingRoute = MutableStateFlow<String?>(null)
    private val locked = MutableStateFlow(false)
    private var backgroundedAt = 0L
    private var lockChecked = false

    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        super.onCreate(savedInstanceState)
        val container = (application as EduBuddyApp).container

        val settings = container.settings.settings
            .stateIn(lifecycleScope, SharingStarted.Eagerly, null)
        splash.setKeepOnScreenCondition { settings.value == null }

        handleIntent(intent)

        lifecycleScope.launch {
            // Lock on cold start if enabled.
            val s = container.settings.current()
            if (s.appLock && savedInstanceState == null) locked.value = true
            lockChecked = true
        }

        setContent {
            val s: Settings? by settings.collectAsStateWithLifecycle()
            val current = s ?: return@setContent
            val isLocked by locked.collectAsStateWithLifecycle()
            val dark = when (current.themeMode) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }
            DisposableEffect(dark) {
                enableEdgeToEdge(
                    statusBarStyle = if (dark) SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
                    else SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
                    navigationBarStyle = if (dark) SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
                    else SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
                )
                onDispose {}
            }
            EduBuddyTheme(themeMode = current.themeMode, dynamicColor = current.dynamicColor) {
                if (isLocked && current.appLock) {
                    LockScreen(onUnlock = ::authenticate)
                } else {
                    key(current.onboarded) {
                        AppRoot(
                            onboarded = current.onboarded,
                            pendingRoute = pendingRoute.asStateFlow(),
                            consumeRoute = { pendingRoute.value = null },
                        )
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        intent?.getStringExtra(EXTRA_ROUTE)?.let { pendingRoute.value = it }
    }

    override fun onStop() {
        super.onStop()
        backgroundedAt = SystemClock.elapsedRealtime()
    }

    override fun onStart() {
        super.onStart()
        if (!lockChecked || backgroundedAt == 0L) return
        val away = SystemClock.elapsedRealtime() - backgroundedAt
        lifecycleScope.launch {
            val s = (application as EduBuddyApp).container.settings.current()
            // Short grace period so picking a backup file or answering a call doesn't re-lock.
            if (s.appLock && away > LOCK_GRACE_MS) locked.value = true
        }
    }

    private fun authenticate() {
        val bm = BiometricManager.from(this)
        val authenticators = BIOMETRIC_WEAK or DEVICE_CREDENTIAL
        if (bm.canAuthenticate(authenticators) != BiometricManager.BIOMETRIC_SUCCESS) {
            // No screen lock set up on the device: nothing to verify against, so don't trap the user.
            locked.value = false
            return
        }
        val prompt = BiometricPrompt(this, ContextCompat.getMainExecutor(this), object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                locked.value = false
            }
        })
        prompt.authenticate(
            BiometricPrompt.PromptInfo.Builder()
                .setTitle("Unlock EduBuddy")
                .setSubtitle("Confirm it's you")
                .setAllowedAuthenticators(authenticators)
                .build()
        )
    }

    companion object {
        const val EXTRA_ROUTE = "route"
        private const val LOCK_GRACE_MS = 60_000L
    }
}
