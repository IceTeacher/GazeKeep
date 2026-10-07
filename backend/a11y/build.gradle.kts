plugins {
    alias(libs.plugins.androidLibrary)
}

android {
    namespace = "cn.rainafter.app.gazekeep.backend.a11y"
    compileSdk = libs.versions.android.compileSdk.get().toInt()
    defaultConfig { minSdk = libs.versions.android.minSdk.get().toInt() }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    api(project(":core"))
    implementation(libs.kotlinx.coroutines.android)
    testImplementation(libs.kotlin.testJunit)
    testImplementation(libs.junit)
}
