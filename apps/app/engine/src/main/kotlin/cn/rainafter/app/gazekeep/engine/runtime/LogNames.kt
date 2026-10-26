package cn.rainafter.app.gazekeep.engine.runtime

/**
 * 日志中的类型短名。Release 保留这些类型的类名（见 proguard-rules.pro），但 R8 会去掉内部类元数据，
 * simpleName 退化为「外部类$内部类」；这里只依赖二进制名，Debug 与 Release 输出一致。
 */
fun logName(value: Any): String = logName(value.javaClass.name)

internal fun logName(binaryName: String): String = binaryName.substringAfterLast('.').substringAfterLast('$')
