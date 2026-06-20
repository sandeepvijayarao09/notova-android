package com.notova.app.ui.integrations

import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import com.notova.app.ui.RealMainDispatcherRule
import com.notova.app.ui.waitForState
import com.notova.integrations.api.NotovaBackendApi
import com.notova.integrations.provider.IntegrationsRepository
import com.notova.integrations.provider.OAuthRedirect
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import retrofit2.Retrofit

/**
 * Coverage for [IntegrationsViewModel] against a [MockWebServer]: list, connect (surfacing the
 * authorize URL for the Custom Tab), the dev-safe not-configured error, disconnect, and the
 * deep-link redirect path (which re-lists and updates state).
 *
 * Uses a REAL Main dispatcher + StateFlow polling because the suspend Retrofit calls resume on
 * OkHttp's background threads (virtual-time advancement can't await them).
 */
class IntegrationsViewModelTest {
    @get:Rule
    val mainDispatcherRule = RealMainDispatcherRule()

    private lateinit var server: MockWebServer
    private lateinit var repository: IntegrationsRepository
    private lateinit var bus: OAuthRedirectBus

    private val json =
        Json {
            ignoreUnknownKeys = true
            explicitNulls = false
        }

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        val api =
            Retrofit.Builder()
                .baseUrl(server.url("/"))
                .client(OkHttpClient.Builder().build())
                .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
                .build()
                .create(NotovaBackendApi::class.java)
        repository = IntegrationsRepository(api)
        bus = OAuthRedirectBus()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun enqueue(
        code: Int,
        body: String,
    ) = server.enqueue(MockResponse().setResponseCode(code).setBody(body))

    private fun viewModel() = IntegrationsViewModel(repository, bus)

    @Test
    fun `init lists providers with connected status`() {
        enqueue(200, """[{"provider":"notion","connected":true},{"provider":"todoist","connected":false}]""")

        val vm = viewModel()
        val state = waitForState(supplier = { vm.uiState.value }) { !it.loading && it.providers.isNotEmpty() }

        assertEquals(2, state.providers.size)
        assertEquals("notion", state.providers[0].provider)
        assertTrue(state.providers[0].connected)
    }

    @Test
    fun `connect surfaces the authorize url for the custom tab`() {
        enqueue(200, """[{"provider":"notion","connected":false}]""")
        enqueue(200, """{"authorizeUrl":"https://notion.so/oauth?x=1","state":"abc"}""")

        val vm = viewModel()
        waitForState(supplier = { vm.uiState.value }) { !it.loading && it.providers.isNotEmpty() }

        vm.connect("notion")
        val state = waitForState(supplier = { vm.uiState.value }) { it.pendingAuthorize != null }

        assertEquals("notion", state.pendingAuthorize?.provider)
        assertEquals("https://notion.so/oauth?x=1", state.pendingAuthorize?.authorizeUrl)
        assertEquals("abc", state.pendingAuthorize?.state)

        vm.onAuthorizeLaunched()
        assertEquals(null, vm.uiState.value.pendingAuthorize)
    }

    @Test
    fun `connect surfaces the dev-safe not-configured message`() {
        enqueue(200, """[{"provider":"slack","connected":false}]""")
        enqueue(501, """{"error":{"code":"provider_not_configured","message":"slack not configured"}}""")

        val vm = viewModel()
        waitForState(supplier = { vm.uiState.value }) { !it.loading && it.providers.isNotEmpty() }

        vm.connect("slack")
        val state = waitForState(supplier = { vm.uiState.value }) { it.message != null && it.busyProvider == null }

        assertEquals(null, state.pendingAuthorize)
        assertTrue(state.message?.contains("configured", ignoreCase = true) == true)
    }

    @Test
    fun `disconnect calls DELETE then re-lists`() {
        enqueue(200, """[{"provider":"notion","connected":true}]""")
        enqueue(200, """{"disconnected":true}""")
        enqueue(200, """[{"provider":"notion","connected":false}]""")

        val vm = viewModel()
        waitForState(supplier = { vm.uiState.value }) { !it.loading && it.providers.isNotEmpty() }

        vm.disconnect("notion")
        val state =
            waitForState(supplier = { vm.uiState.value }) {
                it.providers.firstOrNull()?.connected == false && it.busyProvider == null
            }

        assertEquals(false, state.providers.first().connected)
        assertTrue(state.message?.contains("Disconnected") == true)
    }

    @Test
    fun `a connected deep-link redirect re-lists and reports connected`() {
        enqueue(200, """[{"provider":"notion","connected":false}]""") // init list
        enqueue(200, """[{"provider":"notion","connected":true}]""") // re-list after redirect

        val vm = viewModel()
        waitForState(supplier = { vm.uiState.value }) { !it.loading && it.providers.isNotEmpty() }

        vm.onOAuthRedirect(OAuthRedirect.parse("notova://oauth/notion?status=connected")!!)
        val state =
            waitForState(supplier = { vm.uiState.value }) { it.providers.firstOrNull()?.connected == true }

        assertTrue(state.providers.first().connected)
        assertTrue(state.message?.contains("Connected notion") == true)
    }

    @Test
    fun `an error deep-link redirect surfaces the error and does not require a re-list`() {
        enqueue(200, """[{"provider":"slack","connected":false}]""") // init list only

        val vm = viewModel()
        waitForState(supplier = { vm.uiState.value }) { !it.loading && it.providers.isNotEmpty() }

        vm.onOAuthRedirect(OAuthRedirect.parse("notova://oauth/slack?status=error&error=access_denied")!!)
        val state = waitForState(supplier = { vm.uiState.value }) { it.message?.contains("Couldn't") == true }

        assertTrue(state.message?.contains("Couldn't connect slack") == true)
        assertTrue(state.message?.contains("access_denied") == true)
    }

    @Test
    fun `a redirect arriving via the bus is handled automatically`() {
        enqueue(200, """[{"provider":"notion","connected":false}]""") // init list
        enqueue(200, """[{"provider":"notion","connected":true}]""") // re-list after bus redirect

        val vm = viewModel()
        waitForState(supplier = { vm.uiState.value }) { !it.loading && it.providers.isNotEmpty() }

        bus.emit(OAuthRedirect.parse("notova://oauth/notion?status=connected")!!)
        val state =
            waitForState(supplier = { vm.uiState.value }) { it.providers.firstOrNull()?.connected == true }

        assertTrue(state.providers.first().connected)
    }
}
