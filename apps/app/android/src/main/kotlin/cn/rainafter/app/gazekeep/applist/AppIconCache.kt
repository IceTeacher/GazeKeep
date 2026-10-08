package cn.rainafter.app.gazekeep.applist

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap

/**
 * 应用图标内存缓存与加载管理器。
 * 将应用图标统一渲染并缓存为固定尺寸的 ImageBitmap，避免在主线程/滚动中进行 I/O 与解析，保障列表滚动流畅度。
 */
object AppIconCache {
    private val memoryCache = LruCache<String, ImageBitmap>(300)

    fun get(packageName: String): ImageBitmap? = memoryCache.get(packageName)

    fun put(packageName: String, bitmap: ImageBitmap) {
        memoryCache.put(packageName, bitmap)
    }

    fun loadBitmap(context: Context, packageName: String, sizePx: Int): ImageBitmap? {
        get(packageName)?.let { return it }

        return try {
            val pm = context.packageManager
            val drawable = pm.getApplicationIcon(packageName)
            val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            drawable.setBounds(0, 0, canvas.width, canvas.height)
            drawable.draw(canvas)
            val imageBitmap = bitmap.asImageBitmap()
            put(packageName, imageBitmap)
            imageBitmap
        } catch (_: Throwable) {
            null
        }
    }
}
