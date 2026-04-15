package org.archuser.trapmaster.pwa

import android.content.Context

private const val PREFERENCES_NAME = "trapmaster_launch_settings"
private const val CUSTOM_URL_KEY = "custom_pwa_url"

class PwaLaunchSettings(context: Context) {

    private val preferences = context.applicationContext.getSharedPreferences(
        PREFERENCES_NAME,
        Context.MODE_PRIVATE
    )

    fun currentCustomUrl(): String? =
        preferences.getString(CUSTOM_URL_KEY, null)?.trim()?.takeIf { it.isNotEmpty() }

    fun effectiveUrl(): String = currentCustomUrl() ?: PwaRuntime.launchUrl

    fun resetToDefault() {
        preferences.edit().remove(CUSTOM_URL_KEY).apply()
    }

    fun saveCustomUrl(url: String) {
        preferences.edit().putString(CUSTOM_URL_KEY, url).apply()
    }
}
