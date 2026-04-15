package org.archuser.trapmaster.pwa

import android.content.Context
import androidx.webkit.WebViewAssetLoader

class LocalPwaManager(context: Context) {

    private val appContext = context.applicationContext
    private val snapshotStore = PwaSnapshotStore(appContext)

    fun ensureBundledSnapshotInstalled(): SnapshotMetadata =
        snapshotStore.ensureBundledSnapshotInstalled()

    fun createAssetLoader(): WebViewAssetLoader =
        WebViewAssetLoader.Builder()
            .setDomain(PwaRuntime.localDomain)
            .addPathHandler(
                PwaRuntime.mountPath,
                WebViewAssetLoader.InternalStoragePathHandler(appContext, snapshotStore.activeDirectory())
            )
            .build()
}
