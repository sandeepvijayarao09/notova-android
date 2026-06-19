package com.notova.ai.summarize

import android.content.Context
import android.util.Log
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import com.google.mediapipe.tasks.genai.llminference.LlmInference.LlmInferenceOptions
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Production [LlmEngine] backed by MediaPipe LLM Inference (`com.google.mediapipe:tasks-genai`).
 *
 * Initialization is guarded: [load] returns false (never throws) when the model file is missing or
 * the native engine fails to start (e.g. on an emulator without the right ABI / a corrupt bundle),
 * which lets [LocalGemmaSummarizer] report itself unavailable and the resolver fall through.
 */
@Singleton
class MediaPipeLlmEngine
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) : LlmEngine {
        private val mutex = Mutex()
        private var inference: LlmInference? = null
        private var loadedPath: String? = null

        override suspend fun load(modelPath: String): Boolean =
            mutex.withLock {
                if (inference != null && loadedPath == modelPath) return@withLock true
                val file = File(modelPath)
                if (!file.exists() || file.length() == 0L) return@withLock false
                withContext(Dispatchers.IO) {
                    runCatching {
                        // Release a previously loaded (different) model first.
                        inference?.close()
                        inference = null
                        val options =
                            LlmInferenceOptions.builder()
                                .setModelPath(modelPath)
                                .setMaxTokens(MAX_TOKENS)
                                .build()
                        inference = LlmInference.createFromOptions(context, options)
                        loadedPath = modelPath
                        true
                    }.getOrElse { e ->
                        Log.w(TAG, "MediaPipe LLM init failed for $modelPath", e)
                        inference = null
                        loadedPath = null
                        false
                    }
                }
            }

        override fun isReady(): Boolean = inference != null

        override suspend fun generate(prompt: String): String =
            withContext(Dispatchers.IO) {
                val engine = inference ?: error("LLM engine not loaded")
                engine.generateResponse(prompt)
            }

        override fun close() {
            runCatching { inference?.close() }
            inference = null
            loadedPath = null
        }

        private companion object {
            const val TAG = "MediaPipeLlmEngine"
            const val MAX_TOKENS = 1024
        }
    }
