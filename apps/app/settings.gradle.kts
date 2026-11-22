rootProject.name = "GazeKeep"

pluginManagement {
  // WHY：GitHub Actions 共享出口 IP 易触发 Maven Central 429 限流并导致当次构建禁用 MavenRepo；
  // CI 优先经由 Google 官方镜像拉取插件，保留 gradlePluginPortal 与 mavenCentral 作为兜底。
  val isCi = providers.environmentVariable("CI").map { it.equals("true", ignoreCase = true) }.getOrElse(false)
  repositories {
    google {
      mavenContent {
        includeGroupAndSubgroups("androidx")
        includeGroupAndSubgroups("com.android")
        includeGroupAndSubgroups("com.google")
      }
    }
    if (isCi) {
      maven {
        name = "MavenCentralGoogleMirror"
        url = uri("https://maven-central.storage-download.googleapis.com/maven2/")
      }
      gradlePluginPortal()
    }
    mavenCentral()
    if (!isCi) {
      gradlePluginPortal()
    }
  }
}

dependencyResolutionManagement {
  val isCi = providers.environmentVariable("CI").map { it.equals("true", ignoreCase = true) }.getOrElse(false)
  repositories {
    google {
      mavenContent {
        includeGroupAndSubgroups("androidx")
        includeGroupAndSubgroups("com.android")
        includeGroupAndSubgroups("com.google")
      }
    }
    // WHY：de.robv.android.xposed 仅托管于 api.xposed.info，独占声明可避免先查上游触发 404/429，
    // 同时防止其余依赖穿透查询该仓库。
    exclusiveContent {
      forRepository {
        maven { url = uri("https://api.xposed.info/") }
      }
      filter {
        includeGroup("de.robv.android.xposed")
      }
    }
    if (isCi) {
      maven {
        name = "MavenCentralGoogleMirror"
        url = uri("https://maven-central.storage-download.googleapis.com/maven2/")
      }
    }
    mavenCentral()
  }
}

include(":android")
include(":e2e")
include(":shared")
include(":core")
include(":engine")
include(":backend:a11y")
include(":backend:shizuku")
include(":backend:lsposed")
include(":xposed")
include(":bridge:protocol")

include(":hidden-api-stubs")
