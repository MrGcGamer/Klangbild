import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.roborazzi)
}

// versionCode counts up automatically: minutes between 2026-01-01 and the HEAD commit.
// Deterministic per commit, monotonic for a linear history, works locally and in CI
// (a shallow clone still has HEAD). See docs/DECISIONS.md.
val headCommitEpochSeconds: Long = providers.exec {
    commandLine("git", "log", "-1", "--format=%ct")
    isIgnoreExitValue = true
}.standardOutput.asText.get().trim().toLongOrNull() ?: 1767225600L
val headCommitShortSha: String = providers.exec {
    commandLine("git", "rev-parse", "--short=7", "HEAD")
    isIgnoreExitValue = true
}.standardOutput.asText.get().trim().ifEmpty { "local" }
val versionEpochSeconds = 1767225600L // 2026-01-01T00:00:00Z
val autoVersionCode = ((headCommitEpochSeconds - versionEpochSeconds) / 60L).coerceAtLeast(1L).toInt()
// A release build from a tag gets the tag's name ("1.0.0", passed by CI); every other build
// is named after its commit date and hash.
val autoVersionName: String = providers.gradleProperty("klangbild.versionName").orNull?.takeIf { it.isNotBlank() }
    ?: (
        DateTimeFormatter.ofPattern("yyyy.MM.dd")
            .withZone(ZoneOffset.UTC)
            .format(Instant.ofEpochSecond(headCommitEpochSeconds)) + " ($headCommitShortSha)"
        )

android {
    namespace = "io.github.hazymorning.klangbild"
    compileSdk = 37

    defaultConfig {
        applicationId = "io.github.hazymorning.klangbild"
        minSdk = 31
        targetSdk = 36
        versionCode = autoVersionCode
        versionName = autoVersionName
        // No abiFilters on purpose: the app ships no native code of its own, and any
        // native code pulled in by libraries keeps all ABIs including armeabi-v7a.
    }

    signingConfigs {
        create("release") {
            // A dedicated, project-only key that is committed on purpose so that every
            // build (local or CI) is signed identically and `adb install -r` always works.
            // See docs/DECISIONS.md – this key protects nothing but this sideloaded app.
            storeFile = rootProject.file("keystore/klangbild-release.jks")
            storePassword = "klangbild-sideload"
            keyAlias = "klangbild"
            keyPassword = "klangbild-sideload"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = signingConfigs.getByName("release")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
        debug {
            applicationIdSuffix = ".debug"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            all {
                it.maxHeapSize = "3g"
                // Two JVMs share the Robolectric classes; the screenshot classes run side by side.
                it.maxParallelForks = 2
                it.systemProperty("robolectric.graphicsMode", "NATIVE")
                // Robolectric fetches its android-all jars itself; use the same Maven Central
                // mirror as Gradle (repo1.maven.org rate-limits shared egress IPs).
                it.systemProperty(
                    "robolectric.dependency.repo.url",
                    "https://maven-central.storage-download.googleapis.com/maven2",
                )
            }
        }
    }

    packaging {
        resources {
            excludes += setOf(
                "/META-INF/{AL2.0,LGPL2.1}",
                "/META-INF/LICENSE*",
                "/META-INF/NOTICE*",
                "/META-INF/*.kotlin_module",
                "/META-INF/versions/9/previous-compilation-data.bin",
            )
        }
    }
}

roborazzi {
    outputDir.set(layout.buildDirectory.dir("screenshots"))
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.service)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.profileinstaller)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.text)
    implementation(libs.compose.foundation)
    implementation(libs.compose.animation)
    implementation(libs.compose.ui.tooling.preview)
    debugImplementation(libs.compose.ui.tooling)

    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.okhttp)
    implementation(libs.zxing.core)
    implementation(libs.suncalc)

    testImplementation(libs.junit)
    testImplementation(libs.truth)
    testImplementation(libs.turbine)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.okhttp.mockwebserver)
    testImplementation(libs.robolectric)
    testImplementation(libs.roborazzi)
    testImplementation(libs.roborazzi.compose)
    testImplementation(libs.roborazzi.junit.rule)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.test.ext.junit)
    testImplementation(platform(libs.compose.bom))
    testImplementation(libs.compose.ui.test.junit4)
    debugImplementation(libs.compose.ui.test.manifest)
}
