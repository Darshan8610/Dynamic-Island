# Module Dependency Graph Reference

## Complete Dependency Matrix

```
Module                  Depends On
─────────────────────── ──────────────────────────────────────
app                     feature:*/impl, core:*, sync:work
feature:{f}:impl        feature:{f}:api, core:ui, core:model,
                        core:data, core:designsystem,
                        core:navigation, core:domain (optional)
feature:{f}:api         core:navigation, core:model
core:data               core:model, core:database, core:network,
                        core:datastore, core:common
core:domain             core:model, core:data
core:database           core:model
core:network            core:model
core:datastore          core:model, core:common
core:ui                 core:model, core:designsystem, core:analytics
core:designsystem       (standalone — no core deps)
core:navigation         (standalone)
core:common             (standalone — dispatchers, extensions)
core:model              (standalone — leaf node)
core:analytics          core:model (optional)
core:notifications      core:model, core:common
core:testing            core:model, core:data, core:common
sync:work               core:data, core:model, core:common
```

## Visualizing Dependencies

To verify your dependency graph has no cycles:

```bash
./gradlew :app:dependencies --configuration implementation
```

For a focused view of a single module:

```bash
./gradlew :feature:{feature}:impl:dependencies --configuration implementation
```

## Dependency Depth Guidelines

| Module Type | Max Dependency Depth | Rationale |
|-------------|---------------------|-----------|
| core:model | 0 | Leaf — must compile alone |
| core:common | 0 | Utilities have no business deps |
| core:database | 1 | Only model |
| core:network | 1 | Only model |
| core:data | 2 | model + data sources |
| core:domain | 3 | model + data + data sources |
| feature:*/impl | 4 | Full stack above |
| app | 5 | Everything |

Deep dependency chains slow builds. If a core module exceeds depth 3, consider splitting it.

## Build Performance Impact

### Parallel Build Optimization

Modules with no dependency relationship build in parallel. The ideal module graph is wide and shallow:

```
         app
        / | \
   feat1 feat2 feat3      ← parallel
    |      |      |
   core:data core:ui       ← parallel (shared)
    |      |
  core:db core:net         ← parallel
       \  /
     core:model            ← serial (leaf)
```

### Configuration Caching

Enable in `gradle.properties`:

```properties
org.gradle.configuration-cache=true
org.gradle.parallel=true
org.gradle.caching=true
org.gradle.jvmargs=-Xmx4g
```

### KSP Isolation

Run annotation processors in isolation to avoid cross-module invalidation:

```properties
ksp.incremental=true
ksp.incremental.log=true
```

## Common Mistakes

### Mistake: Feature depends on another feature's impl

```kotlin
// BAD — feature:search:impl depends on feature:profile:impl
dependencies {
    implementation(projects.feature.profile.impl) // WRONG
}
```

**Fix:** Depend on the api module, navigate via NavKey:

```kotlin
dependencies {
    implementation(projects.feature.profile.api) // NavKey only
}
```

### Mistake: Core module depends on a feature

```kotlin
// BAD — core:data depends on feature:search:api
dependencies {
    implementation(projects.feature.search.api) // WRONG
}
```

**Fix:** Move the shared type to `core:model` or `core:navigation`.

### Mistake: Model module has framework dependencies

```kotlin
// BAD — core:model depends on Room
dependencies {
    implementation(libs.room.runtime) // WRONG
}
```

**Fix:** Room annotations belong in `core:database`. The `core:model` module has only plain Kotlin data classes.

### Mistake: Circular dependency via transitive deps

Module A → Module B → Module C → Module A

**Fix:** Extract the shared contract into a new module that all three depend on, or use interface + Hilt binding to break the cycle.

## Migration Checklist

When extracting a module from a monolith:

- [ ] Identify all classes that will move
- [ ] List their current dependencies — these determine which core modules you need
- [ ] Create the module with the appropriate convention plugin
- [ ] Move classes, updating package names
- [ ] Update `settings.gradle.kts`
- [ ] Add module dependency in consumers
- [ ] Run `./gradlew :new-module:build` to verify it compiles in isolation
- [ ] Run full `./gradlew build` to verify nothing broke
- [ ] Check for accidental transitive dependency leaks with `./gradlew dependencies`
