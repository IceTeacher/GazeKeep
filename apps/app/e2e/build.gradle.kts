import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
  alias(libs.plugins.androidTest)
}

kotlin {
  compilerOptions {
    jvmTarget = JvmTarget.JVM_11
  }
}

dependencies {
  implementation(libs.kotlin.test)
  implementation(libs.kotlin.testJunit)
  implementation(libs.androidx.test.runner)
  implementation(libs.androidx.test.rules)
  implementation(libs.androidx.test.ext.junit)
  implementation(libs.androidx.uiautomator)
}

android {
  namespace = "cn.rainafter.app.gazekeep.e2e"
  compileSdk = libs.versions.android.compileSdk.get().toInt()
  targetProjectPath = ":android"

  defaultConfig {
    minSdk = libs.versions.android.minSdk.get().toInt()
    targetSdk = libs.versions.android.targetSdk.get().toInt()
    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
  }
  // 自插桩：测试运行在独立进程，撤销权限或强停被测应用时不会连带杀死 instrumentation。
  experimentalProperties["android.experimental.self-instrumenting"] = true
  // 实机套件按资源键读取页面文案，避免在测试中复制字符串。
  sourceSets.getByName("main").assets.srcDir("../shared/src/commonMain/composeResources/values")
}
