# Compose Screen Patterns

## Screen Structure

Every feature screen follows a two-layer composable pattern: a **Route** (stateful wrapper) and a **Screen** (stateless UI).

```kotlin
// {Feature}Screen.kt — in feature/{feature}/impl/

@Composable
internal fun {Feature}Route(
    viewModel: {Feature}ViewModel = hiltViewModel(),
    onNavigateTo{Target}: () -> Unit,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    {Feature}Screen(
        uiState = uiState,
        onAction = viewModel::onAction,
        onNavigateTo{Target} = onNavigateTo{Target},
    )
}

@Composable
internal fun {Feature}Screen(
    uiState: {Feature}UiState,
    onAction: ({Feature}Action) -> Unit,
    onNavigateTo{Target}: () -> Unit,
    modifier: Modifier = Modifier,
) {
    when (uiState) {
        is {Feature}UiState.Loading -> LoadingIndicator(modifier)
        is {Feature}UiState.Error -> ErrorContent(message = uiState.message, modifier = modifier)
        is {Feature}UiState.Success -> {Feature}Content(
            data = uiState.data,
            onAction = onAction,
            modifier = modifier,
        )
    }
}
```

### Why two layers?

| Layer | Owns | Testability |
|-------|------|-------------|
| `{Feature}Route` | ViewModel injection, lifecycle-aware collection | Integration-tested via Hilt |
| `{Feature}Screen` | Pure rendering from parameters | Preview-friendly, screenshot-testable |

The Screen composable is stateless — it receives everything via parameters. This makes it trivial to preview, screenshot-test, and reuse.

## UI State

Define a sealed interface per screen with explicit states:

```kotlin
// {Feature}UiState.kt

sealed interface {Feature}UiState {
    data object Loading : {Feature}UiState
    data class Error(val message: String) : {Feature}UiState
    data class Success(
        val items: List<{Item}>,
        val isRefreshing: Boolean = false,
    ) : {Feature}UiState
}
```

Rules:
- Always include a `Loading` state — never assume data is available on first frame.
- Keep the `Success` data class flat. Avoid nesting other sealed types inside it.
- For screens with independent sections that load separately, use multiple state flows in the ViewModel rather than one combined sealed type.

### Multiple Independent Sections

When a screen has sections that load at different speeds:

```kotlin
// ViewModel exposes separate flows
val headerState: StateFlow<HeaderUiState>
val feedState: StateFlow<FeedUiState>

// Screen takes them independently
@Composable
internal fun {Feature}Screen(
    headerState: HeaderUiState,
    feedState: FeedUiState,
    onAction: ({Feature}Action) -> Unit,
    modifier: Modifier = Modifier,
)
```

This lets the header render immediately while the feed still loads.

## User Actions

Model user interactions as a sealed interface passed to the ViewModel:

```kotlin
sealed interface {Feature}Action {
    data class ItemClicked(val id: String) : {Feature}Action
    data class ToggleFavorite(val id: String) : {Feature}Action
    data object Refresh : {Feature}Action
    data object RetryLoad : {Feature}Action
}
```

The Screen composable calls `onAction({Feature}Action.ItemClicked(item.id))`. The ViewModel handles it in a single `fun onAction(action: {Feature}Action)` method or via a `when` block.

## Screen Content Patterns

### List Screen

```kotlin
@Composable
private fun {Feature}Content(
    items: List<{Item}>,
    onAction: ({Feature}Action) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(
            items = items,
            key = { it.id },
        ) { item ->
            {Item}Card(
                item = item,
                onClick = { onAction({Feature}Action.ItemClicked(item.id)) },
            )
        }
    }
}
```

### Detail Screen with Arguments

```kotlin
// NavKey carries the argument
@Serializable
data class {Feature}DetailNavKey(val itemId: String) : NavKey

// ViewModel reads it from SavedStateHandle
@HiltViewModel
class {Feature}DetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: {Item}Repository,
) : ViewModel() {

    private val itemId: String = savedStateHandle["itemId"]
        ?: throw IllegalArgumentException("{Feature}DetailNavKey requires itemId")

    val uiState: StateFlow<{Feature}DetailUiState> =
        repository.get{Item}(itemId)
            .map<{Item}, {Feature}DetailUiState> { {Feature}DetailUiState.Success(it) }
            .catch { emit({Feature}DetailUiState.Error(it.message ?: "Unknown error")) }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = {Feature}DetailUiState.Loading,
            )
}
```

### Adaptive Grid Screen

```kotlin
@Composable
private fun {Feature}Grid(
    items: List<{Item}>,
    onAction: ({Feature}Action) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyVerticalStaggeredGrid(
        columns = StaggeredGridCells.Adaptive(300.dp),
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalItemSpacing = 8.dp,
    ) {
        items(
            items = items,
            key = { it.id },
        ) { item ->
            {Item}Card(item = item, onClick = { onAction({Feature}Action.ItemClicked(item.id)) })
        }
    }
}
```

## Previews

Always add previews for each UI state:

```kotlin
@Preview(showBackground = true)
@Composable
private fun {Feature}ScreenLoadingPreview() {
    AppTheme {
        {Feature}Screen(
            uiState = {Feature}UiState.Loading,
            onAction = {},
            onNavigateTo{Target} = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun {Feature}ScreenSuccessPreview() {
    AppTheme {
        {Feature}Screen(
            uiState = {Feature}UiState.Success(
                items = previewItems(),
            ),
            onAction = {},
            onNavigateTo{Target} = {},
        )
    }
}
```

## Edge-to-Edge & Insets

```kotlin
@Composable
internal fun {Feature}Screen(
    uiState: {Feature}UiState,
    onAction: ({Feature}Action) -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
    ) { innerPadding ->
        // Apply innerPadding to content
        Box(modifier = Modifier.padding(innerPadding)) {
            // screen content
        }
    }
}
```

## Testing Hooks

Add `testTag` modifiers to key interactive elements for UI tests:

```kotlin
{Item}Card(
    modifier = Modifier.testTag("item_card_${item.id}"),
    // ...
)
```

Use semantic descriptions via `Modifier.semantics { contentDescription = "..." }` for accessibility and testing.

## File Layout per Feature

```
feature/{feature}/impl/src/main/kotlin/.../
├── {Feature}Screen.kt          # Route + Screen composables
├── {Feature}ViewModel.kt       # State management
├── {Feature}UiState.kt         # Sealed UI state
├── {Feature}Action.kt          # User action sealed interface
└── components/                 # Screen-private composables
    ├── {Item}Card.kt
    └── {Feature}TopBar.kt
```
