package cn.rainafter.app.gazekeep.backend.shizuku

import android.os.Binder
import android.os.IBinder
import android.os.IInterface
import android.os.Parcel

/** App 与 Shizuku shell UserService 之间的最小协议；不传原始 dumpsys 文本。 */
interface IGazeKeepShell : IInterface {
    fun version(): Int
    fun userActivity(uptime: Long)
    fun readPower(): PowerSnapshot
    fun getScreenOffTimeout(): Int
    fun setScreenOffTimeout(millis: Int)

    abstract class Stub : Binder(), IGazeKeepShell {
        init {
            attachInterface(this, DESCRIPTOR)
        }

        override fun asBinder(): IBinder = this

        override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
            if (code == INTERFACE_TRANSACTION) {
                reply?.writeString(DESCRIPTOR)
                return true
            }
            if (code == DESTROY_TRANSACTION || code == DESTROY_TRANSACTION_ALT) {
                runCatching { onDestroyUserService() }
                Thread { exitUserService() }.start()
                return true
            }
            data.enforceInterface(DESCRIPTOR)
            return when (code) {
                TRANSACTION_VERSION -> {
                    reply?.writeNoException()
                    reply?.writeInt(PROTOCOL_VERSION)
                    true
                }
                TRANSACTION_USER_ACTIVITY -> {
                    userActivity(data.readLong())
                    reply?.writeNoException()
                    true
                }
                TRANSACTION_READ_POWER -> {
                    val snapshot = readPower()
                    reply?.writeNoException()
                    reply?.writeLong(snapshot.lastUserActivityUptime ?: UNKNOWN_LONG)
                    reply?.writeInt(snapshot.screenOffTimeoutMs ?: UNKNOWN_INT)
                    reply?.writeInt(snapshot.dimDurationMs ?: UNKNOWN_INT)
                    reply?.writeInt(if (snapshot.hasScreenWakeLock) 1 else 0)
                    reply?.writeInt(if (snapshot.interactive) 1 else 0)
                    true
                }
                TRANSACTION_GET_TIMEOUT -> {
                    reply?.writeNoException()
                    reply?.writeInt(getScreenOffTimeout())
                    true
                }
                TRANSACTION_SET_TIMEOUT -> {
                    setScreenOffTimeout(data.readInt())
                    reply?.writeNoException()
                    true
                }
                else -> super.onTransact(code, data, reply, flags)
            }
        }

        protected open fun onDestroyUserService() = Unit

        private fun exitUserService() {
            // Shizuku 要求 UserService 的 destroy transaction 最终退出 shell 进程。
            System.exit(0)
        }

        companion object {
            private const val DESCRIPTOR = "cn.rainafter.app.gazekeep.backend.shizuku.IGazeKeepShell"
            private const val TRANSACTION_VERSION = IBinder.FIRST_CALL_TRANSACTION
            private const val TRANSACTION_USER_ACTIVITY = IBinder.FIRST_CALL_TRANSACTION + 1
            private const val TRANSACTION_READ_POWER = IBinder.FIRST_CALL_TRANSACTION + 2
            private const val DESTROY_TRANSACTION = 16_777_114
            private const val DESTROY_TRANSACTION_ALT = 16_777_115
            private const val INTERFACE_TRANSACTION = IBinder.INTERFACE_TRANSACTION
            private const val TRANSACTION_GET_TIMEOUT = IBinder.FIRST_CALL_TRANSACTION + 3
            private const val TRANSACTION_SET_TIMEOUT = IBinder.FIRST_CALL_TRANSACTION + 4
            private const val UNKNOWN_LONG = Long.MIN_VALUE
            private const val UNKNOWN_INT = Int.MIN_VALUE
            const val PROTOCOL_VERSION = 2

            fun asInterface(binder: IBinder?): IGazeKeepShell? {
                if (binder == null) return null
                val local = binder.queryLocalInterface(DESCRIPTOR)
                return local as? IGazeKeepShell ?: Proxy(binder)
            }
        }

        private class Proxy(private val remote: IBinder) : IGazeKeepShell {
            override fun asBinder(): IBinder = remote

            override fun version(): Int = transact { data, reply ->
                data.writeInterfaceToken(DESCRIPTOR)
                remote.transact(TRANSACTION_VERSION, data, reply, 0)
                reply.readException()
                reply.readInt()
            }

            override fun userActivity(uptime: Long) {
                transact { data, reply ->
                    data.writeInterfaceToken(DESCRIPTOR)
                    data.writeLong(uptime)
                    remote.transact(TRANSACTION_USER_ACTIVITY, data, reply, 0)
                    reply.readException()
                }
            }

            override fun readPower(): PowerSnapshot = transact { data, reply ->
                data.writeInterfaceToken(DESCRIPTOR)
                remote.transact(TRANSACTION_READ_POWER, data, reply, 0)
                reply.readException()
                PowerSnapshot(
                    lastUserActivityUptime = reply.readLong().takeUnless { it == UNKNOWN_LONG },
                    screenOffTimeoutMs = reply.readInt().takeUnless { it == UNKNOWN_INT },
                    dimDurationMs = reply.readInt().takeUnless { it == UNKNOWN_INT },
                    hasScreenWakeLock = reply.readInt() != 0,
                    interactive = reply.readInt() != 0,
                )
            }

            override fun getScreenOffTimeout(): Int = transact { data, reply ->
                data.writeInterfaceToken(DESCRIPTOR)
                remote.transact(TRANSACTION_GET_TIMEOUT, data, reply, 0)
                reply.readException()
                reply.readInt()
            }

            override fun setScreenOffTimeout(millis: Int) {
                transact { data, reply ->
                    data.writeInterfaceToken(DESCRIPTOR)
                    data.writeInt(millis)
                    remote.transact(TRANSACTION_SET_TIMEOUT, data, reply, 0)
                    reply.readException()
                }
            }

            private fun <T> transact(block: (Parcel, Parcel) -> T): T {
                val data = Parcel.obtain()
                val reply = Parcel.obtain()
                return try {
                    block(data, reply)
                } finally {
                    data.recycle()
                    reply.recycle()
                }
            }
        }
    }
}
