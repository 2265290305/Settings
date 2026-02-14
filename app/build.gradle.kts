plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

val accountProfileQueryUrl = (project.findProperty("ACCOUNT_PROFILE_QUERY_URL") as String?)
    ?: "https://api.example.com/account/profile"
val accountProfileUpdateUrl = (project.findProperty("ACCOUNT_PROFILE_UPDATE_URL") as String?)
    ?: "https://api.example.com/account/profile/update"
val accountAuthToken = (project.findProperty("ACCOUNT_AUTH_TOKEN") as String?)
    ?: ""
fun esc(value: String) = value.replace("\\", "\\\\").replace("\"", "\\\"")

android {
    namespace = "com.android.tv.settings"
    compileSdk = 36

    signingConfigs {
        create("release") {
            storeFile = file("android.jks")
            storePassword = "android"
            keyAlias = "android"
            keyPassword = "android"
        }
    }

    defaultConfig {
        applicationId = "com.oplus.engineernetwork"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
        buildConfigField("String", "ACCOUNT_PROFILE_QUERY_URL", "\"${esc(accountProfileQueryUrl)}\"")
        buildConfigField("String", "ACCOUNT_PROFILE_UPDATE_URL", "\"${esc(accountProfileUpdateUrl)}\"")
        buildConfigField("String", "ACCOUNT_AUTH_TOKEN", "\"${esc(accountAuthToken)}\"")

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("release")
        }
        debug {
            signingConfig = signingConfigs.getByName("release")
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
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.navigation.compose)
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.6.1")
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.ui.graphics)
    implementation("io.coil-kt:coil-compose:2.7.0")
    implementation("io.coil-kt:coil-svg:2.7.0")

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
