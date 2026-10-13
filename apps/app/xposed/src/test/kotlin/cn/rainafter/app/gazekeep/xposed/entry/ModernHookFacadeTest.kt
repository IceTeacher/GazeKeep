package cn.rainafter.app.gazekeep.xposed.entry

import cn.rainafter.app.gazekeep.xposed.hook.HookFacade
import io.github.libxposed.api.XposedInterface
import java.lang.reflect.Proxy
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame

class ModernHookFacadeTest {
  @Test
  fun forwardsChangedArgumentsExactlyOnce() {
    val fixture = FrameworkFixture()
    fixture.facade.hookBefore(fixture.method) { it.args[0] = "changed" }
    assertEquals("result", fixture.invoke())
    assertEquals(listOf("changed"), fixture.proceededArgs)
    assertEquals(1, fixture.calls)
  }

  @Test
  fun replacementIncludingNullSkipsOriginal() {
    val fixture = FrameworkFixture()
    fixture.facade.hookBefore(fixture.method) { it.setResult(null) }
    assertEquals(null, fixture.invoke())
    assertEquals(0, fixture.calls)
  }

  @Test
  fun moduleFailureDiscardsReplacementAndChangedArguments() {
    val fixture = FrameworkFixture()
    fixture.facade.hookBefore(fixture.method) {
      it.args[0] = "changed"
      it.setResult("replacement")
      throw AssertionError("module failure")
    }
    assertEquals("result", fixture.invoke())
    assertEquals(listOf("original"), fixture.proceededArgs)
    assertEquals(1, fixture.calls)
  }

  @Test
  fun argumentReadFailureStillProceedsExactlyOnce() {
    val fixture = FrameworkFixture()
    fixture.argumentFailure = AssertionError("argument read failed")
    fixture.facade.hookBefore(fixture.method) { error("must not call module") }
    assertEquals("result", fixture.invoke())
    assertEquals(listOf("original"), fixture.proceededArgs)
    assertEquals(1, fixture.calls)
  }

  @Test
  fun originalFailurePropagatesWithoutRetry() {
    val fixture = FrameworkFixture()
    val failure = IllegalStateException("original failure")
    fixture.originalFailure = failure
    fixture.facade.hookBefore(fixture.method) { }
    assertSame(failure, assertFailsWith<IllegalStateException> { fixture.invoke() })
    assertEquals(1, fixture.calls)
  }

  @Test
  fun afterFailurePreservesOriginalResult() {
    val fixture = FrameworkFixture()
    fixture.facade.hookAfter(fixture.method) { args, result ->
      assertEquals(listOf("original"), args.toList())
      assertEquals("result", result)
      throw AssertionError("module failure")
    }
    assertEquals("result", fixture.invoke())
    assertEquals(1, fixture.calls)
  }

  @Test
  fun afterDoesNotRunWhenOriginalFails() {
    val fixture = FrameworkFixture()
    val failure = IllegalStateException("original failure")
    fixture.originalFailure = failure
    var callbacks = 0
    fixture.facade.hookAfter(fixture.method) { _, _ -> callbacks++ }
    assertSame(failure, assertFailsWith<IllegalStateException> { fixture.invoke() })
    assertEquals(0, callbacks)
    assertEquals(1, fixture.calls)
  }
}

/** 仅模拟框架契约，不把 JVM fixture 当作实际框架兼容证据。 */
private class FrameworkFixture {
  val method = HashMap::class.java.getMethod("get", Any::class.java)
  private lateinit var hooker: XposedInterface.Hooker
  var calls = 0
  var proceededArgs: List<Any?> = emptyList()
  var originalFailure: Throwable? = null
  var argumentFailure: Throwable? = null
  private val handle = proxy<XposedInterface.HookHandle> { name, _ ->
    when (name) {
      "getExecutable" -> method
      "unhook" -> Unit
      else -> error("Unexpected handle method $name")
    }
  }
  private val builder: XposedInterface.HookBuilder = proxy { name, args ->
    when (name) {
      "setExceptionMode" -> {
        assertEquals(XposedInterface.ExceptionMode.PROTECTIVE, args[0])
        builder
      }

      "intercept" -> {
        hooker = args[0] as XposedInterface.Hooker
        handle
      }

      else -> error("Unexpected builder method $name")
    }
  }
  val facade: HookFacade

  init {
    val module = ModernEntry()
    // 测试充当框架，生产入口不得自行 attachFramework。
    module.attachFramework(
      proxy<XposedInterface> { name, _ ->
        when (name) {
          "hook" -> builder
          else -> error("Unexpected framework method $name")
        }
      },
      {},
    )
    facade = ModernHookFacade(module)
  }

  fun invoke(): Any? = hooker.intercept(
    proxy<XposedInterface.Chain> { name, args ->
      when (name) {
        "getArgs" -> {
          argumentFailure?.let { throw it }
          listOf("original")
        }

        "proceed" -> {
          calls++
          proceededArgs = if (args.isEmpty()) listOf("original") else (args[0] as Array<*>).toList()
          originalFailure?.let { throw it }
          "result"
        }

        else -> error("Unexpected chain method $name")
      }
    },
  )
}

private inline fun <reified T> proxy(crossinline action: (String, Array<out Any?>) -> Any?): T =
  Proxy.newProxyInstance(T::class.java.classLoader, arrayOf(T::class.java)) { _, method, args ->
    action(method.name, args ?: emptyArray())
  } as T
