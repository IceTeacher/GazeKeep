package cn.rainafter.app.gazekeep.backend.lsposed

import android.os.IBinder
import org.lsposed.hiddenapibypass.HiddenApiBypass

internal class SystemPowerBinderResolver {
  fun resolve(): IBinder? = runCatching {
    if (!exemption.ensure()) return null
    val serviceManager = Class.forName("android.os.ServiceManager")
    serviceManager.getDeclaredMethod("getService", String::class.java)
      .invoke(null, "power") as? IBinder
  }.getOrNull()

  private companion object {
    // 设置页探测与运行服务各自持有后端实例，豁免状态须进程共享。
    val exemption = HiddenApiExemption { HiddenApiBypass.addHiddenApiExemptions("Landroid/os/ServiceManager;") }
  }
}
