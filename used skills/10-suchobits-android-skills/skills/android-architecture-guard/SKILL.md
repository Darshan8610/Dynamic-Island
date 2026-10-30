---
name: android-architecture-guard
description: >-
  Enforces Android multi-module architecture rules at build time so violations are unbuildable.
  Use when setting up architectural enforcement, preventing cross-feature dependencies,
  validating module placement, enforcing API/impl splits, wiring architecture checks into
  Gradle builds, pre-commit hooks, or CI pipelines. Covers Gradle validation tasks, dependency
  direction rules, module naming guards, and structure auditing for both human developers
  and AI agents.
license: Apache-2.0
compatibility: Requires Gradle 8+, Kotlin, multi-module Android project
metadata:
  author: luis-suchobits
  version: "1.0"
  based-on: google/nowinandroid
---

# Android Architecture Guard — Automated Enforcement

Makes architectural violations unbuildable. Instead of relying on code review, documentation, or discipline, these checks run automatically at build time, commit time, and CI — catching violations from humans and AI agents alike.

## Enforcement Philosophy

Rules people must remember to follow will be broken. Rules the toolchain enforces cannot be.

```
Developer (human or AI)
    ↓ writes code
Pre-commit hook  ← fast structural checks (< 2 seconds)
    ↓ commits
Gradle build     ← full architecture validation (part of `check` task)
    ↓ pushes
CI pipeline      ← final safety net, blocks merge
```

Each layer catches what the previous one missed. The Gradle task is the primary enforcer because **nobody ships code without building it**.

## What Gets Enforced

| Rule | Enforced By | Severity |
|------|-------------|----------|
| Feature modules live under `feature/` | Gradle task | Build failure |
| Feature modules have api + impl pair | Gradle task | Build failure |
| No feature-to-feature impl dependencies | Gradle task | Build failure |
| `core:model` has no project dependencies | Gradle task | Build failure |
| Module path matches convention plugin type | Gradle task | Build failure |
| Design system components used over Material | Custom lint (ERROR) | Build failure |
| Test naming conventions | Custom lint (WARNING) | Warning |
| Resource prefix matches module path | Convention plugin | Automatic |
| Code formatting | Spotless/ktlint | Build failure |
| No `impl` module in non-app dependencies | Gradle task | Build failure |

## Layer 1: Gradle Build Task (Primary Enforcer)

This is the core. A custom Gradle task that validates the entire project structure and dependency graph at configuration time. Wire it into the `check` lifecycle so `./gradlew build` fails on violations.

### Installation

Create the task in `build-logic/convention/`:

```kotlin
// ArchitectureGuardPlugin.kt

class ArchitectureGuardPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        check(target == target.rootProject) {
            "ArchitectureGuardPlugin must be applied to the root project only."
        }

        target.tasks.register("architectureGuard") {
            group = "verification"
            description = "Validates module structure and dependency rules."
            doLast {
                val violations = mutableListOf<String>()
                val subprojects = target.subprojects

                subprojects.forEach { project ->
                    violations += validateModulePlacement(project)
                    violations += validateDependencyDirection(project)
                    violations += validateApiImplPairing(project, subprojects)
                    violations += validateModelLeafNode(project)
                    violations += validateNoImplLeaks(project)
                }

                if (violations.isNotEmpty()) {
                    val report = violations.joinToString("\n") { "  ✗ $it" }
                    throw GradleException(
                        "Architecture violations found:\n$report\n\n" +
                        "Fix these violations before building. " +
                        "See: skills/android-architecture-guard/SKILL.md"
                    )
                }
            }
        }

        // Wire into the check lifecycle — makes it mandatory
        target.tasks.named("check") {
            dependsOn("architectureGuard")
        }
    }
}
```

Register in `build-logic/convention/build.gradle.kts`:

```kotlin
register("architectureGuard") {
    id = "{appId}.architecture.guard"
    implementationClass = "ArchitectureGuardPlugin"
}
```

Apply in the root `build.gradle.kts`:

```kotlin
plugins {
    alias(libs.plugins.{appId}.architecture.guard)
}
```

### Validation Rules

For the complete implementation of each validation function, see the [validation rules reference](references/validation-rules.md).

Summary of what each checks:

**`validateModulePlacement`** — Modules applying feature plugins must live under `feature/`. Core plugins must live under `core/`. Anything else is flagged.

**`validateDependencyDirection`** — Feature impl can depend on core and its own api. Feature api can depend on core:model and core:navigation only. Feature cannot depend on another feature's impl.

