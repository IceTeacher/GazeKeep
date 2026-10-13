package cn.rainafter.app.gazekeep.xposed.hook

import java.lang.reflect.Method

fun interface HookRegistration {
  fun unhook()
}

/** 只在安装/回滚阶段使用，不进入 PMS 回调。 */
internal class HookRegistrations {
  private val handles = mutableListOf<HookRegistration>()

  fun add(handle: HookRegistration) {
    handles += handle
  }

  fun rollback() {
    handles.asReversed().forEach { runCatching { it.unhook() } }
    handles.clear()
  }
}

internal class TrackingHookFacade(private val delegate: HookFacade, private val registrations: HookRegistrations) :
  HookFacade {
  override fun hookBefore(method: Method, callback: (HookBeforeParam) -> Unit): HookRegistration =
    delegate.hookBefore(method, callback).also(registrations::add)

  override fun hookAfter(method: Method, callback: (Array<Any?>, Any?) -> Unit): HookRegistration =
    delegate.hookAfter(method, callback).also(registrations::add)
}

/** 可选钩子失败只使自身失效；解除失败时仍不得改变已提交的主链路。 */
internal fun installOptionalHooks(facade: HookFacade, disable: () -> Unit, install: (HookFacade) -> Unit): Boolean {
  val registrations = HookRegistrations()
  return try {
    install(TrackingHookFacade(facade, registrations))
    true
  } catch (_: Throwable) {
    runCatching(disable)
    registrations.rollback()
    false
  }
}
