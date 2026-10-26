package cn.rainafter.app.gazekeep.engine.runtime

import cn.rainafter.app.gazekeep.core.engine.EngineEffect
import cn.rainafter.app.gazekeep.core.engine.EngineState
import cn.rainafter.app.gazekeep.core.engine.YieldReason
import kotlin.test.Test
import kotlin.test.assertEquals

class LogNamesTest {
  @Test fun nestedTypesUseTheirOwnNameWithoutTheEnclosingType() {
    assertEquals("Disabled", logName(EngineState.Disabled))
    assertEquals("Yielded", logName(EngineState.Yielded(YieldReason.TooLate)))
    assertEquals("Release", logName(EngineEffect.Release))
  }

  @Test fun binaryNameWithoutInnerClassMetadataStillYieldsTheShortName() {
    // R8 去掉 InnerClasses 属性后 simpleName 会变成 EngineState$Yielded，这里只依赖二进制名。
    assertEquals("Yielded", logName("cn.rainafter.app.gazekeep.core.engine.EngineState\$Yielded"))
    assertEquals("Top", logName("a.b.Top"))
  }
}
