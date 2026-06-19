package com.notova.ai.transcribe

import com.notova.core.model.Transcript
import com.notova.core.transcribe.StubTranscriber
import javax.inject.Inject

/**
 * Always-available [TranscriberEngine] that delegates to the core [StubTranscriber]. The guaranteed
 * fallback at the end of the resolver chain so the pipeline always yields a transcript even when
 * on-device speech recognition is unavailable.
 */
class StubTranscriberEngine
    @Inject
    constructor(
        private val delegate: StubTranscriber,
    ) : TranscriberEngine {
        override val engineName: String = ENGINE_NAME

        override suspend fun isAvailable(): Boolean = true

        override suspend fun transcribe(audioPath: String): Transcript = delegate.transcribe(audioPath)

        companion object {
            const val ENGINE_NAME = "Built-in (placeholder)"
        }
    }
