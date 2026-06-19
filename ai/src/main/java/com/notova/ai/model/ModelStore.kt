package com.notova.ai.model

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Owns the app-private models directory (`filesDir/models`) and the lifecycle of model files in it:
 * listing, importing (copying a stream in), saving and deleting.
 *
 * The directory is injected (rather than `Context.getFilesDir` read inline) so unit tests can point
 * it at a JUnit `TemporaryFolder` and exercise the real file logic without a device. The IO
 * dispatcher is injected too so tests can drive it with a `TestDispatcher`.
 */
@Singleton
class ModelStore
    @Inject
    constructor(
        private val modelsDir: File,
        private val io: CoroutineDispatcher = Dispatchers.IO,
    ) {
        /** Ensures the models directory exists and returns it. */
        fun ensureDir(): File {
            if (!modelsDir.exists()) {
                modelsDir.mkdirs()
            }
            return modelsDir
        }

        /** The absolute path the models live under (created on demand). */
        fun directoryPath(): String = ensureDir().absolutePath

        /** Lists every model file currently installed, newest-first, with detected capability. */
        suspend fun list(): List<InstalledModel> =
            withContext(io) {
                val dir = ensureDir()
                dir.listFiles()
                    ?.filter { it.isFile }
                    ?.sortedByDescending { it.lastModified() }
                    ?.map { it.toInstalledModel() }
                    ?: emptyList()
            }

        /** Returns the first installed model that unlocks [capability], or null if none is present. */
        suspend fun firstWith(capability: ModelCapability): InstalledModel? =
            list().firstOrNull { it.capability == capability }

        /** True when at least one model unlocking [capability] is installed. */
        suspend fun has(capability: ModelCapability): Boolean = firstWith(capability) != null

        /**
         * Copies [source] into the models directory under [fileName], overwriting any existing file
         * with that name. Returns the resulting [InstalledModel]. Used by the Storage Access
         * Framework import flow (the caller opens the picked document's [InputStream]).
         */
        suspend fun import(
            fileName: String,
            source: InputStream,
        ): InstalledModel =
            withContext(io) {
                require(fileName.isNotBlank()) { "model file name must not be blank" }
                val safeName = sanitize(fileName)
                val dest = File(ensureDir(), safeName)
                source.use { input ->
                    dest.outputStream().use { output -> input.copyTo(output) }
                }
                dest.toInstalledModel()
            }

        /** Returns the destination [File] for [fileName] (created lazily on write). For the downloader. */
        fun fileFor(fileName: String): File = File(ensureDir(), sanitize(fileName))

        /** Deletes the model named [fileName]; returns true if a file was removed. */
        suspend fun delete(fileName: String): Boolean =
            withContext(io) {
                val target = File(ensureDir(), sanitize(fileName))
                target.exists() && target.delete()
            }

        private fun File.toInstalledModel(): InstalledModel =
            InstalledModel(
                name = name,
                path = absolutePath,
                sizeBytes = length(),
                capability = ModelCatalog.capabilityFor(name),
            )

        // Strip any path separators so a malicious / awkward picked name can't escape the dir.
        private fun sanitize(fileName: String): String = File(fileName).name
    }
