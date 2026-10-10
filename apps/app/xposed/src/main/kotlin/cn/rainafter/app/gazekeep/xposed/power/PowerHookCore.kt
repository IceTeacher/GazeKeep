package cn.rainafter.app.gazekeep.xposed.power

import android.annotation.SuppressLint
import android.content.Context
import android.os.Binder
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.Parcel
import android.os.PowerManager
import android.os.SystemClock
import android.util.Log
import cn.rainafter.app.gazekeep.xposed.protocol.BridgeProtocol
import cn.rainafter.app.gazekeep.xposed.protocol.IGazeKeepClient
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

/** system_server 中的最小状态核心；不持久化、不启动协程，也不触碰相机。 */
class PowerHookCore(private val systemContext: Context) {
  private val thread = HandlerThread("GazeKeep").apply { start() }
  private val handler = Handler(thread.looper) { message ->
    if (message.what == MSG_PRE_TIMEOUT) {
      dispatch(message.obj as PreTimeout)
      true
    } else {
      false
    }
  }
  private val client = AtomicReference<ClientRecord?>(null)
  private val tickets = ExtendTicketGate()
  private val currentSeq = AtomicLong(0L)

  @Volatile private var lastScreenOffAt = Long.MIN_VALUE

  @Volatile private var ourLastEventTime = -1L

  @Volatile private var appUid: Int? = null

  @Volatile private var closed = false

  /** 在 PMS 锁内调用；这里只计算并向自己的 Handler 投递消息。 */
  fun onDeadlineComputed(screenOffAt: Long, lastActivityTime: Long, screenDimAt: Long = -1L) {
    if (closed) return
    val current = client.get() ?: return
    if (screenOffAt == lastScreenOffAt) return
    tickets.invalidate()
    lastScreenOffAt = screenOffAt
    val seq = currentSeq.incrementAndGet()
    val external = if (lastActivityTime != ourLastEventTime) lastActivityTime else -1L
    handler.removeMessages(MSG_PRE_TIMEOUT)
    val dimAt = screenDimAt.takeIf { it > 0L && it <= screenOffAt } ?: -1L
    val at = preTimeoutAt(screenOffAt, dimAt, current.leadMillis, SystemClock.uptimeMillis())
    handler.sendMessageAtTime(
      handler.obtainMessage(MSG_PRE_TIMEOUT, PreTimeout(seq, screenOffAt, dimAt, external)),
      at,
    )
  }

  /** 由 IPowerManager.Stub#onTransact 的适配器调用。 */
  fun handleTransaction(code: Int, data: Parcel, reply: Parcel, callingUid: Int): Boolean {
    if (closed) return false
    if (code !in BridgeProtocol.TX_PING..BridgeProtocol.TX_BASE + 0xFF) return false
    return try {
      data.enforceInterface(BridgeProtocol.DESCRIPTOR)
      when (code) {
        BridgeProtocol.TX_PING -> {
          reply.writeNoException()
          reply.writeInt(BridgeProtocol.PROTOCOL_VERSION)
          reply.writeInt(BridgeProtocol.HOOK_READY)
          reply.writeString("runtime")
        }

        BridgeProtocol.TX_REGISTER -> register(data, reply, callingUid)

        BridgeProtocol.TX_EXTEND -> extend(data, reply, callingUid)

        BridgeProtocol.TX_UNREGISTER -> unregister(reply, callingUid)

        else -> {
          reply.writeNoException()
          reply.writeInt(BridgeProtocol.RESULT_UNAVAILABLE)
        }
      }
      true
    } catch (throwable: Throwable) {
      Log.w(TAG, "bridge transaction failed", throwable)
      runCatching { reply.writeException(throwable as? Exception ?: RuntimeException(throwable)) }
      true
    }
  }

  fun disable() {
    closed = true
    tickets.invalidate()
    currentSeq.incrementAndGet()
    handler.post { close() }
  }

  fun close() {
    closed = true
    tickets.invalidate()
    currentSeq.incrementAndGet()
    client.getAndSet(null)?.let { runCatching { it.binder.unlinkToDeath(it.deathRecipient, 0) } }
    lastScreenOffAt = Long.MIN_VALUE
    handler.removeCallbacksAndMessages(null)
    thread.quitSafely()
  }

  private fun register(data: Parcel, reply: Parcel, callingUid: Int) {
    if (!isOurUid(callingUid)) {
      reply.writeNoException()
      reply.writeInt(BridgeProtocol.RESULT_DENIED)
      return
    }
    val binder = data.readStrongBinder()
    val callback = IGazeKeepClient.Stub.asInterface(binder)
    val leadMillis = data.readLong().coerceAtLeast(0L)
    if (callback == null) {
      reply.writeNoException()
      reply.writeInt(BridgeProtocol.RESULT_UNAVAILABLE)
      return
    }
    tickets.invalidate()
    currentSeq.incrementAndGet()
    client.getAndSet(null)?.let { runCatching { it.binder.unlinkToDeath(it.deathRecipient, 0) } }
    lastScreenOffAt = Long.MIN_VALUE
    lateinit var record: ClientRecord
    val deathRecipient = IBinder.DeathRecipient {
      if (client.compareAndSet(record, null)) {
        tickets.invalidate()
        currentSeq.incrementAndGet()
        handler.removeMessages(MSG_PRE_TIMEOUT)
      }
    }
    record = ClientRecord(binder, callback, leadMillis, deathRecipient)
    client.set(record)
    try {
      binder.linkToDeath(deathRecipient, 0)
    } catch (error: Throwable) {
      client.compareAndSet(record, null)
      throw error
    }
    reply.writeNoException()
    reply.writeInt(BridgeProtocol.RESULT_OK)
  }

