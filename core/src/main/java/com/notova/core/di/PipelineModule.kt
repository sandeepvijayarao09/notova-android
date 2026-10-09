package com.notova.core.di

import com.notova.core.summarize.Summarizer
import com.notova.core.transcribe.Transcriber

/**
 * The on-device pipeline interfaces ([Transcriber], [Summarizer]) are intentionally NOT bound here.
 *
 * They are bound in `:ai`'s `AiModule` to the runtime resolvers (`ResolvingTranscriber` /
 * `ResolvingSummarizer`), each of which picks the first available engine at call time. Binding
 * lives in `:ai` because the resolvers depend on `:core`; a binding here would create a cycle.
 *
 * Everything else (PipelineUseCase, the Record flow, the worker) depends only on the interfaces.
 */
object PipelineModule
