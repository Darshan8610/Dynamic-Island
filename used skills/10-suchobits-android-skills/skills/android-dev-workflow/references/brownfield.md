# Brownfield Migration Guide

## Philosophy

Don't rewrite. Improve incrementally. Every change should leave the codebase buildable and shippable. The goal is not to reach a perfect architecture in one sprint — it's to stop things from getting worse (enforcement) while making them gradually better (extraction).

Adapted from the "improve codebase architecture" methodology (Ousterhout's *A Philosophy of Software Design*, Matt Pocock's skills).

## The Deletion Test

Before extracting, refactoring, or "improving" any module, ask:

*If I deleted this module entirely, what happens?*

- **Complexity reappears across N callers** → The module was earning its keep. It's deep. Keep it, maybe deepen it further.
- **Callers would just inline the same code** → The module is a pass-through. It's shallow. Merge it into its callers or the module it delegates to.
- **Nothing changes** → It's dead code. Delete it.

### Android-Specific Shallow Module Patterns

| Pattern | Symptom | Action |
|---------|---------|--------|
| Repository wrapping single DAO | `fun getItems() = dao.getItems().map { it.asModel() }` — that's the entire repo | Keep only if sync/offline logic is planned. Otherwise merge into DAO usage. |
| UseCase calling single repo | `operator fun invoke() = repo.getItems()` | Delete. Use case adds value only when combining multiple repos. |
| Manager delegating to one thing | `class FooManager(private val foo: Foo) { fun doThing() = foo.doThing() }` | Inline into callers. |
| Mapper classes | `class FooMapper { fun map(a: A): B = B(a.x, a.y) }` | Use extension functions (`fun A.toB()`) instead of a class. |
| "Base" classes with one child | `abstract class BaseViewModel` with a single subclass | Inline into the child. |

## Enforcement Before Improvement

**Rule: Install the architecture guard BEFORE making improvements.** Otherwise you fix one problem while creating two others.

```kotlin
// Step 1: Install architectureGuard with suppressions for ALL existing violations
extra["architectureGuard.suppress"] = listOf(
    "dependency-direction",   // TODO: fix in #45
    "module-placement",       // TODO: move screens/ to feature/ in #46
)
```

Each suppression is:
- **Visible in code review** — nobody can add one silently
- **Tracked to a ticket** — the comment has the issue number
- **Auditable** — `grep -r "architectureGuard.suppress" .` shows all active ones
- **A migration backlog** — removing a suppression = one task in the migration

New code is held to the full standard immediately. Old code is grandfathered with a ticket to fix it.

## Bottom-Up Extraction Strategy

Extract modules in this order. Each step depends on the previous:

### Phase 1: core:model (The Leaf)

The easiest and highest-impact extraction. Move all shared data classes into a standalone module with zero project dependencies.

**What moves:** Data classes used across features — `User`, `Item`, `Category`, etc. Plain Kotlin, no annotations, no framework deps.

**What stays:** Room `@Entity` classes (they go in `core:database`), `@Serializable` network models (they go in `core:network`).

**Verification:**
```bash
./gradlew :core:model:build  # Compiles in isolation, no other module needed
```

**Issue template:**
```
## What to Build
Extract shared data classes into core:model.
core:model must have ZERO project dependencies — it is the leaf of the dependency tree.

## Acceptance Criteria
- [ ] core:model module exists with android-library convention plugin
- [ ] All shared data classes moved from {current location}
- [ ] No @Entity, @Serializable, or framework annotations in core:model
- [ ] All consumers updated to depend on core:model
- [ ] ./gradlew :core:model:build passes in isolation
- [ ] architectureGuard passes
```

### Phase 2: core:common

Dispatcher qualifiers, extension functions, utilities. Small, quick.

### Phase 3: core:database

Room entities, DAOs, type converters, the database class. Depends only on `core:model`.

**Key decision:** Entities are NOT the same as domain models. Create mapping extensions (`fun ItemEntity.asExternalModel(): Item`).

### Phase 4: core:network

Network models, data source interfaces, Retrofit API interfaces. Depends only on `core:model`.

**Key decision:** Network models are NOT the same as domain models or entities. Create mapping extensions (`fun NetworkItem.asEntity(): ItemEntity`).

### Phase 5: core:data

Repository interfaces and implementations. Depends on `core:model`, `core:database`, `core:network`, `core:common`.

This is where offline-first logic lives. Repositories expose `Flow<T>` of domain models. The database is the source of truth.

### Phase 6: core:designsystem

Theme, colors, typography, wrapped Material components. No business logic dependencies.

