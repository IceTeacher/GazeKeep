@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package cn.rainafter.app.gazekeep.showcase

fun main() = startShowcase()

@JsFun(
  "(screen, status, appearance) => { window.GazeKeepWebShowcaseBridge = { setScreen: (route, revision = 0) => screen(route, revision), setDemoStatus: status, setLocaleAndTheme: (lang, dark) => { Object.defineProperty(navigator, 'languages', { configurable: true, get: () => [lang] }); appearance(lang, dark); } }; }",
)
private external fun bind(
  screen: (String, Int) -> Unit,
  status: (String) -> Unit,
  appearance: (String, Boolean) -> Unit,
)

@JsFun(
  "() => window.parent.dispatchEvent(new CustomEvent('gazekeep:ready', { detail: { attempt: Number(new URLSearchParams(location.search).get('attempt')) } }))",
)
private external fun ready()
actual fun installBridge(
  screen: (String, Int) -> Unit,
  status: (String) -> Unit,
  appearance: (String, Boolean) -> Unit,
) = bind(screen, status, appearance)
actual fun notifyReady() = ready()

@JsFun(
  "(screen, revision) => requestAnimationFrame(() => window.parent.dispatchEvent(new CustomEvent('gazekeep:rendered', { detail: { route: screen, revision, attempt: Number(new URLSearchParams(location.search).get('attempt')) } })))",
)
private external fun rendered(screen: String, revision: Int)
actual fun notifyRendered(screen: String, revision: Int) = rendered(screen, revision)
