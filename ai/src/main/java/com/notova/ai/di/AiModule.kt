package com.notova.ai.di

import android.content.Context
import com.notova.ai.summarize.GeminiNanoSummarizer
import com.notova.ai.summarize.LlmEngine
import com.notova.ai.summarize.LocalGemmaSummarizer
import com.notova.ai.summarize.MediaPipeLlmEngine
import com.notova.ai.summarize.MlKitGenAiSummarizationEngine
import com.notova.ai.summarize.MlKitSummarizationEngine
import com.notova.ai.summarize.ResolvingSummarizer
import com.notova.ai.summarize.StubSummarizerEngine
import com.notova.ai.summarize.SummarizerEngine
import com.notova.ai.transcribe.AndroidSpeechRecognitionEngine
import com.notova.ai.transcribe.ResolvingTranscriber
import com.notova.ai.transcribe.SpeechRecognitionEngine
import com.notova.ai.transcribe.SpeechRecognizerTranscriber
import com.notova.ai.transcribe.StubTranscriberEngine
import com.notova.ai.transcribe.TranscriberEngine
import com.notova.core.summarize.Summarizer
import com.notova.core.transcribe.Transcriber
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import okhttp3.OkHttpClient
import java.io.File
import javax.inject.Named
import javax.inject.Singleton

/**
 * Wires the on-device AI engines and their resolvers.
 *
 * This module OWNS the [Transcriber] / [Summarizer] bindings (formerly bound to stubs in
 * `:core`'s PipelineModule). The Record flow, worker, and pipeline depend only on those interfaces,
 * so swapping in the resolvers required no change to callers. Each binding is to a resolver that
 * picks the first available engine at call time and degrades to the always-available stub engine.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class AiModule {
    @Binds
    @Singleton
    abstract fun bindLlmEngine(impl: MediaPipeLlmEngine): LlmEngine

    @Binds
    @Singleton
    abstract fun bindMlKitSummarizationEngine(impl: MlKitGenAiSummarizationEngine): MlKitSummarizationEngine

    @Binds
    @Singleton
    abstract fun bindSpeechRecognitionEngine(impl: AndroidSpeechRecognitionEngine): SpeechRecognitionEngine

    @Binds
    @Singleton
    abstract fun bindSummarizer(impl: ResolvingSummarizer): Summarizer

    @Binds
    @Singleton
    abstract fun bindTranscriber(impl: ResolvingTranscriber): Transcriber
}

/**
 * Provides the ordered engine chains and the shared infra ([OkHttpClient], models directory) the
 * AI module needs. Kept as an `object` module because the ordered lists are assembled by hand to
 * guarantee priority order (Dagger multibindings give no ordering guarantee).
 */
@Module
@InstallIn(SingletonComponent::class)
object AiProvidesModule {
    @Provides
    @Singleton
    fun provideModelsDir(
        @ApplicationContext context: Context,
    ): File = File(context.filesDir, "models")

    @Provides
    @Singleton
    fun provideIoDispatcher(): CoroutineDispatcher = Dispatchers.IO

    // Qualified so it never collides with :integrations' unqualified OkHttpClient on the app graph.
    @Provides
    @Singleton
    @Named("aiDownloadClient")
    fun provideAiDownloadClient(): OkHttpClient = OkHttpClient.Builder().build()

    @Provides
    @Singleton
    @Named("summarizerEngines")
    fun provideSummarizerEngines(
        gemma: LocalGemmaSummarizer,
        geminiNano: GeminiNanoSummarizer,
        stub: StubSummarizerEngine,
    ): List<@JvmSuppressWildcards SummarizerEngine> = listOf(gemma, geminiNano, stub)

    @Provides
    @Singleton
    @Named("transcriberEngines")
    fun provideTranscriberEngines(
        speech: SpeechRecognizerTranscriber,
        stub: StubTranscriberEngine,
    ): List<@JvmSuppressWildcards TranscriberEngine> = listOf(speech, stub)
}
