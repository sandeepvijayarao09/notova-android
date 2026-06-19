package com.notova.ai.summarize

import android.content.Context
import android.util.Log
import com.google.mlkit.genai.common.DownloadCallback
import com.google.mlkit.genai.common.FeatureStatus
import com.google.mlkit.genai.common.GenAiException
import com.google.mlkit.genai.summarization.Summarization
import com.google.mlkit.genai.summarization.SummarizationRequest
import com.google.mlkit.genai.summarization.Summarizer
import com.google.mlkit.genai.summarization.SummarizerOptions
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.guava.await
import kotlinx.coroutines.suspendCancellableCoroutine
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

/**
 * Production [MlKitSummarizationEngine] backed by ML Kit GenAI Summarization / Gemini Nano via
 * AICore. Every entry point is guarded so unsupported devices (emulators, devices without AICore)
 * surface as [GenAiFeatureStatus.UNAVAILABLE] rather than crashing — keeping the app green.
 */
@Singleton
class MlKitGenAiSummarizationEngine
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) : MlKitSummarizationEngine {
        private val client: Summarizer by lazy {
            Summarization.getClient(SummarizerOptions.builder(context).build())
        }

        override suspend fun checkFeatureStatus(): GenAiFeatureStatus =
            runCatching {
                when (client.checkFeatureStatus().await()) {
                    FeatureStatus.AVAILABLE -> GenAiFeatureStatus.AVAILABLE
                    FeatureStatus.DOWNLOADABLE -> GenAiFeatureStatus.DOWNLOADABLE
                    FeatureStatus.DOWNLOADING -> GenAiFeatureStatus.DOWNLOADING
                    else -> GenAiFeatureStatus.UNAVAILABLE
                }
            }.getOrElse { e ->
                Log.w(TAG, "ML Kit GenAI feature status check failed", e)
                GenAiFeatureStatus.UNAVAILABLE
            }

        override suspend fun ensureDownloaded(): Boolean =
            runCatching {
                suspendCancellableCoroutine { cont ->
                    client.downloadFeature(
                        object : DownloadCallback {
                            override fun onDownloadStarted(bytesToDownload: Long) = Unit

                            override fun onDownloadProgress(totalBytesDownloaded: Long) = Unit

                            override fun onDownloadCompleted() {
                                if (cont.isActive) cont.resume(true)
                            }

                            override fun onDownloadFailed(e: GenAiException) {
                                Log.w(TAG, "ML Kit GenAI feature download failed", e)
                                if (cont.isActive) cont.resume(false)
                            }
                        },
                    )
                }
            }.getOrElse { e ->
                Log.w(TAG, "ML Kit GenAI feature download could not start", e)
                false
            }

        override suspend fun summarize(text: String): String {
            val request = SummarizationRequest.builder(text).build()
            return client.runInference(request).await().summary
        }

        private companion object {
            const val TAG = "MlKitGenAiSummarizer"
        }
    }
