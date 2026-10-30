# Testing Patterns

## Test Infrastructure

### MainDispatcherRule

Required for any test that touches `viewModelScope` or `Dispatchers.Main`:

```kotlin
// core/testing/MainDispatcherRule.kt

class MainDispatcherRule(
    private val testDispatcher: TestDispatcher = UnconfinedTestDispatcher(),
) : TestWatcher() {

    override fun starting(description: Description) {
        Dispatchers.setMain(testDispatcher)
    }

    override fun finished(description: Description) {
        Dispatchers.resetMain()
    }
}
```

Apply in every ViewModel test:

```kotlin
@get:Rule
val mainDispatcherRule = MainDispatcherRule()
```

## Fake Repositories

Create fakes in `core:testing` using `MutableSharedFlow(replay=1)` for controllable emission:

```kotlin
// core/testing/Test{Item}Repository.kt

class Test{Item}Repository : {Item}Repository {

    private val {items}Flow = MutableSharedFlow<List<{Item}>>(replay = 1)

    override fun get{Items}(): Flow<List<{Item}>> = {items}Flow

    override fun get{Item}(id: String): Flow<{Item}> =
        {items}Flow.map { items -> items.first { it.id == id } }

    override suspend fun sync(): Boolean = true

    // Test helper — call this to push data into the flow
    fun send{Items}(items: List<{Item}>) {
        {items}Flow.tryEmit(items)
    }
}
```

```kotlin
class TestUserDataRepository : UserDataRepository {

    private val userDataFlow = MutableSharedFlow<UserData>(replay = 1)

    override val userData: Flow<UserData> = userDataFlow

    override suspend fun setFavorite(id: String, isFavorite: Boolean) {
        val current = userDataFlow.replayCache.firstOrNull() ?: UserData()
        val updated = if (isFavorite) {
            current.copy(favorites = current.favorites + id)
        } else {
            current.copy(favorites = current.favorites - id)
        }
        userDataFlow.tryEmit(updated)
    }

    fun sendUserData(userData: UserData) {
        userDataFlow.tryEmit(userData)
    }
}
```

### Why Fakes over Mocks

- Fakes implement the real interface — compiler catches breaking changes.
- `MutableSharedFlow(replay=1)` gives precise control over when data arrives.
- No mock framework dependency (Mockito/MockK) — faster, simpler, more reliable.
- Fakes are reused across all tests for that repository.

## ViewModel Tests