**Add the DesignSystemDetector lint rule** after this extraction. It turns existing `Button()` / `MaterialTheme` usage into build errors, creating a clear migration backlog.

### Phase 7: First Feature Split (api + impl)

Pick the simplest feature. Extract it into `feature:X:api` (NavKey only) and `feature:X:impl` (ViewModel + Screen + DI).

This is the hardest step because it establishes the pattern. Subsequent features are copy-paste.

### Phase 8: Remove Suppressions

Each suppression in the guard is now a task:

```bash
# Find all active suppressions
grep -r "architectureGuard.suppress" --include="*.kts" .

# For each, create an issue
# Fix the violation, remove the suppression, verify the build
```

When all suppressions are gone, the architecture guard runs without exceptions. The migration is complete.

## Deepening Modules

After extraction, some modules will be shallow. Deepen them:

### Deepen a Repository

Before (shallow — just maps):
```kotlin
class ItemRepository(private val dao: ItemDao) {
    fun getItems(): Flow<List<Item>> =
        dao.getItems().map { it.map(ItemEntity::asExternalModel) }
}
```

After (deep — combines sources, adds sync, handles offline):
```kotlin
class OfflineFirstItemRepository(
    private val dao: ItemDao,
    private val network: NetworkDataSource,
    private val networkMonitor: NetworkMonitor,
) : ItemRepository, Syncable {
    override fun getItems(): Flow<List<Item>> =
        dao.getItems().map { it.map(ItemEntity::asExternalModel) }

    override fun getItemsWithStatus(): Flow<List<ItemWithSyncStatus>> =
        combine(getItems(), networkMonitor.isOnline) { items, online ->
            items.map { ItemWithSyncStatus(it, if (online) Synced else Cached) }
        }

    override suspend fun syncWith(synchronizer: Synchronizer): Boolean = // ...
}
```

The interface is still simple (`getItems()`, `sync()`), but behind it: two data sources, network monitoring, delta sync, and cache management. That's depth.

### Deepen a ViewModel

Before (shallow — just exposes repo flow):
```kotlin
val uiState = repository.getItems()
    .map { HomeUiState.Success(it) }
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState.Loading)
```

After (deep — combines data, handles actions, manages sync):
```kotlin
val uiState = combine(
    repository.getItems(),
    userDataRepository.userData,
    syncManager.isSyncing,
) { items, userData, syncing ->
    HomeUiState.Success(
        items = items.map { item ->
            HomeItem(data = item, isFavorite = item.id in userData.favorites)
        },
        isRefreshing = syncing,
    )
}.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState.Loading)

fun onAction(action: HomeAction) {
    when (action) {
        is HomeAction.ToggleFavorite -> viewModelScope.launch { userDataRepository.toggleFavorite(action.id) }
        is HomeAction.Refresh -> syncManager.requestSync()
    }
}
```

## Diagnosing Bugs in Brownfield Code

When a bug surfaces during migration:

1. **Build a feedback loop first.** Write a failing test that reproduces the bug. If you can't test it (no seam), that's the real finding — the architecture prevents testing.
2. **Generate 3-5 hypotheses** before testing any. Each must be falsifiable.
3. **Fix + regression test.** The test locks down the fix.
4. **Post-mortem: what would have prevented this?** If the answer is "better module boundaries" or "a test seam that doesn't exist," that becomes a new extraction task.

Bugs found during migration often reveal exactly where the architecture needs work. They're diagnostic, not just annoying.

## Migration Velocity

Expect:
- **Phase 1-2** (model + common): 1-2 days, mostly mechanical moves
- **Phase 3-4** (database + network): 2-3 days, involves model mapping decisions
- **Phase 5** (data): 3-5 days, repository patterns need design thought
- **Phase 6** (designsystem): 2-3 days, component wrapping
- **Phase 7** (first feature split): 3-5 days, establishes the pattern
- **Phase 8** (suppressions): Ongoing, 1 suppression per PR

With Ralph handling the mechanical work (moving files, updating imports, creating mappings), an agent can do each phase in 3-8 Ralph iterations. Human reviews the PRs.

## Anti-Patterns

| Don't | Do Instead |
|-------|------------|
| "Big bang" rewrite | Extract one module at a time |
| Improve without enforcement | Install guard + suppressions first |
| Create abstractions "for the future" | Only add seams where two implementations exist today |
| Delete existing tests during refactor | Keep them until new interface-level tests replace them |
| Migrate everything before shipping | Each extraction is independently shippable |
| Skip the deletion test | Apply it to every module — delete shallow pass-throughs |
