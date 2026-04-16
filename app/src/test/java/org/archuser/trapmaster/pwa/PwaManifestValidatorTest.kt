package org.archuser.trapmaster.pwa

import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class PwaManifestValidatorTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun requireExpectedShortNameAcceptsTrapMasterManifest() {
        val snapshotDir = temporaryFolder.newFolder("snapshot")
        snapshotDir.resolve("manifest.json").writeText(
            """
            {
              "short_name": "TrapMaster"
            }
            """.trimIndent()
        )

        PwaManifestValidator.requireExpectedShortName(snapshotDir)
    }

    @Test
    fun requireExpectedShortNameRejectsUnexpectedShortName() {
        val snapshotDir = temporaryFolder.newFolder("snapshot")
        snapshotDir.resolve("manifest.json").writeText(
            """
            {
              "short_name": "Trap Master"
            }
            """.trimIndent()
        )

        val error = assertThrows(IllegalStateException::class.java) {
            PwaManifestValidator.requireExpectedShortName(snapshotDir)
        }

        assertTrue(error.message.orEmpty().contains("short_name must be \"TrapMaster\""))
        assertTrue(error.message.orEmpty().contains("\"Trap Master\""))
    }

    @Test
    fun requireExpectedShortNameRejectsMissingManifest() {
        val snapshotDir = temporaryFolder.newFolder("snapshot")

        val error = assertThrows(IllegalStateException::class.java) {
            PwaManifestValidator.requireExpectedShortName(snapshotDir)
        }

        assertTrue(error.message.orEmpty().contains("PWA manifest is missing"))
    }

    @Test
    fun requireExpectedShortNameRejectsMalformedManifest() {
        val snapshotDir = temporaryFolder.newFolder("snapshot")
        snapshotDir.resolve("manifest.json").writeText("{")

        val error = assertThrows(IllegalStateException::class.java) {
            PwaManifestValidator.requireExpectedShortName(snapshotDir)
        }

        assertTrue(error.message.orEmpty().contains("is not valid JSON"))
    }
}
