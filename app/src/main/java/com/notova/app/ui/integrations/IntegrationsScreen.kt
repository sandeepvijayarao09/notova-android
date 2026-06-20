package com.notova.app.ui.integrations

import android.content.Context
import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.notova.design.component.NotovaCard
import com.notova.integrations.provider.IntegrationProvider

/** Test tags for the Integrations screen controls, used by Compose UI tests. */
object IntegrationsScreenTags {
    const val LOADING = "integrations_loading"
    const val EMPTY = "integrations_empty"
    const val LIST = "integrations_list"
    const val MESSAGE = "integrations_message"

    fun row(provider: String): String = "integrations_row_$provider"

    fun connect(provider: String): String = "integrations_connect_$provider"

    fun disconnect(provider: String): String = "integrations_disconnect_$provider"

    fun busy(provider: String): String = "integrations_busy_$provider"
}

/**
 * Integrations destination. Lists providers with connected status; Connect opens the backend's
 * authorize URL in a Chrome Custom Tab; the `notova://oauth/...` return deep link is routed back
 * through the ViewModel to re-list. Disconnect calls DELETE.
 */
@Composable
fun IntegrationsScreen(
    modifier: Modifier = Modifier,
    viewModel: IntegrationsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(state.pendingAuthorize) {
        state.pendingAuthorize?.let { pending ->
            openCustomTab(context, pending.authorizeUrl)
            viewModel.onAuthorizeLaunched()
        }
    }

    IntegrationsContent(
        state = state,
        modifier = modifier,
        onConnect = viewModel::connect,
        onDisconnect = viewModel::disconnect,
    )
}

/** Opens [url] in a Chrome Custom Tab. */
internal fun openCustomTab(
    context: Context,
    url: String,
) {
    val intent = CustomTabsIntent.Builder().setShowTitle(true).build()
    intent.launchUrl(context, Uri.parse(url))
}

/** Stateless Integrations content, rendered without a Hilt graph for Robolectric/preview. */
@Composable
fun IntegrationsContent(
    state: IntegrationsUiState = IntegrationsUiState(),
    modifier: Modifier = Modifier,
    onConnect: (String) -> Unit = {},
    onDisconnect: (String) -> Unit = {},
) {
    Column(
        modifier = modifier.fillMaxSize().padding(24.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(text = "Integrations", style = MaterialTheme.typography.titleLarge)
        Text(text = "Connect a provider to export your notes. OAuth is brokered by the Notova backend.")

        state.message?.let { message ->
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.fillMaxWidth().testTag(IntegrationsScreenTags.MESSAGE),
            )
        }

        when {
            state.loading ->
                Text(
                    text = "Loading integrations…",
                    modifier = Modifier.testTag(IntegrationsScreenTags.LOADING),
                )
            state.providers.isEmpty() ->
                Text(
                    text = "No integrations available.",
                    modifier = Modifier.testTag(IntegrationsScreenTags.EMPTY),
                )
            else ->
                Column(
                    modifier = Modifier.fillMaxWidth().testTag(IntegrationsScreenTags.LIST),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    state.providers.forEach { provider ->
                        ProviderRow(
                            provider = provider,
                            busy = state.busyProvider == provider.provider,
                            onConnect = onConnect,
                            onDisconnect = onDisconnect,
                        )
                    }
                }
        }
    }
}

@Composable
private fun ProviderRow(
    provider: IntegrationProvider,
    busy: Boolean,
    onConnect: (String) -> Unit,
    onDisconnect: (String) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().testTag(IntegrationsScreenTags.row(provider.provider)),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        NotovaCard(
            title = provider.provider,
            subtitle = if (provider.connected) "Connected" else "Not connected",
            modifier = Modifier.weight(1f),
        )
        when {
            busy ->
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp).testTag(IntegrationsScreenTags.busy(provider.provider)),
                )
            provider.connected ->
                OutlinedButton(
                    onClick = { onDisconnect(provider.provider) },
                    modifier = Modifier.testTag(IntegrationsScreenTags.disconnect(provider.provider)),
                ) {
                    Text("Disconnect")
                }
            else ->
                Button(
                    onClick = { onConnect(provider.provider) },
                    modifier = Modifier.testTag(IntegrationsScreenTags.connect(provider.provider)),
                ) {
                    Text("Connect")
                }
        }
    }
}
