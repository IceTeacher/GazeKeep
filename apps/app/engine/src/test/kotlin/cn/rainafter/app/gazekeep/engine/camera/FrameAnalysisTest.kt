package cn.rainafter.app.gazekeep.engine.camera

import androidx.camera.core.ImageProxy
import kotlinx.coroutines.CancellationException
import java.lang.reflect.Proxy
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

class FrameAnalysisTest {
  @Test fun callbackFailurePreservesErrorAndClosesFrame() {
    for (error in listOf(IllegalStateException("classification failed"), CancellationException("cancelled"))) {
      var closes = 0
      val image = Proxy.newProxyInstance(ImageProxy::class.java.classLoader, arrayOf(ImageProxy::class.java)) {
          _,
          method,
          _,
        ->
        if (method.name == "close") closes++
        null
      } as ImageProxy
      val frame = analyzeFrame(image) { throw error }
      assertEquals(1, closes)
      assertSame(error, frame.exceptionOrNull())
    }
  }
}
