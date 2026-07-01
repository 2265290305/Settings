# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile

# ===== 数生 iotsdk（爱加密/ijiami 加固）=====
# SDK 通过 native 层 ijiami_esmart_sdk.NCall 以「原始类名」反射加载内部类
# （如 com.digitallife.iotsdk.api.IotSDK$IotSDKHolder），R8 移除/重命名会导致
# ClassNotFoundException。必须整包 keep，禁止移除与改名。
-keep class com.digitallife.iotsdk.** { *; }
-keep interface com.digitallife.iotsdk.** { *; }
-keep class ijiami_esmart_sdk.** { *; }
-keepclassmembers class com.digitallife.iotsdk.** { *; }
-keepclassmembers class ijiami_esmart_sdk.** { *; }
-dontwarn com.digitallife.iotsdk.**
-dontwarn ijiami_esmart_sdk.**

# iotsdk 依赖的第三方库（被其反射/直接调用）
-keep class org.eclipse.paho.** { *; }
-dontwarn org.eclipse.paho.**
-keep class com.google.gson.** { *; }
-keepclassmembers class okhttp3.** { *; }
-dontwarn okhttp3.**
-dontwarn org.conscrypt.**

# ===== 一键检测 SDK（QuickDetector + CtaTvAidl）=====
-keep class com.telecom.quickdetector.** { *; }
-keep class cn.com.chinatelecom.account.tv.** { *; }
-keep class * implements android.os.IInterface { *; }
-dontwarn com.telecom.quickdetector.**
-dontwarn cn.com.chinatelecom.account.tv.**
-dontwarn retrofit2.**
-dontwarn io.reactivex.**