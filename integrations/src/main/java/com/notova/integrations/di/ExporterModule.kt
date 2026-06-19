package com.notova.integrations.di

import com.notova.core.integration.IntegrationExporter
import com.notova.integrations.export.BackendIntegrationExporter
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Binds the on-device [IntegrationExporter] interface to the backend-brokered implementation.
 * `:core` deliberately does NOT bind [IntegrationExporter] so this module owns it.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class ExporterModule {
    @Binds
    @Singleton
    abstract fun bindIntegrationExporter(impl: BackendIntegrationExporter): IntegrationExporter
}
