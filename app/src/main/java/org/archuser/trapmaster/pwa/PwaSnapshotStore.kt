package org.archuser.trapmaster.pwa

import android.content.Context
import org.json.JSONObject
import java.io.File

private const val SOURCE_BUNDLED = "bundled"
private const val SOURCE_REMOTE = "remote"

data class SnapshotMetadata(
    val source: String,
    val activeVersion: String,
    val remoteRevision: String? = null
)

class PwaSnapshotStore(private val context: Context) {

    private val rootDir = File(context.filesDir, "trapmaster-pwa")
    private val currentDir = File(rootDir, "current")
    private val stagingDir = File(rootDir, "staging")
    private val previousDir = File(rootDir, "previous")
    private val metadataFile = File(rootDir, "metadata.json")

    fun ensureBundledSnapshotInstalled(): SnapshotMetadata = synchronized(lock) {
        rootDir.mkdirs()

        val bundledVersion = readBundledVersion()
        val currentMetadata = readMetadata()
        val snapshotReady = currentDir.exists() && currentDir.resolve("index.html").isFile

        val shouldInstallBundled = !snapshotReady ||
            currentMetadata == null ||
            (currentMetadata.source == SOURCE_BUNDLED && currentMetadata.activeVersion != bundledVersion)

        if (shouldInstallBundled) {
            installBundledSnapshot(bundledVersion)
        }

        readMetadata()
            ?: error("Bundled PWA installation completed without metadata.")
    }

    fun activeDirectory(): File = currentDir

    fun currentMetadata(): SnapshotMetadata? = synchronized(lock) {
        readMetadata()
    }

    fun installRemoteSnapshot(extractedSnapshotDir: File, remoteRevision: String): SnapshotMetadata =
        synchronized(lock) {
            check(extractedSnapshotDir.resolve("index.html").isFile) {
                "Downloaded PWA snapshot does not contain index.html."
            }

            resetDirectory(stagingDir)
            extractedSnapshotDir.copyRecursively(stagingDir, overwrite = true)
            prepareSnapshotDirectory(stagingDir, remoteRevision)
            promoteStaging(
                SnapshotMetadata(
                    source = SOURCE_REMOTE,
                    activeVersion = remoteRevision,
                    remoteRevision = remoteRevision
                )
            )

            readMetadata()
                ?: error("Remote PWA installation completed without metadata.")
        }

    private fun installBundledSnapshot(bundledVersion: String) {
        resetDirectory(stagingDir)
        copyAssetDirectory(PwaRuntime.bundledAssetRoot, stagingDir)
        prepareSnapshotDirectory(stagingDir, bundledVersion)
        promoteStaging(
            SnapshotMetadata(
                source = SOURCE_BUNDLED,
                activeVersion = bundledVersion
            )
        )
    }

    private fun prepareSnapshotDirectory(snapshotDir: File, version: String) {
        versionServiceWorkerCache(snapshotDir, version.take(12))
    }

    private fun copyAssetDirectory(assetPath: String, destinationDir: File) {
        val children = context.assets.list(assetPath).orEmpty()
        destinationDir.mkdirs()

        children.forEach { child ->
            val childAssetPath = "$assetPath/$child"
            val nestedChildren = context.assets.list(childAssetPath).orEmpty()
            val childTarget = File(destinationDir, child)

            if (nestedChildren.isEmpty()) {
                childTarget.parentFile?.mkdirs()
                context.assets.open(childAssetPath).use { input ->
                    childTarget.outputStream().use { output -> input.copyTo(output) }
                }
            } else {
                copyAssetDirectory(childAssetPath, childTarget)
            }
        }
    }

    private fun promoteStaging(metadata: SnapshotMetadata) {
        previousDir.deleteRecursively()

        if (currentDir.exists() && !currentDir.renameTo(previousDir)) {
            previousDir.deleteRecursively()
            currentDir.copyRecursively(previousDir, overwrite = true)
            currentDir.deleteRecursively()
        }

        if (!stagingDir.renameTo(currentDir)) {
            currentDir.deleteRecursively()
            stagingDir.copyRecursively(currentDir, overwrite = true)
            stagingDir.deleteRecursively()
        }

        writeMetadata(metadata)
        previousDir.deleteRecursively()
    }

    private fun readBundledVersion(): String =
        context.assets.open(PwaRuntime.bundledVersionAssetPath).bufferedReader().use { reader ->
            reader.readText().trim().ifEmpty { error("Bundled PWA version metadata is empty.") }
        }

    private fun versionServiceWorkerCache(snapshotDir: File, version: String) {
        val serviceWorkerFile = snapshotDir.resolve("service-worker.js")
        if (!serviceWorkerFile.isFile) return

        val cacheNamePattern = Regex("""const\s+CACHE_NAME\s*=\s*['"][^'"]+['"];""")
        val currentContent = serviceWorkerFile.readText()
        val updatedContent = if (cacheNamePattern.containsMatchIn(currentContent)) {
            cacheNamePattern.replace(
                currentContent,
                "const CACHE_NAME = 'trap-coach-cache-$version';"
            )
        } else {
            currentContent
        }

        serviceWorkerFile.writeText(updatedContent)
    }

    private fun writeMetadata(metadata: SnapshotMetadata) {
        metadataFile.writeText(
            JSONObject()
                .put("source", metadata.source)
                .put("activeVersion", metadata.activeVersion)
                .put("remoteRevision", metadata.remoteRevision)
                .toString()
        )
    }

    private fun readMetadata(): SnapshotMetadata? {
        if (!metadataFile.isFile) return null

        return runCatching {
            val json = JSONObject(metadataFile.readText())
            SnapshotMetadata(
                source = json.getString("source"),
                activeVersion = json.getString("activeVersion"),
                remoteRevision = json.optString("remoteRevision").ifBlank { null }
            )
        }.getOrNull()
    }

    private fun resetDirectory(directory: File) {
        directory.deleteRecursively()
        check(directory.mkdirs()) {
            "Could not create staging directory at ${directory.absolutePath}."
        }
    }

    private companion object {
        val lock = Any()
    }
}
