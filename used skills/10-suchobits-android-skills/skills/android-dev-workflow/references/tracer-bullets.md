# Tracer Bullets for Android

## The Problem

AI agents naturally build in horizontal layers:

```
Iteration 1: Create ALL data models
Iteration 2: Create ALL DAOs
Iteration 3: Create ALL repositories
Iteration 4: Create ALL ViewModels
Iteration 5: Create ALL screens
Iteration 6: Realize nothing works together
```

This fails because:
- Context rot — by iteration 5, the agent has forgotten decisions from iteration 1
- No feedback — you don't know if the architecture works until everything is wired
- Blast radius — when something is wrong in the model layer, everything above it breaks

## Tracer Bullets

From *The Pragmatic Programmer*: build a tiny end-to-end vertical slice through all system layers first. Test it. Get feedback. Then expand.

```
Iteration 1: Item model → ItemEntity → ItemDao → ItemRepository → ItemViewModel → ItemScreen → ItemViewModelTest
             (ONE thing, ALL layers, WORKS end-to-end)

Iteration 2: Add error/loading states to ItemScreen + test
Iteration 3: Add second model (Category) end-to-end
Iteration 4: Add relationships + combined queries
...
```

Each iteration produces a buildable, testable, demonstrable increment.

## Android Tracer Bullet Sequence

For a new feature with a list screen and detail screen:

### Slice 1: Data Layer Foundation (1 iteration)

```
core:model      → {Item} data class
core:database   → {Item}Entity + {Item}Dao (basic CRUD)
core:data       → {Item}Repository interface + OfflineFirst{Item}Repository
                   (get{Items}() only — no sync yet)
```

**Verify:** `./gradlew :core:database:connectedDemoDebugAndroidTest` passes.

### Slice 2: Feature Scaffold — Happy Path (1 iteration)

```
feature:X:api   → {Feature}NavKey
feature:X:impl  → {Feature}ViewModel (stateIn + Loading/Success)
                   {Feature}Screen (renders Success state only)
                   {Feature}Route (wires ViewModel to Screen)
app             → Wire EntryProvider + navigation
```

**Verify:** App launches, navigates to feature, shows data from database.

### Slice 3: ViewModel Tests + Error States (1 iteration)

```
core:testing    → Test{Item}Repository fake
feature:X:impl  → {Feature}ViewModelTest (Loading, Success, Error)
                   {Feature}Screen Error/Loading/Empty composables
```

**Verify:** `./gradlew :feature:X:impl:testDemoDebug` — all green.

### Slice 4: Detail Screen End-to-End (1 iteration)

```
feature:X:api   → {Feature}DetailNavKey
feature:X:impl  → {Feature}DetailViewModel
                   {Feature}DetailScreen
                   Navigation from list → detail
```

**Verify:** App navigates from list to detail, back works.

### Slice 5: Sync + Offline (1 iteration)

```
core:network    → Network{Item} + AppNetworkDataSource
core:data       → syncWith() implementation
sync:work       → Add to SyncWorker
```

**Verify:** App syncs on startup, shows fresh data.

### Slice 6: Polish (1-2 iterations)

```
Screenshot tests, edge cases, animations, accessibility
```

## The Tracer Bullet Prompt

Include this in your PRD or Ralph prompt:

```
When building features, use TRACER BULLET discipline:
- Build a tiny, end-to-end slice through all layers first
- One model + one DAO + one repository + one ViewModel + one screen
- Test it. Verify it builds and runs.
- Then expand outward from that working skeleton.

Do NOT:
- Create all models before any DAOs
- Create all DAOs before any repositories
- Build entire layers in isolation
- Leave wiring/integration for "later"
```

## Why This Matters More for Android

Android's module structure amplifies the layer-cake problem:

```
core:model → core:database → core:data → core:domain → feature:X:impl
```

Each arrow is a module boundary with its own `build.gradle.kts`, namespace, and dependency declarations. If an agent builds all of `core:model` before touching `core:database`, it can't verify its model design works with Room until 3 iterations later. By then, the context window has rotated and the agent doesn't remember why it made those model decisions.

Tracer bullets force the agent to cross all module boundaries in iteration 1, catching design issues immediately.

## Verifying Tracer Bullet Discipline

Signs the agent is following tracer bullets:
- Each commit touches files in 2+ modules
- Tests appear alongside implementation, not in a separate "testing iteration"
- The app is launchable after every iteration
- `progress.txt` shows vertical tasks ("create Item end-to-end") not horizontal ("create all models")

Signs the agent is building layer cake:
- First 3 commits are all in `core:model`
- Tests don't appear until iteration 5
- The app doesn't compile until halfway through
- `progress.txt` shows "created 8 data models" with no ViewModel or screen

If you see layer cake in HITL mode, adjust the prompt before going AFK.

## Anti-Patterns

| Don't | Do Instead |
|-------|------------|
| "Create all entities first" | Create one entity end-to-end, then the next |
| "We'll add tests at the end" | Tests are part of each tracer bullet slice |
| "Let's build the data layer completely" | Build one vertical slice through the data layer |
| "Navigation wiring can wait" | Wire navigation in the first feature slice |
| Spend 5 iterations on models alone | Spend 1 iteration on one model through to screen |
