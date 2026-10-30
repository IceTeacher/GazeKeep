@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package cn.rainafter.app.gazekeep.showcase

fun main() = startShowcase()

@JsFun(
  "(screen, status, scene, appearance) => { window.GazeKeepWebShowcaseBridge = { setScreen: (route, revision = 0, restore = false) => screen(route, revision, restore), setDemoStatus: status, setDemoScene: scene, setLocaleAndTheme: (lang, dark) => { Object.defineProperty(navigator, 'languages', { configurable: true, get: () => [lang] }); appearance(lang, dark); } }; }",
)
private external fun bind(
  screen: (String, Int, Boolean) -> Unit,
  status: (String) -> Unit,
  scene: (String) -> Unit,
  appearance: (String, Boolean) -> Unit,
)

@JsFun(
  "() => window.parent.dispatchEvent(new CustomEvent('gazekeep:ready', { detail: { attempt: Number(new URLSearchParams(location.search).get('attempt')) } }))",
)
private external fun ready()
actual fun installBridge(
  screen: (String, Int, Boolean) -> Unit,
  status: (String) -> Unit,
  scene: (String) -> Unit,
  appearance: (String, Boolean) -> Unit,
) = bind(screen, status, scene, appearance)
actual fun notifyReady() = ready()

@JsFun(
  "(screen, revision) => requestAnimationFrame(() => window.parent.dispatchEvent(new CustomEvent('gazekeep:rendered', { detail: { route: screen, revision, attempt: Number(new URLSearchParams(location.search).get('attempt')) } })))",
)
private external fun rendered(screen: String, revision: Int)
actual fun notifyRendered(screen: String, revision: Int) = rendered(screen, revision)

// 与官网 screen-touch.ts 的转发一致：触摸指针、画布坐标直接写入 offset，避免浏览器按设备像素比换算。
@JsFun(
  "(type, x, y) => { let canvas = document.querySelector('canvas'); if (!canvas) for (const e of document.querySelectorAll('*')) { canvas = e.shadowRoot?.querySelector('canvas'); if (canvas) break; } if (!canvas) return; const box = canvas.getBoundingClientRect(); const down = type === 'pointerdown'; const event = new PointerEvent(type, { bubbles: true, cancelable: true, composed: true, clientX: x + box.left, clientY: y + box.top, pointerId: 7, pointerType: 'touch', isPrimary: true, button: 0, buttons: down ? 1 : 0, pressure: down ? 0.5 : 0 }); Object.defineProperties(event, { offsetX: { value: x }, offsetY: { value: y } }); canvas.dispatchEvent(event); }",
)
private external fun pointer(type: String, x: Double, y: Double)
actual fun dispatchDemoPointer(type: String, x: Double, y: Double) = pointer(type, x, y)

@JsFun("(callback) => document.addEventListener('pointerdown', (e) => { if (e.pointerId !== 7) callback(); }, true)")
private external fun visitor(callback: () -> Unit)
actual fun watchVisitorPointer(onPointer: () -> Unit) = visitor(onPointer)

@JsFun(
  "(focus, leadSeconds, loose, holdMinutes, hideIndicator) => window.parent.dispatchEvent(new CustomEvent('gazekeep:demo', { detail: { focus, leadSeconds, loose, holdMinutes, hideIndicator, attempt: Number(new URLSearchParams(location.search).get('attempt')) } }))",
)
private external fun demo(focus: String, leadSeconds: Int, loose: Boolean, holdMinutes: Int, hideIndicator: Boolean)
actual fun notifyDemo(focus: String, leadSeconds: Int, loose: Boolean, holdMinutes: Int, hideIndicator: Boolean) =
  demo(focus, leadSeconds, loose, holdMinutes, hideIndicator)
