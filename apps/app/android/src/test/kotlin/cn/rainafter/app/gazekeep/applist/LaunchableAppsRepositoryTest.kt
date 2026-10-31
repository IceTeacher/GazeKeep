package cn.rainafter.app.gazekeep.applist

import android.content.pm.ApplicationInfo
import cn.rainafter.app.gazekeep.ui.contract.AppEntry
import cn.rainafter.app.gazekeep.ui.contract.AppListLoadState
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LaunchableAppsRepositoryTest {
  @Test fun loadedEmptyResultIsCached() = runBlocking {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    var queries = 0
    val repository = LaunchableAppsRepository(scope, { _, _ -> }, {
      queries++
      emptyList()
    })
    try {
      repository.load()
      repository.load()
      assertEquals(1, queries)
    } finally {
      repository.close()
      scope.cancel()
    }
  }

  @Test fun failedQueryCanBeRetried() = runBlocking {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    val states = mutableListOf<AppListLoadState>()
    var queries = 0
    val repository = LaunchableAppsRepository(scope, { state, _ -> states.add(state) }, {
      if (queries++ == 0) throw IllegalStateException("query failed")
      emptyList()
    })
    try {
      repository.load()
      repository.load()
      assertEquals(
        listOf(AppListLoadState.Loading, AppListLoadState.Failed, AppListLoadState.Loading, AppListLoadState.Loaded),
        states,
      )
      assertEquals(2, queries)
    } finally {
      repository.close()
      scope.cancel()
    }
  }

  @Test fun concurrentLoadsShareQueryAndCloseCancelsIt() = runBlocking {
    val parent = SupervisorJob()
    val scope = CoroutineScope(parent + Dispatchers.Unconfined)
    val query = CompletableDeferred<List<AppEntry>>()
    val cancelled = CompletableDeferred<Unit>()
    var queries = 0
    val states = mutableListOf<AppListLoadState>()
    val repository = LaunchableAppsRepository(scope, { state, _ -> states.add(state) }, {
      queries++
      try {
        query.await()
      } finally {
        cancelled.complete(Unit)
      }
    })
    try {
      val first = async(start = CoroutineStart.UNDISPATCHED) { repository.load() }
      val second = async(start = CoroutineStart.UNDISPATCHED) { repository.load() }
      assertEquals(1, queries)
      repository.close()
      withTimeout(2_000L) {
        cancelled.await()
        first.await()
        second.await()
      }
      assertEquals(listOf(AppListLoadState.Loading), states)
      assertTrue(parent.isActive)
    } finally {
      repository.close()
      scope.cancel()
    }
  }

  @Test fun closeCancelsIconPreload() = runBlocking {
    val parent = SupervisorJob()
    val scope = CoroutineScope(parent + Dispatchers.Unconfined)
    val started = CompletableDeferred<Unit>()
    val stopped = CompletableDeferred<Unit>()
    val repository = LaunchableAppsRepository(scope, { _, _ -> }, {
      listOf(AppEntry("example.reader", "阅读"))
    }, {
      started.complete(Unit)
      try {
        awaitCancellation()
      } finally {
        stopped.complete(Unit)
      }
    })
    try {
      repository.load()
      withTimeout(2_000L) { started.await() }
      repository.close()
      withTimeout(2_000L) { stopped.await() }
      assertTrue(parent.isActive)
    } finally {
      repository.close()
      scope.cancel()
    }
  }

  @Test fun onlyNeverUpdatedPreinstalledAppsCountAsSystem() {
    assertFalse(isSystemApp(0))
    assertTrue(isSystemApp(ApplicationInfo.FLAG_SYSTEM))
    // 商店更新过的预装应用（如 Chrome）按用户应用显示。
    assertFalse(isSystemApp(ApplicationInfo.FLAG_SYSTEM or ApplicationInfo.FLAG_UPDATED_SYSTEM_APP))
  }
}
