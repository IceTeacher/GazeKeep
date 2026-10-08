package cn.rainafter.app.gazekeep.xposed.protocol

object BridgeProtocol {
  const val DESCRIPTOR = "cn.rainafter.app.gazekeep.bridge"
  const val TX_BASE = 0x475A_4B00
  const val TX_PING = TX_BASE + 1
  const val TX_REGISTER = TX_BASE + 2
  const val TX_EXTEND = TX_BASE + 3
  const val TX_UNREGISTER = TX_BASE + 4
  const val PROTOCOL_VERSION = 2

  const val RESULT_OK = 0
  const val RESULT_STALE = 1
  const val RESULT_DENIED = 2
  const val RESULT_UNAVAILABLE = 3

  const val HOOK_UNSUPPORTED = 0
  const val HOOK_READY = 1
}
