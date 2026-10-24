package cn.rainafter.app.gazekeep.e2e

import android.os.Build
import android.os.ParcelFileDescriptor
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until
import org.junit.Assume.assumeTrue
import org.w3c.dom.Element
import javax.xml.parsers.DocumentBuilderFactory

const val PACKAGE = "cn.rainafter.app.gazekeep"
private const val TIMEOUT_MS = 10_000L

/**
 * 实机旅程的设备会话：只修改测试声明的设置，并在结束时恢复记录的原值。
 * 不读取帧、人脸或日志正文；shell 输出只用于读取系统设置与权限状态。
 */
class DeviceSession {
  private val instrumentation = InstrumentationRegistry.getInstrumentation()
  val device: UiDevice = UiDevice.getInstance(instrumentation)
  private val restores = ArrayDeque<() -> Unit>()

  /** 页面文案按资源键读取自 shared 的默认中文资源。 */
  private val labels: Map<String, String> by lazy {
    instrumentation.context.assets.open("strings.xml").use { input ->
      val nodes = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(input).getElementsByTagName("string")
      (0 until nodes.length).map { nodes.item(it) as Element }.associate { it.getAttribute("name") to it.textContent }
    }
  }

  fun label(key: String): String = requireNotNull(labels[key]) { "缺少字符串资源：$key" }

  /** 限定在被测应用内匹配文案：系统设置等其他应用也有「设置」等同名文字。 */
  fun appText(key: String) = By.pkg(PACKAGE).text(label(key))

  fun shell(command: String): String {
    val output = instrumentation.uiAutomation.executeShellCommand(command)
    return ParcelFileDescriptor.AutoCloseInputStream(output).bufferedReader().use { it.readText() }.trim()
  }

  /** 记录一项设置的原值并登记恢复动作；按登记的逆序恢复。 */
  fun rememberSetting(namespace: String, key: String): String {
    val original = shell("settings get $namespace $key")
    restores.addFirst {
      if (original ==
        "null"
      ) {
        shell("settings delete $namespace $key")
      } else {
        shell("settings put $namespace $key $original")
      }
    }
    return original
  }

  fun onCleanup(action: () -> Unit) = restores.addFirst(action)

  /** 以中文界面运行应用，使断言与默认资源一致；需要 Android 13 的按应用语言。 */
  fun useChineseUi() {
    assumeTrue("按应用语言需要 Android 13+，当前 API ${Build.VERSION.SDK_INT}", Build.VERSION.SDK_INT >= 33)
    // 输出格式随系统版本不同（如 "Locales: [..]" 或 "Locales for <pkg> for user 0 are [..]"），只取方括号内的列表。
    val original = Regex("""\[(.*)]""").find(
      shell("cmd locale get-app-locales $PACKAGE --user current"),
    )?.groupValues?.get(1).orEmpty().trim()
    shell("cmd locale set-app-locales $PACKAGE --user current --locales zh-CN")
    onCleanup {
      // executeShellCommand 不经 shell 解析，传入 "" 会被当成字面值；省略 --locales 才是空列表。
      if (original.isBlank()) {
        shell("cmd locale set-app-locales $PACKAGE --user current")
      } else {
        shell("cmd locale set-app-locales $PACKAGE --user current --locales $original")
      }
    }
  }

  fun launch() {
    device.wakeUp()
    shell("wm dismiss-keyguard")
    // 重装、撤权引起的进程清理是异步的，刚启动的界面可能随即被关掉并露出其他任务；确认主界面停留在前台，否则重新启动。
    repeat(3) {
      // 清空任务重建界面：不沿用上一用例停留的子页面；部分 ROM 会丢弃注入的返回键，不能靠返回退回主界面。
      // 只重建 Activity，不影响已在运行的服务。
      shell("am start -W -f 0x10008000 -n $PACKAGE/.MainActivity")
      if (device.wait(Until.findObject(appText("nav_settings")), TIMEOUT_MS) != null &&
        !device.wait(Until.gone(appText("nav_settings")), 1_500L)
      ) {
        device.waitForIdle()
        return
      }
    }
    error("应用主界面未显示")
  }

  fun forceStop() {
    shell("am force-stop $PACKAGE")
  }

  fun find(text: String): UiObject2? = device.wait(Until.findObject(By.text(text)), TIMEOUT_MS)

  fun require(text: String): UiObject2 = checkNotNull(find(text)) { "未找到文本：$text" }

  /** 文本可能位于列表下方，先向下滚动查找。 */
  fun scrollToText(text: String): UiObject2 {
    repeat(6) {
      device.findObject(By.text(text))?.let { return it }
      device.swipe(
        device.displayWidth / 2,
        device.displayHeight * 3 / 4,
        device.displayWidth / 2,
        device.displayHeight / 4,
        20,
      )
      device.waitForIdle()
    }
    return require(text)
  }

  fun tap(text: String) {
    scrollToText(text).click()
    device.waitForIdle()
  }

  /** 部分 ROM 在启动动画期间会丢弃注入的首次点击，切换后以设置页独有条目确认，未切换则重试。 */
  fun openSettingsTab() {
    repeat(5) {
      checkNotNull(device.wait(Until.findObject(appText("nav_settings")), TIMEOUT_MS)) { "未找到应用底部导航" }.click()
      device.waitForIdle()
      if (device.wait(Until.hasObject(appText("settings_backend")), 4_000L)) return
    }
    error("切换到设置页失败")
  }

  fun close() {
    while (restores.isNotEmpty()) runCatching(restores.removeFirst())
  }
}
