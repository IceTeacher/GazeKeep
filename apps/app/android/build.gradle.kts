import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
  alias(libs.plugins.androidApplication)
  alias(libs.plugins.composeCompiler)
}

kotlin {
  compilerOptions {
    jvmTarget = JvmTarget.JVM_11
  }
}
dependencies {
  implementation(project(":shared"))
  implementation(project(":engine"))
  implementation(project(":backend:a11y"))
  implementation(project(":backend:shizuku"))
  implementation(project(":backend:lsposed"))
  implementation(project(":xposed"))
  implementation(libs.kotlinx.coroutines.android)
  implementation(libs.androidx.lifecycle.service)
  implementation(libs.androidx.core.ktx)
  implementation(libs.shizuku.api)
  implementation(libs.shizuku.provider)

  implementation(libs.androidx.camera.view)
  implementation(libs.androidx.camera.core)
  implementation(libs.androidx.activity.compose)
  implementation(libs.compose.ui)
  implementation(libs.compose.foundation)
  implementation(libs.miuix.ui)

  debugImplementation(libs.compose.uiTooling)
  testImplementation(libs.kotlin.test)
  testImplementation(libs.kotlin.testJunit)
  testImplementation(libs.junit)
  testImplementation(libs.robolectric)
}

android {
  namespace = "cn.rainafter.app.gazekeep"
  compileSdk = libs.versions.android.compileSdk.get().toInt()

  defaultConfig {
    applicationId = "cn.rainafter.app.gazekeep"
    minSdk = libs.versions.android.minSdk.get().toInt()
    targetSdk = libs.versions.android.targetSdk.get().toInt()
    versionCode = 5
    versionName = "1.1.3"
  }
  testOptions.unitTests {
    // 通知文案测试按资源键读取各语言字符串。
    isIncludeAndroidResources = true
    // Robolectric 的 Parcel/FileDescriptor 拦截需要访问 JDK 内部 API。
    all { it.jvmArgs("--add-opens=java.base/jdk.internal.access=ALL-UNNAMED") }
  }
  packaging {
    resources {
      excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
    jniLibs {
      // 32 位 x86 只剩旧模拟器，通用包也不携带。
      excludes += "lib/x86/**"
      // WHY：AGP 默认不压缩 .so，ML Kit 原生库让下载体积成倍增加；改为压缩存放，安装时解压。
      useLegacyPackaging = true
    }
  }
  // 按 ABI 拆分分发包，另保留通用包给不确定设备架构的用户；各包共用同一 versionCode，可互相覆盖安装。
  splits {
    abi {
      isEnable = true
      reset()
      include("arm64-v8a", "armeabi-v7a", "x86_64")
      isUniversalApk = true
    }
  }
  val signingValues = listOf(
    "GAZEKEEP_STORE_FILE",
    "GAZEKEEP_STORE_PASSWORD",
    "GAZEKEEP_KEY_ALIAS",
    "GAZEKEEP_KEY_PASSWORD",
  ).map { providers.environmentVariable(it).orNull }
  require(signingValues.all { it == null } || signingValues.all { !it.isNullOrBlank() }) {
    "Release 签名环境配置必须完整；未配置时只构建 unsigned APK"
  }
  if (signingValues.all { it != null }) {
    signingConfigs.create("distribution") {
      storeFile = file(requireNotNull(signingValues[0]))
      storePassword = signingValues[1]
      keyAlias = signingValues[2]
      keyPassword = signingValues[3]
    }
  }
  buildTypes {
    release {
      isMinifyEnabled = true
      isShrinkResources = true
      if (signingValues.all { it != null }) signingConfig = signingConfigs.getByName("distribution")
      proguardFiles(
        getDefaultProguardFile("proguard-android-optimize.txt"),
        "proguard-rules.pro",
      )
    }
  }
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
  }
  buildFeatures {
    compose = true
  }
}
