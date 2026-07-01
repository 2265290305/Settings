plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)

    id("org.jetbrains.kotlin.android")
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
        applicationId = "com.android.speaker.settings"
        minSdk = 24
        targetSdk = 34
        versionCode = 2
        versionName = "2.0"
        buildConfigField("String", "ACCOUNT_PROFILE_QUERY_URL", "\"${esc(accountProfileQueryUrl)}\"")
        buildConfigField("String", "ACCOUNT_PROFILE_UPDATE_URL", "\"${esc(accountProfileUpdateUrl)}\"")
        buildConfigField("String", "ACCOUNT_AUTH_TOKEN", "\"${esc(accountAuthToken)}\"")

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
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
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
        buildConfig = true
        aidl = true
    }


}

kotlin {
    jvmToolchain(17)
}

// 把 framework-hidden.jar 前置到编译 classpath 最前面，使 BluetoothDevice.removeBond() 等
// @SystemApi 从 stub 解析（否则会被 SDK 的 android.jar 公开签名遮蔽，导致 “cannot find symbol”）。
val frameworkHidden = files("libs/framework-hidden.jar")
tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    libraries.from(frameworkHidden)
}
tasks.withType<JavaCompile>().configureEach {
    options.compilerArgs.addAll(listOf("-Xbootclasspath/p:${frameworkHidden.asPath}"))
}


dependencies {
    // AOSP framework stub（system_current/android.jar，含 @SystemApi/hidden：removeBond()、cancelBondProcess() 等）。
    // 仅编译期可见、不打包进 APK（compileOnly）；运行期由系统 framework 提供真身。
    // 本应用为 platform 签名 + priv-app，故可直接调用这些隐藏 API，无需反射。
    // 注意：必须前置到 bootClasspath（见下方 tasks.withType 的 hook），否则会被 android.jar 的公开签名遮蔽。
    compileOnly(files("libs/framework-hidden.jar"))
    implementation(files("libs/iotsdk.aar"))
    implementation("com.google.code.gson:gson:2.10.1")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.eclipse.paho:org.eclipse.paho.client.mqttv3:1.2.5")
    implementation("org.eclipse.paho:org.eclipse.paho.android.service:1.1.1")
    implementation("androidx.localbroadcastmanager:localbroadcastmanager:1.0.0")

    // 一键检测 SDK（QuickDetector）及其依赖。规范建议 okhttp 3.14.9，但本工程 iotsdk 已用 4.12.0，
    // 同一 classpath 只能一个 okhttp，故统一对齐到 4.12.0（sse/logging-interceptor 同版本配对，避免 3.x/4.x 混用）。
    implementation(files("libs/QuickdetectorSdk-0.1.1-release.aar"))
    implementation(files("libs/CtaTvAidlSdk-v2.0.8.aar"))
    implementation("androidx.appcompat:appcompat:1.6.1") // SDK 资源引用 Theme.AppCompat.*
    implementation("io.reactivex.rxjava2:rxandroid:2.1.0")
    implementation("io.reactivex.rxjava2:rxjava:2.2.21")
    implementation("com.squareup.okhttp3:okhttp-sse:4.12.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-gson:2.9.0")
    implementation("com.squareup.retrofit2:adapter-rxjava2:2.9.0")
    implementation("com.jakewharton.retrofit:retrofit2-rxjava2-adapter:1.0.0")
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
    implementation(libs.core.ktx)
    //implementation(libs.androidx.constraintlayout)
    //implementation(libs.androidx.ui.graphics)
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
