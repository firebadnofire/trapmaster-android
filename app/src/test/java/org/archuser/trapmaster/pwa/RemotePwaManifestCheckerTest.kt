package org.archuser.trapmaster.pwa

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class RemotePwaManifestCheckerTest {

    @Test
    fun resolveManifestUrlUsesDirectoryUrl() {
        val manifestUrl = RemotePwaManifestChecker
            .resolveManifestUrl("https://archuser.org/trapmaster/")
            .toString()

        assertEquals("https://archuser.org/trapmaster/manifest.json", manifestUrl)
    }

    @Test
    fun resolveManifestUrlUsesIndexHtmlParentDirectory() {
        val manifestUrl = RemotePwaManifestChecker
            .resolveManifestUrl("https://archuser.org/trapmaster/index.html")
            .toString()

        assertEquals("https://archuser.org/trapmaster/manifest.json", manifestUrl)
    }

    @Test
    fun resolveManifestUrlTreatsExtensionlessPathAsDirectory() {
        val manifestUrl = RemotePwaManifestChecker
            .resolveManifestUrl("https://archuser.org/trapmaster")
            .toString()

        assertEquals("https://archuser.org/trapmaster/manifest.json", manifestUrl)
    }

    @Test
    fun resolveManifestUrlRejectsHttpUrl() {
        assertThrows(IllegalStateException::class.java) {
            RemotePwaManifestChecker.resolveManifestUrl("http://archuser.org/trapmaster/")
        }
    }
}
