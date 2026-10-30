package cn.rainafter.app.gazekeep.ui.showcase

import androidx.compose.foundation.ScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import top.yukonga.miuix.kmp.basic.ScrollBehavior

/** 页面滚动容器与顶栏折叠行为，演示脚本借此平滑滚动并让顶栏随之折叠。 */
internal class ShowcaseScroll(val state: ScrollState, val behavior: ScrollBehavior)

/**
 * 官网演示脚本的接入点。只有官网展示入口提供实现；Android 端为 null，页面行为与布局不变。
 * 登记函数返回注销函数，只注销自己登记的那一份，转场期间新旧页面并存时不会互相覆盖。
 */
internal interface ShowcaseDriver {
  /** 可点按目标在窗口中的像素范围。 */
  fun anchor(key: String, bounds: Rect)

  fun scroll(key: String, target: ShowcaseScroll): () -> Unit

  /** 输入框写入口：脚本逐字写入，不聚焦真实输入框。 */
  fun input(key: String, write: (String) -> Unit): () -> Unit
}

internal val LocalShowcaseDriver = staticCompositionLocalOf<ShowcaseDriver?> { null }

@Composable
internal fun Modifier.showcaseAnchor(key: String): Modifier {
  val driver = LocalShowcaseDriver.current ?: return this
  return onGloballyPositioned { driver.anchor(key, it.boundsInWindow()) }
}

@Composable
internal fun ShowcaseScrollTarget(key: String, state: ScrollState, behavior: ScrollBehavior) {
  val driver = LocalShowcaseDriver.current ?: return
  DisposableEffect(driver, key, state, behavior) {
    val release = driver.scroll(key, ShowcaseScroll(state, behavior))
    onDispose { release() }
  }
}

@Composable
internal fun ShowcaseInput(key: String, write: (String) -> Unit) {
  val driver = LocalShowcaseDriver.current ?: return
  val current by rememberUpdatedState(write)
  DisposableEffect(driver, key) {
    val release = driver.input(key) { current(it) }
    onDispose { release() }
  }
}
