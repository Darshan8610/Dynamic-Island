# Sync Strategies Reference

## Full Sync vs. Delta Sync

| Strategy | When to Use | Trade-off |
|----------|-------------|-----------|
| **Full sync** | Small datasets (<100 items), first-time sync | Simple but wasteful for large datasets |
| **Delta sync (change list)** | Large datasets, frequent updates | Efficient but requires server support |
| **Timestamp-based** | Server tracks `updatedAt` per record | Simpler than change lists but can miss deletes |

## Full Sync Implementation

```kotlin
override suspend fun sync(): Boolean {
    return try {
        val remote{Items} = network.get{Items}()
        {item}Dao.deleteAll()
        {item}Dao.upsert{Items}(remote{Items}.map(Network{Item}::asEntity))
        true
    } catch (e: Exception) {
        false
    }
}
```

Use for small reference data (categories, settings).

## Batch Sync

For large change lists, process in batches to avoid memory pressure:

```kotlin
private const val SYNC_BATCH_SIZE = 40

suspend fun <T> changeListSync(
    changeListFetcher: suspend (Int) -> List<NetworkChangeList>,
    modelUpdater: suspend (List<String>) -> Unit,
) {
    val changeList = changeListFetcher(currentVersion)

    val (deletions, updates) = changeList.partition { it.isDelete }
    modelDeleter(deletions.map { it.id })

    updates.map { it.id }
        .chunked(SYNC_BATCH_SIZE)
        .forEach { batch ->
            modelUpdater(batch)
        }
}
```

## First-Sync Handling

On first app launch, all data is new. Avoid overwhelming the user:

```kotlin
override suspend fun syncWith(synchronizer: Synchronizer): Boolean {
    val isFirstSync = synchronizer.getChangeListVersions().{item}Version == -1

    val success = synchronizer.changeListSync(/* ... */)

    if (isFirstSync && success) {
        // Mark all items as "seen" to avoid showing everything as new
        userDataRepository.markAllViewed()
    }

    return success
}
```

## Conflict Resolution

When the user edits data offline and the server has a different version:

### Last-Write-Wins (Simplest)

```kotlin
// Server version always wins during sync
modelUpdater = { changedIds ->
    val serverItems = network.get{Items}(ids = changedIds)
    {item}Dao.upsert{Items}(serverItems.map(Network{Item}::asEntity))
}
```

### Client-Wins for Pending Changes

```kotlin
modelUpdater = { changedIds ->
    val serverItems = network.get{Items}(ids = changedIds)
    val pendingLocalEdits = {item}Dao.getPendingEdits().map { it.id }.toSet()

    val itemsToUpsert = serverItems
        .filter { it.id !in pendingLocalEdits }  // Don't overwrite pending edits
        .map(Network{Item}::asEntity)

    {item}Dao.upsert{Items}(itemsToUpsert)
}
```

### Optimistic Writes with Queue

```kotlin
// 1. Write locally immediately
{item}Dao.upsert{Item}(localEdit.asEntity().copy(pendingSync = true))

// 2. Queue the write for sync
syncQueue.enqueue(PendingWrite(id = localEdit.id, data = localEdit))

// 3. During sync, push pending writes first
override suspend fun syncWith(synchronizer: Synchronizer): Boolean {
    pushPendingWrites()
    return synchronizer.changeListSync(/* ... */)
}

private suspend fun pushPendingWrites() {
    val pending = syncQueue.getAll()
    pending.forEach { write ->
        try {
            network.update{Item}(write.id, write.data)
            syncQueue.remove(write.id)
            {item}Dao.clearPendingFlag(write.id)
        } catch (e: Exception) {
            // Will retry next sync
        }
    }
}
```

## Periodic Sync

For data that should stay fresh even when the user isn't active:

```kotlin
fun schedulePeriodic Sync(context: Context) {
    val periodicWork = PeriodicWorkRequestBuilder<SyncWorker>(
        repeatInterval = 1, repeatIntervalTimeUnit = TimeUnit.HOURS,
    )
        .setConstraints(
            Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .setRequiresBatteryNotLow(true)
                .build()
        )
        .build()

    WorkManager.getInstance(context).enqueueUniquePeriodicWork(
        "periodic_sync",
        ExistingPeriodicWorkPolicy.KEEP,
        periodicWork,
    )
}
```

## Sync Notifications

Show progress for long-running syncs:

```kotlin
fun Context.syncForegroundInfo(): ForegroundInfo {
    val notification = NotificationCompat.Builder(this, SYNC_CHANNEL_ID)
        .setSmallIcon(R.drawable.ic_sync)
        .setContentTitle("Syncing data...")
        .setOngoing(true)
        .build()

    return ForegroundInfo(SYNC_NOTIFICATION_ID, notification)
}
```

## Testing Sync

```kotlin
class Test{Item}Repository : {Item}Repository, Syncable {
    private val {items}Flow = MutableSharedFlow<List<{Item}>>(replay = 1)
    private var syncResult = true

    override fun get{Items}(): Flow<List<{Item}>> = {items}Flow

    override suspend fun syncWith(synchronizer: Synchronizer): Boolean = syncResult

    fun setSyncResult(result: Boolean) { syncResult = result }
    fun send{Items}(items: List<{Item}>) { {items}Flow.tryEmit(items) }
}

class TestSyncManager : SyncManager {
    private val syncingFlow = MutableStateFlow(false)
    override val isSyncing: Flow<Boolean> = syncingFlow
    override fun requestSync() { syncingFlow.value = true }
    fun setSyncing(syncing: Boolean) { syncingFlow.value = syncing }
}
```

## Anti-Patterns

| Don't | Do Instead |
|-------|------------|
| Sync on the main thread | Use WorkManager with `CoroutineWorker` |
| Fetch all data every sync | Use delta sync with change lists |
| Ignore sync failures silently | Return `Result.retry()` for transient errors |
| Sync without network constraint | Set `NetworkType.CONNECTED` constraint |
| Block UI on sync completion | UI observes database; sync writes to database in background |
| Store sync versions in SharedPreferences | Use Proto DataStore — it's type-safe and corruption-resistant |
