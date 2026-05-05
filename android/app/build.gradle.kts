plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

// Bumped by hand for each meaningful change. Treat as semver:
//   MAJOR — breaking UX or data changes
//   MINOR — features
//   PATCH — bug fixes / small tweaks
val appVersionName = "1.1.0"

// Build number is derived from git so it is monotonically increasing across
// pushes and unique per commit. Falls back to 1 outside a git checkout.
//
// Wrapped in providers.exec so Gradle tracks the call properly under the
// configuration cache — running ProcessBuilder at config time is rejected.
val gitCommitCount: Provider<Int> = providers.exec {
    commandLine("git", "rev-list", "--count", "HEAD")
    workingDir = rootProject.projectDir
    isIgnoreExitValue = true
}.standardOutput.asText.map { it.trim().toIntOrNull() ?: 1 }

val gitShortSha: Provider<String> = providers.exec {
    commandLine("git", "rev-parse", "--short=7", "HEAD")
    workingDir = rootProject.projectDir
    isIgnoreExitValue = true
}.standardOutput.asText.map { it.trim().ifBlank { "dev" } }

// Lets CI grab the resolved version without scraping the APK manifest:
//   ./gradlew :app:printVersion -q
//   →  base=1.1.0
//      code=42
//      sha=abc1234
tasks.register("printVersion") {
    val baseName = appVersionName
    val codeProvider = gitCommitCount
    val shaProvider = gitShortSha
    doLast {
        println("base=$baseName")
        println("code=${codeProvider.get()}")
        println("sha=${shaProvider.get()}")
    }
}

android {
    namespace = "com.intuiti.cardscanner"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.intuiti.cardscanner"
        minSdk = 26
        targetSdk = 35
        versionCode = gitCommitCount.get()
        versionName = "$appVersionName+${gitShortSha.get()}"

        // Make version metadata available at runtime (e.g. About line in settings).
        buildConfigField("String", "VERSION_NAME_BASE", "\"$appVersionName\"")
        buildConfigField("String", "GIT_SHA", "\"${gitShortSha.get()}\"")
        buildConfigField("int", "VERSION_CODE", "${gitCommitCount.get()}")

        vectorDrawables {
            useSupportLibrary = true
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
        debug {
            applicationIdSuffix = ".debug"
            isDebuggable = true
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.exifinterface)

    implementation(libs.tesseract4android)
    implementation(libs.okhttp)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.coil.compose)

    debugImplementation(libs.androidx.compose.ui.tooling)
}
