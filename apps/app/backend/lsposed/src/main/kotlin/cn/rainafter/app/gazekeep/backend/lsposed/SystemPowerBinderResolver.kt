package cn.rainafter.app.gazekeep.backend.lsposed

import android.os.IBinder
import org.lsposed.hiddenapibypass.HiddenApiBypass

internal class SystemPowerBinderResolver {
    fun resolve(): IBinder? = runCatching {
        HiddenApiBypass.addHiddenApiExemptions("Landroid/os/ServiceManager;")
        val serviceManager = Class.forName("android.os.ServiceManager")
        serviceManager.getDeclaredMethod("getService", String::class.java)
            .invoke(null, "power") as? IBinder
    }.getOrNull()
}
