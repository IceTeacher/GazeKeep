package cn.rainafter.app.gazekeep.xposed.hook

import kotlin.test.Test
import kotlin.test.assertEquals

class DeadlineResolverTest {
    @Test
    fun recordsAllCandidatesWhenFrameworkClassesAreAbsent() {
        val resolution = DeadlineResolver(javaClass.classLoader ?: ClassLoader.getSystemClassLoader()).resolve()
        assertEquals(null, resolution.candidate)
        assertEquals(3, resolution.tried.size)
    }
}
