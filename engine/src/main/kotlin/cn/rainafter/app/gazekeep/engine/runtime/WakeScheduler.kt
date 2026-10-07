package cn.rainafter.app.gazekeep.engine.runtime

import android.os.Handler
import android.os.Looper

interface WakeScheduler {
    fun schedule(atUptime: Long, onWake: () -> Unit)
    fun cancel()
}

class HandlerWakeScheduler : WakeScheduler {
    private val handler = Handler(Looper.getMainLooper())
    private var pending: Runnable? = null
    override fun schedule(atUptime: Long, onWake: () -> Unit) {
        cancel()
        val runnable = Runnable(onWake)
        pending = runnable
        handler.postAtTime(runnable, atUptime)
    }
    override fun cancel() {
        pending?.let(handler::removeCallbacks)
        pending = null
    }
}
