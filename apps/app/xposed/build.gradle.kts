plugins {
  alias(libs.plugins.androidLibrary)
}

android {
  namespace = "cn.rainafter.app.gazekeep.xposed"
  compileSdk = libs.versions.android.compileSdk.get().toInt()
  defaultConfig { minSdk = libs.versions.android.minSdk.get().toInt() }
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
  }
}

dependencies {
  implementation(project(":bridge:protocol"))
  compileOnly(libs.xposed.api)
  compileOnly(libs.libxposed.api)
  compileOnly(project(":hidden-api-stubs"))
  testImplementation(libs.libxposed.api)
  testImplementation(libs.kotlin.testJunit)
  testImplementation(libs.junit)
}
