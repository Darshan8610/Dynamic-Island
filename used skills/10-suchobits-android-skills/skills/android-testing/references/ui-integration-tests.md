# UI & Integration Tests

## Compose UI Tests

These run on a device/emulator via `src/androidTest/`.

### Basic Screen Test

```kotlin
class {Feature}ScreenTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun showsLoadingIndicator_whenLoading() {
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
    fun showsItems_whenSuccess() {
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
    fun callsAction_onItemClick() {
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

    @Test
    fun showsErrorMessage_whenError() {
        composeTestRule.setContent {
            AppTheme {
                {Feature}Screen(
                    uiState = {Feature}UiState.Error("Network failed"),
                    onAction = {},
                )
            }
        }
        composeTestRule.onNodeWithText("Network failed").assertIsDisplayed()
    }
}
```

### Common Finders & Assertions

```kotlin
// Finders
composeTestRule.onNodeWithText("label")
composeTestRule.onNodeWithTag("test_tag")
composeTestRule.onNodeWithContentDescription("description")
composeTestRule.onAllNodesWithTag("item_card")

// Assertions
.assertIsDisplayed()
.assertExists()
.assertDoesNotExist()
.assertIsSelected()
.assertIsNotSelected()
.assertHasClickAction()

// Actions
.performClick()
.performTextInput("query")
.performScrollToNode(hasText("target"))
.performTouchInput { swipeUp() }
```

### String Resource Helper

```kotlin
fun AndroidComposeTestRule<*, *>.stringResource(@StringRes id: Int): String =
    activity.getString(id)

// Usage
composeTestRule.onNodeWithText(composeTestRule.stringResource(R.string.title)).assertExists()
```

## Room DAO Tests

Run in `src/androidTest/` with an in-memory database:

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
    fun teardown() = db.close()

    @Test
    fun insertAndRead() = runTest {
        dao.upsert{Items}(listOf(
            test{Item}Entity(id = "1", title = "First"),
            test{Item}Entity(id = "2", title = "Second"),
        ))

        val result = dao.get{Item}Entities().first()
        assertEquals(2, result.size)
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
    fun deleteRemovesCorrectItems() = runTest {
        dao.upsert{Items}(listOf(
            test{Item}Entity(id = "1"),
            test{Item}Entity(id = "2"),
        ))
        dao.delete{Items}(listOf("1"))

        val result = dao.get{Item}Entities().first()
        assertEquals(1, result.size)
        assertEquals("2", result[0].id)
    }

    @Test
    fun queryReturnsFlowUpdates() = runTest {
        val flow = dao.get{Item}Entities()

        // Initial empty
        assertEquals(0, flow.first().size)

        // Insert triggers new emission
        dao.upsert{Items}(listOf(test{Item}Entity(id = "1")))
        assertEquals(1, flow.first().size)
    }
}
```

## WorkManager Tests

```kotlin
@RunWith(AndroidJUnit4::class)
class SyncWorkerTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Before
    fun setup() {
        val config = Configuration.Builder()
            .setMinimumLoggingLevel(Log.DEBUG)
            .setExecutor(SynchronousExecutor())
            .build()
        WorkManagerTestInitHelper.initializeTestWorkManager(context, config)
    }

    @Test
    fun syncWorker_succeeds() {
        val request = SyncWorker.startUpSyncWork()
        val workManager = WorkManager.getInstance(context)

        workManager.enqueue(request).result.get()

        val workInfo = workManager.getWorkInfoById(request.id).get()
        assertEquals(WorkInfo.State.SUCCEEDED, workInfo.state)
    }
}
```

## Navigation Tests

Full app navigation tests with Hilt:

```kotlin
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class NavigationTest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun firstScreen_isHome() {
        composeTestRule.onNodeWithTag("home_screen").assertIsDisplayed()
    }

    @Test
    fun navigateToSearch_showsSearchScreen() {
        composeTestRule.onNodeWithContentDescription("Search").performClick()
        composeTestRule.onNodeWithTag("search_screen").assertIsDisplayed()
    }

    @Test
    fun backFromDetail_returnsToList() {
        // Navigate to detail
        composeTestRule.onNodeWithText("Item Title").performClick()
        composeTestRule.onNodeWithTag("detail_screen").assertIsDisplayed()

        // Press back
        Espresso.pressBack()
        composeTestRule.onNodeWithTag("home_screen").assertIsDisplayed()
    }
}
```

### Hilt Test Runner

```kotlin
class AppTestRunner : AndroidJUnitRunner() {
    override fun newApplication(cl: ClassLoader, name: String, context: Context): Application =
        super.newApplication(cl, HiltTestApplication::class.java.name, context)
}
```

Register in `build.gradle.kts`:

```kotlin
android {
    defaultConfig {
        testInstrumentationRunner = "com.example.testing.AppTestRunner"
    }
}
```

## Test Method Naming

### Unit Tests (src/test)

Use backtick-quoted descriptive names:

```kotlin
@Test
fun `emits Success when data loads`()

@Test
fun `toggle favorite updates state`()

@Test
fun `initial state is Loading`()
```

### Instrumentation Tests (src/androidTest)

Use `given_when_then` or `when_then` format:

```kotlin
@Test
fun items_whenLoaded_showInList()

@Test
fun searchBar_whenQueryTyped_showsResults()

@Test
fun backButton_whenOnDetail_returnsToList()
```

## Anti-Patterns

| Don't | Do Instead |
|-------|------------|
| Test ViewModel logic in UI tests | Unit-test ViewModels separately |
| Use `Thread.sleep()` for waiting | Use `composeTestRule.waitUntil {}` |
| Test with real network in UI tests | Use test doubles |
| Skip `teardown()` for database tests | Always `db.close()` to prevent leaks |
| Test every composable in androidTest | Use Roborazzi for visual; androidTest for interaction |
