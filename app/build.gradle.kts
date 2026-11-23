import java.util.Properties

// The release key comes from local.properties (never committed):
//   plate.keystore=/path/to/plate-release.keystore
//   plate.storePassword=...  plate.keyAlias=...  plate.keyPassword=...
// Updates only install over an existing Plate if they're signed with the same key.
val signing = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.mrboora.plate"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.mrboora.plate"
        minSdk = 26
        targetSdk = 35
        versionCode = 33
        versionName = "2.10.1"
    }

    signingConfigs {
        signing.getProperty("plate.keystore")?.let { path ->
            create("plate") {
                storeFile = file(path)
                storePassword = signing.getProperty("plate.storePassword")
                keyAlias = signing.getProperty("plate.keyAlias")
                keyPassword = signing.getProperty("plate.keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // Plate's own key if configured, else the machine's debug key (fine for local testing only).
            signingConfig = signingConfigs.findByName("plate") ?: signingConfigs.getByName("debug")
        }
    }

    buildFeatures { compose = true }


    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }

    testOptions {
        unitTests.isIncludeAndroidResources = true
        unitTests.all { it.systemProperty("screenshots.dir", rootProject.file("docs/screenshots").absolutePath) }
    }
}

ksp { arg("room.schemaLocation", "$projectDir/schemas") }

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.play.services.code.scanner)
    implementation(libs.androidx.work.runtime.ktx)
    testImplementation(libs.junit)
    testImplementation(libs.org.json)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.test.manifest)
}
