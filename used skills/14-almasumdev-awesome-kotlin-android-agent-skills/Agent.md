# AGENTS Guidelines for This Repository

This repository contains a Kotlin-first Android application project. When working on the project interactively with an AI coding agent, please follow the guidelines below to ensure architectural consistency, maximum performance, and a smooth development experience.

## 1. Project Specifications
- **Kotlin:** 2.0+ with the K2 compiler enabled
- **JVM Target:** 17 (`jvmTarget = "17"`, `sourceCompatibility = JavaVersion.VERSION_17`)
- **Android SDK:** `minSdk 24`, `targetSdk 35`, `compileSdk 35`
- **Gradle:** 8.7+ with Kotlin DSL (`build.gradle.kts`) and version catalogs (`gradle/libs.versions.toml`)
- **Package Manager:** Gradle with Maven Central + Google Maven. Prefer version catalog entries over hard-coded coordinates.

## 2. Architecture & Design Patterns
We follow a layered Clean Architecture approach with Gradle-module separation:
- **Presentation Layer:** Jetpack Compose screens, `ViewModel`s exposing immutable `StateFlow<UiState>`, Unidirectional Data Flow. Never put business logic in composables.
- **Domain Layer:** Pure Kotlin (no Android, no Compose). UseCases/Interactors, value classes, repository **interfaces**. Lives in `:core:domain` or `:feature:*:domain` modules.
- **Data Layer:** Repository **implementations**, Room DAOs, Retrofit services, DTOs + mappers to domain models.
- **Dependency Injection:** Hilt (`@HiltAndroidApp`, `@HiltViewModel`, `@Module @InstallIn`). Koin is an acceptable alternative for KMP-shared projects.

## 3. Asynchronous Programming
- **Concurrency:** Kotlin Coroutines with structured concurrency. Use `viewModelScope`, `lifecycleScope`, or an injected `CoroutineScope` — never `GlobalScope`.
- **Streams:** `Flow` for cold streams, `StateFlow` for UI state, `SharedFlow` for one-shot events. Collect on the UI with `collectAsStateWithLifecycle()`.
- **Heavy Work:** `Dispatchers.Default` for CPU-bound, `Dispatchers.IO` for disk/network. Inject dispatchers for testability.
- **Cancellation:** Cooperate with cancellation (`ensureActive()`, `yield()`). Cancel `Job`s in `onCleared()`/`onDestroy()` where needed.

## 4. UI Framework
- **Jetpack Compose + Material 3** with `dynamicColorScheme` on Android 12+ and a branded fallback palette.
- **Stability:** Prefer `@Immutable`/`@Stable` data classes and `kotlinx.collections.immutable` for list state.
- **Navigation:** Compose Navigation with typed routes via `kotlinx.serialization` (`navigation-compose` 2.8+).
- **Theming:** Single `AppTheme { ... }` composable wrapping `MaterialTheme`, typography, and `CompositionLocalProvider`s.

## 5. Data & Networking
- **Remote:** Retrofit + OkHttp + `kotlinx.serialization` (`Json { ignoreUnknownKeys = true }`). Use `Converter.Factory` from `retrofit2-kotlinx-serialization-converter`.
- **Local:** Room with KSP, `@Transaction` for multi-step writes, `Flow<T>` return types for observable queries.
- **Preferences:** `DataStore<Preferences>` or typed DataStore with a Proto schema. Do not use `SharedPreferences` in new code.
- **Offline-first:** Single source of truth is Room; network writes update Room, UI observes Room.

## 6. Testing Philosophy
- **Unit Tests:** JUnit5 (`org.junit.jupiter`), MockK for mocks, Turbine for Flow assertions, `kotlinx-coroutines-test` with `runTest` + `StandardTestDispatcher`.
- **Assertions:** `assertk` or `kotest-assertions-core` for fluent matchers.
- **Compose UI Tests:** `createAndroidComposeRule<MainActivity>()`, semantics matchers, `composeTestRule.onNodeWithTag(...)`.
- **Screenshot Tests:** Paparazzi (JVM, no emulator) or Roborazzi (Robolectric-based). Pick one per module and stick with it.
- **Rule:** Test ViewModel state transitions, not composable internals.

## 7. Build & Tooling
- **Kotlin DSL + convention plugins** in `build-logic/` for shared Android library/application config.
- **KSP over KAPT** wherever the annotation processor supports it (Room, Hilt 2.48+, Moshi, Dagger).
- **Config Cache + Build Cache** must stay green. Use `./gradlew :app:assembleDebug --configuration-cache` in CI.
- **Lint & Static Analysis:** Android Lint (`lintOptions { abortOnError true }`), detekt, ktlint via Spotless.

## 8. External Documentation
- Kotlin: <https://kotlinlang.org/docs/home.html>
- Android developer guides: <https://developer.android.com/guide>
- Jetpack Compose samples: <https://github.com/android/compose-samples>
- Now in Android reference app: <https://github.com/android/nowinandroid>

## 9. Useful Agent Skills Recap

| Skill Folder                  | Purpose                                                       |
| ----------------------------- | ------------------------------------------------------------- |
| `architecture/`               | Clean architecture, Compose state, offline-first data layer.  |
| `ui/`                         | Compose, navigation, images, a11y, animations, localization.  |
| `performance/`                | Recomposition profiling and Gradle build performance.         |
| `migration/`                  | Views → Compose, LiveData → Flow.                             |
| `testing_and_automation/`     | JUnit5/MockK/Turbine, Compose UI and screenshot tests.        |
| `concurrency_and_networking/` | Coroutines, Flow, Retrofit + kotlinx.serialization.           |
| `build_and_tooling/`          | Gradle Kotlin DSL, convention plugins, GitHub Actions CI.     |

---

Following these practices ensures that the agent-assisted development workflow stays reliable and consistent. When in doubt, always refer to the specific agent skills provided in `.github/skills/` for deeper task-specific context!

*Note to developers: Update this file whenever the project makes architectural shifts to ensure AI agents stay aligned with your conventions.*
