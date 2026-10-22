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

// 官网演示的版本号取自 android 模块的 versionName，与发布门禁（scripts/repo-tools/scripts/check-apk.ts）读取同一来源。
val androidBuildScript = layout.projectDirectory.file("../android/build.gradle.kts")
val showcaseVersionName =
  providers.fileContents(androidBuildScript).asText.map { text ->
    Regex("""versionName\s*=\s*"([^"]+)"""").find(text)?.groupValues?.get(1)
      ?: error("android/build.gradle.kts 缺少 versionName")
  }
val generateShowcaseVersion by tasks.registering {
  val output = layout.buildDirectory.dir("generated/showcaseVersion/kotlin")
  val versionName = showcaseVersionName
  inputs.property("versionName", versionName)
  outputs.dir(output)
  doLast {
    val file = output.get().file("cn/rainafter/app/gazekeep/showcase/ShowcaseVersion.kt").asFile
    file.parentFile.mkdirs()
    file.writeText(
      "package cn.rainafter.app.gazekeep.showcase\n\n" +
        "/** 由 :shared:generateShowcaseVersion 生成，勿手改。 */\n" +
        "internal const val SHOWCASE_VERSION_NAME = \"${versionName.get()}\"\n",
    )
  }
}
kotlin.sourceSets.getByName("showcaseMain").kotlin.srcDir(generateShowcaseVersion)

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
