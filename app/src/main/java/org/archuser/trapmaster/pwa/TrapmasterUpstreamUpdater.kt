package org.archuser.trapmaster.pwa

import android.content.Context
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

private const val CONNECT_TIMEOUT_MS = 15_000
private const val READ_TIMEOUT_MS = 15_000
private const val CHECK_INTERVAL_MS = 6 * 60 * 60 * 1000L
private const val USER_AGENT = "TrapmasterAndroidContainer/1.0"
private const val PREFERENCES_NAME = "trapmaster_updater"
private const val LAST_CHECK_KEY = "last_check_ms"

sealed interface TrapmasterUpdateOutcome {
    data object Skipped : TrapmasterUpdateOutcome
    data class NoChange(val revision: String) : TrapmasterUpdateOutcome
    data class Updated(val revision: String) : TrapmasterUpdateOutcome
    data class Failed(val message: String, val cause: Throwable? = null) : TrapmasterUpdateOutcome
}

class TrapmasterUpstreamUpdater(context: Context) {

    private val appContext = context.applicationContext
    private val snapshotStore = PwaSnapshotStore(appContext)
    private val preferences = appContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    fun updateIfNeeded(force: Boolean): TrapmasterUpdateOutcome {
        return runCatching {
            snapshotStore.ensureBundledSnapshotInstalled()

            if (!force && !shouldCheckNow()) {
                return TrapmasterUpdateOutcome.Skipped
            }

            val latestRevision = fetchLatestRevision()
            markCheckedNow()

            val currentMetadata = snapshotStore.currentMetadata()
            if (currentMetadata?.activeVersion == latestRevision || currentMetadata?.remoteRevision == latestRevision) {
                return TrapmasterUpdateOutcome.NoChange(latestRevision)
            }

            val archiveFile = File(appContext.cacheDir, "trapmaster-$latestRevision.zip")
            val extractedDir = File(appContext.cacheDir, "trapmaster-$latestRevision")

            archiveFile.delete()
            extractedDir.deleteRecursively()

            try {
                downloadArchive(latestRevision, archiveFile)
                archiveFile.inputStream().use { input ->
                    RepositoryZipExtractor.extract(input, extractedDir)
                }

                snapshotStore.installRemoteSnapshot(extractedDir, latestRevision)
            } finally {
                archiveFile.delete()
                extractedDir.deleteRecursively()
            }

            TrapmasterUpdateOutcome.Updated(latestRevision)
        }.getOrElse { error ->
            TrapmasterUpdateOutcome.Failed(
                message = error.message ?: "Unknown update error.",
                cause = error
            )
        }
    }

    private fun shouldCheckNow(): Boolean {
        val lastCheck = preferences.getLong(LAST_CHECK_KEY, 0L)
        return System.currentTimeMillis() - lastCheck >= CHECK_INTERVAL_MS
    }

    private fun markCheckedNow() {
        preferences.edit().putLong(LAST_CHECK_KEY, System.currentTimeMillis()).apply()
    }

    private fun fetchLatestRevision(): String {
        val connection = openConnection(
            url = URL("https://api.github.com/repos/firebadnofire/trapmaster/commits/main"),
            accept = "application/vnd.github+json"
        )

        connection.useInputStream("upstream revision lookup") { input ->
            val response = input.bufferedReader().use { reader -> reader.readText() }
            return JSONObject(response).getString("sha")
        }
    }

    private fun downloadArchive(revision: String, destination: File) {
        val connection = openConnection(
            url = URL("https://github.com/firebadnofire/trapmaster/archive/$revision.zip"),
            accept = "application/zip"
        )

        connection.useInputStream("upstream archive download") { input ->
            destination.outputStream().use { output -> input.copyTo(output) }
        }
    }

    private fun openConnection(url: URL, accept: String): HttpURLConnection {
        return (url.openConnection() as HttpURLConnection).apply {
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            instanceFollowRedirects = true
            requestMethod = "GET"
            setRequestProperty("Accept", accept)
            setRequestProperty("User-Agent", USER_AGENT)
        }
    }

    private inline fun <T> HttpURLConnection.useInputStream(
        actionName: String,
        block: (java.io.InputStream) -> T
    ): T {
        return try {
            val responseCode = responseCode
            check(responseCode in 200..299) {
                "$actionName failed with HTTP $responseCode."
            }
            inputStream.use(block)
        } finally {
            disconnect()
        }
    }
}
