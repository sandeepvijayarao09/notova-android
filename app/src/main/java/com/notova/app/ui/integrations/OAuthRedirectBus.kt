package com.notova.app.ui.integrations

import com.notova.integrations.provider.OAuthRedirect
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * App-scoped bus that carries OAuth return deep links from [com.notova.app.MainActivity] (which
 * receives the `notova://oauth/...` intent) to whoever is observing — typically the Integrations
 * screen's ViewModel. A small replay buffer ensures a redirect that arrives before the screen is
 * collecting still gets delivered when it starts.
 */
@Singleton
class OAuthRedirectBus
    @Inject
    constructor() {
        private val _redirects = MutableSharedFlow<OAuthRedirect>(replay = 1, extraBufferCapacity = 1)
        val redirects: SharedFlow<OAuthRedirect> = _redirects.asSharedFlow()

        /** Emits a parsed redirect to observers. */
        fun emit(redirect: OAuthRedirect) {
            _redirects.tryEmit(redirect)
        }
    }
