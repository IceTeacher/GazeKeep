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
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ShowcaseScriptsTest {
  /**
   * 按设置页的真实交互解释脚本：点按下拉项展开列表、选项写入设置，自定义项打开输入框，
   * 开启实验性开关需要再确认一次。
   */
  private class SettingsModel(var settings: ShowcaseSettings) {
    private var popup: String? = null
    private var dialogOpen = false
    private var experimentalPending = false
    private var input = ""
    val focus = mutableListOf<String?>()
    val expected = mutableListOf<ShowcaseSettings>()

    fun run(steps: List<ShowcaseStep>) = steps.forEach(::apply)

    private fun apply(step: ShowcaseStep) {
      when (step) {
        is Tap -> tap(step.key)

        is Pick -> {
          assertEquals(step.key, popup, "选项必须来自刚展开的下拉列表")
          popup = null
          settings = when (step.key) {
            ShowcaseKeys.LEAD -> {
              assertEquals(3, step.count)
              settings.copy(leadSeconds = listOf(5, 8)[step.index])
            }

            ShowcaseKeys.SENSITIVITY -> {
              assertEquals(2, step.count)
              settings.copy(loose = step.index == 1)
            }

            ShowcaseKeys.MAX_HOLD -> {
              assertEquals(5, step.count)
              assertEquals(3, step.index, "单次最长保亮通过自定义输入")
              dialogOpen = true
              input = settings.holdMinutes.toString()
              settings
            }

            else -> error("未知下拉项 ${step.key}")
          }
        }

        is Type -> {
          assertEquals(ShowcaseKeys.HOLD_INPUT, step.key)
          assertTrue(dialogOpen, "输入前必须已打开自定义输入框")
          input = step.text
        }

        is Focus -> focus += step.key

        is Expect -> {
          assertEquals(step.settings, settings, "脚本执行结果与期望一致")
          expected += step.settings
        }

        is Wait -> assertTrue(step.millis > 0)

        is Glide -> error("设置演示不滚动页面")
      }
    }

    private fun tap(key: String) {
      when (key) {
        ShowcaseKeys.LEAD, ShowcaseKeys.SENSITIVITY, ShowcaseKeys.MAX_HOLD -> popup = key

        ShowcaseKeys.HOLD_CONFIRM -> {
          assertTrue(dialogOpen)
          dialogOpen = false
          settings = settings.copy(holdMinutes = input.toInt())
        }

        ShowcaseKeys.HIDE_INDICATOR -> if (settings.hideIndicator) {
          settings = settings.copy(hideIndicator = false)
        } else {
          experimentalPending = true
        }

        ShowcaseKeys.EXPERIMENTAL_CONFIRM -> {
          assertTrue(experimentalPending, "确认框只在开启实验性功能时出现")
          experimentalPending = false
          settings = settings.copy(hideIndicator = true)
        }

        else -> error("未知目标 $key")
      }
    }
  }

  @Test
  fun settingsLoopAppliesBothRoundsAndReturnsToDefaults() {
    val model = SettingsModel(ShowcaseScripts.SETTINGS_DEFAULT)
    model.run(ShowcaseScripts.settings())
    assertEquals(
      listOf(
        ShowcaseSettings(leadSeconds = 5, loose = true, holdMinutes = 60, hideIndicator = true),
        ShowcaseSettings(leadSeconds = 8, loose = false, holdMinutes = 30, hideIndicator = false),
      ),
      model.expected,
    )
    // 第二轮回到默认值，循环首尾相接。
    assertEquals(ShowcaseScripts.SETTINGS_DEFAULT, model.settings)
    val round =
      listOf(ShowcaseKeys.LEAD, ShowcaseKeys.SENSITIVITY, ShowcaseKeys.MAX_HOLD, ShowcaseKeys.HIDE_INDICATOR, null)
    assertEquals(round + round, model.focus)
  }

  @Test
  fun settingsTypesCustomHoldMinutesCharacterByCharacter() {
    val typed = ShowcaseScripts.settings().filterIsInstance<Type>().map { it.text }
    assertEquals(listOf("", "6", "60", "", "3", "30"), typed)
  }

  private val apps = listOf(
    AppEntry("cn.rainafter.app.gazekeep", "GazeKeep"),
    AppEntry("org.lsposed.manager", "LSPosed"),
    AppEntry("com.topjohnwu.magisk", "Magisk"),
    AppEntry("moe.shizuku.privileged.api", "Shizuku"),
  )

  @Test
  fun appsLoopTogglesFewDistinctAppsThenSearchesAndClears() {
    repeat(50) { seed ->
      val steps = ShowcaseScripts.apps(apps, Random(seed))
      val taps = steps.filterIsInstance<Tap>().map { it.key }
      assertTrue(taps.size in 1..3, "seed=$seed 切换 ${taps.size} 个应用")
      assertEquals(taps.distinct(), taps)
      assertTrue(taps.all { key -> apps.any { ShowcaseKeys.app(it.packageName) == key } })
      val typed = steps.filterIsInstance<Type>()
      assertTrue(typed.all { it.key == ShowcaseKeys.APPS_SEARCH })
      // 逐字输入某个应用名称，结束时清空，下一轮从完整列表开始。
      val query = typed[typed.lastIndex - 1].text
      assertTrue(apps.any { it.label == query }, "seed=$seed 搜索 $query")
      assertEquals(query.indices.map { query.take(it + 1) }, typed.dropLast(1).map { it.text })
      assertEquals("", typed.last().text)
      val lastTap = steps.indexOfLast { it is Tap }
      assertTrue(lastTap < steps.indexOfFirst { it is Type }, "先切换应用再搜索")
    }
    val samples = (0 until 50).map { ShowcaseScripts.apps(apps, Random(it)) }
    assertTrue(samples.distinct().size > 1, "不同轮次随机选择")
  }

  @Test
  fun tourScrollsHomeThenSettingsAndReturnsHome() {
    val actions = ShowcaseScripts.tour().filter { it !is Wait }
    assertEquals(
      listOf(
        Glide(ShowcaseKeys.HOME, toEnd = true, millis = 2600),
        Glide(ShowcaseKeys.HOME, toEnd = false, millis = 1600),
        Tap(ShowcaseKeys.NAV, column = 1, columns = 2),
        Glide(ShowcaseKeys.SETTINGS, toEnd = true, millis = 2600),
        Glide(ShowcaseKeys.SETTINGS, toEnd = false, millis = 1600),
        Tap(ShowcaseKeys.NAV, column = 0, columns = 2),
      ),
      actions,
    )
  }

  @Test
  fun privacyScrollsToBottomAndBack() {
    val glides = ShowcaseScripts.privacy().filterIsInstance<Glide>()
    assertEquals(listOf(true, false), glides.map { it.toEnd })
    assertTrue(glides.all { it.key == ShowcaseKeys.PRIVACY })
  }

  /** 相邻可见操作之间的停顿；Focus、Expect 不占时间，首尾相接计入下一轮开头。 */
  private fun pauses(steps: List<ShowcaseStep>): List<Long> {
    val visible = steps.withIndex().filter { (_, s) -> s is Tap || s is Pick || s is Glide || s is Type }
    val gaps = visible.zipWithNext { a, b -> waited(steps.subList(a.index + 1, b.index)) }
    val tail = waited(steps.subList(visible.last().index + 1, steps.size))
    val head = waited(steps.subList(0, visible.first().index))
    return gaps + (tail + head)
  }

  private fun waited(steps: List<ShowcaseStep>) = steps.filterIsInstance<Wait>().sumOf { it.millis }

  @Test
  fun scenesStartImmediatelyAndKeepPausesShort() {
    for (scene in listOf("tour", "privacy", "apps", "settings")) {
      repeat(10) { seed ->
        val steps = ShowcaseScripts.forScene(scene, apps, Random(seed))!!
        val lead = steps.takeWhile { it !is Tap && it !is Pick && it !is Glide && it !is Type }
        assertEquals(0, waited(lead), "$scene 进入区段后第一步就是操作")
        val longest = pauses(steps).max()
        assertTrue(longest <= 1800, "$scene seed=$seed 最长停顿 ${longest}ms")
      }
    }
  }

  @Test
  fun scenesWithoutInScreenScriptReturnNull() {
    for (scene in listOf("none", "detect", "backends", "unknown")) {
      assertNull(ShowcaseScripts.forScene(scene, apps, Random(0)))
    }
    for (scene in listOf("tour", "privacy", "apps", "settings")) {
      assertTrue(ShowcaseScripts.forScene(scene, apps, Random(0))!!.isNotEmpty())
    }
  }
}
