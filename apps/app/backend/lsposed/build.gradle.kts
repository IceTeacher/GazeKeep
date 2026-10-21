plugins {
  alias(libs.plugins.androidLibrary)
}

android {
  namespace = "cn.rainafter.app.gazekeep.backend.lsposed"
  compileSdk = libs.versions.android.compileSdk.get().toInt()
  defaultConfig { minSdk = libs.versions.android.minSdk.get().toInt() }
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
  }
  // Robolectric 的 Parcel/FileDescriptor 拦截需要访问 JDK 内部 API。
  testOptions.unitTests.all { it.jvmArgs("--add-opens=java.base/jdk.internal.access=ALL-UNNAMED") }
}

dependencies {
  api(project(":core"))
  implementation(project(":bridge:protocol"))
  implementation(libs.kotlinx.coroutines.android)
  implementation(libs.hidden.api.bypass)
  testImplementation(libs.kotlin.test)
  testImplementation(libs.kotlin.testJunit)
  testImplementation(libs.junit)
  testImplementation(libs.kotlinx.coroutines.test)
  testImplementation(libs.robolectric)
}
