package cn.rainafter.app.gazekeep.platform

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.contract.ActivityResultContract
import androidx.core.app.ActivityOptionsCompat
import cn.rainafter.app.gazekeep.app.AppGraph
import cn.rainafter.app.gazekeep.controller.AndroidGazeKeepController
import cn.rainafter.app.gazekeep.core.model.ConfigBackupCodec
import cn.rainafter.app.gazekeep.core.model.ConfigExportResult
import cn.rainafter.app.gazekeep.core.model.GazeConfig
import cn.rainafter.app.gazekeep.ui.contract.ConfigBackupFeedback
import cn.rainafter.app.gazekeep.ui.contract.ConfigReadResult
import cn.rainafter.app.gazekeep.ui.contract.ConfigTransferResult
import cn.rainafter.app.gazekeep.ui.contract.ControllerState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
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

    val exportedJson = assertIs<ConfigExportResult.Success>(
      ConfigBackupCodec.encode(GazeConfig(leadMillis = 12_000L, maxHoldMinutes = 45)),
    ).json
    val fakeOutput = ByteArrayOutputStream()
    val importedResults = mutableListOf<ConfigReadResult>()
    val exportResults = mutableListOf<ConfigTransferResult>()

    // 1) 旧 Activity 实例发起导出后发生旋转重建，SAF 返回结果由全新实例接收并完成写盘。
    val oldCoordinator = SafConfigBackupCoordinator(
      registry = registry,
      scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined),
      canExportNow = { true },
      exportConfig = { ConfigExportResult.Success(exportedJson) },
      importConfig = { error("old activity should not import") },
      reportExportResult = { exportResults += it },
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
      canExportNow = { true },
      exportConfig = { ConfigExportResult.Success(exportedJson) },
      importConfig = {
        importedResults += it
        it is ConfigReadResult.Loaded
      },
      reportExportResult = { exportResults += it },
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
      canExportNow = { true },
      exportConfig = { ConfigExportResult.Success(exportedJson) },
      importConfig = {
        importedResults += it
        it is ConfigReadResult.Loaded
      },
      reportExportResult = { exportResults += it },
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

  @Test
  fun safCoordinatorRefusesOversizedExportBeforeAndAfterPicker() {
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

    val overLimitPackages = (1..(ConfigBackupCodec.MAX_PACKAGE_LIST_SIZE + 1))
      .map { "com.example.pkg$it" }
      .toSet()
    val overLimitConfig = GazeConfig(whitelistPackages = overLimitPackages)
    val exportResults = mutableListOf<ConfigTransferResult>()
    val deletedUris = mutableListOf<Uri>()
    var openedOutput = false

    // 1) 已加载且名单超限时直接拒绝，不唤起 SAF 创建空文件。
    val loadedCoordinator = SafConfigBackupCoordinator(
      registry = registry,
      scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined),
      canExportNow = { ConfigBackupCodec.encode(overLimitConfig) is ConfigExportResult.Success },
      exportConfig = { ConfigBackupCodec.encode(overLimitConfig) },
      importConfig = { false },
      reportExportResult = { exportResults += it },
      openOutput = {
        openedOutput = true
        ByteArrayOutputStream()
      },
      openInput = { null },
      deleteExportedDocument = { deletedUris += it },
      ioDispatcher = Dispatchers.Unconfined,
      mainDispatcher = Dispatchers.Unconfined,
    )
    loadedCoordinator.launchExport()
    assertTrue(pendingRequestCodes.isEmpty(), "picker must not open when loaded list exceeds export limit")
    assertEquals(listOf<ConfigTransferResult>(ConfigTransferResult.TooLarge), exportResults)

    // 2) 若选择器打开后配置变超限，拒绝写盘并尝试清理空文档，汇报 TooLarge。
    val unloadedCoordinator = SafConfigBackupCoordinator(
      registry = registry,
      scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined),
      canExportNow = { true },
      exportConfig = { ConfigBackupCodec.encode(overLimitConfig) },
      importConfig = { false },
      reportExportResult = { exportResults += it },
      openOutput = {
        openedOutput = true
        ByteArrayOutputStream()
      },
      openInput = { null },
      deleteExportedDocument = { deletedUris += it },
      ioDispatcher = Dispatchers.Unconfined,
      mainDispatcher = Dispatchers.Unconfined,
    )
    unloadedCoordinator.launchExport()
    val reqCode = pendingRequestCodes.removeFirst()
    val targetUri = Uri.parse("content://test/empty.json")
    registry.dispatchResult(reqCode, Activity.RESULT_OK, Intent().setData(targetUri))
    assertEquals(false, openedOutput)
    assertEquals(listOf(targetUri), deletedUris)
    assertEquals(
      listOf<ConfigTransferResult>(ConfigTransferResult.TooLarge, ConfigTransferResult.TooLarge),
      exportResults,
    )
  }

  @Test
  fun androidConfigBackupSurvivesActivityDestructionAfterPickerReturns() {
    AndroidGazeKeepController.backupNoticeFlow.value = null
    val importActivityController = Robolectric.buildActivity(ComponentActivity::class.java).setup()
    val importActivity = importActivityController.get()
    val appContext = importActivity.applicationContext
    val store = AppGraph(appContext).settings
    val importCoordinator = createAndroidConfigBackupCoordinator(
      activity = importActivity,
      currentState = { ControllerState(loaded = true) },
      store = store,
    )

    val backupJson = assertIs<ConfigExportResult.Success>(
      ConfigBackupCodec.encode(GazeConfig(leadMillis = 12_000L, maxHoldMinutes = 45)),
    ).json
    val importUri = Uri.parse("content://test/survive-import.json")
    val readStarted = CountDownLatch(1)
    val allowReadFinish = CountDownLatch(1)
    val delegateInput = ByteArrayInputStream(backupJson.toByteArray(Charsets.UTF_8))
    val gatedInput = object : InputStream() {
      override fun read(): Int {
        readStarted.countDown()
        allowReadFinish.await(5, TimeUnit.SECONDS)
        return delegateInput.read()
      }

      override fun read(b: ByteArray, off: Int, len: Int): Int {
        readStarted.countDown()
        allowReadFinish.await(5, TimeUnit.SECONDS)
        return delegateInput.read(b, off, len)
      }
    }
    Shadows.shadowOf(appContext.contentResolver).registerInputStream(importUri, gatedInput)

    // 1) 选择器返回 URI 后，IO 读取中途销毁 Activity，导入协程不得被取消且须落盘并发布反馈。
    importCoordinator.launchImport()
    val startedImport = requireNotNull(Shadows.shadowOf(importActivity).nextStartedActivityForResult)
    Shadows.shadowOf(importActivity).receiveResult(
      startedImport.intent,
      Activity.RESULT_OK,
      Intent().setData(importUri),
    )
    assertTrue(readStarted.await(5, TimeUnit.SECONDS), "expected import IO to start")

    importActivityController.destroy()
    allowReadFinish.countDown()

    val importDeadline = System.currentTimeMillis() + 3_000L
    while (System.currentTimeMillis() < importDeadline) {
      Shadows.shadowOf(Looper.getMainLooper()).idle()
      if (AndroidGazeKeepController.backupNoticeFlow.value?.feedback == ConfigBackupFeedback.Imported) {
        break
      }
      Thread.sleep(20)
    }
    val stored = runBlocking { store.settings.first().config }
    assertEquals(12_000L, stored.leadMillis)
    assertEquals(45, stored.maxHoldMinutes)
    assertEquals(ConfigBackupFeedback.Imported, AndroidGazeKeepController.backupNoticeFlow.value?.feedback)

    // 2) 选择器返回导出 URI 后，IO 写盘中途销毁 Activity，导出协程不得被取消且须写完并发布反馈。
    val exportActivityController = Robolectric.buildActivity(ComponentActivity::class.java).setup()
    val exportActivity = exportActivityController.get()
    val exportCoordinator = createAndroidConfigBackupCoordinator(
      activity = exportActivity,
      currentState = { ControllerState(loaded = true, config = stored) },
      store = store,
    )
    val exportUri = Uri.parse("content://test/survive-export.json")
    val writeStarted = CountDownLatch(1)
    val allowWriteFinish = CountDownLatch(1)
    val capturedOutput = ByteArrayOutputStream()
    val gatedOutput = object : OutputStream() {
      override fun write(b: Int) {
        writeStarted.countDown()
        allowWriteFinish.await(5, TimeUnit.SECONDS)
        capturedOutput.write(b)
      }

      override fun write(b: ByteArray, off: Int, len: Int) {
        writeStarted.countDown()
        allowWriteFinish.await(5, TimeUnit.SECONDS)
        capturedOutput.write(b, off, len)
      }
    }
    Shadows.shadowOf(appContext.contentResolver).registerOutputStream(exportUri, gatedOutput)

    exportCoordinator.launchExport()
    val startedExport = requireNotNull(Shadows.shadowOf(exportActivity).nextStartedActivityForResult)
    Shadows.shadowOf(exportActivity).receiveResult(
      startedExport.intent,
      Activity.RESULT_OK,
      Intent().setData(exportUri),
    )
    assertTrue(writeStarted.await(5, TimeUnit.SECONDS), "expected export IO to start")

    exportActivityController.destroy()
    allowWriteFinish.countDown()

    val exportDeadline = System.currentTimeMillis() + 3_000L
    while (System.currentTimeMillis() < exportDeadline) {
      Shadows.shadowOf(Looper.getMainLooper()).idle()
      if (AndroidGazeKeepController.backupNoticeFlow.value?.feedback == ConfigBackupFeedback.Exported) {
        break
      }
      Thread.sleep(20)
    }
    assertEquals(ConfigBackupFeedback.Exported, AndroidGazeKeepController.backupNoticeFlow.value?.feedback)
    assertTrue(capturedOutput.toByteArray().decodeToString().contains("\"leadMillis\": 12000"))
  }
}
