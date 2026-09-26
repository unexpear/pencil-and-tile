import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

// Keep upload credentials outside the project: either the four PENCILTILE_* environment variables, or
// ~/.pencil-and-tile/upload.properties (storeFile, storePassword, keyAlias, keyPassword). Without either,
// release artifacts stay unsigned for local checks and cannot be uploaded to Play.
val uploadProperties = Properties().apply {
    val file = File(System.getProperty("user.home"), ".pencil-and-tile/upload.properties")
    if (file.isFile) file.inputStream().use { load(it) }
}
fun uploadSetting(env: String, key: String): String? = providers.environmentVariable(env).orNull ?: uploadProperties.getProperty(key)
val uploadStorePath = uploadSetting("PENCILTILE_KEYSTORE_PATH", "storeFile")?.let {
    val f = File(it); if (f.isAbsolute) f.path else File(System.getProperty("user.home"), ".pencil-and-tile/$it").path
}
val uploadStorePassword = uploadSetting("PENCILTILE_KEYSTORE_PASSWORD", "storePassword")
val uploadKeyAlias = uploadSetting("PENCILTILE_KEY_ALIAS", "keyAlias")
val uploadKeyPassword = uploadSetting("PENCILTILE_KEY_PASSWORD", "keyPassword")
val uploadCredentials = listOf(uploadStorePath, uploadStorePassword, uploadKeyAlias, uploadKeyPassword)
require(uploadCredentials.all { it == null } || uploadCredentials.all { !it.isNullOrBlank() }) {
    "Upload signing is only partly configured: set all four values or none."
}

android {
    namespace = "com.simplegamegen.sudoku"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.simplegamegen.puzzles"
        minSdk = 26
        targetSdk = 36
        versionCode = 2
        versionName = "1.1.0"
    }

    signingConfigs {
        if (uploadStorePath != null) {
            create("upload") {
                storeFile = file(uploadStorePath)
                storePassword = uploadStorePassword
                keyAlias = uploadKeyAlias
                keyPassword = uploadKeyPassword
            }
        }
    }
    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.findByName("upload")
        }
    }
    buildFeatures {
        compose = true
    }
    testOptions {
        unitTests.all {
            it.useJUnitPlatform()
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation(project(":sudoku-engine"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.navigation.compose)
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    implementation(libs.datastore.preferences)
    implementation(libs.onnxruntime.android)
    ksp(libs.room.compiler)
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.kotlinx.coroutines.test)
    testRuntimeOnly(libs.junit.platform.launcher)
}