  private fun extend(data: Parcel, reply: Parcel, callingUid: Int) {
    val seq = data.readLong()
    val result = when {
      !isOurUid(callingUid) -> BridgeProtocol.RESULT_DENIED
      closed || client.get() == null || seq != currentSeq.get() -> BridgeProtocol.RESULT_STALE
      !tickets.consume(seq, SystemClock.uptimeMillis()) -> BridgeProtocol.RESULT_STALE
      invokeUserActivity() -> BridgeProtocol.RESULT_OK
      else -> BridgeProtocol.RESULT_UNAVAILABLE
    }
    reply.writeNoException()
    reply.writeInt(result)
  }

  private fun unregister(reply: Parcel, callingUid: Int) {
    if (isOurUid(callingUid)) {
      tickets.invalidate()
      currentSeq.incrementAndGet()
      client.getAndSet(null)?.let { runCatching { it.binder.unlinkToDeath(it.deathRecipient, 0) } }
      lastScreenOffAt = Long.MIN_VALUE
      handler.removeMessages(MSG_PRE_TIMEOUT)
    }
    reply.writeNoException()
    reply.writeInt(if (isOurUid(callingUid)) BridgeProtocol.RESULT_OK else BridgeProtocol.RESULT_DENIED)
  }

  private fun dispatch(timeout: PreTimeout) {
    if (closed) return
    val current = client.get() ?: return
    if (timeout.seq != currentSeq.get() || SystemClock.uptimeMillis() >= timeout.screenOffAt) return
    tickets.offer(timeout.seq, timeout.screenOffAt)
    runCatching {
      current.callback.onPreTimeout(
        timeout.seq,
        timeout.screenOffAt,
        timeout.screenDimAt,
        foregroundPackage(),
        timeout.lastExternalActivity,
      )
    }.onFailure { error ->
      tickets.invalidate()
      currentSeq.incrementAndGet()
      if (client.compareAndSet(current, null)) {
        runCatching { current.binder.unlinkToDeath(current.deathRecipient, 0) }
        handler.removeMessages(MSG_PRE_TIMEOUT)
      }
      Log.w(TAG, "pre-timeout callback failed", error)
    }
  }

  // 仅在 system_server 的 Handler 中执行；普通应用不调用，ROM 签名差异返回未知。
  @SuppressLint("BlockedPrivateApi")
  private fun foregroundPackage(): String? = runCatching {
    val manager = Class.forName("android.app.ActivityTaskManager")
    val instance = manager.getDeclaredMethod("getInstance").invoke(null) ?: return null
    val method = manager.getDeclaredMethod("getTasks", Int::class.javaPrimitiveType)
    val task = (method.invoke(instance, 1) as? List<*>)?.singleOrNull() ?: return null
    val top = task.javaClass.getField("topActivity").get(task) as? android.content.ComponentName
    top?.packageName
  }.getOrNull()

  private fun invokeUserActivity(): Boolean {
    val now = SystemClock.uptimeMillis()
    // system_server 加载 Xposed 入口时，power 服务可能尚未注册；每次执行时再取，避免永久缓存 null。
    val power = runCatching {
      systemContext.getSystemService(PowerManager::class.java)
    }.getOrNull() ?: return false
    val token = Binder.clearCallingIdentity()
    return try {
      val method = power.javaClass.methods.firstOrNull { candidate ->
        candidate.name == "userActivity" && candidate.parameterTypes.contentEquals(
          arrayOf(Long::class.javaPrimitiveType, Int::class.javaPrimitiveType, Int::class.javaPrimitiveType),
        )
      } ?: return false
      method.invoke(power, now, USER_ACTIVITY_EVENT_OTHER, 0)
      ourLastEventTime = now
      true
    } catch (throwable: Throwable) {
      Log.w(TAG, "userActivity unavailable", throwable)
      false
    } finally {
      Binder.restoreCallingIdentity(token)
    }
  }

  private fun isOurUid(uid: Int): Boolean {
    val known = appUid ?: runCatching {
      systemContext.packageManager.getPackageUid(PACKAGE_NAME, 0)
    }.getOrNull()?.also { appUid = it } ?: return false
    return appId(uid) == appId(known)
  }

  private fun appId(uid: Int): Int = uid % USER_RANGE

  private data class ClientRecord(
    val binder: IBinder,
    val callback: IGazeKeepClient,
    val leadMillis: Long,
    val deathRecipient: IBinder.DeathRecipient,
  )

  private data class PreTimeout(
    val seq: Long,
    val screenOffAt: Long,
    val screenDimAt: Long,
    val lastExternalActivity: Long,
  )

  private companion object {
    const val TAG = "GazeKeepXposed"
    const val PACKAGE_NAME = "cn.rainafter.app.gazekeep"
    const val MSG_PRE_TIMEOUT = 1
    const val USER_ACTIVITY_EVENT_OTHER = 1
    const val USER_RANGE = 100_000
  }
}
