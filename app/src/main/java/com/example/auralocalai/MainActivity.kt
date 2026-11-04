package com.example.auralocalai

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.example.auralocalai.theme.AuraLocalAITheme
import com.example.auralocalai.ui.LlmViewModel

class MainActivity : FragmentActivity() {
  private val llmViewModel: LlmViewModel by viewModels()

  /** False while the app lock is on and the user has not unlocked yet. */
  private val unlocked = mutableStateOf(true)
  /** Moment the app went to the background (elapsed realtime), 0 while visible. */
  private var stoppedAt = 0L
  private var authenticating = false

  override fun attachBaseContext(newBase: android.content.Context) {
    super.attachBaseContext(AppLocale.wrap(newBase))
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 101)
    }

    val openBriefing = intent?.getBooleanExtra(EXTRA_OPEN_BRIEFING, false) == true
    val briefingProfileId = intent?.getStringExtra(EXTRA_BRIEFING_PROFILE)
    val sharedText = if (intent?.action == Intent.ACTION_SEND && intent?.type?.startsWith("text/") == true) {
      intent?.getStringExtra(Intent.EXTRA_TEXT)
    } else null
    val sharedImage = if (intent?.action == Intent.ACTION_SEND && intent?.type?.startsWith("image/") == true) {
      @Suppress("DEPRECATION")
      intent?.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
    } else null

    unlocked.value = !AppSecurity.isLockEnabled(this)

    enableEdgeToEdge()
    setContent {
      val themeMode by llmViewModel.themeMode.collectAsState()
      val isUnlocked by unlocked
      AuraLocalAITheme(themeMode = themeMode) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
          // The app stays composed under the lock screen, so open screens, picked files and
          // half-typed text are kept while the user unlocks.
          androidx.compose.foundation.layout.Box(modifier = Modifier.fillMaxSize()) {
            MainNavigation(
              llmViewModel,
              openBriefing = openBriefing,
              briefingProfileId = briefingProfileId,
              sharedText = sharedText,
              sharedImage = sharedImage
            )
            if (!isUnlocked) {
              androidx.activity.compose.BackHandler { moveTaskToBack(true) }
              Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                Column(
                  modifier = Modifier.fillMaxSize().padding(32.dp),
                  verticalArrangement = Arrangement.Center,
                  horizontalAlignment = Alignment.CenterHorizontally
                ) {
                  Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.primary)
                  Spacer(modifier = Modifier.height(16.dp))
                  Text(stringResource(R.string.lock_title), style = MaterialTheme.typography.headlineSmall)
                  Spacer(modifier = Modifier.height(24.dp))
                  Button(onClick = { authenticate() }) { Text(stringResource(R.string.lock_unlock)) }
                }
              }
            }
          }
        }
      }
    }
  }

  override fun onResume() {
    super.onResume()
    if (!unlocked.value) authenticate()
  }

  override fun onStart() {
    super.onStart()
    // Lock again only after the app was really away for a while. Picking a file, taking a photo
    // or the system speech dialog also send the app to the background for a moment.
    val awayMs = if (stoppedAt > 0L) android.os.SystemClock.elapsedRealtime() - stoppedAt else 0L
    if (AppSecurity.isLockEnabled(this) && stoppedAt > 0L && awayMs > LOCK_GRACE_MS) unlocked.value = false
    stoppedAt = 0L
  }

  override fun onStop() {
    super.onStop()
    if (!isChangingConfigurations) stoppedAt = android.os.SystemClock.elapsedRealtime()
  }

  private fun authenticate() {
    if (authenticating) return
    val authenticators = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
      BiometricManager.Authenticators.BIOMETRIC_WEAK or BiometricManager.Authenticators.DEVICE_CREDENTIAL
    } else {
      BiometricManager.Authenticators.BIOMETRIC_WEAK
    }
    if (BiometricManager.from(this).canAuthenticate(authenticators) != BiometricManager.BIOMETRIC_SUCCESS) {
      // No fingerprint or screen lock set up: do not lock the user out.
      unlocked.value = true
      return
    }
    authenticating = true
    val prompt = BiometricPrompt(this, ContextCompat.getMainExecutor(this), object : BiometricPrompt.AuthenticationCallback() {
      override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
        authenticating = false
        unlocked.value = true
      }
      override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
        authenticating = false
      }
      override fun onAuthenticationFailed() {}
    })
    val info = BiometricPrompt.PromptInfo.Builder()
      .setTitle(getString(R.string.lock_title))
      .apply {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
          setAllowedAuthenticators(authenticators)
        } else {
          setNegativeButtonText(getString(R.string.cancel))
        }
      }
      .build()
    prompt.authenticate(info)
  }

  companion object {
    /** Set by the daily-briefing notification to open the briefing screen. */
    const val EXTRA_OPEN_BRIEFING = "open_briefing"
    const val EXTRA_BRIEFING_PROFILE = "briefing_profile"
    /** The app locks again only after being away longer than this. */
    const val LOCK_GRACE_MS = 2L * 60L * 1000L
  }
}
