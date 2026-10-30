---
name: android-architect
description: >-
  Coaches modern Android app architecture using Clean Architecture, MVVM, and Repository patterns.
  Use when creating new Android modules, features, ViewModels, repositories, data layers,
  navigation, dependency injection with Hilt, Jetpack Compose UI, offline-first sync,
  convention plugins, or structuring a multi-module Kotlin Android project.
  Covers Room, Retrofit, Proto DataStore, Coroutines Flow, WorkManager, and modularization
  with API/impl splits.
license: Apache-2.0
compatibility: Requires Android Studio, Kotlin, Gradle with version catalogs
metadata:
  author: luis-suchobits
  version: "1.0"
  based-on: google/nowinandroid
---

# Android Architect — Modern Android Architecture Guide

Patterns and templates for production-grade, multi-module Android apps with Kotlin and Jetpack Compose. All code examples use `{Feature}`, `{Item}`, `{Target}` as placeholders — substitute your actual names.

## Architecture Overview

The architecture follows **Clean Architecture with MVVM** and the **Repository pattern**. Data flows unidirectionally:

```
Compose UI → ViewModel → UseCase (optional) → Repository → DataSource (DB / Network / DataStore)
```

Every layer depends only on the layer below it. The UI layer never touches data sources directly.

## Module Structure

```
app/                  # Entry point, wires features together
core/                 # Shared libraries (no feature logic)
  ├── model/          # Plain data classes shared across layers
  ├── data/           # Repository interfaces + implementations
  ├── domain/         # Use cases (optional, for cross-repo logic)
  ├── database/       # Room entities, DAOs, type converters
  ├── network/        # Network data source interfaces + implementations
  ├── datastore/      # Proto DataStore preferences
  ├── designsystem/   # Material 3 theme, shared components
  ├── ui/             # Shared Compose utilities
  ├── navigation/     # Navigator, NavKey abstractions
  ├── common/         # Dispatcher qualifiers, extensions
  └── testing/        # Shared test doubles (fake repositories, rules)
feature/              # One module per screen/flow
  ├── {feature}/
  │   ├── api/        # Public NavKey + entry composable
  │   └── impl/       # ViewModel, screens, DI — private
  └── ...
```

**Key rule**: Feature modules depend on `core`, never on other features. The `app` module wires features together.

### API / Impl Split

Each feature has two submodules:

- **api/** — Exports a `NavKey` data class and an entry composable. Other modules depend on this to navigate without coupling to internals.
- **impl/** — Contains the ViewModel, screens, and Hilt bindings. Only `app` includes impl modules.

## UI Layer

For complete screen patterns with code templates, see [screens reference](references/screens.md).

Summary of the screen architecture:

- **Route/Screen split**: `{Feature}Route` (stateful, owns ViewModel) wraps `{Feature}Screen` (stateless, preview-friendly).
- **Sealed UI state**: `{Feature}UiState` with `Loading`, `Error`, `Success` variants.
- **Action sealed interface**: `{Feature}Action` models all user interactions.
- **StateFlow** with `stateIn(SharingStarted.WhileSubscribed(5_000))`.
- **Previews** for every state variant.

## Data Layer

### Three-Tier Model Separation

Never reuse a single model class across layers:

| Layer | Naming | Annotation | Module |
|-------|--------|------------|--------|
| Network | `Network{Item}` | `@Serializable` (kotlinx) | `core:network` |
| Database | `{Item}Entity` | `@Entity` (Room) | `core:database` |
| Domain/UI | `{Item}` | None | `core:model` |

Write mapping extensions (`asEntity()`, `asExternalModel()`) next to each model.

### Repository Pattern (Offline-First)

```kotlin
class OfflineFirst{Item}Repository @Inject constructor(
    private val dao: {Item}Dao,
    private val network: AppNetworkDataSource,
) : {Item}Repository {

    override fun get{Items}(): Flow<List<{Item}>> =
        dao.get{Items}().map { it.map({Item}Entity::asExternalModel) }
}
```

- Expose `Flow<T>` from repositories for reactive updates.
- Local database (Room) is the source of truth.
- Network syncs into the database; UI observes the database.

For detailed data layer patterns, see [data-models reference](references/data-models.md) (coming soon).

## Dependency Injection (Hilt)

```kotlin
@Module
@InstallIn(SingletonComponent::class)
abstract class DataModule {
    @Binds
    abstract fun binds{Item}Repository(impl: OfflineFirst{Item}Repository): {Item}Repository
}
```

- `@Binds` for interface→implementation. `@Provides` only for complex construction.
- Dispatcher qualifiers: `@Dispatcher(IO)`, `@Dispatcher(Default)`.
- Feature impl modules auto-apply Hilt via convention plugin.

## Convention Plugins

Use a `build-logic/` included build to share Gradle config:

| Plugin | Purpose |
|--------|---------|
| `AndroidLibraryConventionPlugin` | Library defaults + resource prefixing |
| `AndroidFeatureImplConventionPlugin` | Auto-applies Hilt, lifecycle, navigation deps |
| `AndroidFeatureApiConventionPlugin` | Thin public interface module |
| `AndroidLibraryComposeConventionPlugin` | Compose compiler config |
| `AndroidRoomConventionPlugin` | Room schema setup |
| `HiltConventionPlugin` | Hilt KSP setup |

Use a Gradle version catalog (`libs.versions.toml`) for all dependency versions.

## Testing

- **Fakes over mocks**: Create test doubles in `core:testing` using `MutableSharedFlow(replay=1)`.
- **MainDispatcherRule**: Swaps `Dispatchers.Main` with a test dispatcher.
- **ViewModel tests**: Construct with fakes, use `runTest` + `advanceUntilIdle()`.
- **Database tests**: Room in-memory database in instrumentation tests.
- **UI tests**: Screenshot testing via Roborazzi; `testTag` modifiers for element selection.

## Reference Guides

Detailed templates and code examples for each layer:

- [Screens & Compose UI](references/screens.md) — Route/Screen split, UI state, actions, previews
- [ViewModels](references/viewmodels.md) — StateFlow, combine, events, use cases
- [Repositories](references/repositories.md) — Offline-first, sync, network monitor
- [Data Models](references/data-models.md) — Three-tier models, mapping, Room DAOs, FTS
- [Hilt DI](references/di-hilt.md) — Modules, qualifiers, scopes, flavor bindings
- [Navigation](references/navigation.md) — NavKey, EntryProvider, Navigator, deep links
- [Convention Plugins](references/convention-plugins.md) — Build logic, plugin templates, version catalog
- [Testing](references/testing.md) — Fakes, ViewModel tests, DAO tests, screenshot tests
