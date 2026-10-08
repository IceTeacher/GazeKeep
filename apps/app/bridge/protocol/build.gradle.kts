plugins {
  alias(libs.plugins.androidLibrary)
}

android {
  namespace = "cn.rainafter.app.gazekeep.bridge.protocol"
  compileSdk = libs.versions.android.compileSdk.get().toInt()
  defaultConfig { minSdk = libs.versions.android.minSdk.get().toInt() }
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
  }
  buildFeatures { aidl = true }
}
