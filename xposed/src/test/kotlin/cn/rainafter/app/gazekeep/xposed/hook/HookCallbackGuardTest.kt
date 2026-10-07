package cn.rainafter.app.gazekeep.xposed.hook

import kotlin.test.Test
import kotlin.test.assertEquals

class HookCallbackGuardTest {
    @Test
    fun disablesAfterFiveCumulativeFailuresEvenWithSuccessBetween() {
        var calls = 0
        var disabled = 0
        var errors = 0
        val guard = HookCallbackGuard({ errors++ }, { disabled++ })
        repeat(5) {
            guard.run { calls++; throw AssertionError("failure") }
            guard.run { calls++ }
        }
        assertEquals(5, errors)
        assertEquals(1, disabled)
        assertEquals(9, calls)
        guard.run { calls++ }
        assertEquals(9, calls)
    }

    @Test
    fun swallowsFailuresFromReportingAndDisabling() {
        val guard = HookCallbackGuard(
            { throw AssertionError("reporting") },
            { throw AssertionError("cleanup") },
        )
        repeat(6) { guard.run { throw AssertionError("callback") } }
    }
}
