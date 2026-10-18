@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package cn.rainafter.app.gazekeep.showcase

fun main() = startShowcase()

@JsFun(
  "(screen, status, appearance) => { window.GazeKeepWebShowcaseBridge = { setScreen: screen, setDemoStatus: status, setLocaleAndTheme: (lang, dark) => { Object.defineProperty(navigator, 'languages', { configurable: true, get: () => [lang] }); appearance(lang, dark); } }; }",
)
private external fun bind(screen: (String) -> Unit, status: (String) -> Unit, appearance: (String, Boolean) -> Unit)

@JsFun(
  "() => window.parent.dispatchEvent(new CustomEvent('gazekeep:ready', { detail: { attempt: Number(new URLSearchParams(location.search).get('attempt')) } }))",
)
private external fun ready()
actual fun installBridge(screen: (String) -> Unit, status: (String) -> Unit, appearance: (String, Boolean) -> Unit) =
  bind(screen, status, appearance)
actual fun notifyReady() = ready()
