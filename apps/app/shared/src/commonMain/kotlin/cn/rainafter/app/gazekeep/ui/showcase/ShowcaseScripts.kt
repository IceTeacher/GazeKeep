package cn.rainafter.app.gazekeep.ui.showcase

import cn.rainafter.app.gazekeep.ui.contract.AppEntry
import cn.rainafter.app.gazekeep.ui.showcase.ShowcaseStep.Expect
import cn.rainafter.app.gazekeep.ui.showcase.ShowcaseStep.Focus
import cn.rainafter.app.gazekeep.ui.showcase.ShowcaseStep.Glide
import cn.rainafter.app.gazekeep.ui.showcase.ShowcaseStep.Pick
import cn.rainafter.app.gazekeep.ui.showcase.ShowcaseStep.Tap
import cn.rainafter.app.gazekeep.ui.showcase.ShowcaseStep.Type
import cn.rainafter.app.gazekeep.ui.showcase.ShowcaseStep.Wait
import kotlin.random.Random

/** 官网演示可操作目标的登记键，页面以 [showcaseAnchor] 等登记，脚本按键操作。 */
internal object ShowcaseKeys {
  const val HOME = "home"
  const val SETTINGS = "settings"
  const val PRIVACY = "privacy"
  const val NAV = "nav"
  const val LEAD = "lead"
  const val SENSITIVITY = "sensitivity"
  const val MAX_HOLD = "max_hold"
  const val HIDE_INDICATOR = "hide_indicator"
  const val LEAD_INPUT = "lead.input"
  const val LEAD_CONFIRM = "lead.confirm"
  const val HOLD_INPUT = "max_hold.input"
  const val HOLD_CONFIRM = "max_hold.confirm"
  const val EXPERIMENTAL_CONFIRM = "experimental.confirm"
  const val APPS_SEARCH = "apps.search"

  fun app(packageName: String) = "app:$packageName"
}

/** 演示脚本的一步；脚本只描述操作，由官网展示入口按真实页面执行。 */
internal sealed interface ShowcaseStep {
  data class Wait(val millis: Long) : ShowcaseStep

  /** 轻点登记目标；columns 大于 1 时点按第 column 等分列的中心（底栏页签）。 */
  data class Tap(val key: String, val column: Int = 0, val columns: Int = 1) : ShowcaseStep

  /** 轻点由 key 展开的下拉列表中第 index 项，列表共 count 项。 */
  data class Pick(val key: String, val index: Int, val count: Int) : ShowcaseStep

  /** 平滑滚动登记的页面到底部或顶部，顶栏随之折叠或展开。 */
  data class Glide(val key: String, val toEnd: Boolean, val millis: Long) : ShowcaseStep

  /** 把输入框内容改为 text；逐字输入由脚本拆成多步。 */
  data class Type(val key: String, val text: String) : ShowcaseStep

  /** 告知官网正在演示的设置项，null 表示一轮结束。 */
  data class Focus(val key: String?) : ShowcaseStep

  /** 一轮操作后的期望设置；点按未命中时直接写入，循环不走样。 */
  data class Expect(val settings: ShowcaseSettings) : ShowcaseStep
}

internal data class ShowcaseSettings(
  val leadSeconds: Int,
  val loose: Boolean,
  val holdMinutes: Int,
  val hideIndicator: Boolean,
)

/** 官网各区段的内屏演示，一份列表为一轮，展示入口循环执行。 */
internal object ShowcaseScripts {
  /** 与 GazeConfig 默认值一致：提前 8 秒、标准灵敏度、30 分钟上限、显示指示器。 */
  val SETTINGS_DEFAULT = ShowcaseSettings(leadSeconds = 8, loose = false, holdMinutes = 30, hideIndicator = false)
  private val SETTINGS_TUNED = ShowcaseSettings(leadSeconds = 5, loose = true, holdMinutes = 60, hideIndicator = true)

  private const val TAP_GAP = 550L
  private const val POPUP_SHOW = 450L
  private const val KEY_GAP = 180L

  fun forScene(scene: String, apps: List<AppEntry>, random: Random): List<ShowcaseStep>? = when (scene) {
    "tour" -> tour()
    "privacy" -> privacy()
    "apps" -> apps(apps, random)
    "settings" -> settings()
    else -> null
  }

  // 各脚本第一步即是操作，进入区段立即开始；轮间停顿放在末尾。

