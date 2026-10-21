@file:OptIn(org.jetbrains.kotlin.gradle.ExperimentalWasmDsl::class)

import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
  alias(libs.plugins.kotlinMultiplatform)
  alias(libs.plugins.androidMultiplatformLibrary)
  alias(libs.plugins.composeMultiplatform)
  alias(libs.plugins.composeCompiler)
}

kotlin {
  wasmJs {
    browser()
    binaries.executable()
  }
  js {
    browser()
    binaries.executable()
  }

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

  applyDefaultHierarchyTemplate {
    common {
      group("showcase") {
        withJs()
        withWasmJs()
      }
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
    getByName("androidHostTest").dependencies {
      implementation(libs.kotlin.testJunit)
      implementation(libs.junit)
      implementation(libs.kotlinx.coroutines.test)
      implementation(libs.robolectric)
      implementation(libs.roborazzi)
      implementation(libs.roborazzi.compose)
      implementation(libs.androidx.activity.compose)
      implementation(libs.androidx.compose.uiTestJunit4)
      implementation(libs.androidx.compose.uiTestManifest)
    }
  }
}

// 截图基线随源码提交；默认校验，`-Pgazekeep.recordScreenshots=true` 时重新录制。
val recordScreenshots = providers.gradleProperty("gazekeep.recordScreenshots").map { it == "true" }.orElse(false)
tasks.withType<Test>().configureEach {
  if (name.contains("HostTest")) {
    val record = recordScreenshots.get()
    systemProperty("roborazzi.test.record", record.toString())
    systemProperty("roborazzi.test.verify", (!record).toString())
    // Robolectric 的 Parcel/FileDescriptor 拦截需要访问 JDK 内部 API。
    jvmArgs("--add-opens=java.base/jdk.internal.access=ALL-UNNAMED")
  }
}
