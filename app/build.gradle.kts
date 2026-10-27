import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

/**
 * The version comes from the release tag in CI (`-PappVersion=1.2.0`, see .github/workflows/release.yml);
 * local builds are 1.0.0. versionCode is derived from it (1.2.3 → 1002003), so every release
 * installs over the previous one.
 */
val appVersion = (findProperty("appVersion") as String?) ?: "1.0.0"
val appVersionCode = appVersion.substringBefore('-').split('.').map { it.toInt() }
    .let { (it + listOf(0, 0, 0)).take(3) }
    .let { (major, minor, patch) -> major * 1_000_000 + minor * 1_000 + patch }

/** GitHub repository the in-app updater checks for new releases ("owner/name"); forks set their own. */
val updateRepository = (findProperty("updateRepository") as String?) ?: "dahunni/myrsvp"

/**
 * Release signing from environment variables (CI secrets) or a local, git-ignored
 * keystore.properties; without them release builds are unsigned. See docs/RELEASE.md.
 */
val signing: Map<String, String?> = run {
    val local = Properties().apply {
        val file = rootProject.file("keystore.properties")
        if (file.exists()) file.inputStream().use { load(it) }
    }
    listOf("RELEASE_KEYSTORE_PATH", "RELEASE_KEYSTORE_PASSWORD", "RELEASE_KEY_ALIAS", "RELEASE_KEY_PASSWORD")
        .associateWith { System.getenv(it) ?: local.getProperty(it) }
}
val canSignRelease = signing.values.all { !it.isNullOrEmpty() }

android {
    namespace = "de.dahunni.myrsvp"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "de.dahunni.myrsvp"
        minSdk = 26
        targetSdk = 37
        versionCode = appVersionCode
        versionName = appVersion
        buildConfigField("String", "UPDATE_REPOSITORY", "\"$updateRepository\"")

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (canSignRelease) {
            create("release") {
                storeFile = file(signing.getValue("RELEASE_KEYSTORE_PATH")!!)
                storePassword = signing["RELEASE_KEYSTORE_PASSWORD"]
                keyAlias = signing["RELEASE_KEY_ALIAS"]
                keyPassword = signing["RELEASE_KEY_PASSWORD"]
            }
        }
    }

    buildTypes {
        release {
            if (canSignRelease) signingConfig = signingConfigs.getByName("release")
            optimization {
                enable = true
                packageScope = setOf("androidx.**", "kotlin.**", "kotlinx.**")
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.jsoup)
    implementation(libs.kotlinx.serialization.json)
    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}