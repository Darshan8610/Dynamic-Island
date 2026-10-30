# Dependency Injection with Hilt

## Module Organization

Group bindings by the core module they belong to:

```
core/data/di/DataModule.kt        # Repository bindings
core/network/di/NetworkModule.kt  # OkHttp, Retrofit, network data source
core/database/di/DatabaseModule.kt # Room database, DAOs
core/datastore/di/DataStoreModule.kt # Proto DataStore
core/common/di/DispatchersModule.kt  # Coroutine dispatchers
```

## Binding Interfaces with @Binds

Use `@Binds` in an abstract class when binding an interface to its implementation:

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

    @Binds
    abstract fun bindsNetworkMonitor(
        impl: ConnectivityNetworkMonitor,
    ): NetworkMonitor
}
```

## Providing Complex Objects with @Provides

Use `@Provides` in an `object` when construction requires logic:

```kotlin
@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun providesAppDatabase(
        @ApplicationContext context: Context,
    ): AppDatabase = Room.databaseBuilder(
        context,
        AppDatabase::class.java,
        "app-database",
    ).build()
}
```

```kotlin
@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun providesJson(): Json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    @Provides
    @Singleton
    fun providesOkHttpClient(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    @Provides
    @Singleton
    fun providesRetrofit(
        json: Json,
        okHttpClient: OkHttpClient,
    ): Retrofit = Retrofit.Builder()
        .baseUrl(BuildConfig.API_BASE_URL)
        .client(okHttpClient)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()

    @Provides
    @Singleton
    fun providesAppApi(retrofit: Retrofit): AppApi =
        retrofit.create(AppApi::class.java)
}
```

## Dispatcher Qualifiers

Define custom qualifiers to inject specific coroutine dispatchers:

```kotlin
// core/common/Dispatchers.kt

@Qualifier
@Retention(AnnotationRetention.RUNTIME)
annotation class Dispatcher(val appDispatcher: AppDispatchers)

enum class AppDispatchers { Default, IO }
```

```kotlin
// core/common/di/DispatchersModule.kt

@Module
@InstallIn(SingletonComponent::class)
object DispatchersModule {

    @Provides
    @Dispatcher(AppDispatchers.IO)
    fun providesIODispatcher(): CoroutineDispatcher = Dispatchers.IO

    @Provides
    @Dispatcher(AppDispatchers.Default)
    fun providesDefaultDispatcher(): CoroutineDispatcher = Dispatchers.Default
}
```

Usage in a repository:

```kotlin
class OfflineFirst{Item}Repository @Inject constructor(
    private val dao: {Item}Dao,
    private val network: AppNetworkDataSource,
    @Dispatcher(AppDispatchers.IO) private val ioDispatcher: CoroutineDispatcher,
) : {Item}Repository
```

This makes dispatchers swappable in tests without `MainDispatcherRule` hacks.

## Scope Annotations

| Scope | Lifetime | Use For |
|-------|----------|---------|
| `@Singleton` | App process | Database, Retrofit, DataStore, repositories |
| `@ActivityRetainedScoped` | Survives config changes | Shared ViewModel dependencies (rare) |
| `@ViewModelScoped` | ViewModel lifecycle | ViewModel-specific coordinators (rare) |
| No annotation | Created each time | Use cases, mappers, stateless helpers |

Default to no scope. Only add `@Singleton` when the object is expensive to create or must be shared.

## ViewModel Injection

ViewModels use `@HiltViewModel` with constructor injection — no manual factory needed:

```kotlin
@HiltViewModel
class {Feature}ViewModel @Inject constructor(
    private val {item}Repository: {Item}Repository,
    private val userDataRepository: UserDataRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel()
```

Hilt auto-provides `SavedStateHandle` and `Application` if declared. No `@Module` entry needed for ViewModels.

## Feature Module DI

Feature `impl` modules apply the Hilt convention plugin, which adds:
- `com.google.dagger.hilt.android` plugin
- `com.google.devtools.ksp` plugin
- `hilt-android` and `hilt-compiler` dependencies

No manual setup needed in feature module `build.gradle.kts` — the convention plugin handles it.

## WorkManager Integration

Use `@HiltWorker` with `@AssistedInject` for background workers:

```kotlin
@HiltWorker
class SyncWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted workerParams: WorkerParameters,
    private val {item}Repository: {Item}Repository,
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        val success = {item}Repository.sync()
        return if (success) Result.success() else Result.retry()
    }
}
```

## Build Flavor Bindings

Swap implementations per build flavor using flavor-specific modules:

```kotlin
// core/network/di/FlavoredNetworkModule.kt (in demo source set)
@Module
@InstallIn(SingletonComponent::class)
abstract class FlavoredNetworkModule {
    @Binds
    abstract fun bindsNetworkDataSource(
        impl: DemoAppNetworkDataSource,
    ): AppNetworkDataSource
}

// core/network/di/FlavoredNetworkModule.kt (in prod source set)
@Module
@InstallIn(SingletonComponent::class)
abstract class FlavoredNetworkModule {
    @Binds
    abstract fun bindsNetworkDataSource(
        impl: RetrofitAppNetwork,
    ): AppNetworkDataSource
}
```

## Lazy Injection

Use `dagger.Lazy<T>` for optional or expensive services that may not be needed:

```kotlin
class SomeManager @Inject constructor(
    private val analytics: dagger.Lazy<AnalyticsHelper>,
) {
    fun trackIfEnabled() {
        if (isTrackingEnabled) {
            analytics.get().logEvent(/* ... */)
        }
    }
}
```

## Anti-Patterns

| Don't | Do Instead |
|-------|------------|
| Use `@Provides` for simple interface bindings | Use `@Binds` — it's more efficient (no method body compiled) |
| Scope everything as `@Singleton` | Default to unscoped; scope only when sharing or cost justify it |
| Inject `Context` directly | Use `@ApplicationContext` or `@ActivityContext` qualifiers |
| Create manual ViewModel factories | Use `@HiltViewModel` |
| Use `@Inject` on the interface | Use `@Inject` on the implementation constructor |
| Put multiple unrelated bindings in one module | Group by core module / domain concern |
