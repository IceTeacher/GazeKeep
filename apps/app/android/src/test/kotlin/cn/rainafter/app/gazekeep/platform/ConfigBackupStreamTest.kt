package cn.rainafter.app.gazekeep.platform

import android.app.Activity
import android.content.Intent
import android.net.Uri
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.contract.ActivityResultContract
import androidx.core.app.ActivityOptionsCompat
import cn.rainafter.app.gazekeep.core.model.ConfigBackupCodec
import cn.rainafter.app.gazekeep.core.model.GazeConfig
import cn.rainafter.app.gazekeep.ui.contract.ConfigReadResult
import cn.rainafter.app.gazekeep.ui.contract.ConfigTransferResult
import cn.rainafter.app.gazekeep.ui.contract.ControllerState
import cn.rainafter.app.gazekeep.ui.contract.GazeKeepController
import cn.rainafter.app.gazekeep.ui.contract.SelfTestState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
class ConfigBackupStreamTest {
  @Test
  fun readBoundedUtf8ReadsValidPayloadUpToExactLimit() {
    val exact = "a".repeat(ConfigBackupCodec.MAX_BACKUP_BYTES)
    val result = assertIs<ConfigReadResult.Loaded>(
      readBoundedUtf8(ByteArrayInputStream(exact.toByteArray(Charsets.UTF_8))),
    )
    assertEquals(exact, result.content)

    val chinese = """{"schemaVersion":1,"note":"中文"}"""
    val utf8Result = assertIs<ConfigReadResult.Loaded>(
      readBoundedUtf8(ByteArrayInputStream(chinese.toByteArray(Charsets.UTF_8))),
    )
    assertEquals(chinese, utf8Result.content)
  }

  @Test
  fun readBoundedUtf8RejectsPayloadExceedingLimit() {
    val tooLarge = ByteArray(ConfigBackupCodec.MAX_BACKUP_BYTES + 1) { 'a'.code.toByte() }
    assertEquals(
      ConfigReadResult.TooLarge,
      readBoundedUtf8(ByteArrayInputStream(tooLarge)),
    )
  }

  @Test
  fun safCoordinatorCompletesExportAndImportAcrossActivityRecreation() {
    val pendingRequestCodes = ArrayDeque<Int>()
    val registry = object : ActivityResultRegistry() {
      override fun <I, O> onLaunch(
        requestCode: Int,
        contract: ActivityResultContract<I, O>,
        input: I,
        options: ActivityOptionsCompat?,
      ) {
        pendingRequestCodes += requestCode
      }
    }

    val exportedJson = ConfigBackupCodec.encode(GazeConfig(leadMillis = 12_000L, maxHoldMinutes = 45))
    val fakeOutput = ByteArrayOutputStream()
    val importedResults = mutableListOf<ConfigReadResult>()
    val exportResults = mutableListOf<ConfigTransferResult>()

    fun newController() = object : GazeKeepController {
      override val state = MutableStateFlow(ControllerState())
      override val selfTest = MutableStateFlow(SelfTestState())
      override suspend fun updateConfig(transform: (GazeConfig) -> GazeConfig) = Unit
      override suspend fun exportConfigJson(): String = exportedJson
      override suspend fun importConfigContent(readResult: ConfigReadResult): Boolean {
        importedResults += readResult
        return readResult is ConfigReadResult.Loaded
      }
      override fun reportConfigExportResult(result: ConfigTransferResult) {
        exportResults += result
      }
      override fun clearBackupNotice(token: Long) = Unit
      override suspend fun setEnabled(enabled: Boolean) = Unit
      override suspend fun beginSelfTest() = Unit
      override suspend fun captureSelfTest() = Unit
      override fun cancelSelfTest() = Unit
      override fun clearLogs() = Unit
      override fun refreshLogs() = Unit
      override fun refresh() = Unit
      override suspend fun loadApps() = Unit
    }

    // 1) 旧 Activity 实例发起导出后发生旋转重建，SAF 返回结果由全新实例接收并完成写盘。
    val oldCoordinator = SafConfigBackupCoordinator(
      registry = registry,
      scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined),
      controller = newController(),
      openOutput = { error("old activity should not write") },
      openInput = { error("old activity should not read") },
      ioDispatcher = Dispatchers.Unconfined,
      mainDispatcher = Dispatchers.Unconfined,
    )
    oldCoordinator.launchExport()
    val exportReqCode = pendingRequestCodes.removeFirst()

    val recreatedExportCoordinator = SafConfigBackupCoordinator(
      registry = registry,
      scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined),
      controller = newController(),
      openOutput = { fakeOutput },
      openInput = { null },
      ioDispatcher = Dispatchers.Unconfined,
      mainDispatcher = Dispatchers.Unconfined,
    )
    registry.dispatchResult(
      exportReqCode,
      Activity.RESULT_OK,
      Intent().setData(Uri.parse("content://test/export.json")),
    )
    assertEquals(exportedJson, fakeOutput.toByteArray().decodeToString())
    assertEquals(listOf<ConfigTransferResult>(ConfigTransferResult.Saved), exportResults)

    // 2) 重建后的实例发起导入并再次经历重建，SAF 返回结果由第三个全新实例接收并完成导入。
    recreatedExportCoordinator.launchImport()
    val importReqCode = pendingRequestCodes.removeFirst()
    SafConfigBackupCoordinator(
      registry = registry,
      scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined),
      controller = newController(),
      openOutput = { null },
      openInput = { ByteArrayInputStream(exportedJson.toByteArray(Charsets.UTF_8)) },
      ioDispatcher = Dispatchers.Unconfined,
      mainDispatcher = Dispatchers.Unconfined,
    )
    registry.dispatchResult(
      importReqCode,
      Activity.RESULT_OK,
      Intent().setData(Uri.parse("content://test/import.json")),
    )
    assertEquals(1, importedResults.size)
    val loaded = assertIs<ConfigReadResult.Loaded>(importedResults.single())
    assertTrue(loaded.content.contains("\"leadMillis\": 12000"))
  }
}
