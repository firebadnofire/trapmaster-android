package org.archuser.trapmaster

import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.View
import android.webkit.JsResult
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.webkit.WebResourceErrorCompat
import androidx.webkit.WebViewAssetLoader
import androidx.webkit.WebViewClientCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import org.archuser.trapmaster.databinding.ActivityMainBinding
import org.archuser.trapmaster.pwa.LocalPwaManager
import org.archuser.trapmaster.pwa.PwaLaunchSettings
import org.archuser.trapmaster.pwa.PwaRuntime
import org.archuser.trapmaster.pwa.RemotePwaManifestChecker
import org.archuser.trapmaster.pwa.TrapmasterUpdateOutcome
import org.archuser.trapmaster.pwa.TrapmasterUpdateScheduler
import org.archuser.trapmaster.pwa.TrapmasterUpstreamUpdater
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var localPwaManager: LocalPwaManager
    private lateinit var launchSettings: PwaLaunchSettings
    private val startupExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private val updater by lazy { TrapmasterUpstreamUpdater(applicationContext) }
    private var lastAttemptedUrl: String? = null

    @Volatile
    private var mainFrameLoadFailed = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        localPwaManager = LocalPwaManager(this)
        launchSettings = PwaLaunchSettings(this)
        binding.beginButton.setOnClickListener { launchSelectedPwa() }
        binding.advancedSettingsButton.setOnClickListener { showAdvancedSettingsDialog() }
        binding.retryButton.setOnClickListener { retryLaunch() }
        binding.changePwaUrlButton.setOnClickListener {
            showAdvancedSettingsDialog(launchAfterChange = true)
        }

        updateLaunchTargetSummary()
        showStartScreen()
    }

    override fun onDestroy() {
        binding.webView.stopLoading()
        binding.webView.destroy()
        startupExecutor.shutdownNow()
        super.onDestroy()
    }

    private fun installAndLoadBundledSnapshot() {
        showLoading(
            title = getString(R.string.loading_trapmaster),
            message = getString(R.string.loading_details)
        )

        startupExecutor.execute {
            val result = runCatching {
                localPwaManager.ensureBundledSnapshotInstalled()
                localPwaManager.createAssetLoader()
            }

            runOnUiThread {
                result.onSuccess { assetLoader ->
                    configureWebView(assetLoader)
                    binding.webView.loadUrl(PwaRuntime.launchUrl)
                    TrapmasterUpdateScheduler.schedule(applicationContext)
                    startBackgroundRefresh()
                }.onFailure { error ->
                    val detail = error.message?.takeIf(String::isNotBlank)
                        ?: getString(R.string.loading_failed_message)
                    showError(
                        title = getString(R.string.loading_failed_title),
                        message = getString(R.string.loading_failed_reason, detail)
                    )
                }
            }
        }
    }

    private fun loadConfiguredRemoteUrl(url: String) {
        showLoading(
            title = getString(R.string.loading_trapmaster),
            message = getString(R.string.checking_remote_manifest_details, url)
        )

        startupExecutor.execute {
            val result = runCatching {
                RemotePwaManifestChecker.requireExpectedShortName(url)
            }

            runOnUiThread {
                result.onSuccess {
                    configureWebView(assetLoader = null)
                    binding.webView.loadUrl(url)
                    TrapmasterUpdateScheduler.schedule(applicationContext)
                    startBackgroundRefresh()
                }.onFailure { error ->
                    val detail = error.message?.takeIf(String::isNotBlank)
                        ?: getString(R.string.loading_failed_message)
                    showError(
                        title = getString(R.string.loading_failed_title),
                        message = getString(R.string.loading_failed_reason, detail)
                    )
                }
            }
        }
    }

    private fun configureWebView(assetLoader: WebViewAssetLoader?) {
        binding.webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            allowFileAccess = false
            allowContentAccess = false
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            setSupportMultipleWindows(false)
        }

        binding.webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                if (newProgress >= 100 && !mainFrameLoadFailed) {
                    showWebView()
                }
            }

            override fun onJsConfirm(
                view: WebView?,
                url: String?,
                message: String?,
                result: JsResult
            ): Boolean {
                showJavaScriptConfirm(message.orEmpty(), result)
                return true
            }
        }

        binding.webView.webViewClient = object : WebViewClientCompat() {
            override fun shouldInterceptRequest(
                view: WebView,
                request: WebResourceRequest
            ): WebResourceResponse? = assetLoader?.shouldInterceptRequest(request.url)

            @Deprecated("Deprecated in Java")
            override fun shouldInterceptRequest(view: WebView, url: String): WebResourceResponse? =
                assetLoader?.shouldInterceptRequest(Uri.parse(url))

            override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
                mainFrameLoadFailed = false
                val message = if (lastAttemptedUrl == PwaRuntime.launchUrl) {
                    getString(R.string.loading_details)
                } else {
                    getString(R.string.loading_remote_details, lastAttemptedUrl.orEmpty())
                }
                showLoading(title = getString(R.string.loading_trapmaster), message = message)
            }

            override fun onPageFinished(view: WebView, url: String?) {
                if (!mainFrameLoadFailed) {
                    showWebView()
                }
            }

            override fun onReceivedError(
                view: WebView,
                request: WebResourceRequest,
                error: WebResourceErrorCompat
            ) {
                if (!request.isForMainFrame) return

                mainFrameLoadFailed = true
                val detail = error.description?.toString()?.takeIf(String::isNotBlank)
                    ?: getString(R.string.loading_failed_message)
                showError(
                    title = getString(R.string.loading_failed_title),
                    message = getString(R.string.loading_failed_reason, detail)
                )
            }
        }
    }

    private fun showLoading(title: String, message: String) {
        binding.loadingTitle.text = title
        binding.loadingMessage.text = message
        binding.startContainer.visibility = View.GONE
        binding.loadingContainer.visibility = View.VISIBLE
        binding.errorContainer.visibility = View.GONE
        binding.webView.visibility = View.INVISIBLE
    }

    private fun showError(title: String, message: String) {
        binding.errorTitle.text = title
        binding.errorMessage.text = message
        binding.startContainer.visibility = View.GONE
        binding.loadingContainer.visibility = View.GONE
        binding.errorContainer.visibility = View.VISIBLE
        binding.webView.visibility = View.INVISIBLE
    }

    private fun showWebView() {
        binding.startContainer.visibility = View.GONE
        binding.loadingContainer.visibility = View.GONE
        binding.errorContainer.visibility = View.GONE
        binding.webView.visibility = View.VISIBLE
    }

    private fun showStartScreen() {
        binding.startContainer.visibility = View.VISIBLE
        binding.loadingContainer.visibility = View.GONE
        binding.errorContainer.visibility = View.GONE
        binding.webView.visibility = View.INVISIBLE
    }

    private fun startBackgroundRefresh() {
        startupExecutor.execute {
            when (val outcome = updater.updateIfNeeded(force = false)) {
                is TrapmasterUpdateOutcome.Failed -> {
                    Log.w("TrapmasterUpdate", "Launch-time update failed: ${outcome.message}", outcome.cause)
                }

                else -> Unit
            }
        }
    }

    private fun showJavaScriptConfirm(message: String, result: JsResult) {
        if (isFinishing || isDestroyed) {
            result.cancel()
            return
        }

        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.app_name)
            .setMessage(message)
            .setPositiveButton(android.R.string.ok) { _, _ -> result.confirm() }
            .setNegativeButton(android.R.string.cancel) { _, _ -> result.cancel() }
            .setOnCancelListener { result.cancel() }
            .show()
    }

    private fun launchSelectedPwa() {
        val url = launchSettings.effectiveUrl()
        lastAttemptedUrl = url
        if (url == PwaRuntime.launchUrl) {
            installAndLoadBundledSnapshot()
        } else {
            loadConfiguredRemoteUrl(url)
        }
    }

    private fun retryLaunch() {
        val url = lastAttemptedUrl
        if (url == null) {
            showStartScreen()
            return
        }

        if (url == PwaRuntime.launchUrl) {
            installAndLoadBundledSnapshot()
        } else {
            loadConfiguredRemoteUrl(url)
        }
    }

    private fun updateLaunchTargetSummary() {
        val customUrl = launchSettings.currentCustomUrl()
        binding.launchTargetSummary.text = if (customUrl == null) {
            getString(R.string.launch_target_default)
        } else {
            getString(R.string.launch_target_custom, customUrl)
        }
    }

    private fun showAdvancedSettingsDialog(launchAfterChange: Boolean = false) {
        if (isFinishing || isDestroyed) return

        val contentView = layoutInflater.inflate(R.layout.dialog_advanced_settings, null)
        val inputLayout = contentView.findViewById<TextInputLayout>(R.id.pwaUrlInputLayout)
        val input = contentView.findViewById<TextInputEditText>(R.id.pwaUrlInput)
        input.setText(launchSettings.currentCustomUrl().orEmpty())
        input.setSelection(input.text?.length ?: 0)

        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle(R.string.advanced_settings_title)
            .setView(contentView)
            .setNegativeButton(android.R.string.cancel, null)
            .setNeutralButton(R.string.advanced_settings_reset, null)
            .setPositiveButton(R.string.advanced_settings_save, null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val candidate = input.text?.toString().orEmpty().trim()
                val validatedUrl = validateCustomPwaUrl(candidate)

                if (validatedUrl == null) {
                    inputLayout.error = getString(R.string.advanced_settings_invalid_url)
                    return@setOnClickListener
                }

                inputLayout.error = null
                launchSettings.saveCustomUrl(validatedUrl)
                updateLaunchTargetSummary()
                dialog.dismiss()
                if (launchAfterChange) {
                    launchSelectedPwa()
                }
            }

            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener {
                inputLayout.error = null
                launchSettings.resetToDefault()
                updateLaunchTargetSummary()
                dialog.dismiss()
                if (launchAfterChange) {
                    launchSelectedPwa()
                }
            }
        }

        dialog.show()
    }

    private fun validateCustomPwaUrl(candidate: String): String? {
        if (candidate.isBlank()) {
            return null
        }

        val parsed = Uri.parse(candidate)
        val scheme = parsed.scheme?.lowercase()
        val host = parsed.host

        if (scheme != "https" || host.isNullOrBlank()) {
            return null
        }

        return parsed.toString()
    }
}
