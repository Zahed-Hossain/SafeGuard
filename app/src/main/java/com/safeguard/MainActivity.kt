package com.safeguard

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.safeguard.ui.navigation.SafeGuardNavHost
import com.safeguard.ui.theme.SafeGuardTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val app = application as SafeGuardApplication

        setContent {
            val isOnboardingCompleted by app.preferencesRepository.isOnboardingCompleted
                .collectAsStateWithLifecycle(initialValue = false)

            SafeGuardTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    SafeGuardNavHost(
                        isOnboardingCompleted = isOnboardingCompleted
                    )
                }
            }
        }
    }
}
