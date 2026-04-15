package org.archuser.trapmaster.pwa

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RepositoryZipExtractorTest {

    @Test
    fun normalizedSegmentsRejectsTraversalSegments() {
        assertNull(RepositoryZipExtractor.normalizedSegments("../index.html"))
        assertNull(RepositoryZipExtractor.normalizedSegments("repo/../../evil.txt"))
    }

    @Test
    fun resolveRelativePathDropsArchiveRootPrefix() {
        val relativePath = RepositoryZipExtractor.resolveRelativePath(
            segments = listOf("trapmaster-main", "icons", "icon-192x192.png"),
            archiveRoot = "trapmaster-main"
        )

        assertEquals("icons/icon-192x192.png", relativePath)
    }

    @Test
    fun resolveRelativePathSkipsArchiveRootDirectoryEntry() {
        val relativePath = RepositoryZipExtractor.resolveRelativePath(
            segments = listOf("trapmaster-main"),
            archiveRoot = "trapmaster-main"
        )

        assertNull(relativePath)
    }
}
