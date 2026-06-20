package com.notova.integrations.provider

/**
 * Parsed result of the OAuth return deep link `notova://oauth/<provider>?status=<status>[&...]`.
 *
 * The backend redirects the browser/Custom Tab back to this scheme once the provider's OAuth
 * dance finishes. We only trust it as a cue to re-fetch the integrations list from the backend
 * (which holds the authoritative connected state) — never as the source of truth itself.
 */
data class OAuthRedirect(
    val provider: String,
    val status: String,
    val errorMessage: String?,
) {
    /** `true` when the backend reported a successful connection. */
    val isConnected: Boolean get() = status.equals(STATUS_CONNECTED, ignoreCase = true)

    companion object {
        const val SCHEME = "notova"
        const val HOST = "oauth"
        const val STATUS_CONNECTED = "connected"
        private const val PARAM_STATUS = "status"
        private const val PARAM_ERROR = "error"

        /**
         * Parses a `notova://oauth/<provider>?status=...` URI string into an [OAuthRedirect], or
         * `null` if it isn't an OAuth-return link. Implemented as pure string parsing (no
         * `android.net.Uri`) so it is unit-testable on the plain JVM.
         */
        fun parse(uri: String?): OAuthRedirect? {
            if (uri == null) return null
            val prefix = "$SCHEME://$HOST/"
            if (!uri.startsWith(prefix)) return null

            val remainder = uri.removePrefix(prefix)
            val pathPart = remainder.substringBefore('?')
            val provider = pathPart.substringBefore('/').trim().lowercase()
            if (provider.isEmpty()) return null

            val query = remainder.substringAfter('?', missingDelimiterValue = "")
            val params = parseQuery(query)

            return OAuthRedirect(
                provider = provider,
                status = params[PARAM_STATUS].orEmpty(),
                errorMessage = params[PARAM_ERROR],
            )
        }

        private fun parseQuery(query: String): Map<String, String> {
            if (query.isEmpty()) return emptyMap()
            return query.split('&')
                .mapNotNull { pair ->
                    if (pair.isEmpty()) return@mapNotNull null
                    val key = pair.substringBefore('=')
                    val value = pair.substringAfter('=', missingDelimiterValue = "")
                    if (key.isEmpty()) null else key to decode(value)
                }
                .toMap()
        }

        private fun decode(value: String): String =
            runCatching {
                java.net.URLDecoder.decode(value, Charsets.UTF_8.name())
            }.getOrDefault(value)
    }
}
