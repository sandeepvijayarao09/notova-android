package com.notova.integrations.di

import com.notova.integrations.api.NotovaBackendApi
import com.notova.integrations.api.UserDto
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.Retrofit

/**
 * Exercises the [IntegrationsModule] `@Provides` functions directly (no Hilt graph needed) to lock
 * in the JSON leniency config and the Retrofit/OkHttp wiring used in production.
 */
class IntegrationsModuleTest {
    @Test
    fun `provideJson ignores unknown keys`() {
        val json = IntegrationsModule.provideJson()
        assertTrue(json.configuration.ignoreUnknownKeys)
    }

    @Test
    fun `provideJson does not serialize explicit nulls`() {
        val json = IntegrationsModule.provideJson()
        assertTrue(!json.configuration.explicitNulls)
    }

    @Test
    fun `provideJson decodes a payload with extra unknown fields`() {
        val json: Json = IntegrationsModule.provideJson()
        val decoded =
            json.decodeFromString(
                UserDto.serializer(),
                """{"id":"u1","email":"e@x.com","createdAt":"2026-01-01T00:00:00Z","extra":"ignored"}""",
            )
        assertEquals("u1", decoded.id)
    }

    @Test
    fun `provideOkHttpClient builds a client with an interceptor`() {
        val client = IntegrationsModule.provideOkHttpClient()
        assertNotNull(client)
        assertTrue(client.interceptors.isNotEmpty())
    }

    @Test
    fun `provideRetrofit and provideNotovaBackendApi build a usable api`() {
        val json = IntegrationsModule.provideJson()
        val client = IntegrationsModule.provideOkHttpClient()
        val retrofit: Retrofit = IntegrationsModule.provideRetrofit(client, json)
        assertEquals(client, retrofit.callFactory())

        val api: NotovaBackendApi = IntegrationsModule.provideNotovaBackendApi(retrofit)
        assertNotNull(api)
    }
}
