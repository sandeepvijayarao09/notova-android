package com.notova.core.di

import com.notova.core.summarize.Summarizer
import com.notova.core.transcribe.Transcriber

/**
 * The on-device pipeline interfaces ([Transcriber], [Summarizer]) are intentionally NOT bound here.
 *
 * They are bound in `:ai`'s `AiModule` to the runtime resolvers (`ResolvingTranscriber` /
 * `ResolvingSummarizer`), each of which picks the first available engine at call time and degrades
 * to the always-available stub engine. Binding lives in `:ai` because the resolvers depend on
 * `:core` — a binding here would create a cycle. `:core`'s stubs ([com.notova.core.summarize
 * .StubSummarizer], [com.notova.core.transcribe.StubTranscriber]) remain the guaranteed fallback,
 * wrapped by the engine adapters in `:ai`.
 *
 * Everything else (PipelineUseCase, the Record flow, the worker) depends only on the interfaces, so
 * the swap to real engines required no change to callers.
 */
object PipelineModule
