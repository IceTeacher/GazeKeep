package cn.rainafter.app.gazekeep.xposed.hook

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DeadlineResolverTest {
    @Test
    fun rejectsWrongNameParametersAndReturnType() {
        val longType = Long::class.javaPrimitiveType!!
        val method = SignatureFixture::class.java.getDeclaredMethod("updateUserActivity", longType, longType)
        assertTrue(matchesHookSignature(method, "updateUserActivity", arrayOf(longType, longType), longType))
        assertFalse(matchesHookSignature(method, "wrongName", arrayOf(longType, longType), longType))
        assertFalse(matchesHookSignature(method, "updateUserActivity", arrayOf(longType), longType))
        assertFalse(matchesHookSignature(method, "updateUserActivity", arrayOf(longType, longType), Void.TYPE))
    }

    @Test
    fun recordsAllCandidatesWhenFrameworkClassesAreAbsent() {
        val resolution = DeadlineResolver(javaClass.classLoader ?: ClassLoader.getSystemClassLoader()).resolve()
        assertEquals(null, resolution.candidate)
        assertEquals(3, resolution.tried.size)
    }
}

private class SignatureFixture {
    fun updateUserActivity(nextDimming: Long, duration: Long): Long = nextDimming + duration
}
