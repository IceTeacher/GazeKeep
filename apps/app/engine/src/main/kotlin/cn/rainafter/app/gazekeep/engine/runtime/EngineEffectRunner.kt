package cn.rainafter.app.gazekeep.engine.runtime

import cn.rainafter.app.gazekeep.core.engine.EngineEffect
import cn.rainafter.app.gazekeep.core.engine.EngineEvent

/** 将所有外部回调线性化；执行器的结果必须重新变成 EngineEvent。 */
fun interface EngineEffectRunner {
    suspend fun run(effect: EngineEffect, emit: (EngineEvent) -> Unit)
}
