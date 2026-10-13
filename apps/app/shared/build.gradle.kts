import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
  alias(libs.plugins.kotlinMultiplatform)
  alias(libs.plugins.androidMultiplatformLibrary)
  alias(libs.plugins.composeMultiplatform)
  alias(libs.plugins.composeCompiler)
}

kotlin {

  android {
    namespace = "cn.rainafter.app.gazekeep.shared"
    compileSdk = libs.versions.android.compileSdk.get().toInt()
    minSdk = libs.versions.android.minSdk.get().toInt()

    compilerOptions {
      jvmTarget = JvmTarget.JVM_11
    }
    androidResources {
      enable = true
    }
    withHostTest {
      isIncludeAndroidResources = true
    }
    withDeviceTestBuilder {
      sourceSetTreeName = "test"
    }.configure {
      instrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
  }

  sourceSets {
    commonMain.dependencies {
      api(project(":core"))
      implementation(libs.kotlinx.coroutines.core)
      implementation(libs.compose.runtime)
      implementation(libs.compose.foundation)
      implementation(libs.miuix.ui)
      implementation(libs.miuix.preference)
      implementation(libs.miuix.icons)
      implementation(libs.compose.ui)
      implementation(libs.compose.components.resources)
    }
    commonTest.dependencies {
      implementation(libs.kotlin.test)
    }
  }
}
