package cn.rainafter.app.gazekeep.backend.a11y

/** 在主线程同步投递活动信号，确保 UserActivity 先于新 deadline。 */
class A11ySignals {
    private class Listener(val activity: () -> Unit, val unavailable: () -> Unit)
    private val listeners = linkedSetOf<Listener>()
    fun subscribe(onActivity: () -> Unit, onUnavailable: () -> Unit): AutoCloseable {
        val listener = Listener(onActivity, onUnavailable)
        listeners.add(listener)
        return AutoCloseable { listeners.remove(listener) }
    }
    fun activity() {
        listeners.toList().forEach { it.activity() }
    }
    fun unavailable() {
        listeners.toList().forEach { it.unavailable() }
    }
}
