# Awesome Kotlin Android Agent Skills

[![Agent Skills](https://img.shields.io/badge/Agent-Skills-blue?style=flat&logo=github)](https://agentskills.io)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.0+-7F52FF?style=flat&logo=kotlin)](https://kotlinlang.org)
[![Android](https://img.shields.io/badge/Android-minSdk%2024-3DDC84?style=flat&logo=android)](https://developer.android.com)
[![Jetpack Compose](https://img.shields.io/badge/Jetpack-Compose-4285F4?style=flat&logo=jetpackcompose)](https://developer.android.com/jetpack/compose)
[![License: MIT](https://img.shields.io/badge/Code-MIT-green?style=flat)](LICENSE-CODE)
[![License: CC BY 4.0](https://img.shields.io/badge/Docs-CC%20BY%204.0-lightgrey?style=flat)](LICENSE-DOCS)

A collection of **20 agent skills** that give AI coding assistants (GitHub Copilot, Claude, Gemini, Cursor, etc.) expert-level knowledge of modern Kotlin-first Android development.

Drop the `.github/skills/` folder into your project and your agent automatically follows your architecture, uses current best practices, and ships code that passes review.

> Learn more about the Agent Skills standard at [agentskills.io](https://agentskills.io).

---

## Why use this

- **Kotlin-first, Compose-first** -- skills assume Kotlin 2.0+ (K2), Jetpack Compose, Material 3, and coroutines/Flow as defaults.
- **Modern defaults** -- Gradle Kotlin DSL, version catalogs, Hilt, Retrofit + kotlinx.serialization, Room, DataStore.
- **Opinionated but swappable** -- each skill explains the preferred tool *and* the alternative (Hilt vs Koin, Paparazzi vs Roborazzi, KSP vs KAPT).
- **Actionable checklists** -- every skill ends with a verification checklist the agent can self-audit against.

---

## How it works

The `Agent.md` file is the foundation. Every AI agent reads it first to understand your project conventions. Individual skills layer on top for specific tasks.

```text
       ┌───────────────────────────────────────────┐
       │                  Agent.md                 │
       │     (read by all AI agents implicitly)    │
       └─────────────────────┬─────────────────────┘
                             │
     ┌───────────────┬───────┴───────┬───────────────┐
     ▼               ▼               ▼               ▼
┌──────────┐   ┌───────────┐   ┌────────────┐  ┌────────────┐
│ Architect│   │  UI & UX  │   │ Migration  │  │  Delivery  │
├──────────┤   ├───────────┤   ├────────────┤  ├────────────┤
│clean-arch│   │compose    │   │views→      │  │testing     │
│state-mgmt│   │navigation │   │  compose   │  │compose-ui  │
│data-layer│   │images     │   │livedata→   │  │gradle-dsl  │
│          │   │a11y       │   │  flow      │  │ci-cd       │
│          │   │animations │   │            │  │            │
│          │   │i18n       │   │            │  │            │
└──────────┘   └───────────┘   └────────────┘  └────────────┘
```

---

## Available Skills

All skills live in `.github/skills/<category>/<skill-name>/SKILL.md`.

### Architecture

| Skill | What it covers |
|-------|----------------|
| [Kotlin Android Architecture](.github/skills/architecture/kotlin-android-architecture/SKILL.md) | Clean Architecture, modularization (app/feature/core/data/domain), version catalogs |
| [Compose State Management](.github/skills/architecture/compose-state-management/SKILL.md) | ViewModel + StateFlow, remember/rememberSaveable, state hoisting, UDF |
| [Kotlin Data Layer](.github/skills/architecture/kotlin-data-layer/SKILL.md) | Repository pattern with Room + Retrofit, Flow-based observables, offline-first |

### UI

| Skill | What it covers |
|-------|----------------|
| [Jetpack Compose](.github/skills/ui/jetpack-compose/SKILL.md) | Composable patterns, recomposition, stability, Material 3, theming |
| [Compose Navigation](.github/skills/ui/compose-navigation/SKILL.md) | NavHost, typed routes with kotlinx.serialization, deep links |
| [Compose Images](.github/skills/ui/compose-images/SKILL.md) | Coil 3, placeholders, caching, memory tuning |
| [Compose Accessibility](.github/skills/ui/compose-accessibility/SKILL.md) | Semantics modifier, TalkBack, touch targets, contrast, large text |
| [Compose Animations](.github/skills/ui/compose-animations/SKILL.md) | animate*AsState, AnimatedVisibility, updateTransition, shared element |
| [Android Localization](.github/skills/ui/android-localization/SKILL.md) | res/values-*, plurals, ICU MessageFormat, RTL, per-app locale |

### Migration

| Skill | What it covers |
|-------|----------------|
| [Views to Compose](.github/skills/migration/views-to-compose/SKILL.md) | Interop with AndroidView/ComposeView, incremental migration |
| [LiveData to Flow](.github/skills/migration/livedata-to-flow/SKILL.md) | Migrating LiveData to StateFlow/SharedFlow, lifecycle-aware collection |

### Performance

| Skill | What it covers |
|-------|----------------|
| [Compose Performance](.github/skills/performance/compose-performance/SKILL.md) | Recomposition tracing, stable/immutable annotations, compiler metrics |
| [Kotlin Build Performance](.github/skills/performance/kotlin-build-performance/SKILL.md) | Gradle config cache, build cache, KSP vs KAPT, module graph |

### Concurrency & Networking

| Skill | What it covers |
|-------|----------------|
| [Kotlin Coroutines](.github/skills/concurrency_and_networking/kotlin-coroutines/SKILL.md) | Structured concurrency, scopes, supervisorScope, exception handling |
| [Kotlin Flow](.github/skills/concurrency_and_networking/kotlin-flow/SKILL.md) | Cold/hot flows, StateFlow/SharedFlow, operators, Turbine testing |
| [Retrofit + Kotlin](.github/skills/concurrency_and_networking/retrofit-kotlin/SKILL.md) | Retrofit + OkHttp + kotlinx.serialization, interceptors, auth, retries |

### Testing & Automation

| Skill | What it covers |
|-------|----------------|
| [Kotlin Testing](.github/skills/testing_and_automation/kotlin-testing/SKILL.md) | JUnit5, MockK, coroutines-test, Turbine, assertK/kotest |
| [Compose UI Testing](.github/skills/testing_and_automation/compose-ui-testing/SKILL.md) | createAndroidComposeRule, semantics tree, screenshot tests |

### Build & Tooling

| Skill | What it covers |
|-------|----------------|
| [Gradle Kotlin DSL](.github/skills/build_and_tooling/gradle-kotlin-dsl/SKILL.md) | build.gradle.kts patterns, convention plugins, version catalogs |
| [Kotlin Android CI](.github/skills/build_and_tooling/kotlin-android-ci/SKILL.md) | GitHub Actions, Gradle Remote Cache, signing, Play Publisher |

---

## Quick Start

1. **Copy** the `.github/skills/` folder and `Agent.md` to your project root.
2. **Verify** the structure:
   ```text
   my-android-project/
   ├── Agent.md
   ├── .github/
   │   └── skills/
   │       ├── architecture/
   │       │   ├── kotlin-android-architecture/
   │       │   │   └── SKILL.md
   │       │   └── ...
   │       └── ...
   ├── app/
   └── ...
   ```
3. **Reload** your editor (Copilot and similar extensions may need a window reload to index new skills).

Then just ask your agent naturally:

> "How should I structure the User Profile feature?"
> "Create a repository for fetching News with offline support."

The agent picks the right skill automatically.

---

## Other Environments

- **Claude**: copy or symlink `.github/skills` to `.claude/skills/`.
- **OpenCode**: supports `.opencode/skill/` and `.claude/skills/`. Global skills go in `~/.config/opencode/skill/`.

---

## Adding Custom Skills

Create a folder in `.github/skills/` with a `SKILL.md` containing the required frontmatter:

```markdown
---
name: my-custom-skill
description: What this skill does
---
# Instructions
...
```

---

## License

Dual-licensed:

- **Code** (snippets, configs, scripts) -- [MIT](LICENSE-CODE)
- **Documentation** (SKILL.md, README, Agent.md) -- [CC BY 4.0](LICENSE-DOCS)

See [LICENSE](LICENSE) for details.
