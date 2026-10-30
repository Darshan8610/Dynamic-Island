---
name: android-offline-sync
description: >-
  Coaches offline-first data sync architecture for Android apps. Use when implementing
  background sync with WorkManager, building offline-first repositories, designing
  change-list delta sync, coordinating sync status across the UI, or handling network
  connectivity changes. Covers Syncable/Synchronizer patterns, CoroutineWorker,
  sync scheduling, conflict resolution, and sync UI indicators.
license: Apache-2.0
compatibility: Requires Android, Kotlin Coroutines, WorkManager, Room
metadata:
  author: luis-suchobits
  version: "1.0"
  based-on: google/nowinandroid
---

# Android Offline Sync — Background Data Synchronization Guide

Patterns for building offline-first Android apps that sync data in the background. The local database is always the source of truth; network data flows into it via sync, and the UI observes only the database.

## Architecture Overview

```
UI ← observes ← Room DB ← sync writes ← Network API
                  ↑ source of truth

WorkManager triggers → SyncWorker → Repositories.sync() → Network + DB
```

## Core Abstractions

### Syncable Interface

Every repository that supports sync implements this:

```kotlin
interface Syncable {
    suspend fun syncWith(synchronizer: Synchronizer): Boolean
}
```

### Synchronizer

Manages sync versions and coordinates the delta sync process:

```kotlin
interface Synchronizer {
    suspend fun getChangeListVersions(): ChangeListVersions
    suspend fun updateChangeListVersions(update: ChangeListVersions.() -> ChangeListVersions)

    suspend fun <T> changeListSync(
        versionReader: ChangeListVersions.() -> Int,
        changeListFetcher: suspend (Int) -> List<NetworkChangeList>,
        versionUpdater: ChangeListVersions.(Int) -> ChangeListVersions,
        modelDeleter: suspend (List<String>) -> Unit,
        modelUpdater: suspend (List<String>) -> Unit,
    ): Boolean
}
```

### ChangeListVersions

Tracks the last-synced version per entity type:

```kotlin
data class ChangeListVersions(
    val {item}Version: Int = -1,
    val categoryVersion: Int = -1,
)
```

Store in Proto DataStore so it persists across app restarts.

## Delta Sync (Change Lists)

Instead of fetching all data every sync, fetch only what changed:

```kotlin
override suspend fun syncWith(synchronizer: Synchronizer): Boolean =
    synchronizer.changeListSync(
        versionReader = ChangeListVersions::{item}Version,
        changeListFetcher = { currentVersion ->
            network.get{Item}ChangeList(after = currentVersion)
        },
        versionUpdater = { latestVersion ->
            copy({item}Version = latestVersion)
        },
        modelDeleter = { changedIds ->
            {item}Dao.delete{Items}(changedIds)
        },
        modelUpdater = { changedIds ->
            val updated = network.get{Items}(ids = changedIds)
            {item}Dao.upsert{Items}(updated.map(Network{Item}::asEntity))
        },
    )
```

### How It Works

1. Read the last-synced version from DataStore.
2. Ask the server: "What changed since version X?"
3. Server returns a list of changed IDs + a new version number.
4. Delete locally-removed items, fetch and upsert changed items.
5. Save the new version number.

### Network Change List Model

```kotlin
@Serializable
data class NetworkChangeList(
    val id: String,
    val changeListVersion: Int,
    val isDelete: Boolean,
)
```

## WorkManager Sync Worker

```kotlin
@HiltWorker
class SyncWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted workerParams: WorkerParameters,
    private val {item}Repository: {Item}Repository,
    private val categoryRepository: CategoryRepository,
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result = coroutineScope {
        val syncResults = awaitAll(
            async { {item}Repository.syncWith(synchronizer) },
            async { categoryRepository.syncWith(synchronizer) },
        )

        if (syncResults.all { it }) Result.success() else Result.retry()
    }

    override suspend fun getForegroundInfo(): ForegroundInfo =
        context.syncForegroundInfo()

    companion object {
        fun startUpSyncWork() = OneTimeWorkRequestBuilder<DelegatingWorker>()
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .build()
    }
}
```

### Key Patterns

- **Parallel sync**: Use `async/awaitAll()` to sync multiple repositories simultaneously.
- **Network constraint**: Only sync when connected.
- **Expedited work**: Prioritize startup sync to get fresh data quickly.
- **Foreground info**: Show a notification for long-running syncs.
- **Retry on failure**: Return `Result.retry()` for transient errors, `Result.failure()` for permanent ones.

## Sync Manager

Expose sync status to the UI:

```kotlin
interface SyncManager {
    val isSyncing: Flow<Boolean>
    fun requestSync()
}

class WorkManagerSyncManager @Inject constructor(
    @ApplicationContext private val context: Context,
) : SyncManager {

    override val isSyncing: Flow<Boolean> =
        WorkManager.getInstance(context)
            .getWorkInfosForUniqueWorkFlow(SYNC_WORK_NAME)
            .map { workInfos -> workInfos.any { it.state == WorkInfo.State.RUNNING } }
            .conflate()

    override fun requestSync() {
        WorkManager.getInstance(context).enqueueUniqueWork(
            SYNC_WORK_NAME,
            ExistingWorkPolicy.KEEP,
            SyncWorker.startUpSyncWork(),
        )
    }
}
```

## Network Monitor

Detect connectivity changes to show offline banners and gate sync:

```kotlin
interface NetworkMonitor {
    val isOnline: Flow<Boolean>
}

class ConnectivityNetworkMonitor @Inject constructor(
    @ApplicationContext private val context: Context,
) : NetworkMonitor {

    override val isOnline: Flow<Boolean> = callbackFlow {
        val manager = context.getSystemService<ConnectivityManager>()!!
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) { trySend(true) }
            override fun onLost(network: Network) { trySend(false) }
        }
        manager.registerDefaultNetworkCallback(callback)
        trySend(manager.activeNetwork != null)
        awaitClose { manager.unregisterNetworkCallback(callback) }
    }.distinctUntilChanged()
}
```

## Initialization

Trigger sync on app startup:

```kotlin
class AppApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        Sync.initialize(this)
    }
}

object Sync {
    fun initialize(context: Context) {
        WorkManager.getInstance(context).enqueueUniqueWork(
            SYNC_WORK_NAME,
            ExistingWorkPolicy.KEEP,
            SyncWorker.startUpSyncWork(),
        )
    }
}
```

## UI Integration

### Pull-to-Refresh

```kotlin
@Composable
fun {Feature}Route(viewModel: {Feature}ViewModel = hiltViewModel()) {
    val isSyncing by viewModel.isSyncing.collectAsStateWithLifecycle()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    PullToRefreshBox(
        isRefreshing = isSyncing,
        onRefresh = viewModel::requestSync,
    ) {
        {Feature}Screen(uiState = uiState, onAction = viewModel::onAction)
    }
}
```

### Offline Banner

```kotlin
@Composable
fun OfflineBanner(isOnline: Boolean, modifier: Modifier = Modifier) {
    AnimatedVisibility(visible = !isOnline) {
        Surface(color = MaterialTheme.colorScheme.errorContainer, modifier = modifier.fillMaxWidth()) {
            Text("You are offline", modifier = Modifier.padding(8.dp), textAlign = TextAlign.Center)
        }
    }
}
```

For detailed sync patterns, see the [sync strategies reference](references/sync-strategies.md).
