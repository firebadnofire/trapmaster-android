package org.archuser.trapmaster.pwa

import java.io.BufferedInputStream
import java.io.File
import java.io.InputStream
import java.util.zip.ZipInputStream

object RepositoryZipExtractor {

    fun extract(zipStream: InputStream, targetDir: File) {
        targetDir.deleteRecursively()
        check(targetDir.mkdirs()) {
            "Could not create extraction directory at ${targetDir.absolutePath}."
        }

        ZipInputStream(BufferedInputStream(zipStream)).use { input ->
            var archiveRoot: String? = null

            while (true) {
                val entry = input.nextEntry ?: break
                val segments = normalizedSegments(entry.name)

                if (segments == null) {
                    input.closeEntry()
                    continue
                }

                if (archiveRoot == null) {
                    archiveRoot = when {
                        entry.isDirectory && segments.size == 1 -> segments.firstOrNull()
                        segments.size > 1 -> segments.first()
                        else -> null
                    }
                }

                val relativePath = resolveRelativePath(segments, archiveRoot)
                if (relativePath == null) {
                    input.closeEntry()
                    continue
                }

                val output = File(targetDir, relativePath)
                if (entry.isDirectory) {
                    output.mkdirs()
                } else {
                    output.parentFile?.mkdirs()
                    output.outputStream().use { outputStream -> input.copyTo(outputStream) }
                }

                input.closeEntry()
            }
        }

        check(targetDir.resolve("index.html").isFile) {
            "Downloaded PWA snapshot does not contain index.html."
        }
    }

    internal fun normalizedSegments(entryName: String): List<String>? {
        val normalized = entryName.replace('\\', '/').trim('/')
        if (normalized.isEmpty()) return emptyList()

        val segments = normalized.split('/').filter(String::isNotEmpty)
        if (segments.any { it == "." || it == ".." }) return null
        return segments
    }

    internal fun resolveRelativePath(segments: List<String>, archiveRoot: String?): String? {
        if (segments.isEmpty()) return null

        val relativeSegments = when {
            archiveRoot != null && segments.first() == archiveRoot -> segments.drop(1)
            archiveRoot != null -> return null
            else -> segments
        }

        if (relativeSegments.isEmpty()) return null
        return relativeSegments.joinToString("/")
    }
}
