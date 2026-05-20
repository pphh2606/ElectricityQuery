import java.util.Properties
import java.io.FileInputStream
import java.io.FileWriter

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

// ============================================================
// Auto-increment versionCode on every build
// Reads from version.properties, increments after successful build
// ============================================================
val versionPropsFile = file("version.properties")
val versionProps = Properties()

if (versionPropsFile.exists()) {
    versionProps.load(FileInputStream(versionPropsFile))
}

val currentVersionCode = (versionProps["VERSION_CODE"] as? String)?.toIntOrNull() ?: 1

android {
    namespace = "edu.cqwu.electricity"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "edu.cqwu.electricity"
        minSdk = 23
        targetSdk = 36
        versionCode = currentVersionCode
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        debug {

        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
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
    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.security.crypto)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.core)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.okhttp)
    implementation(libs.gson)
    implementation(libs.coroutines.android)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.coil.compose)
    implementation(libs.androidx.swiperefreshlayout)
    implementation(libs.zxing.core)
    implementation(libs.material.kolor)
    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}

// ============================================================
// Auto-increment versionCode on every build (post-build task)
// ============================================================
tasks.register("incrementVersionCode") {
    doLast {
        val newVersionCode = currentVersionCode + 1
        versionProps["VERSION_CODE"] = newVersionCode.toString()
        versionProps.store(FileWriter(versionPropsFile), "Auto-incremented by build")
        logger.lifecycle("✅ versionCode auto-incremented: $currentVersionCode → $newVersionCode")
    }
}

// Hook into all assemble tasks (covers both debug and release builds)
tasks.matching { it.name.startsWith("assemble") }.configureEach {
    finalizedBy("incrementVersionCode")
}