**`validateApiImplPairing`** — Every `feature:X:impl` must have a corresponding `feature:X:api` and vice versa. Orphaned halves are flagged.

**`validateModelLeafNode`** — `core:model` must have zero project dependencies. It's the leaf of the dependency tree.

**`validateNoImplLeaks`** — Only the `app` module may depend on `feature:*:impl` modules. If `core:data` depends on `feature:search:impl`, that's a violation.

## Layer 2: Pre-Commit Hook (Fast Feedback)

The Gradle task runs during a full build. For instant feedback at commit time, add a lightweight shell script that catches the most common mistakes in under 2 seconds.

### Installation

```bash
# Copy the hook
cp skills/android-architecture-guard/scripts/pre-commit .git/hooks/pre-commit
chmod +x .git/hooks/pre-commit
```

Or use a hook manager like `lefthook` or `husky`:

```yaml
# .lefthook.yml
pre-commit:
  commands:
    architecture-check:
      run: bash skills/android-architecture-guard/scripts/pre-commit
```

The hook checks:
- New modules are in the correct directory
- `settings.gradle.kts` entries match directory structure
- No `implementation(projects.feature.*.impl)` outside of `app/`

See [scripts/pre-commit](scripts/pre-commit) for the implementation.

## Layer 3: CI Pipeline (Final Safety Net)

Add the architecture check as a dedicated CI step that runs before tests:

```yaml
# .github/workflows/build.yml

- name: Architecture guard
  run: ./gradlew architectureGuard

- name: Check formatting
  run: ./gradlew spotlessCheck

- name: Lint
  run: ./gradlew :app:lintProdRelease

- name: Tests
  run: ./gradlew testDemoDebug
```

The architecture guard runs first because it's the fastest check and produces the clearest error messages. No point running 10 minutes of tests if a module is in the wrong directory.

## Handling Violations

When a developer (human or AI) hits a violation:

### "I added a feature module but it's not under `feature/`"

```
✗ Module :screens:home applies feature-impl plugin but is not under feature/
  Expected: feature/home/impl
  Actual:   screens/home
```

**Fix:** Move the module to `feature/home/impl/` and update `settings.gradle.kts`.

### "My feature depends on another feature's impl"

```
✗ Module :feature:search:impl depends on :feature:profile:impl
  Features must not depend on other features' implementations.
  Depend on :feature:profile:api instead, or move shared logic to a core module.
```

**Fix:** Depend on the api module for navigation, or extract shared logic to `core:domain`.

### "core:model has a project dependency"

```
✗ Module :core:model depends on :core:common
  core:model must be a leaf node with zero project dependencies.
```

**Fix:** Move the shared code to `core:common` and have consumers depend on both.

### Suppressing a Rule (Escape Hatch)

Sometimes a rule needs to be temporarily broken during a migration. Use a tracked suppression:

```kotlin
// In the module's build.gradle.kts
extra["architectureGuard.suppress"] = listOf(
    "dependency-direction",  // Temporary: migrating search to api/impl split — tracked in JIRA-1234
)
```

The Gradle task reads this and skips the named check for that module. The suppression is visible in code review, tracked in git history, and can be audited:

```bash
# Find all active suppressions
grep -r "architectureGuard.suppress" --include="*.kts" .
```

## Custom Rules

Add project-specific rules by extending the validation:

```kotlin
// Example: enforce that all database entities have an @Entity annotation test
fun validateEntityTests(project: Project): List<String> {
    if (!project.path.contains("database")) return emptyList()
    val entityFiles = project.fileTree("src/main") { include("**/*Entity.kt") }.files
    val testFiles = project.fileTree("src/androidTest") { include("**/*DaoTest.kt") }.files

    val untestedEntities = entityFiles.map { it.nameWithoutExtension.removeSuffix("Entity") }
        .filter { name -> testFiles.none { it.name.contains(name) } }

    return untestedEntities.map {
        "${project.path}: Entity ${it}Entity has no corresponding DAO test"
    }
}
```

## Complementary Enforcement

This skill works alongside:

- **[android-lint-enforcement](../android-lint-enforcement/SKILL.md)** — Source-level checks (design system usage, test naming). Lint catches what the Gradle task can't see (import statements, API usage in source code).
- **[android-architect](../android-architect/SKILL.md)** — Teaches the patterns this skill enforces. Architecture Guard is the stick; Architect is the carrot.
- **[android-modularizer](../android-modularizer/SKILL.md)** — Guides module creation. Architecture Guard validates the result.
