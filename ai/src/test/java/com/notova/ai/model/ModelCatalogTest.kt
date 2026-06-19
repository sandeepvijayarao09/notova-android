package com.notova.ai.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pure capability-detection mapping: which file names unlock the Gemma summarizer. */
class ModelCatalogTest {
    @Test
    fun `task files map to the Gemma summarizer capability`() {
        assertEquals(ModelCapability.GEMMA_SUMMARIZER, ModelCatalog.capabilityFor("gemma-2b-it.task"))
    }

    @Test
    fun `litertlm files map to the Gemma summarizer capability`() {
        assertEquals(ModelCapability.GEMMA_SUMMARIZER, ModelCatalog.capabilityFor("model.litertlm"))
    }

    @Test
    fun `bin files map to the Gemma summarizer capability`() {
        assertEquals(ModelCapability.GEMMA_SUMMARIZER, ModelCatalog.capabilityFor("weights.bin"))
    }

    @Test
    fun `detection is case-insensitive on the extension`() {
        assertEquals(ModelCapability.GEMMA_SUMMARIZER, ModelCatalog.capabilityFor("Gemma.TASK"))
    }

    @Test
    fun `unknown extensions map to UNKNOWN`() {
        assertEquals(ModelCapability.UNKNOWN, ModelCatalog.capabilityFor("notes.txt"))
        assertEquals(ModelCapability.UNKNOWN, ModelCatalog.capabilityFor("audio.m4a"))
        assertEquals(ModelCapability.UNKNOWN, ModelCatalog.capabilityFor("no-extension"))
    }

    @Test
    fun `isGemmaModel agrees with capabilityFor`() {
        assertTrue(ModelCatalog.isGemmaModel("a.task"))
        assertFalse(ModelCatalog.isGemmaModel("a.mp3"))
    }
}
