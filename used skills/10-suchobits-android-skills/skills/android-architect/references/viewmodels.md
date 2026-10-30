# ViewModel Patterns

## Basic ViewModel

```kotlin
@HiltViewModel
class {Feature}ViewModel @Inject constructor(
    private val {item}Repository: {Item}Repository,
) : ViewModel() {

    val uiState: StateFlow<{Feature}UiState> =
        {item}Repository.get{Items}()
            .map<List<{Item}>, {Feature}UiState> { {Feature}UiState.Success(items = it) }
            .catch { emit({Feature}UiState.Error(it.message ?: "Unknown error")) }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = {Feature}UiState.Loading,
            )

    fun onAction(action: {Feature}Action) {
        when (action) {
            is {Feature}Action.Refresh -> refresh()
            is {Feature}Action.ItemClicked -> { /* navigate or expand */ }
            is {Feature}Action.ToggleFavorite -> toggleFavorite(action.id)
        }
    }

    private fun toggleFavorite(id: String) {
        viewModelScope.launch {
            {item}Repository.toggleFavorite(id)
        }
    }

    private fun refresh() {
        viewModelScope.launch {
            {item}Repository.sync()
        }
    }
}
```

## StateFlow Rules

- Always use `stateIn(SharingStarted.WhileSubscribed(5_000))` — the 5-second window prevents resubscription churn during configuration changes (rotation, theme switch).
- Set `initialValue` to the `Loading` state, never to a fake "empty" success.
- Use `.catch { }` to map exceptions into the `Error` UI state rather than crashing.

## Combining Multiple Data Sources

When a screen needs data from more than one repository, use `combine`:

```kotlin
@HiltViewModel
class {Feature}ViewModel @Inject constructor(
    private val {item}Repository: {Item}Repository,
    private val userDataRepository: UserDataRepository,
) : ViewModel() {

    val uiState: StateFlow<{Feature}UiState> = combine(
        {item}Repository.get{Items}(),
        userDataRepository.userData,
    ) { items, userData ->
        {Feature}UiState.Success(
            items = items.map { item ->
                {Feature}Item(
                    data = item,
                    isFavorite = item.id in userData.favorites,
                )
            },
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = {Feature}UiState.Loading,
    )
}
```

For three or more sources, `combine` accepts a vararg or an array of flows.

## ViewModel with Navigation Arguments

Use `SavedStateHandle` for arguments passed via NavKey:

```kotlin
@HiltViewModel
class {Feature}DetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val {item}Repository: {Item}Repository,
) : ViewModel() {

    private val {item}Id: String = savedStateHandle["{item}Id"]
        ?: throw IllegalArgumentException("{item}Id is required")

    val uiState: StateFlow<{Feature}DetailUiState> =
        {item}Repository.get{Item}({item}Id)
            .map<{Item}, {Feature}DetailUiState> { {Feature}DetailUiState.Success(it) }
            .catch { emit({Feature}DetailUiState.Error(it.message ?: "Not found")) }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = {Feature}DetailUiState.Loading,
            )
}
```

## Independent Section States

When a screen has sections that load independently (header, feed, sidebar), expose separate StateFlows:

```kotlin
@HiltViewModel
class {Feature}ViewModel @Inject constructor(
    private val {item}Repository: {Item}Repository,
    private val promoRepository: PromoRepository,
) : ViewModel() {

    val feedState: StateFlow<FeedUiState> =
        {item}Repository.get{Items}()
            .map<List<{Item}>, FeedUiState> { FeedUiState.Success(it) }
            .catch { emit(FeedUiState.Error(it.message ?: "")) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), FeedUiState.Loading)

    val promoState: StateFlow<PromoUiState> =
        promoRepository.getActivePromos()
            .map<List<Promo>, PromoUiState> { PromoUiState.Success(it) }
            .catch { emit(PromoUiState.Error(it.message ?: "")) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PromoUiState.Loading)
}
```

The Route collects each independently:

```kotlin
@Composable
internal fun {Feature}Route(viewModel: {Feature}ViewModel = hiltViewModel()) {
    val feedState by viewModel.feedState.collectAsStateWithLifecycle()
    val promoState by viewModel.promoState.collectAsStateWithLifecycle()

    {Feature}Screen(feedState = feedState, promoState = promoState, onAction = viewModel::onAction)
}
```

## One-Shot Operations

For operations that don't produce a continuous stream (submit, delete, create), use `viewModelScope.launch` and emit results via a `Channel` or a transient state flag:

```kotlin
private val _events = Channel<{Feature}Event>(Channel.BUFFERED)
val events: Flow<{Feature}Event> = _events.receiveAsFlow()

fun submit(data: {Item}Input) {
    viewModelScope.launch {
        try {
            {item}Repository.create(data)
            _events.send({Feature}Event.SubmitSuccess)
        } catch (e: Exception) {
            _events.send({Feature}Event.SubmitError(e.message ?: "Failed"))
        }
    }
}
```

Collect events in the Route with `LaunchedEffect`:

```kotlin
LaunchedEffect(Unit) {
    viewModel.events.collect { event ->
        when (event) {
            is {Feature}Event.SubmitSuccess -> onNavigateBack()
            is {Feature}Event.SubmitError -> snackbarHostState.showSnackbar(event.message)
        }
    }
}
```

## Use Cases

Use cases are optional. Add them only when a ViewModel needs combined logic from multiple repositories that would be reused:

```kotlin
class Get{Feature}FeedUseCase @Inject constructor(
    private val {item}Repository: {Item}Repository,
    private val userDataRepository: UserDataRepository,
) {
    operator fun invoke(): Flow<List<{Feature}FeedItem>> =
        combine(
            {item}Repository.get{Items}(),
            userDataRepository.userData,
        ) { items, userData ->
            items.map { item ->
                {Feature}FeedItem(
                    data = item,
                    isSaved = item.id in userData.savedIds,
                )
            }
        }
}
```

If the ViewModel only reads from one repository, skip the use case — it adds indirection with no value.

## Anti-Patterns

| Don't | Do Instead |
|-------|------------|
| Expose `MutableStateFlow` publicly | Expose `StateFlow` via `stateIn()` |
| Use `LiveData` | Use `StateFlow` — it's coroutines-native and Compose-friendly |
| Call repository methods in `init {}` eagerly | Let `stateIn()` trigger collection lazily via `WhileSubscribed` |
| Catch exceptions in the Route/Screen | Catch in the ViewModel and map to `Error` UI state |
| Put navigation logic in the ViewModel | Emit events; let the Route handle navigation callbacks |
| Inject `Context` into ViewModels | Use `@ApplicationContext` only if absolutely necessary; prefer passing data |
