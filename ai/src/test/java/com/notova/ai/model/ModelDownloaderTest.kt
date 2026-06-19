package com.notova.ai.model

import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * Drives [ModelDownloader] against a [MockWebServer]: progress emission, completion (file landed in
 * the store with the right bytes + capability), and failure (partial file cleaned up, terminal
 * [DownloadProgress.Failed] emitted rather than thrown).
 */
class ModelDownloaderTest {
    @get:Rule
    val temp = TemporaryFolder()

    private lateinit var server: MockWebServer
    private lateinit var store: ModelStore
    private lateinit var downloader: ModelDownloader
    private lateinit var modelsDir: File

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        modelsDir = File(temp.root, "models")
        store = ModelStore(modelsDir)
        downloader = ModelDownloader(OkHttpClient.Builder().build(), store)
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `successful download emits Started Running and Completed and lands the file`() =
        runTest {
            val bytes = "X".repeat(2048).toByteArray()
            server.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Length", bytes.size.toString())
                    .setBody(Buffer().write(bytes)),
            )

            val events =
                downloader.download(server.url("/gemma.task").toString(), "gemma.task").toList()

            assertTrue(events.first() is DownloadProgress.Started)
            assertTrue(events.any { it is DownloadProgress.Running })
            val completed = events.last()
            assertTrue(completed is DownloadProgress.Completed)
            completed as DownloadProgress.Completed
            assertEquals("gemma.task", completed.model.name)
            assertEquals(ModelCapability.GEMMA_SUMMARIZER, completed.model.capability)
            assertEquals(bytes.size.toLong(), completed.model.sizeBytes)
            assertTrue(File(modelsDir, "gemma.task").exists())
        }

    @Test
    fun `Started carries the total bytes from Content-Length`() =
        runTest {
            val bytes = "abc".toByteArray()
            server.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Length", bytes.size.toString())
                    .setBody(Buffer().write(bytes)),
            )

            val events = downloader.download(server.url("/m.task").toString(), "m.task").toList()
            val started = events.first() as DownloadProgress.Started
            assertEquals(bytes.size.toLong(), started.totalBytes)
        }

    @Test
    fun `final Running progress reaches one when total size is known`() =
        runTest {
            val bytes = "Y".repeat(4096).toByteArray()
            server.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Length", bytes.size.toString())
                    .setBody(Buffer().write(bytes)),
            )

            val events = downloader.download(server.url("/m.task").toString(), "m.task").toList()
            val lastRunning = events.filterIsInstance<DownloadProgress.Running>().last()
            assertEquals(1f, lastRunning.fraction, 0.0001f)
            assertEquals(bytes.size.toLong(), lastRunning.bytesRead)
        }

    @Test
    fun `an HTTP error emits Failed and leaves no file behind`() =
        runTest {
            server.enqueue(MockResponse().setResponseCode(404).setBody("nope"))

            val events =
                downloader.download(server.url("/missing.task").toString(), "missing.task").toList()

            assertTrue(events.last() is DownloadProgress.Failed)
            assertFalse(File(modelsDir, "missing.task").exists())
        }

    @Test
    fun `download writes exactly the served bytes`() =
        runTest {
            val payload = "the-real-model-weights".toByteArray()
            server.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Length", payload.size.toString())
                    .setBody(Buffer().write(payload)),
            )

            downloader.download(server.url("/w.task").toString(), "w.task").toList()

            assertEquals(
                "the-real-model-weights",
                File(modelsDir, "w.task").readBytes().toString(Charsets.UTF_8),
            )
        }
}
