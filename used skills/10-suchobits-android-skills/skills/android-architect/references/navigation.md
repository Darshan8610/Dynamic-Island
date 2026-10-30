# Navigation Patterns

## Navigation Architecture

Navigation uses type-safe `NavKey` objects with a `Navigator` class managing back stacks. Each feature exposes a `NavKey` in its `api` module and an `EntryProvider` in its `impl` module.

## NavKey Definition

NavKeys live in the feature `api` module. They're serializable data classes (or objects for argument-free screens):

```kotlin
// feature/{feature}/api/{Feature}NavKey.kt

@Serializable
data class {Feature}NavKey(val {item}Id: String) : NavKey

// For screens with no arguments
@Serializable
data object {Feature}NavKey : NavKey
```

Rules:
- One NavKey per navigable screen.
- NavKeys carry only primitive arguments (String, Int, Boolean) — no complex objects.
- Other modules depend on the `api` module to reference the NavKey, never the `impl`.

## EntryProvider

EntryProviders live in the feature `impl` module. They map a NavKey to the screen composable:

```kotlin
// feature/{feature}/impl/{Feature}EntryProvider.kt

class {Feature}EntryProvider @Inject constructor() : EntryProvider<{Feature}NavKey> {

    @Composable
    override fun Entry(key: {Feature}NavKey) {
        {Feature}Route(
            onNavigateBack = { LocalNavigator.current.popBack() },
            onNavigateTo{Target} = { id ->
                LocalNavigator.current.navigateTo({Target}NavKey(id))
            },
        )
    }
}
```

The EntryProvider bridges navigation into the feature's composable tree without the feature knowing about the Navigator directly.

## Navigator

A singleton that manages the back stack:

```kotlin
// core/navigation/Navigator.kt

@Singleton
class Navigator @Inject constructor() {

    private val _backStack = mutableStateListOf<NavKey>(HomeNavKey)
    val backStack: List<NavKey> get() = _backStack

    fun navigateTo(key: NavKey) {
        _backStack.add(key)
    }

    fun popBack(): Boolean {
        if (_backStack.size <= 1) return false
        _backStack.removeLast()
        return true
    }

    fun popBackTo(key: NavKey, inclusive: Boolean = false) {
        val index = _backStack.indexOfLast { it == key }
        if (index < 0) return
        val removeFrom = if (inclusive) index else index + 1
        while (_backStack.size > removeFrom) {
            _backStack.removeLast()
        }
    }

    fun replaceTop(key: NavKey) {
        if (_backStack.isNotEmpty()) _backStack.removeLast()
        _backStack.add(key)
    }
}
```

## Top-Level Navigation (Bottom Nav / Tabs)

For apps with a bottom navigation bar, manage top-level destinations separately:

```kotlin
@Singleton
class Navigator @Inject constructor() {

    // Top-level tabs — each has its own sub-stack
    private val _currentTab = mutableStateOf(TopLevelDestination.HOME)
    val currentTab: TopLevelDestination get() = _currentTab.value

    private val subStacks = mutableMapOf<TopLevelDestination, MutableList<NavKey>>()

    fun switchTab(tab: TopLevelDestination) {
        _currentTab.value = tab
    }

    fun navigateTo(key: NavKey) {
        val stack = subStacks.getOrPut(currentTab) { mutableListOf() }
        stack.add(key)
    }

    fun popBack(): Boolean {
        val stack = subStacks[currentTab] ?: return false
        if (stack.isEmpty()) return false
        stack.removeLast()
        return true
    }
}

enum class TopLevelDestination(
    val navKey: NavKey,
    val icon: ImageVector,
    val labelResId: Int,
) {
    HOME(HomeNavKey, Icons.Default.Home, R.string.home),
    SEARCH(SearchNavKey, Icons.Default.Search, R.string.search),
    PROFILE(ProfileNavKey, Icons.Default.Person, R.string.profile),
}
```

## Wiring in the App Module

The `app` module collects all EntryProviders and wires them into the navigation host:

```kotlin
// app/MainApp.kt

@Composable
fun MainApp(
    navigator: Navigator,
    entryProviders: Map<KClass<out NavKey>, EntryProvider<*>>,
) {
    val backStack = navigator.backStack

    NavDisplay(backStack = backStack) { key ->
        val provider = entryProviders[key::class]
            ?: error("No EntryProvider registered for ${key::class}")

        @Suppress("UNCHECKED_CAST")
        (provider as EntryProvider<NavKey>).Entry(key = key)
    }
}
```

### Registering Providers via Hilt Multibinding

```kotlin
@Module
@InstallIn(SingletonComponent::class)
abstract class NavigationModule {

    @Binds
    @IntoMap
    @NavKeyKey({Feature}NavKey::class)
    abstract fun binds{Feature}EntryProvider(
        impl: {Feature}EntryProvider,
    ): EntryProvider<*>

    // Repeat for each feature...
}
```

## Deep Links

Handle deep links by parsing the intent in the app module and pushing the appropriate NavKey:

```kotlin
fun handleDeepLink(intent: Intent, navigator: Navigator) {
    val uri = intent.data ?: return
    when {
        uri.pathSegments.firstOrNull() == "{items}" -> {
            val id = uri.pathSegments.getOrNull(1) ?: return
            navigator.navigateTo({Feature}NavKey({item}Id = id))
        }
    }
}
```

## Passing Results Back

For flows that return a result (e.g., a picker), use a shared `SavedStateHandle` or a callback:

```kotlin
// Callback approach — simplest
@Composable
fun {Feature}Route(
    onItemSelected: ({Item}) -> Unit,
) {
    // Screen calls onItemSelected when user picks an item
}

// In the caller's EntryProvider
{Feature}Route(
    onItemSelected = { item ->
        // handle result
        navigator.popBack()
    },
)
```

## Anti-Patterns

| Don't | Do Instead |
|-------|------------|
| Use string routes (`"feature/{id}"`) | Use typed NavKey data classes |
| Pass complex objects as navigation args | Pass IDs; let the destination ViewModel fetch the data |
| Navigate from the ViewModel | Emit events; let the Route/EntryProvider call Navigator |
| Import `impl` module for navigation | Import only the `api` module (NavKey) |
| Hardcode destinations in features | Features expose callbacks (`onNavigateTo{Target}`); wiring is in the app module |
