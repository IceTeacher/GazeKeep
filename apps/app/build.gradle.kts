plugins {
  // this is necessary to avoid the plugins to be loaded multiple times
  // in each subproject's classloader
  alias(libs.plugins.androidApplication) apply false
  alias(libs.plugins.androidLibrary) apply false
  alias(libs.plugins.androidMultiplatformLibrary) apply false
  alias(libs.plugins.composeMultiplatform) apply false
  alias(libs.plugins.composeCompiler) apply false
  alias(libs.plugins.kotlinMultiplatform) apply false
}

// Kotlin/JS 与 Kotlin/Wasm 用 Gradle 下载的 Yarn 1 安装依赖。Yarn 1 会从 build/js、build/wasm 向上查找
// 第一个声明 packageManager 的 package.json，越过生成目录后读到仓库根的 pnpm 声明并中止安装；
// 因此在生成的根 package.json 中声明 Yarn 1，使查找停在构建目录内。Yarn 1 只校验主版本为 1。
tasks.withType<org.jetbrains.kotlin.gradle.targets.js.npm.tasks.RootPackageJsonTask>().configureEach {
  val packageJson = rootPackageJsonFile
  val packageManager = "yarn@1.22.22"
  doLast {
    val file = packageJson.get().asFile

    @Suppress("UNCHECKED_CAST")
    val json = groovy.json.JsonSlurper().parse(file) as MutableMap<String, Any?>
    json["packageManager"] = packageManager
    file.writeText(groovy.json.JsonOutput.prettyPrint(groovy.json.JsonOutput.toJson(json)) + "\n")
  }
}

// JS 依赖安装与 Wasm 测试工具安装都会启动 Yarn 1 并共用其全局缓存；配置缓存下二者会并行执行，
// 冷缓存时 Yarn 1 的并发写入可能留下不完整的 node_modules 或缺失 yarn.lock。限定同一时刻只运行一个。
abstract class SerialYarnService : BuildService<BuildServiceParameters.None>

val serialYarn = gradle.sharedServices.registerIfAbsent("serialYarn", SerialYarnService::class) {
  maxParallelUsages.set(1)
}
tasks.withType<org.jetbrains.kotlin.gradle.targets.js.npm.tasks.KotlinNpmInstallTask>().configureEach {
  usesService(serialYarn)
}
tasks.withType<org.jetbrains.kotlin.gradle.targets.js.npm.tasks.KotlinToolingSetupTask>().configureEach {
  usesService(serialYarn)
}
