package com.notova.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.notova.app.ui.NotovaAppRoot
import com.notova.app.ui.integrations.OAuthRedirectBus
import com.notova.design.theme.NotovaTheme
import com.notova.integrations.provider.OAuthRedirect
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject
    lateinit var oAuthRedirectBus: OAuthRedirectBus

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // The launching intent may be the OAuth return deep link (notova://oauth/...).
        handleOAuthRedirect(intent)
        setContent {
            NotovaTheme {
                NotovaAppRoot()
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleOAuthRedirect(intent)
    }

    /** Parses a `notova://oauth/...` deep link off [intent] and forwards it to the redirect bus. */
    private fun handleOAuthRedirect(intent: Intent?) {
        val data = intent?.data?.toString() ?: return
        OAuthRedirect.parse(data)?.let { oAuthRedirectBus.emit(it) }
    }
}
