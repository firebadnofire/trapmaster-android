package org.archuser.trapmaster.pwa

import java.net.URI
import java.net.URL
import javax.net.ssl.HttpsURLConnection

private const val REMOTE_CONNECT_TIMEOUT_MS = 15_000
private const val REMOTE_READ_TIMEOUT_MS = 15_000
private const val REMOTE_USER_AGENT = "TrapmasterAndroidContainer/1.0"

object RemotePwaManifestChecker {

    fun requireExpectedShortName(launchUrl: String) {
        val manifestUrl = resolveManifestUrl(launchUrl)
        val connection = openConnection(manifestUrl)

        connection.useInputStream("PWA manifest lookup") { input ->
            val manifestJson = input.bufferedReader().use { reader -> reader.readText() }
            PwaManifestValidator.requireExpectedShortName(
                manifestJson = manifestJson,
                manifestLocation = manifestUrl.toString()
            )
        }
    }

    internal fun resolveManifestUrl(launchUrl: String): URL {
        val launchUri = URI(launchUrl)
        check(launchUri.scheme.equals("https", ignoreCase = true) && !launchUri.host.isNullOrBlank()) {
            "Configured PWA URL must be an absolute HTTPS URL."
        }

        val path = launchUri.path.orEmpty().ifBlank { "/" }
        val directoryPath = when {
            path.endsWith("/") -> path
            path.substringAfterLast('/').contains(".") -> path.substringBeforeLast('/') + "/"
            else -> "$path/"
        }

        return URI("https", launchUri.authority, directoryPath, null, null)
            .resolve("manifest.json")
            .toURL()
    }

    private fun openConnection(url: URL): HttpsURLConnection {
        check(url.protocol.equals("https", ignoreCase = true)) {
            "PWA manifest lookup requires HTTPS."
        }

        return (url.openConnection() as HttpsURLConnection).apply {
            connectTimeout = REMOTE_CONNECT_TIMEOUT_MS
            readTimeout = REMOTE_READ_TIMEOUT_MS
            instanceFollowRedirects = true
            requestMethod = "GET"
            setRequestProperty("Accept", "application/manifest+json, application/json;q=0.9, */*;q=0.1")
            setRequestProperty("User-Agent", REMOTE_USER_AGENT)
        }
    }

    private inline fun <T> HttpsURLConnection.useInputStream(
        actionName: String,
        block: (java.io.InputStream) -> T
    ): T {
        return try {
            val responseCode = responseCode
            check(url.protocol.equals("https", ignoreCase = true)) {
                "$actionName cannot follow a non-HTTPS redirect."
            }
            check(responseCode in 200..299) {
                "$actionName failed with HTTP $responseCode."
            }
            inputStream.use(block)
        } finally {
            disconnect()
        }
    }
}
