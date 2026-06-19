package com.notova.ai.model

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

/** Progress events emitted while a model file is being fetched into the models directory. */
sealed interface DownloadProgress {
    /** Download has begun. [totalBytes] is -1 when the server sends no Content-Length. */
    data class Started(val totalBytes: Long) : DownloadProgress

    /** Incremental progress. [fraction] is in 0f..1f, or -1f when total size is unknown. */
    data class Running(
        val bytesRead: Long,
        val totalBytes: Long,
        val fraction: Float,
    ) : DownloadProgress

    /** Download finished successfully; the file is installed at [model]. */
    data class Completed(val model: InstalledModel) : DownloadProgress

    /** Download failed; the partial file (if any) has been removed. */
    data class Failed(val error: Throwable) : DownloadProgress
}

/**
 * Downloads a model file from a URL into the [ModelStore]'s directory, emitting [DownloadProgress]
 * as a cold [Flow]. Deliberately takes a URL + file name (no giant file is hard-coded) so the
 * Settings UI can offer a small Gemma bundle without baking a multi-GB asset into the app.
 *
 * The [OkHttpClient] is injected so tests can drive it against a `MockWebServer`.
 */
@Singleton
class ModelDownloader
    @Inject
    constructor(
        @Named("aiDownloadClient") private val client: OkHttpClient,
        private val store: ModelStore,
        private val io: CoroutineDispatcher = Dispatchers.IO,
    ) {
        /**
         * Streams the body at [url] into `models/[fileName]`, emitting progress. On any failure the
         * partial file is deleted and a terminal [DownloadProgress.Failed] is emitted (never thrown),
         * so collectors degrade gracefully.
         */
        fun download(
            url: String,
            fileName: String,
        ): Flow<DownloadProgress> =
            flow {
                val dest = store.fileFor(fileName)
                try {
                    val request = Request.Builder().url(url).build()
                    client.newCall(request).execute().use { response ->
                        if (!response.isSuccessful) error("HTTP ${response.code} for $url")
                        val body = response.body ?: error("empty body for $url")
                        emit(DownloadProgress.Started(body.contentLength()))
                        copyBody(body.byteStream(), dest, body.contentLength()) { emit(it) }
                    }
                    emit(DownloadProgress.Completed(installedModelFor(dest)))
                } catch (e: IOException) {
                    failAndCleanUp(dest, e)
                } catch (e: IllegalStateException) {
                    failAndCleanUp(dest, e)
                }
            }.flowOn(io)

        private suspend fun copyBody(
            input: InputStream,
            dest: File,
            total: Long,
            emit: suspend (DownloadProgress) -> Unit,
        ) {
            input.use { dest.outputStream().use { output -> pump(input, output, total, emit) } }
        }

        private suspend fun pump(
            input: InputStream,
            output: OutputStream,
            total: Long,
            emit: suspend (DownloadProgress) -> Unit,
        ) {
            val buffer = ByteArray(BUFFER_SIZE)
            var readSoFar = 0L
            var lastFraction = -1f
            var count = input.read(buffer)
            while (count >= 0) {
                output.write(buffer, 0, count)
                readSoFar += count
                val fraction = fractionOf(readSoFar, total)
                if (shouldEmit(fraction, lastFraction, readSoFar, total)) {
                    lastFraction = fraction
                    emit(DownloadProgress.Running(readSoFar, total, fraction))
                }
                count = input.read(buffer)
            }
            output.flush()
        }

        private suspend fun FlowCollector<DownloadProgress>.failAndCleanUp(
            dest: File,
            error: Throwable,
        ) {
            runCatching { if (dest.exists()) dest.delete() }
            emit(DownloadProgress.Failed(error))
        }

        private fun installedModelFor(dest: File): InstalledModel =
            InstalledModel(
                name = dest.name,
                path = dest.absolutePath,
                sizeBytes = dest.length(),
                capability = ModelCatalog.capabilityFor(dest.name),
            )

        private fun fractionOf(
            readSoFar: Long,
            total: Long,
        ): Float = if (total > 0) (readSoFar.toFloat() / total).coerceIn(0f, 1f) else -1f

        // Throttle emissions to whole-percent steps (or the final byte) to avoid flooding collectors.
        private fun shouldEmit(
            fraction: Float,
            lastFraction: Float,
            readSoFar: Long,
            total: Long,
        ): Boolean = fraction < 0f || fraction - lastFraction >= EMIT_STEP || readSoFar == total

        private companion object {
            const val BUFFER_SIZE = 64 * 1024
            const val EMIT_STEP = 0.01f
        }
    }
