rootProject.name = "GazeKeep"

pluginManagement {
    repositories {
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        mavenCentral()
        maven { url = uri("https://api.xposed.info/") }
    }
}

include(":androidApp")
include(":shared")
include(":core")
include(":engine")
include(":backend:a11y")
include(":backend:shizuku")
include(":backend:lsposed")
include(":xposed")
