# Unit Test Patterns

## ViewModel Tests

```kotlin
class {Feature}ViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val {item}Repository = Test{Item}Repository()
    private val userDataRepository = TestUserDataRepository()

    private lateinit var viewModel: {Feature}ViewModel

    @Before
    fun setup() {
        viewModel = {Feature}ViewModel(
            {item}Repository = {item}Repository,
            userDataRepository = userDataRepository,
        )
    }

    @Test
    fun `initial state is Loading`() {
        assertEquals({Feature}UiState.Loading, viewModel.uiState.value)
    }

    @Test
    fun `emits Success when data loads`() = runTest {
        {item}Repository.send{Items}(listOf(test{Item}(id = "1")))
        userDataRepository.sendUserData(UserData(favorites = setOf("1")))

        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertIs<{Feature}UiState.Success>(state)
        assertEquals(1, state.items.size)
        assertTrue(state.items[0].isFavorite)
    }

    @Test
    fun `toggle favorite updates user data`() = runTest {
        {item}Repository.send{Items}(listOf(test{Item}(id = "1")))
        userDataRepository.sendUserData(UserData())
        advanceUntilIdle()

        viewModel.onAction({Feature}Action.ToggleFavorite("1"))
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertIs<{Feature}UiState.Success>(state)
        assertTrue(state.items[0].isFavorite)
    }
}
```

### With SavedStateHandle

```kotlin
@Test
fun `loads item by ID from navigation args`() = runTest {
    val viewModel = {Feature}DetailViewModel(
        savedStateHandle = SavedStateHandle(mapOf("{item}Id" to "42")),
        {item}Repository = {item}Repository,
    )

    {item}Repository.send{Items}(listOf(test{Item}(id = "42", title = "Detail")))
    advanceUntilIdle()

    assertIs<{Feature}DetailUiState.Success>(viewModel.uiState.value)
}
```

## Repository Tests

```kotlin
class OfflineFirst{Item}RepositoryTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val {item}Dao = Test{Item}Dao()
    private val network = TestNetworkDataSource()
    private val repository = OfflineFirst{Item}Repository(
        {item}Dao = {item}Dao,
        network = network,
        ioDispatcher = UnconfinedTestDispatcher(),
    )

    @Test
    fun `get items returns mapped domain models`() = runTest {
        {item}Dao.upsert{Items}(listOf(test{Item}Entity(id = "1", title = "Test")))

        val items = repository.get{Items}().first()
        assertEquals(1, items.size)
        assertEquals("Test", items[0].title)
    }

    @Test
    fun `sync fetches from network and upserts to database`() = runTest {
        network.set{Items}(listOf(testNetwork{Item}(id = "1")))

        val result = repository.sync()

        assertTrue(result)
        assertEquals(1, {item}Dao.get{Item}Entities().first().size)
    }
}
```

### Test DAO (In-Memory)

```kotlin
class Test{Item}Dao : {Item}Dao {
    private val entities = MutableStateFlow<List<{Item}Entity>>(emptyList())

    override fun get{Item}Entities(): Flow<List<{Item}Entity>> = entities

    override fun get{Item}Entity(id: String): Flow<{Item}Entity> =
        entities.map { list -> list.first { it.id == id } }

    override suspend fun upsert{Items}(items: List<{Item}Entity>) {
        entities.update { current ->
            val ids = items.map { it.id }.toSet()
            current.filterNot { it.id in ids } + items
        }
    }

    override suspend fun delete{Items}(ids: List<String>) {
        entities.update { current -> current.filterNot { it.id in ids } }
    }
}
```

## Use Case Tests

```kotlin
class Get{Feature}FeedUseCaseTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val {item}Repository = Test{Item}Repository()
    private val userDataRepository = TestUserDataRepository()
    private val useCase = Get{Feature}FeedUseCase({item}Repository, userDataRepository)

    @Test
    fun `combines items with user favorites`() = runTest {
        {item}Repository.send{Items}(listOf(test{Item}(id = "1"), test{Item}(id = "2")))
        userDataRepository.sendUserData(UserData(favorites = setOf("1")))

        val result = useCase().first()

        assertEquals(2, result.size)
        assertTrue(result[0].isFavorite)
        assertFalse(result[1].isFavorite)
    }
}
```

## DataStore Tests

```kotlin
class AppPreferencesDataSourceTest {

    private val testDataStore = InMemoryDataStore(defaultValue = UserPreferences.getDefaultInstance())
    private val dataSource = AppPreferencesDataSource(testDataStore)

    @Test
    fun `set dark theme updates preference`() = runTest {
        dataSource.setDarkThemeConfig(DarkThemeConfig.DARK)

        val userData = dataSource.userData.first()
        assertEquals(DarkThemeConfig.DARK, userData.darkThemeConfig)
    }

    @Test
    fun `toggle favorite adds and removes`() = runTest {
        dataSource.setFavorite("1", true)
        assertTrue("1" in dataSource.userData.first().favorites)

        dataSource.setFavorite("1", false)
        assertFalse("1" in dataSource.userData.first().favorites)
    }
}
```

### InMemoryDataStore

```kotlin
class InMemoryDataStore<T>(private val defaultValue: T) : DataStore<T> {
    private val flow = MutableStateFlow(defaultValue)
    override val data: Flow<T> = flow
    override suspend fun updateData(transform: suspend (t: T) -> T): T {
        val updated = transform(flow.value)
        flow.value = updated
        return updated
    }
}
```

## Test Utilities

### TestNetworkMonitor

```kotlin
class TestNetworkMonitor : NetworkMonitor {
    private val connectivityFlow = MutableStateFlow(true)
    override val isOnline: Flow<Boolean> = connectivityFlow
    fun setConnected(connected: Boolean) { connectivityFlow.value = connected }
}
```

### TestSyncManager

```kotlin
class TestSyncManager : SyncManager {
    private val syncingFlow = MutableStateFlow(false)
    override val isSyncing: Flow<Boolean> = syncingFlow
    override fun requestSync() { syncingFlow.value = true }
    fun setSyncing(syncing: Boolean) { syncingFlow.value = syncing }
}
```

### TestAnalyticsHelper

```kotlin
class TestAnalyticsHelper : AnalyticsHelper {
    val events = mutableListOf<AnalyticsEvent>()
    override fun logEvent(event: AnalyticsEvent) { events.add(event) }
}
```

## Anti-Patterns

| Don't | Do Instead |
|-------|------------|
| Use Mockito/MockK for repositories | Fakes with `MutableSharedFlow(replay=1)` |
| Test private methods | Test via public `uiState` and `onAction` |
| Skip `advanceUntilIdle()` | Coroutines need to complete before assertions |
| Hardcode test data inline | Use `test{Item}()` builder functions |
| Test formatting/rendering in unit tests | Test logic; visual checks go in screenshot tests |
