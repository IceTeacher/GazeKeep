package cn.rainafter.app.gazekeep.engine.classify

import androidx.camera.core.ImageProxy

/** 只读取 Y 平面，先于模型拦截明显暗光帧。 */
object LumaProbe {
    const val DEFAULT_MIN_LUMA = 25.0

    fun mean(image: ImageProxy, step: Int = 8): Double {
        require(step > 0)
        val plane = image.planes.firstOrNull() ?: return 0.0
        val buffer = plane.buffer.duplicate()
        val rowStride = plane.rowStride
        val pixelStride = plane.pixelStride
        var sum = 0L
        var count = 0L
        var y = 0
        while (y < image.height) {
            var x = 0
            while (x < image.width) {
                val index = y * rowStride + x * pixelStride
                if (index < buffer.limit()) {
                    sum += buffer.get(index).toInt() and 0xff
                    count++
                }
                x += step
            }
            y += step
        }
        return if (count == 0L) 0.0 else sum.toDouble() / count
    }

    fun isTooDark(image: ImageProxy, minLuma: Double = DEFAULT_MIN_LUMA): Boolean =
        mean(image) < minLuma
}
