/*
 * Portions of this file are adapted from Google AI Edge Gallery
 * (https://github.com/google-ai-edge/gallery), specifically its
 * `LlmChatModelHelper` LiteRT-LM Engine/Conversation usage.
 *
 * Copyright 2025 Google LLC
 * Copyright 2026 Notova
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.notova.ai.summarize

import android.util.Log
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.Conversation
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.ExperimentalApi
import com.google.ai.edge.litertlm.Message
import com.google.ai.edge.litertlm.MessageCallback
import com.google.ai.edge.litertlm.SamplerConfig
import com.notova.ai.transcribe.AudioTranscriptionEngine
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Production on-device engine backed by LiteRT-LM (`com.google.ai.edge.litertlm`), running a Gemma
 * 3n `.litertlm` bundle. Implements both:
 *  - [LlmEngine] — text generation for [LocalGemmaSummarizer], and
 *  - [AudioTranscriptionEngine] — audio-modality transcription for `GemmaAudioTranscriber`.
 *
 * A single [Engine] is loaded once (audio modality enabled) and shared between both capabilities;
 * each request runs on its own short-lived [Conversation] so transcription and summarization never
 * share context. Inference is serialized with a [Mutex] because the native engine is not safe for
 * concurrent use (Notova's pipeline transcribes then summarizes sequentially anyway).
 *
 * Initialization is fully guarded: [load] returns false (never throws) when the model file is
 * missing or the native engine fails to start, so the callers report themselves unavailable and the
 * resolvers fall through to the next engine.
 *
 * The Engine/Conversation lifecycle is adapted from Google AI Edge Gallery's `LlmChatModelHelper`.
 */
@Singleton
class LiteRtLmEngine
    @Inject
    constructor() : LlmEngine, AudioTranscriptionEngine {
        private val mutex = Mutex()
        private var engine: Engine? = null
        private var loadedPath: String? = null

        @OptIn(ExperimentalApi::class)
        override suspend fun load(modelPath: String): Boolean =
            mutex.withLock {
                if (engine != null && loadedPath == modelPath) return@withLock true
                val file = File(modelPath)
                if (!file.exists() || file.length() == 0L) return@withLock false
                withContext(Dispatchers.IO) {
                    runCatching {
                        // Release any previously loaded (different) model first.
                        engine?.close()
                        engine = null
                        // CPU for both the main and audio backends: Gemma 3n's audio modality must
                        // run on CPU, and a CPU main backend is the most portable default (GPU can be
                        // enabled per-device later). visionBackend stays null — Notova has no image input.
                        val config =
                            EngineConfig(
                                modelPath = modelPath,
                                backend = Backend.CPU(),
                                visionBackend = null,
                                audioBackend = Backend.CPU(),
                                maxNumTokens = MAX_TOKENS,
                            )
                        Engine(config).also { it.initialize() }.let { engine = it }
                        loadedPath = modelPath
                        true
                    }.getOrElse { e ->
                        Log.w(TAG, "LiteRT-LM init failed for $modelPath", e)
                        engine = null
                        loadedPath = null
                        false
                    }
                }
            }

        override fun isReady(): Boolean = engine != null

        override suspend fun generate(prompt: String): String = runOnce(listOf(Content.Text(prompt)))

        override suspend fun transcribe(
            pcm16kMono: ByteArray,
            prompt: String,
        ): String =
            // Audio first, then the instruction text, so the last token is the prompt (matches the
            // gallery's ordering for accurate generation).
            runOnce(listOf(Content.AudioBytes(pcm16kMono), Content.Text(prompt)))

        override fun close() {
            runCatching { engine?.close() }
            engine = null
            loadedPath = null
        }

        /**
         * Runs one request on a fresh [Conversation] and returns the full text once streaming
         * completes. Serialized via [mutex]; the conversation is always closed afterwards.
         */
        @OptIn(ExperimentalApi::class)
        private suspend fun runOnce(contents: List<Content>): String =
            mutex.withLock {
                val eng = engine ?: error("LiteRT-LM engine not loaded")
                withContext(Dispatchers.IO) {
                    val conversation =
                        eng.createConversation(
                            ConversationConfig(
                                samplerConfig =
                                    SamplerConfig(
                                        topK = TOP_K,
                                        topP = TOP_P,
                                        temperature = TEMPERATURE,
                                    ),
                                systemInstruction = null,
                                tools = emptyList(),
                            ),
                        )
                    try {
                        collectResponse(conversation, contents)
                    } finally {
                        runCatching { conversation.close() }
                    }
                }
            }

        /** Bridges LiteRT-LM's streaming [MessageCallback] into a suspend function. */
        private suspend fun collectResponse(
            conversation: Conversation,
            contents: List<Content>,
        ): String =
            suspendCancellableCoroutine { cont ->
                val builder = StringBuilder()
                conversation.sendMessageAsync(
                    Contents.of(contents),
                    object : MessageCallback {
                        override fun onMessage(message: Message) {
                            builder.append(message.toString())
                        }

                        override fun onDone() {
                            if (cont.isActive) cont.resume(builder.toString())
                        }

                        override fun onError(throwable: Throwable) {
                            when {
                                throwable is CancellationException ->
                                    if (cont.isActive) cont.resume(builder.toString())
                                cont.isActive -> cont.resumeWithException(throwable)
                            }
                        }
                    },
                    emptyMap(),
                )
                cont.invokeOnCancellation { runCatching { conversation.cancelProcess() } }
            }

        private companion object {
            const val TAG = "LiteRtLmEngine"
            const val MAX_TOKENS = 4096
            const val TOP_K = 64
            const val TOP_P = 0.95
            const val TEMPERATURE = 1.0
        }
    }
