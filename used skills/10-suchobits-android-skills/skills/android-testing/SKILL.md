---
name: android-testing
description: >-
  Coaches Android testing strategy — unit tests, UI tests, screenshot tests, and benchmarks.
  Use when writing ViewModel tests, Compose UI tests, Room DAO tests, screenshot regression
  tests with Roborazzi, baseline profiles, macro benchmarks, creating test doubles/fakes,
  setting up test infrastructure, or designing a testing strategy for a multi-module
  Android project. Covers MainDispatcherRule, fake repositories, test data builders,
  and CI integration.
license: Apache-2.0
compatibility: Requires Android Studio, Kotlin, JUnit 4, Compose Testing, Roborazzi (optional)
metadata:
  author: luis-suchobits
  version: "1.0"
  based-on: google/nowinandroid
---

# Android Testing — Comprehensive Testing Guide

Patterns for testing multi-module Android apps across all layers: unit tests, Compose UI tests, screenshot regression tests, database tests, and performance benchmarks. All examples use `{Feature}`, `{Item}` as placeholders.

## Testing Pyramid

```
    ╱ Benchmarks ╲            — Performance (macro, baseline profiles)
   ╱ Screenshot   ╲           — Visual regression (Roborazzi)
  ╱ UI Tests        ╲         — Compose component tests
 ╱ Integration Tests  ╲       — DAO, DataStore, WorkManager
╱ Unit Tests            ╲     — ViewModel, Repository, UseCase
```

Most tests should be unit tests. Screenshot and UI tests catch visual regressions. Benchmarks run in CI but less frequently.

## Test Infrastructure

### MainDispatcherRule

Required for any test touching `viewModelScope` or `Dispatchers.Main`:

```kotlin
class MainDispatcherRule(
    private val testDispatcher: TestDispatcher = UnconfinedTestDispatcher(),
) : TestWatcher() {
    override fun starting(description: Description) { Dispatchers.setMain(testDispatcher) }
    override fun finished(description: Description) { Dispatchers.resetMain() }
}
```

### Fake Repositories

Use fakes over mocks. Fakes implement the real interface with `MutableSharedFlow(replay=1)`:

```kotlin
class Test{Item}Repository : {Item}Repository {
    private val {items}Flow = MutableSharedFlow<List<{Item}>>(replay = 1)
    override fun get{Items}(): Flow<List<{Item}>> = {items}Flow
    fun send{Items}(items: List<{Item}>) { {items}Flow.tryEmit(items) }
}
```

### Test Data Builders

```kotlin
fun test{Item}(
    id: String = "test-id",
    title: String = "Test Title",
) = {Item}(id = id, title = title, /* defaults for all fields */)
```

## Test Types

| Type | Location | Runs On | Speed |
|------|----------|---------|-------|
| Unit (ViewModel, UseCase) | `src/test/` | JVM | Fast |
| Screenshot (Roborazzi) | `src/test/` | JVM (Robolectric) | Medium |
| UI (Compose) | `src/androidTest/` | Device/Emulator | Slow |
| DAO (Room) | `src/androidTest/` | Device/Emulator | Medium |
| Benchmark | `benchmarks/` | Device | Slow |

For detailed patterns per test type, see the reference guides:

- [Unit Tests](references/unit-tests.md) — ViewModel, Repository, UseCase testing
- [Screenshot Tests](references/screenshot-tests.md) — Roborazzi setup, multi-theme capture, CI
- [UI & Integration Tests](references/ui-integration-tests.md) — Compose tests, DAO tests, WorkManager
- [Benchmarks](references/benchmarks.md) — Macro benchmarks, baseline profiles

## File Locations

```
feature/{feature}/impl/
├── src/test/kotlin/...
│   ├── {Feature}ViewModelTest.kt
│   └── {Feature}ScreenScreenshotTests.kt
└── src/androidTest/kotlin/...
    └── {Feature}ScreenTest.kt

core/data/src/test/kotlin/...
├── repository/OfflineFirst{Item}RepositoryTest.kt
└── testdoubles/Test{Item}Dao.kt

core/database/src/androidTest/kotlin/...
└── dao/{Item}DaoTest.kt

core/testing/src/main/kotlin/...
├── repository/Test{Item}Repository.kt
├── data/TestData.kt
├── util/MainDispatcherRule.kt
├── TestNetworkMonitor.kt
└── TestSyncManager.kt

benchmarks/src/main/kotlin/...
├── {Feature}Benchmark.kt
└── baselineprofile/{Feature}BaselineProfile.kt
```

## CI Integration

```bash
# Unit tests
./gradlew testDemoDebug

# Screenshot verification (fails on visual diff)
./gradlew verifyRoborazziDemoDebug

# Record new screenshot baselines
./gradlew recordRoborazziDemoDebug

# Instrumentation tests
./gradlew connectedDemoDebugAndroidTest

# Lint checks (including custom lint rule tests)
./gradlew :lint:test
```
