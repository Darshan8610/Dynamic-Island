# Repository Patterns

## Repository Interface

Define repository contracts in `core:data` as interfaces returning `Flow<T>`:

```kotlin
// core/data/{Item}Repository.kt

interface {Item}Repository {
    fun get{Items}(): Flow<List<{Item}>>
    fun get{Item}(id: String): Flow<{Item}>
    fun get{Items}ByCategory(categoryId: String): Flow<List<{Item}>>
    suspend fun sync(): Boolean
}
```

Rules:
- Read operations return `Flow<T>` for reactive observation.
- Write/sync operations are `suspend` functions.
- Never expose database entities or network models — only domain models from `core:model`.

## Offline-First Repository

The local database is the single source of truth. Network data syncs into the database; the UI observes the database.

```kotlin
// core/data/OfflineFirst{Item}Repository.kt

class OfflineFirst{Item}Repository @Inject constructor(
    private val {item}Dao: {Item}Dao,
    private val network: AppNetworkDataSource,
    @Dispatcher(IO) private val ioDispatcher: CoroutineDispatcher,
) : {Item}Repository {

    override fun get{Items}(): Flow<List<{Item}>> =
        {item}Dao.get{Item}Entities()
            .map { entities -> entities.map({Item}Entity::asExternalModel) }

    override fun get{Item}(id: String): Flow<{Item}> =
        {item}Dao.get{Item}Entity(id)
            .map { it.asExternalModel() }

    override suspend fun sync(): Boolean = withContext(ioDispatcher) {
        try {
            val network{Items} = network.get{Items}()
            {item}Dao.upsert{Items}(network{Items}.map(Network{Item}::asEntity))
            true
        } catch (e: Exception) {
            false
        }
    }
}
```

### Data Flow

```
Network API  →  sync()  →  Room Database  →  Flow<Entity>  →  map to Domain Model  →  UI
                               ↑ source of truth
```

## Change-List Sync (Delta Sync)

For large datasets, sync only what changed since the last sync:

```kotlin
override suspend fun sync(): Boolean =
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
            val updated{Items} = network.get{Items}(ids = changedIds)
            {item}Dao.upsert{Items}(updated{Items}.map(Network{Item}::asEntity))
        },
    )
```

Components:
- **ChangeListVersions**: Tracks the last-synced version per entity type (stored in DataStore).
- **changeListFetcher**: Asks the server "what changed since version X?"
- **modelDeleter/modelUpdater**: Applies the delta to the local database.

## Network-Only Repository

When offline support isn't needed:

```kotlin
class Network{Item}Repository @Inject constructor(
    private val network: AppNetworkDataSource,
    @Dispatcher(IO) private val ioDispatcher: CoroutineDispatcher,
) : {Item}Repository {

    override fun get{Items}(): Flow<List<{Item}>> = flow {
        val items = network.get{Items}().map(Network{Item}::asExternalModel)
        emit(items)
    }.flowOn(ioDispatcher)
}
```

Use this sparingly — prefer offline-first for better UX.

## User Data Repository (Preferences)

For user settings and preferences, back the repository with Proto DataStore:

```kotlin
class OfflineFirstUserDataRepository @Inject constructor(
    private val preferencesDataSource: AppPreferencesDataSource,
) : UserDataRepository {

    override val userData: Flow<UserData> =
        preferencesDataSource.userData

    override suspend fun setFavorite(id: String, isFavorite: Boolean) {
        preferencesDataSource.toggleFavorite(id, isFavorite)
    }

    override suspend fun setDarkTheme(config: DarkThemeConfig) {
        preferencesDataSource.setDarkThemeConfig(config)
    }
}
```

## Hilt Bindings

Bind all repositories in a single `DataModule`:

```kotlin
@Module
@InstallIn(SingletonComponent::class)
abstract class DataModule {

    @Binds
    abstract fun binds{Item}Repository(
        impl: OfflineFirst{Item}Repository,
    ): {Item}Repository

    @Binds
    abstract fun bindsUserDataRepository(
        impl: OfflineFirstUserDataRepository,
    ): UserDataRepository
}
```

## Cross-Cutting Concerns

### Network Monitor

Expose connectivity state as a Flow for the UI to show offline banners:

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

### Sync Manager

Coordinate background sync status for pull-to-refresh and loading indicators:

```kotlin
interface SyncManager {
    val isSyncing: Flow<Boolean>
    fun requestSync()
}
```

## Anti-Patterns

| Don't | Do Instead |
|-------|------------|
| Return network models from repositories | Map to domain models via `asExternalModel()` |
| Call network directly from ViewModels | Go through a repository |
| Use `suspend fun get()` for observable data | Use `Flow<T>` so UI updates reactively |
| Put business logic in repositories | Keep repositories as data coordinators; put logic in use cases |
| Create one mega-repository | One repository per domain concept ({Item}Repository, UserDataRepository) |
