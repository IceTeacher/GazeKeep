package cn.rainafter.app.gazekeep.showcase

fun main() = startShowcase()

actual fun installBridge(
  screen: (String, Int, Boolean) -> Unit,
  status: (String) -> Unit,
  scene: (String) -> Unit,
  appearance: (String, Boolean) -> Unit,
) {
  js(
    "window.GazeKeepWebShowcaseBridge = { setScreen: (route, revision = 0, restore = false) => screen(route, revision, restore), setDemoStatus: status, setDemoScene: scene, setLocaleAndTheme: (lang, dark) => { Object.defineProperty(navigator, 'languages', { configurable: true, get: () => [lang] }); appearance(lang, dark); } }",
  )
}
actual fun notifyReady() {
  js(
    "window.parent.dispatchEvent(new CustomEvent('gazekeep:ready', { detail: { attempt: Number(new URLSearchParams(location.search).get('attempt')) } }))",
  )
}

actual fun notifyRendered(screen: String, revision: Int) {
  js(
    "requestAnimationFrame(() => window.parent.dispatchEvent(new CustomEvent('gazekeep:rendered', { detail: { route: screen, revision: revision, attempt: Number(new URLSearchParams(location.search).get('attempt')) } })))",
  )
}

// 与官网 screen-touch.ts 的转发一致：触摸指针、画布坐标直接写入 offset，避免浏览器按设备像素比换算。
actual fun dispatchDemoPointer(type: String, x: Double, y: Double) {
  js(
    "(function () { var canvas = document.querySelector('canvas'); if (!canvas) { var all = document.querySelectorAll('*'); for (var i = 0; i < all.length && !canvas; i++) canvas = all[i].shadowRoot && all[i].shadowRoot.querySelector('canvas'); } if (!canvas) return; var box = canvas.getBoundingClientRect(); var down = type === 'pointerdown'; var event = new PointerEvent(type, { bubbles: true, cancelable: true, composed: true, clientX: x + box.left, clientY: y + box.top, pointerId: 7, pointerType: 'touch', isPrimary: true, button: 0, buttons: down ? 1 : 0, pressure: down ? 0.5 : 0 }); Object.defineProperties(event, { offsetX: { value: x }, offsetY: { value: y } }); canvas.dispatchEvent(event); })()",
  )
}

actual fun watchVisitorPointer(onPointer: () -> Unit) {
  js("document.addEventListener('pointerdown', function (e) { if (e.pointerId !== 7) onPointer(); }, true)")
}

actual fun notifyDemo(focus: String, leadSeconds: Int, loose: Boolean, holdMinutes: Int, hideIndicator: Boolean) {
  js(
    "window.parent.dispatchEvent(new CustomEvent('gazekeep:demo', { detail: { focus: focus, leadSeconds: leadSeconds, loose: loose, holdMinutes: holdMinutes, hideIndicator: hideIndicator, attempt: Number(new URLSearchParams(location.search).get('attempt')) } }))",
  )
}
