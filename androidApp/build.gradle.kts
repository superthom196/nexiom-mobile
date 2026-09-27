import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

// isIgnoreExitValue: a repo without commits (or a source tarball) still builds, as 0.0.0 (1).
val gitCommitCount = providers.exec { commandLine("git", "rev-list", "--count", "HEAD"); isIgnoreExitValue = true }
    .standardOutput.asText.map { it.trim().toIntOrNull() ?: 1 }
val gitDescribe = providers.exec { commandLine("git", "describe", "--tags", "--dirty", "--always"); isIgnoreExitValue = true }
    .standardOutput.asText.map { it.trim().removePrefix("v").ifBlank { "0.0.0" } }

// The release signing key. CI passes it through the environment; locally an optional
// signing.properties (gitignored) does the same job, so a build from this machine and a build
// from Actions install over each other. With neither, release is debug-signed.
val signingProps: Properties? =
    rootProject.file("signing.properties").takeIf { it.exists() }?.let { f ->
        Properties().apply { f.inputStream().use { load(it) } }
    }

fun signingValue(env: String, prop: String): String? =
    providers.environmentVariable(env).orNull ?: signingProps?.getProperty(prop)

val keystorePath = signingValue("NEXIOM_KEYSTORE_PATH", "storeFile")

android {
    namespace = "io.github.superthom196.nexiom"
    compileSdk = 37

    defaultConfig {
        applicationId = "io.github.superthom196.nexiom"
        minSdk = 31
        // 36, not 37: Android 17 blocks local-network access for apps targeting 37 until they ask for
        // ACCESS_LOCAL_NETWORK at runtime, and the whole app is local-network access.
        targetSdk = 36
        // versionName is the latest tag plus distance ("0.1.0", "0.1.0-3-gabc1234-dirty");
        // versionCode is the commit count, which only ever grows. Tag with `git tag vX.Y.Z`.
        versionCode = gitCommitCount.get()
        versionName = gitDescribe.get()
    }

    signingConfigs {
        if (keystorePath != null) {
            create("release") {
                storeFile = file(keystorePath)
                storePassword = signingValue("NEXIOM_KEYSTORE_PASSWORD", "storePassword")
                keyAlias = signingValue("NEXIOM_KEY_ALIAS", "keyAlias")
                keyPassword = signingValue("NEXIOM_KEY_PASSWORD", "keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(project(":shared"))
    implementation(libs.activity.compose)
    implementation(libs.compose.ui)
}
