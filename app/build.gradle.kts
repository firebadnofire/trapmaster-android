import java.io.File
import java.security.MessageDigest

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

fun computeDirectoryHash(root: File): String {
    val digest = MessageDigest.getInstance("SHA-256")
    val separator = byteArrayOf(0)

    root.walkTopDown()
        .filter(File::isFile)
        .sortedBy { it.relativeTo(root).invariantSeparatorsPath }
        .forEach { file ->
            digest.update(file.relativeTo(root).invariantSeparatorsPath.toByteArray(Charsets.UTF_8))
            digest.update(separator)

            file.inputStream().use { input ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    digest.update(buffer, 0, read)
                }
            }

            digest.update(separator)
        }

    return digest.digest().joinToString(separator = "") { byte -> "%02x".format(byte) }
}

val upstreamPwaSourceDir = rootProject.layout.projectDirectory.dir("trapmaster-upstream")
val bundledPwaAssetsRoot = layout.buildDirectory.dir("generated/bundledPwaAssets")
val bundledPwaWebRoot = bundledPwaAssetsRoot.map { it.dir("pwa") }
val bundledPwaMetadataRoot = bundledPwaAssetsRoot.map { it.dir("pwa-metadata") }

val prepareBundledPwaAssets by tasks.registering(Sync::class) {
    from(upstreamPwaSourceDir) {
        exclude(
            ".git",
            ".git/**",
            ".github",
            ".github/**",
            ".gitignore",
            ".DS_Store"
        )
    }
    into(bundledPwaWebRoot)

    doFirst {
        val sourceDir = upstreamPwaSourceDir.asFile
        check(sourceDir.exists() && sourceDir.isDirectory) {
            "Missing upstream PWA source at ${sourceDir.absolutePath}. Expected the provided trapmaster-upstream clone."
        }
    }

    doLast {
        val webRoot = bundledPwaWebRoot.get().asFile
        val metadataDir = bundledPwaMetadataRoot.get().asFile
        metadataDir.mkdirs()
        metadataDir.resolve("version.txt").writeText(computeDirectoryHash(webRoot))
    }
}

android {
    namespace = "org.archuser.trapmaster"
    compileSdk = 36

    defaultConfig {
        applicationId = "org.archuser.trapmaster"
        minSdk = 24
        targetSdk = 36
        versionCode = 2
        versionName = "1.1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("debug")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        viewBinding = true
    }

    sourceSets.getByName("main").assets.srcDir(bundledPwaAssetsRoot)
}

tasks.named("preBuild").configure {
    dependsOn(prepareBundledPwaAssets)
}

dependencies {

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.lifecycle.livedata.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.navigation.fragment.ktx)
    implementation(libs.androidx.navigation.ui.ktx)
    implementation(libs.androidx.webkit)
    implementation(libs.androidx.work.runtime.ktx)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}
