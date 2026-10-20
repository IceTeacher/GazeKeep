package cn.rainafter.app.gazekeep.showcase

fun main() = startShowcase()

actual fun installBridge(
  screen: (String, Int) -> Unit,
  status: (String) -> Unit,
  appearance: (String, Boolean) -> Unit,
) {
  js(
    "window.GazeKeepWebShowcaseBridge = { setScreen: (route, revision = 0) => screen(route, revision), setDemoStatus: status, setLocaleAndTheme: (lang, dark) => { Object.defineProperty(navigator, 'languages', { configurable: true, get: () => [lang] }); appearance(lang, dark); } }",
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