  /** 工作流标题处：主页滚到底再回顶，切到设置页同样往返，再回到主页。 */
  fun tour() = listOf(
    Glide(ShowcaseKeys.HOME, toEnd = true, millis = 2600),
    Wait(400),
    Glide(ShowcaseKeys.HOME, toEnd = false, millis = 1600),
    Wait(300),
    Tap(ShowcaseKeys.NAV, column = 1, columns = 2),
    Wait(500),
    Glide(ShowcaseKeys.SETTINGS, toEnd = true, millis = 2600),
    Wait(400),
    Glide(ShowcaseKeys.SETTINGS, toEnd = false, millis = 1600),
    Wait(300),
    Tap(ShowcaseKeys.NAV, column = 0, columns = 2),
    Wait(500),
  )

  /** 第二幕：隐私页从顶部滚到底部，再回到顶部。 */
  fun privacy() = listOf(
    Glide(ShowcaseKeys.PRIVACY, toEnd = true, millis = 3000),
    Wait(500),
    Glide(ShowcaseKeys.PRIVACY, toEnd = false, millis = 2000),
    Wait(500),
  )

  /** 专注范围：随机切换一至三个应用，再逐字搜索一个应用名称，停留后清空。 */
  fun apps(apps: List<AppEntry>, random: Random): List<ShowcaseStep> {
    if (apps.isEmpty()) return listOf(Wait(1000))
    val toggled = apps.shuffled(random).take(random.nextInt(1, minOf(3, apps.size) + 1))
    val query = apps.random(random).label
    return buildList {
      toggled.forEach {
        add(Tap(ShowcaseKeys.app(it.packageName)))
        add(Wait(500))
      }
      add(Wait(200))
      query.indices.forEach {
        add(Type(ShowcaseKeys.APPS_SEARCH, query.take(it + 1)))
        add(Wait(120))
      }
      add(Wait(1000))
      add(Type(ShowcaseKeys.APPS_SEARCH, ""))
      add(Wait(600))
    }
  }

  /** 设置区：先改为 5 秒、宽松、60 分钟并隐藏指示器，再改回 8 秒、标准、30 分钟并恢复指示器。 */
  fun settings() = round(SETTINGS_TUNED, from = SETTINGS_DEFAULT) + round(SETTINGS_DEFAULT, from = SETTINGS_TUNED)

  private fun round(target: ShowcaseSettings, from: ShowcaseSettings) = buildList {
    add(Focus(ShowcaseKeys.LEAD))
    add(Tap(ShowcaseKeys.LEAD))
    add(Wait(POPUP_SHOW))
    add(Pick(ShowcaseKeys.LEAD, index = if (target.leadSeconds == 5) 0 else 1, count = 3))
    add(Wait(TAP_GAP))
    add(Focus(ShowcaseKeys.SENSITIVITY))
    add(Tap(ShowcaseKeys.SENSITIVITY))
    add(Wait(POPUP_SHOW))
    add(Pick(ShowcaseKeys.SENSITIVITY, index = if (target.loose) 1 else 0, count = 2))
    add(Wait(TAP_GAP))
    add(Focus(ShowcaseKeys.MAX_HOLD))
    add(Tap(ShowcaseKeys.MAX_HOLD))
    add(Wait(POPUP_SHOW))
    add(Pick(ShowcaseKeys.MAX_HOLD, index = 3, count = 5))
    add(Wait(350))
    // 输入框预填当前值，先清空再逐字输入，不聚焦真实输入框以免唤起访客设备的键盘。
    val minutes = target.holdMinutes.toString()
    add(Type(ShowcaseKeys.HOLD_INPUT, ""))
    add(Wait(KEY_GAP))
    minutes.indices.forEach {
      add(Type(ShowcaseKeys.HOLD_INPUT, minutes.take(it + 1)))
      add(Wait(KEY_GAP))
    }
    add(Wait(200))
    add(Tap(ShowcaseKeys.HOLD_CONFIRM))
    add(Wait(TAP_GAP))
    add(Focus(ShowcaseKeys.HIDE_INDICATOR))
    if (target.hideIndicator != from.hideIndicator) {
      add(Tap(ShowcaseKeys.HIDE_INDICATOR))
      // 开启实验性功能需要再确认一次，关闭直接生效。
      if (target.hideIndicator) {
        add(Wait(POPUP_SHOW))
        add(Tap(ShowcaseKeys.EXPERIMENTAL_CONFIRM))
      }
    }
    add(Wait(TAP_GAP))
    add(Expect(target))
    add(Focus(null))
    add(Wait(1200))
  }
}
