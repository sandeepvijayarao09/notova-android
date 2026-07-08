package com.notova.ai.transcribe

/**
 * Seam over an on-device LLM runtime that can transcribe raw audio via a model's audio modality
 * (Gemma 3n through LiteRT-LM in production).
 *
 * Kept separate from [com.notova.ai.summarize.LlmEngine] (text generation) so the two capabilities
 * can be faked independently in JVM tests. The production [com.notova.ai.summarize.LiteRtLmEngine]
 * implements both interfaces and is a singleton, so a single loaded model serves summarization and
 * transcription.
 */
interface AudioTranscriptionEngine {
    /**
     * Loads the model at [modelPath] (if not already loaded) with the audio modality enabled and
     * returns true on success. MUST NOT throw: a missing file or failed native init returns false
     * so [GemmaAudioTranscriber] reports itself unavailable and the resolver falls through.
     */
    suspend fun load(modelPath: String): Boolean

    /**
     * Transcribes a single chunk of 16 kHz, mono, 16-bit little-endian PCM ([pcm16kMono]) using
     * [prompt] as the instruction. Returns the model's raw text output. May throw on inference error
     * (the resolver catches and falls through to the next engine).
     */
    suspend fun transcribe(
        pcm16kMono: ByteArray,
        prompt: String,
    ): String
}
