package com.notova.ai.model

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.File

/**
 * Exercises the real file logic of [ModelStore] against a JUnit [TemporaryFolder] — list, import,
 * capability detection, first-with / has, and delete — no Android device required.
 */
class ModelStoreTest {
    @get:Rule
    val temp = TemporaryFolder()

    private lateinit var modelsDir: File
    private lateinit var store: ModelStore

    @Before
    fun setUp() {
        modelsDir = File(temp.root, "models")
        store = ModelStore(modelsDir)
    }

    private fun stream(content: String) = ByteArrayInputStream(content.toByteArray())

    @Test
    fun `ensureDir creates the models directory`() {
        assertFalse(modelsDir.exists())
        store.ensureDir()
        assertTrue(modelsDir.exists())
    }

    @Test
    fun `list is empty for a fresh store`() =
        runTest {
            assertTrue(store.list().isEmpty())
        }

    @Test
    fun `import copies the stream and detects Gemma capability`() =
        runTest {
            val installed = store.import("gemma-2b.task", stream("model-bytes"))

            assertEquals("gemma-2b.task", installed.name)
            assertEquals(ModelCapability.GEMMA_SUMMARIZER, installed.capability)
            assertEquals("model-bytes".toByteArray().size.toLong(), installed.sizeBytes)
            assertTrue(File(modelsDir, "gemma-2b.task").exists())
        }

    @Test
    fun `imported non-model files are listed as UNKNOWN capability`() =
        runTest {
            store.import("readme.txt", stream("hello"))
            val listed = store.list().single()
            assertEquals(ModelCapability.UNKNOWN, listed.capability)
        }

    @Test
    fun `import sanitizes path separators in the file name`() =
        runTest {
            val installed = store.import("../../evil.task", stream("x"))
            assertEquals("evil.task", installed.name)
            assertTrue(File(modelsDir, "evil.task").exists())
        }

    @Test
    fun `firstWith finds an installed Gemma model`() =
        runTest {
            store.import("notes.txt", stream("a"))
            store.import("gemma.task", stream("b"))
            val found = store.firstWith(ModelCapability.GEMMA_SUMMARIZER)
            assertEquals("gemma.task", found?.name)
        }

    @Test
    fun `firstWith returns null when no model unlocks the capability`() =
        runTest {
            store.import("notes.txt", stream("a"))
            assertNull(store.firstWith(ModelCapability.GEMMA_SUMMARIZER))
        }

    @Test
    fun `has reflects presence of a capability`() =
        runTest {
            assertFalse(store.has(ModelCapability.GEMMA_SUMMARIZER))
            store.import("gemma.task", stream("b"))
            assertTrue(store.has(ModelCapability.GEMMA_SUMMARIZER))
        }

    @Test
    fun `delete removes an installed model`() =
        runTest {
            store.import("gemma.task", stream("b"))
            assertTrue(store.delete("gemma.task"))
            assertTrue(store.list().isEmpty())
        }

    @Test
    fun `delete of a missing file returns false`() =
        runTest {
            assertFalse(store.delete("nope.task"))
        }

    @Test
    fun `import overwrites an existing model of the same name`() =
        runTest {
            store.import("gemma.task", stream("first"))
            val second = store.import("gemma.task", stream("second-longer"))
            assertEquals(1, store.list().size)
            assertEquals("second-longer".toByteArray().size.toLong(), second.sizeBytes)
        }

    @Test
    fun `directoryPath returns the absolute models path and creates it`() {
        val path = store.directoryPath()
        assertEquals(modelsDir.absolutePath, path)
        assertTrue(modelsDir.exists())
    }
}
