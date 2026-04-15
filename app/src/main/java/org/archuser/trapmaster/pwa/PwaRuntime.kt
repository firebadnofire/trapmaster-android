package org.archuser.trapmaster.pwa

object PwaRuntime {
    const val localDomain = "appassets.androidplatform.net"
    const val mountPath = "/trapmaster/"
    const val launchUrl = "https://$localDomain${mountPath}index.html"
    const val bundledAssetRoot = "pwa"
    const val bundledVersionAssetPath = "pwa-metadata/version.txt"
}
