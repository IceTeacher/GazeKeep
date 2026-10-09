package cn.rainafter.app.gazekeep.backend.shizuku.protocol

import java.util.concurrent.TimeoutException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ShellCommandRunnerTest {
  @Test fun commandsAreBoundedAndFailureIsReported() {
    assertFailsWith<TimeoutException> { ShellCommandRunner.run("sleep", "2", timeoutMs = 100L) }
    assertEquals("ok\n", ShellCommandRunner.run("printf", "ok\n"))
    assertFailsWith<IllegalStateException> { ShellCommandRunner.run("false") }
  }
}
