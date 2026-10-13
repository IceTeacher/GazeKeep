package cn.rainafter.app.gazekeep.xposed.entry

import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedModuleInterface
import java.lang.reflect.Proxy
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class ModernEntryLifecycleTest {
  @Test
  fun wrongProcessAndMissingCapabilityNeverReadSystemClassLoader() {
    for ((system, properties) in listOf(false to 1L, true to 0L)) {
      val entry = ModernEntry()
      entry.attachFramework(
        fake<XposedInterface> { name ->
          when (name) {
            "getFrameworkProperties" -> properties
            "log" -> Unit
            else -> error("Unexpected framework method $name")
          }
        },
        {},
      )
      entry.onModuleLoaded(fake { name -> if (name == "isSystemServer") system else error(name) })
      var reads = 0
      entry.onSystemServerStarting(
        fake { name ->
          if (name == "getClassLoader") {
            reads++
            javaClass.classLoader
          } else {
            error(name)
          }
        },
      )
      assertEquals(0, reads)
    }
  }

  @Test
  fun unattachedFrameworkAndOutOfOrderCallbacksFailClosed() {
    val entry = ModernEntry()
    entry.onModuleLoaded(fake { error("must not read process") })
    entry.onSystemServerStarting(fake { error("must not resolve context") })
    assertFalse(entry.onHotReloading(fake<XposedModuleInterface.HotReloadingParam> { error("must not read reload") }))
  }
}

private inline fun <reified T> fake(crossinline action: (String) -> Any?): T =
  Proxy.newProxyInstance(T::class.java.classLoader, arrayOf(T::class.java)) { _, method, _ -> action(method.name) } as T
