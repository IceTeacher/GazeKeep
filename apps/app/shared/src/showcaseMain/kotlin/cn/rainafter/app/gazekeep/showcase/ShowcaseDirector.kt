package cn.rainafter.app.gazekeep.showcase

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import cn.rainafter.app.gazekeep.ui.showcase.ShowcaseDriver
import cn.rainafter.app.gazekeep.ui.showcase.ShowcasePause
import cn.rainafter.app.gazekeep.ui.showcase.ShowcaseScroll
import cn.rainafter.app.gazekeep.ui.showcase.ShowcaseSettings
import cn.rainafter.app.gazekeep.ui.showcase.ShowcaseStep
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** 触点动画的一次按压，位置为窗口像素。 */
internal class TapMark(val center: Offset, val id: Long)

/**
 * 执行演示脚本：按页面登记的目标位置向画布派发触摸按压（与访客操作走同一条输入路径），
 * 平滑滚动登记的页面并经顶栏的嵌套滚动连接让顶栏随之折叠，逐字写入输入框，并绘制触点动画。
 * 手机离开视口时停在当前一步（滚动停在当前位置），回来后从原处继续；按压很短，按下后照常完成。
 */
internal class ShowcaseDirector(
  private val pointer: (type: String, x: Double, y: Double) -> Unit,
  private val settings: () -> ShowcaseSettings,
  private val applySettings: (ShowcaseSettings) -> Unit,
) : ShowcaseDriver {
  private val anchors = HashMap<String, Rect>()
  private val scrolls = HashMap<String, ShowcaseScroll>()
  private val inputs = HashMap<String, (String) -> Unit>()
  private var nextMark = 0L

  /** 窗口像素到画布 CSS 像素的比例与画布高度，由组合时的 LocalDensity 与 LocalWindowInfo 写入。 */
  var density = 1f
  var windowHeight = Float.MAX_VALUE
  val marks = mutableStateListOf<TapMark>()
  val focus = MutableStateFlow<String?>(null)
  val pause = ShowcasePause()

  /** 脚本改动过页面或设置；切换场景时据此决定是否复位。 */
  var dirty = false

  override fun anchor(key: String, bounds: Rect) {
    anchors[key] = bounds
  }

  override fun scroll(key: String, target: ShowcaseScroll): () -> Unit {
    scrolls[key] = target
    return { if (scrolls[key] === target) scrolls.remove(key) }
  }

  override fun input(key: String, write: (String) -> Unit): () -> Unit {
    inputs[key] = write
    return { if (inputs[key] === write) inputs.remove(key) }
  }

  suspend fun perform(step: ShowcaseStep) {
    pause.awaitActive()
    when (step) {
      is ShowcaseStep.Wait -> pause.delay(step.millis)

      is ShowcaseStep.Tap -> {
        val bounds = await { anchors[step.key] } ?: return
        val x = bounds.left + bounds.width * (step.column + 0.5f) / step.columns
        tap(Offset(x, bounds.center.y))
      }

      is ShowcaseStep.Pick -> {
        val bounds = await { anchors[step.key] } ?: return
        tap(popupOption(bounds, step.index, step.count))
      }

      is ShowcaseStep.Glide -> {
        val target = await { scrolls[step.key] } ?: return
        dirty = true
        glide(target, step.toEnd, step.millis)
      }

      is ShowcaseStep.Type -> {
        val write = await { inputs[step.key] } ?: return
        dirty = true
        write(step.text)
      }

      is ShowcaseStep.Focus -> focus.value = step.key

      // 点按未命中（字体或组件尺寸与预期不同）时直接写入目标设置，下一轮仍从预期状态开始。
      is ShowcaseStep.Expect -> if (settings() != step.settings) applySettings(step.settings)
    }
  }

  fun reset() {
    focus.value = null
    marks.clear()
    dirty = false
  }

  /** 页面切换后目标需要一两帧才完成登记与布局。 */
  private suspend fun <T : Any> await(find: () -> T?): T? {
    repeat(90) {
      find()?.let { return it }
      withFrameMillis {}
    }
    return null
  }

  private suspend fun tap(at: Offset) {
    dirty = true
    marks += TapMark(at, nextMark++)
    val x = (at.x / density).toDouble()
    val y = (at.y / density).toDouble()
    pointer("pointerdown", x, y)
    // 访客中途按下时脚本被取消，按压以取消收尾，不在内屏留下点击。
    var released = false
    try {
      delay(90)
      pointer("pointerup", x, y)
      released = true
    } finally {
      if (!released) pointer("pointercancel", x, y)
    }
  }

  /**
   * miuix 下拉列表贴在行尾操作区下方 8dp（下方放不下时改到上方），右端与行内边距对齐；
   * 列表上下各留 8dp，各项文本单行等高。
   */
  private fun popupOption(row: Rect, index: Int, count: Int): Offset {
    val dp = density
    val item = POPUP_ITEM.value * dp
    val height = count * item + 2 * POPUP_EDGE.value * dp
    val end = Rect(row.left, row.center.y - POPUP_ANCHOR.value * dp, row.right, row.center.y + POPUP_ANCHOR.value * dp)
    val top = if (windowHeight - end.bottom > height) {
      end.bottom + POPUP_MARGIN.value * dp
    } else {
      end.top - height - POPUP_MARGIN.value * dp
    }
    val y = top + POPUP_EDGE.value * dp + item * (index + 0.5f)
    return Offset(row.right - POPUP_TAP_INSET.value * dp, y)
  }

  private suspend fun glide(target: ShowcaseScroll, toEnd: Boolean, millis: Long) {
    val bar = target.behavior.state
    val content = if (toEnd) target.state.maxValue - target.state.value else target.state.value
    val collapse = if (toEnd) bar.heightOffset - bar.heightOffsetLimit else -bar.heightOffset
    val distance = content + collapse
    if (distance < 1f) return
    // 嵌套滚动的偏移是手指位移：向上拖为负，内容向下滚动。
    val sign = if (toEnd) -1f else 1f
    // 只累计可见期间的帧间隔，离开视口后滚动停在当前位置。
    var last = withFrameMillis { it }
    var elapsed = 0L
    var applied = 0f
    while (true) {
      if (pause.awaitActive()) last = withFrameMillis { it }
      val now = withFrameMillis { it }
      elapsed += now - last
      last = now
      val progress = (elapsed.toFloat() / millis).coerceIn(0f, 1f)
      val goal = distance * FastOutSlowInEasing.transform(progress)
      drag(target, sign * (goal - applied))
      applied = goal
      if (progress >= 1f) break
    }
    // 收尾让顶栏按松手吸附到完全折叠或展开。
    val connection = target.behavior.nestedScrollConnection
    connection.onPreFling(Velocity.Zero)
    connection.onPostFling(Velocity.Zero, Velocity.Zero)
  }

  private suspend fun drag(target: ShowcaseScroll, delta: Float) {
    if (delta == 0f) return
    val connection = target.behavior.nestedScrollConnection
    val pre = connection.onPreScroll(Offset(0f, delta), NestedScrollSource.UserInput)
    val left = delta - pre.y
    var consumed = 0f
    target.state.scroll { consumed = -scrollBy(-left) }
    connection.onPostScroll(Offset(0f, consumed), Offset(0f, left - consumed), NestedScrollSource.UserInput)
  }

  private companion object {
    // miuix 0.9.4 实测：单行选项高 47dp，行尾操作区上下各距行中心约 9dp。
    val POPUP_ITEM = 47.dp
    val POPUP_EDGE = 8.dp
    val POPUP_ANCHOR = 9.dp
    val POPUP_MARGIN = 8.dp
    val POPUP_TAP_INSET = 72.dp
  }
}

/** 演示点按处的触点：按下时出现，随后扩散淡出。访客自己的操作不绘制。 */
@Composable
internal fun TapMarks(director: ShowcaseDirector) {
  Box(Modifier.fillMaxSize()) {
    director.marks.forEach { mark ->
      key(mark.id) { TapMarkRing(mark) { director.marks.remove(mark) } }
    }
  }
}

@Composable
private fun TapMarkRing(mark: TapMark, onFinished: () -> Unit) {
  val progress = remember { Animatable(0f) }
  val color = MiuixTheme.colorScheme.onSurface
  LaunchedEffect(Unit) {
    progress.animateTo(1f, tween(durationMillis = 650, easing = FastOutSlowInEasing))
    onFinished()
  }
  Canvas(Modifier.fillMaxSize()) {
    val p = progress.value
    drawCircle(color.copy(alpha = 0.22f * (1 - p)), radius = (18 + 18 * p).dp.toPx(), center = mark.center)
    drawCircle(color.copy(alpha = 0.32f * (1 - p * p)), radius = 12.dp.toPx(), center = mark.center)
  }
}
