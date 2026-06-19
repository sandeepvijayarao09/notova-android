package com.notova.ai.model

/**
 * The on-device capability a model file can unlock once it is present in the models directory.
 */
enum class ModelCapability {
    /** A Gemma `.task` / `.litertlm` bundle that enables the MediaPipe LLM summarizer. */
    GEMMA_SUMMARIZER,

    /** A file Notova does not (yet) know how to map to an engine. */
    UNKNOWN,
}

/**
 * A model file present in the app's private models directory.
 *
 * @property name file name as stored on disk
 * @property path absolute path on disk
 * @property sizeBytes size of the file in bytes
 * @property capability what engine this file unlocks, derived from its extension
 */
data class InstalledModel(
    val name: String,
    val path: String,
    val sizeBytes: Long,
    val capability: ModelCapability,
)

/**
 * Pure capability detection: maps a file name to the engine it unlocks. Kept free of Android /
 * filesystem dependencies so it is trivially unit-testable.
 */
object ModelCatalog {
    /** Extensions that MediaPipe LLM Inference can load as a Gemma bundle. */
    val GEMMA_EXTENSIONS = listOf(".task", ".litertlm", ".bin")

    fun capabilityFor(fileName: String): ModelCapability {
        val lower = fileName.lowercase()
        return if (GEMMA_EXTENSIONS.any { lower.endsWith(it) }) {
            ModelCapability.GEMMA_SUMMARIZER
        } else {
            ModelCapability.UNKNOWN
        }
    }

    fun isGemmaModel(fileName: String): Boolean = capabilityFor(fileName) == ModelCapability.GEMMA_SUMMARIZER
}