### Structure

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
        val testItems = listOf(
            test{Item}(id = "1", title = "First"),
            test{Item}(id = "2", title = "Second"),
        )
        {item}Repository.send{Items}(testItems)
        userDataRepository.sendUserData(UserData(favorites = setOf("1")))

        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertIs<{Feature}UiState.Success>(state)
        assertEquals(2, state.items.size)
        assertTrue(state.items[0].isFavorite)
        assertFalse(state.items[1].isFavorite)
    }

    @Test
    fun `toggle favorite updates state`() = runTest {
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

### Test Data Builders

Create helper functions for test data:

```kotlin
// core/testing/TestData.kt

fun test{Item}(
    id: String = "test-id",
    title: String = "Test Title",
    description: String = "Test description",
    categoryId: String = "cat-1",
) = {Item}(
    id = id,
    title = title,
    description = description,
    imageUrl = "https://example.com/$id.jpg",
    category = testCategory(id = categoryId),
    createdAt = Clock.System.now(),
    tags = emptyList(),
)

fun testCategory(
    id: String = "cat-1",
    name: String = "Test Category",
) = Category(id = id, name = name)
```

### ViewModel with SavedStateHandle

```kotlin
@Test
fun `loads item by ID from SavedStateHandle`() = runTest {
    val viewModel = {Feature}DetailViewModel(
        savedStateHandle = SavedStateHandle(mapOf("{item}Id" to "42")),
        {item}Repository = {item}Repository,
    )

    {item}Repository.send{Items}(listOf(test{Item}(id = "42", title = "Detail Item")))
    advanceUntilIdle()

    val state = viewModel.uiState.value
    assertIs<{Feature}DetailUiState.Success>(state)
    assertEquals("Detail Item", state.item.title)
}
```

## Room Database Tests

Use in-memory databases in Android instrumentation tests:

```kotlin
@RunWith(AndroidJUnit4::class)
class {Item}DaoTest {

    private lateinit var db: AppDatabase
    private lateinit var dao: {Item}Dao

    @Before
    fun setup() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java,
        ).build()
        dao = db.{item}Dao()
    }

    @After
    fun teardown() {
        db.close()
    }

    @Test
    fun insertAndRead() = runTest {
        val entities = listOf(
            test{Item}Entity(id = "1", title = "First"),
            test{Item}Entity(id = "2", title = "Second"),
        )
        dao.upsert{Items}(entities)

        val result = dao.get{Item}Entities().first()
        assertEquals(2, result.size)
        assertEquals("First", result[0].title)
    }

    @Test
    fun upsertUpdatesExisting() = runTest {
        dao.upsert{Items}(listOf(test{Item}Entity(id = "1", title = "Original")))
        dao.upsert{Items}(listOf(test{Item}Entity(id = "1", title = "Updated")))

        val result = dao.get{Item}Entities().first()
        assertEquals(1, result.size)
        assertEquals("Updated", result[0].title)
    }

    @Test
    fun deleteRemovesItems() = runTest {
        dao.upsert{Items}(listOf(
            test{Item}Entity(id = "1"),
            test{Item}Entity(id = "2"),
        ))
        dao.delete{Items}(listOf("1"))

        val result = dao.get{Item}Entities().first()
        assertEquals(1, result.size)
        assertEquals("2", result[0].id)
    }
}
```

## UI / Screenshot Tests

### Compose UI Tests

```kotlin
class {Feature}ScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `shows loading indicator when state is Loading`() {
        composeTestRule.setContent {
            AppTheme {
                {Feature}Screen(
                    uiState = {Feature}UiState.Loading,
                    onAction = {},
                )
            }
        }

        composeTestRule.onNodeWithTag("loading_indicator").assertIsDisplayed()
    }

    @Test
    fun `shows items when state is Success`() {
        composeTestRule.setContent {
            AppTheme {
                {Feature}Screen(
                    uiState = {Feature}UiState.Success(
                        items = listOf(test{Item}(title = "Test Item")),
                    ),
                    onAction = {},
                )
            }
        }

        composeTestRule.onNodeWithText("Test Item").assertIsDisplayed()
    }

    @Test
    fun `calls action on item click`() {
        var clickedId: String? = null
        composeTestRule.setContent {
            AppTheme {
                {Feature}Screen(
                    uiState = {Feature}UiState.Success(
                        items = listOf(test{Item}(id = "1", title = "Clickable")),
                    ),
                    onAction = { action ->
                        if (action is {Feature}Action.ItemClicked) clickedId = action.id
                    },
                )
            }
        }

        composeTestRule.onNodeWithText("Clickable").performClick()
        assertEquals("1", clickedId)
    }
}
```

### Screenshot Tests (Roborazzi)

```kotlin
class {Feature}ScreenScreenshotTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun {feature}Screen_loading() {
        composeTestRule.setContent {
            AppTheme {
                {Feature}Screen(
                    uiState = {Feature}UiState.Loading,
                    onAction = {},
                )
            }
        }
        composeTestRule.onRoot().captureRoboImage()
    }

    @Test
    fun {feature}Screen_success() {
        composeTestRule.setContent {
            AppTheme {
                {Feature}Screen(
                    uiState = {Feature}UiState.Success(items = previewItems()),
                    onAction = {},
                )
            }
        }
        composeTestRule.onRoot().captureRoboImage()
    }
}
```

## Test Utilities

### TestNetworkMonitor

```kotlin
class TestNetworkMonitor : NetworkMonitor {
    private val connectivityFlow = MutableStateFlow(true)
    override val isOnline: Flow<Boolean> = connectivityFlow

    fun setConnected(isConnected: Boolean) {
        connectivityFlow.value = isConnected
    }
}
```

### TestSyncManager

```kotlin
class TestSyncManager : SyncManager {
    private val syncingFlow = MutableStateFlow(false)
    override val isSyncing: Flow<Boolean> = syncingFlow

    override fun requestSync() {
        syncingFlow.value = true
    }

    fun setSyncing(syncing: Boolean) {
        syncingFlow.value = syncing
    }
}
```

## Test File Locations

```
feature/{feature}/impl/src/test/kotlin/...
├── {Feature}ViewModelTest.kt           # Unit tests
└── {Feature}ScreenTest.kt              # Compose UI tests

core/database/src/androidTest/kotlin/...
└── {Item}DaoTest.kt                    # Instrumentation tests

core/testing/src/main/kotlin/...
├── Test{Item}Repository.kt             # Fakes
├── TestUserDataRepository.kt
├── TestNetworkMonitor.kt
├── TestSyncManager.kt
├── MainDispatcherRule.kt
└── TestData.kt                         # Builders
```

## Anti-Patterns

| Don't | Do Instead |
|-------|------------|
| Use Mockito/MockK for repositories | Use fakes with `MutableSharedFlow(replay=1)` |
| Test private ViewModel methods | Test via public `uiState` and `onAction` |
| Hardcode test data inline everywhere | Create reusable `test{Item}()` builder functions |
| Skip `advanceUntilIdle()` in flow tests | Always call it — coroutines need to complete |
| Test UI logic in instrumentation tests | Test in unit tests; reserve instrumentation for DAO and integration |
| Assert on implementation details | Assert on observable behavior (UI state, emitted values) |
