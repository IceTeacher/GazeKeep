package cn.rainafter.app.gazekeep.showcase

fun main() = startShowcase()

actual fun installBridge(screen: (String) -> Unit, status: (String) -> Unit, appearance: (String, Boolean) -> Unit) {
  js(
    "window.GazeKeepWebShowcaseBridge = { setScreen: screen, setDemoStatus: status, setLocaleAndTheme: (lang, dark) => { Object.defineProperty(navigator, 'languages', { configurable: true, get: () => [lang] }); appearance(lang, dark); } }",
  )
}
actual fun notifyReady() {
  js(
    "window.parent.dispatchEvent(new CustomEvent('gazekeep:ready', { detail: { attempt: Number(new URLSearchParams(location.search).get('attempt')) } }))",
  )
}
