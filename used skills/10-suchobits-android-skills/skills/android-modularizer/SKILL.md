---
name: android-modularizer
description: >-
  Coaches Android app modularization — breaking monoliths into multi-module projects or adding
  new modules correctly. Use when creating feature modules, core modules, extracting code into
  modules, resolving circular dependencies, setting up module boundaries, or planning a
  modularization strategy for an Android Gradle project. Covers API/impl splits, dependency
  graphs, and module naming conventions.
license: Apache-2.0
compatibility: Requires Android Studio, Kotlin, Gradle with version catalogs
metadata:
  author: luis-suchobits
  version: "1.0"
  based-on: google/nowinandroid
---

# Android Modularizer — Multi-Module Architecture Guide

Patterns for breaking Android monoliths into well-structured multi-module projects, or adding new modules to an existing modular codebase. All examples use `{feature}`, `{item}`, `{appId}` as placeholders.

## Module Taxonomy

Every module falls into one of four categories:

| Type | Purpose | Examples |
|------|---------|---------|
| **app** | Entry point, wires everything together | `app/` |
| **feature** | One screen or user flow, split into api + impl | `feature/{feature}/api`, `feature/{feature}/impl` |
| **core** | Shared library, no feature logic | `core:model`, `core:data`, `core:ui` |
| **sync/infra** | Background work, build tooling | `sync:work`, `build-logic/` |

## Dependency Rules

```
app → feature:*/impl → feature:*/api
                     → core:*

feature:*/impl → core:ui, core:model, core:data, core:domain, core:designsystem, core:navigation
feature:*/api  → core:model, core:navigation (minimal surface)

core:data      → core:model, core:database, core:network, core:datastore, core:common
core:domain    → core:model, core:data
core:database  → core:model
core:network   → core:model
core:ui        → core:model, core:designsystem
core:testing   → core:model, core:data, core:common
```

### Rules That Must Never Break

1. **Feature → Feature is forbidden.** Features communicate through `core` modules or navigation only.
2. **core:model depends on nothing.** It's the leaf of the dependency tree.
3. **impl depends on api, never the reverse.** The api module has no knowledge of its implementation.
4. **Only `app` includes feature impl modules.** No other module should transitively pull in a feature implementation.

## API / Impl Split

The most important modularization pattern. Every feature has two submodules:

### api/ — The Public Contract

```kotlin
// feature/{feature}/api/build.gradle.kts
plugins {
    alias(libs.plugins.{appId}.android.feature.api)
}

// Exports only:
// 1. A NavKey
@Serializable
data class {Feature}NavKey(val {item}Id: String) : NavKey

// 2. Optionally, a composable entry point type
```

**What goes in api:**
- NavKey data classes
- Shared sealed types other modules need to reference
- Nothing else — no ViewModels, no screens, no repositories

### impl/ — The Private Implementation

```kotlin
// feature/{feature}/impl/build.gradle.kts
plugins {
    alias(libs.plugins.{appId}.android.feature.impl)
}

dependencies {
    implementation(projects.feature.{feature}.api)
    implementation(projects.core.data)
}
```

Contains: ViewModel, Screen composables, Hilt bindings, feature-specific components.

### Why Split?

Without the split, any module that needs to navigate to `{Feature}` must depend on its entire implementation — pulling in its ViewModel, its repositories, its UI. The api module breaks this: other features depend only on the NavKey, and the `app` module wires the impl at the top level.

## Adding a New Feature Module

### Step-by-step

1. **Create directories:**
   ```
   feature/{feature}/api/src/main/kotlin/com/example/feature/{feature}/api/
   feature/{feature}/impl/src/main/kotlin/com/example/feature/{feature}/impl/
   ```

2. **Register in `settings.gradle.kts`:**
   ```kotlin
   include(":feature:{feature}:api")
   include(":feature:{feature}:impl")
   ```

3. **Create api `build.gradle.kts`:**
   ```kotlin
   plugins {
       alias(libs.plugins.{appId}.android.feature.api)
   }
   android { namespace = "com.example.feature.{feature}.api" }
   ```

4. **Create impl `build.gradle.kts`:**
   ```kotlin
   plugins {
       alias(libs.plugins.{appId}.android.feature.impl)
   }
   android { namespace = "com.example.feature.{feature}.impl" }
   dependencies {
       implementation(projects.feature.{feature}.api)
       implementation(projects.core.data)
   }
   ```

5. **Define the NavKey** in api.
6. **Create ViewModel, Screen, EntryProvider** in impl.
7. **Wire in `app`:** Add impl dependency and register the EntryProvider.

## Adding a New Core Module

1. Create `core/{module}/` with a `build.gradle.kts` applying the library convention plugin.
2. Register in `settings.gradle.kts`.
3. Resources are auto-prefixed by module path (e.g., `core_ui_` for `core:ui`).

## Extracting a Module from a Monolith

### Strategy: Bottom-Up Extraction

Extract in this order — each step unlocks the next:

```
1. core:model       (pure data classes, no deps)
2. core:common      (utilities, dispatcher qualifiers)
3. core:database    (Room entities + DAOs, depends on model)
4. core:network     (network models + data source, depends on model)
5. core:datastore   (preferences, depends on model)
6. core:data        (repositories, depends on database + network + datastore)
7. core:domain      (use cases, depends on data)
8. core:designsystem (theme + components, no business logic deps)
9. core:ui          (shared composables, depends on designsystem + model)
10. core:navigation  (NavKey base types)
11. core:testing     (test doubles for all the above)
12. feature:*/api    (NavKeys)
13. feature:*/impl   (screens + ViewModels)
```

### Resolving Circular Dependencies

If module A depends on module B and B depends on A:

1. **Extract shared types** into a new module (often `core:model`) that both depend on.
2. **Use interfaces**: A defines an interface, B implements it, binding happens via Hilt in a third module.
3. **Use navigation**: Instead of A calling B directly, A navigates to B via NavKey (depends only on B's api module).

### Identifying Module Boundaries

Ask these questions about a group of classes:

- Do they change together? → Same module.
- Do they have different dependency needs (e.g., one needs Room, another needs Retrofit)? → Different modules.
- Could a team own this independently? → Feature module.
- Is it reused across features? → Core module.

## Module Naming Conventions

| Pattern | Example | When to Use |
|---------|---------|-------------|
| `core:{concern}` | `core:data`, `core:ui` | Shared library by technical concern |
| `feature:{name}:api` | `feature:search:api` | Public feature contract |
| `feature:{name}:impl` | `feature:search:impl` | Private feature implementation |
| `sync:{mechanism}` | `sync:work` | Background processing |

## Gradle Configuration

### Resource Prefixing

Convention plugins auto-prefix resources by module path to prevent conflicts:

```kotlin
resourcePrefix = path.drop(1).replace(':', '_').replace('-', '_') + "_"
// core:designsystem → core_designsystem_
// feature:search:impl → feature_search_impl_
```

### Namespace Convention

```kotlin
android {
    namespace = "com.example.{module.path.with.dots}"
    // core:data → com.example.core.data
    // feature:search:impl → com.example.feature.search.impl
}
```

For detailed module dependency patterns, see the [dependency graph reference](references/dependency-graph.md).
