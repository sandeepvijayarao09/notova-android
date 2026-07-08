package com.notova.ai.audio

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [AudioDecoder] backed by the platform [MediaExtractor] + [MediaCodec] (no extra dependency).
 *
 * Decodes the recording's compressed audio (Notova records AAC in an MP4 container) to raw 16-bit
 * PCM, downmixes to mono and linearly resamples to 16 kHz, emitting it as windows of at most
 * [MAX_CLIP_SEC] seconds — the per-clip limit of Gemma 3n's audio modality.
 *
 * Emission is streaming: at most one ~30 s window of source audio is buffered at a time, so a
 * multi-hour import uses the same bounded memory as a short one. Each window is resampled
 * independently; the <1-sample discontinuity at window boundaries is inaudible and irrelevant to
 * speech recognition.
 */
@Singleton
class MediaCodecAudioDecoder
    @Inject
    constructor() : AudioDecoder {
        override fun decodeToPcm16kMono(audioPath: String): Flow<PcmChunk> =
            flow {
                val extractor = MediaExtractor()
                try {
                    extractor.setDataSource(audioPath)
                    val trackIndex = firstAudioTrack(extractor)
                    require(trackIndex >= 0) { "no audio track in $audioPath" }
                    extractor.selectTrack(trackIndex)
                    decodeStreaming(extractor, extractor.getTrackFormat(trackIndex)) { emit(it) }
                } finally {
                    runCatching { extractor.release() }
                }
            }.flowOn(Dispatchers.IO)

        private fun firstAudioTrack(extractor: MediaExtractor): Int =
            (0 until extractor.trackCount).firstOrNull { i ->
                extractor
                    .getTrackFormat(i)
                    .getString(MediaFormat.KEY_MIME)
                    ?.startsWith("audio/") == true
            } ?: -1

        /** Configures the decoder and runs the feed/drain loop, emitting windows as they complete. */
        private suspend fun decodeStreaming(
            extractor: MediaExtractor,
            inputFormat: MediaFormat,
            emit: suspend (PcmChunk) -> Unit,
        ) {
            val mime = requireNotNull(inputFormat.getString(MediaFormat.KEY_MIME))
            val codec = MediaCodec.createDecoderByType(mime)
            val window =
                Window(
                    srcRate = inputFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE),
                    channels = inputFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT),
                )
            try {
                codec.configure(inputFormat, null, null, 0)
                codec.start()
                val info = MediaCodec.BufferInfo()
                var sawInputEos = false
                var sawOutputEos = false
                while (!sawOutputEos) {
                    if (!sawInputEos) sawInputEos = feedInput(codec, extractor)
                    sawOutputEos = drainInto(codec, info, window, emit)
                }
                window.flush(emit)
            } finally {
                runCatching { codec.stop() }
                runCatching { codec.release() }
            }
        }

        /** Feeds one input buffer to the codec; returns true once end-of-stream has been queued. */
        private fun feedInput(
            codec: MediaCodec,
            extractor: MediaExtractor,
        ): Boolean {
            val inIndex = codec.dequeueInputBuffer(TIMEOUT_US)
            if (inIndex < 0) return false
            val inBuf = codec.getInputBuffer(inIndex)!!
            val size = extractor.readSampleData(inBuf, 0)
            return if (size < 0) {
                codec.queueInputBuffer(inIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                true
            } else {
                codec.queueInputBuffer(inIndex, 0, size, extractor.sampleTime, 0)
                extractor.advance()
                false
            }
        }

        /** Drains one output buffer into [window], emitting any completed clips; returns output EOS. */
        private suspend fun drainInto(
            codec: MediaCodec,
            info: MediaCodec.BufferInfo,
            window: Window,
            emit: suspend (PcmChunk) -> Unit,
        ): Boolean =
            when (val outIndex = codec.dequeueOutputBuffer(info, TIMEOUT_US)) {
                MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    window.updateFormat(codec.outputFormat)
                    false
                }
                MediaCodec.INFO_TRY_AGAIN_LATER -> false
                else -> if (outIndex >= 0) consumeOutput(codec, outIndex, info, window, emit) else false
            }

        /** Reads + downmixes a decoded buffer into [window], emits full clips, returns output EOS. */
        private suspend fun consumeOutput(
            codec: MediaCodec,
            outIndex: Int,
            info: MediaCodec.BufferInfo,
            window: Window,
            emit: suspend (PcmChunk) -> Unit,
        ): Boolean {
            val samples = readOutput(codec, outIndex, info)
            codec.releaseOutputBuffer(outIndex, false)
            window.append(samples)
            window.emitFullClips(emit)
            return info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
        }

        private fun readOutput(
            codec: MediaCodec,
            outIndex: Int,
            info: MediaCodec.BufferInfo,
        ): ShortArray {
            val outBuf = codec.getOutputBuffer(outIndex)!!
            val bytes = ByteArray(info.size)
            outBuf.position(info.offset)
            outBuf.get(bytes, 0, info.size)
            outBuf.clear()
            return bytesToShortsLe(bytes)
        }

        /**
         * Buffers decoded mono samples at the source rate and emits fixed-length 16 kHz clips. Holds
         * at most one clip's worth of source audio plus one decode buffer, keeping memory bounded.
         */
        private inner class Window(
            private var srcRate: Int,
            private var channels: Int,
        ) {
            private var mono = ShortArray(0)
            private var size = 0
            private var emittedOutSamples = 0L

            fun updateFormat(format: MediaFormat) {
                srcRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            }

            fun append(interleaved: ShortArray) {
                val downmixed = downmixToMono(interleaved, channels)
                if (mono.size < size + downmixed.size) {
                    mono = mono.copyOf(maxOf(size + downmixed.size, mono.size * 2, INITIAL_CAPACITY))
                }
                System.arraycopy(downmixed, 0, mono, size, downmixed.size)
                size += downmixed.size
            }

            suspend fun emitFullClips(emit: suspend (PcmChunk) -> Unit) {
                val clipSamples = srcRate * MAX_CLIP_SEC
                while (size >= clipSamples) {
                    emitClip(take(clipSamples), emit)
                }
            }

            suspend fun flush(emit: suspend (PcmChunk) -> Unit) {
                if (size > 0) emitClip(take(size), emit)
            }

            /** Removes and returns the first [n] buffered samples, shifting the remainder down. */
            private fun take(n: Int): ShortArray {
                val out = mono.copyOfRange(0, n)
                System.arraycopy(mono, n, mono, 0, size - n)
                size -= n
                return out
            }

            private suspend fun emitClip(
                sourceClip: ShortArray,
                emit: suspend (PcmChunk) -> Unit,
            ) {
                val out = resampleMono16k(sourceClip, srcRate)
                if (out.isEmpty()) return
                val startMs = emittedOutSamples * MILLIS_PER_SEC / TARGET_RATE
                emittedOutSamples += out.size
                val endMs = emittedOutSamples * MILLIS_PER_SEC / TARGET_RATE
                emit(PcmChunk(bytes = shortsToBytesLe(out), startMs = startMs, endMs = endMs))
            }
        }

        private fun downmixToMono(
            interleaved: ShortArray,
            channels: Int,
        ): ShortArray {
            if (channels <= 1) return interleaved
            val frames = interleaved.size / channels
            val mono = ShortArray(frames)
            for (frame in 0 until frames) {
                var sum = 0
                for (ch in 0 until channels) sum += interleaved[frame * channels + ch]
                mono[frame] = (sum / channels).toShort()
            }
            return mono
        }

        /** Linearly resamples already-mono samples from [srcRate] to [TARGET_RATE]. */
        private fun resampleMono16k(
            mono: ShortArray,
            srcRate: Int,
        ): ShortArray {
            if (srcRate == TARGET_RATE || mono.isEmpty()) return mono
            val ratio = TARGET_RATE.toDouble() / srcRate
            val outLength = (mono.size * ratio).toInt().coerceAtLeast(1)
            val out = ShortArray(outLength)
            for (i in 0 until outLength) {
                val srcPos = i / ratio
                val left = srcPos.toInt()
                val right = (left + 1).coerceAtMost(mono.size - 1)
                val frac = srcPos - left
                out[i] = (mono[left] * (1 - frac) + mono[right] * frac).toInt().toShort()
            }
            return out
        }

        private fun bytesToShortsLe(bytes: ByteArray): ShortArray {
            val shorts = ShortArray(bytes.size / 2)
            ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(shorts)
            return shorts
        }

        private fun shortsToBytesLe(shorts: ShortArray): ByteArray {
            val buffer = ByteBuffer.allocate(shorts.size * 2).order(ByteOrder.LITTLE_ENDIAN)
            buffer.asShortBuffer().put(shorts)
            return buffer.array()
        }

        private companion object {
            const val TARGET_RATE = 16_000
            const val MAX_CLIP_SEC = 30
            const val MILLIS_PER_SEC = 1_000
            const val TIMEOUT_US = 10_000L
            const val INITIAL_CAPACITY = 16_000
        }
    }
