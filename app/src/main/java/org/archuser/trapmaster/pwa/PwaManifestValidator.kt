package org.archuser.trapmaster.pwa

import org.json.JSONException
import org.json.JSONObject
import java.io.File

object PwaManifestValidator {

    private const val MANIFEST_FILE_NAME = "manifest.json"
    private const val SHORT_NAME_KEY = "short_name"
    internal const val EXPECTED_SHORT_NAME = "TrapMaster"

    fun requireExpectedShortName(snapshotDir: File) {
        val manifestFile = snapshotDir.resolve(MANIFEST_FILE_NAME)
        check(manifestFile.isFile) {
            "PWA manifest is missing at ${manifestFile.absolutePath}."
        }

        requireExpectedShortName(
            manifestJson = manifestFile.readText(),
            manifestLocation = manifestFile.absolutePath
        )
    }

    fun requireExpectedShortName(manifestJson: String, manifestLocation: String) {
        val shortName = readShortName(manifestJson, manifestLocation)
        check(shortName == EXPECTED_SHORT_NAME) {
            "PWA manifest short_name must be \"$EXPECTED_SHORT_NAME\" but was ${formatActualValue(shortName)}."
        }
    }

    private fun readShortName(manifestJson: String, manifestLocation: String): String {
        val manifest = try {
            JSONObject(manifestJson)
        } catch (error: JSONException) {
            throw IllegalStateException(
                "PWA manifest at $manifestLocation is not valid JSON.",
                error
            )
        }

        return manifest.optString(SHORT_NAME_KEY).trim()
    }

    private fun formatActualValue(value: String): String =
        if (value.isBlank()) {
            "missing"
        } else {
            "\"$value\""
        }
}
