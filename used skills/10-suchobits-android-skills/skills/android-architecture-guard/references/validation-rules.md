# Validation Rules — Complete Implementation

## Validation Functions

Each function returns a list of violation strings. Empty list = pass.

### Module Placement

Ensures modules live in the directory their plugin type implies:

```kotlin
fun validateModulePlacement(project: Project): List<String> {
    val violations = mutableListOf<String>()
    val path = project.path  // e.g., ":feature:home:impl"
    val plugins = project.plugins

    val isFeatureImpl = plugins.hasPlugin("{appId}.android.feature.impl")
    val isFeatureApi = plugins.hasPlugin("{appId}.android.feature.api")
    val isCoreModule = plugins.hasPlugin("{appId}.android.library")
        && !isFeatureImpl && !isFeatureApi
        && !plugins.hasPlugin("{appId}.android.application")

    // Feature modules must live under :feature:*
    if (isFeatureImpl && !path.startsWith(":feature:")) {
        violations += "Module $path applies feature-impl plugin but is not under :feature:. " +
            "Move to :feature:${project.name.removeSuffix("-impl")}:impl"
    }
    if (isFeatureApi && !path.startsWith(":feature:")) {
        violations += "Module $path applies feature-api plugin but is not under :feature:. " +
            "Move to :feature:${project.name.removeSuffix("-api")}:api"
    }

    // Feature impl must end with :impl
    if (isFeatureImpl && !path.endsWith(":impl")) {
        violations += "Module $path applies feature-impl plugin but path doesn't end with :impl."
    }

    // Feature api must end with :api
    if (isFeatureApi && !path.endsWith(":api")) {
        violations += "Module $path applies feature-api plugin but path doesn't end with :api."
    }

    // Core modules should live under :core:*
    if (isCoreModule && path.startsWith(":feature:")) {
        violations += "Module $path applies library plugin (not feature). " +
            "It should be under :core:, not :feature:."
    }

    return violations
}
```

### Dependency Direction

Enforces the dependency DAG:

```kotlin
fun validateDependencyDirection(project: Project): List<String> {
    val violations = mutableListOf<String>()
    val path = project.path

    val implDeps = project.configurations
        .filter { it.name == "implementation" || it.name == "api" }
        .flatMap { it.dependencies }
        .filterIsInstance<ProjectDependency>()
        .map { it.dependencyProject.path }

    // Rule: Feature impl cannot depend on another feature's impl
    if (path.matches(Regex(":feature:[^:]+:impl"))) {
        val featureName = path.split(":")[2]
        implDeps.forEach { dep ->
            if (dep.matches(Regex(":feature:[^:]+:impl")) && !dep.contains(":$featureName:")) {
                violations += "Module $path depends on $dep. " +
                    "Features must not depend on other features' implementations. " +
                    "Depend on ${dep.replace(":impl", ":api")} instead, " +
                    "or move shared logic to a core module."
            }
        }
    }

    // Rule: Feature api should only depend on core:model, core:navigation, and minimal core modules
    if (path.matches(Regex(":feature:[^:]+:api"))) {
        val allowedApiDeps = setOf(":core:model", ":core:navigation", ":core:common")
        implDeps.forEach { dep ->
            if (dep.startsWith(":feature:")) {
                violations += "Module $path (api) depends on $dep. " +
                    "Feature api modules must not depend on other feature modules."
            }
            if (dep.startsWith(":core:") && dep !in allowedApiDeps) {
                violations += "Module $path (api) depends on $dep. " +
                    "Feature api modules should only depend on: ${allowedApiDeps.joinToString()}. " +
                    "Keep the api surface minimal."
            }
        }
    }

    // Rule: Core modules cannot depend on feature modules
    if (path.startsWith(":core:")) {
        implDeps.forEach { dep ->
            if (dep.startsWith(":feature:")) {
                violations += "Module $path (core) depends on $dep (feature). " +
                    "Core modules must never depend on feature modules. " +
                    "Move shared types to core:model or core:common."
            }
        }
    }

    return violations
}
```

### API / Impl Pairing

Every feature must have both halves:

```kotlin
fun validateApiImplPairing(project: Project, allProjects: Set<Project>): List<String> {
    val violations = mutableListOf<String>()
    val path = project.path

    if (path.matches(Regex(":feature:[^:]+:impl"))) {
        val apiPath = path.replace(":impl", ":api")
        if (allProjects.none { it.path == apiPath }) {
            violations += "Module $path exists but $apiPath does not. " +
                "Every feature impl must have a corresponding api module."
        }
    }

    if (path.matches(Regex(":feature:[^:]+:api"))) {
        val implPath = path.replace(":api", ":impl")
        if (allProjects.none { it.path == implPath }) {
            violations += "Module $path exists but $implPath does not. " +
                "Every feature api must have a corresponding impl module. " +
                "If the feature is in progress, create an empty impl module."
        }
    }

    return violations
}
```

### Model Leaf Node

`core:model` is the bottom of the dependency tree:

```kotlin
fun validateModelLeafNode(project: Project): List<String> {
    if (project.path != ":core:model") return emptyList()

    val projectDeps = project.configurations
        .filter { it.name == "implementation" || it.name == "api" }
        .flatMap { it.dependencies }
        .filterIsInstance<ProjectDependency>()

    return projectDeps.map { dep ->
        "Module :core:model depends on ${dep.dependencyProject.path}. " +
            "core:model must have zero project dependencies — it is the leaf of the dependency tree. " +
            "Move shared utilities to :core:common."
    }
}
```

### No Impl Leaks

Only the `app` module should depend on feature impl modules:

```kotlin
fun validateNoImplLeaks(project: Project): List<String> {
    if (project.path == ":app") return emptyList()  // app is allowed

    val violations = mutableListOf<String>()

    val implDeps = project.configurations
        .filter { it.name == "implementation" || it.name == "api" }
        .flatMap { it.dependencies }
        .filterIsInstance<ProjectDependency>()
        .filter { it.dependencyProject.path.matches(Regex(":feature:[^:]+:impl")) }

    implDeps.forEach { dep ->
        violations += "Module ${project.path} depends on ${dep.dependencyProject.path}. " +
            "Only :app may depend on feature impl modules. " +
            "Use the api module for navigation, or move shared logic to a core module."
    }

    return violations
}
```

## Suppression Mechanism

Allow tracked, visible suppressions for migrations:

```kotlin
fun shouldSkip(project: Project, ruleName: String): Boolean {
    val suppressions = project.extensions.extraProperties
        .takeIf { it.has("architectureGuard.suppress") }
        ?.get("architectureGuard.suppress") as? List<*>
        ?: return false
    return ruleName in suppressions.map { it.toString() }
}

// Usage in each validation function:
fun validateModulePlacement(project: Project): List<String> {
    if (shouldSkip(project, "module-placement")) return emptyList()
    // ... rest of validation
}
```

## Complete Plugin with All Rules

```kotlin
class ArchitectureGuardPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        check(target == target.rootProject) {
            "ArchitectureGuardPlugin must be applied to the root project only."
        }

        target.gradle.projectsEvaluated {
            target.tasks.register("architectureGuard") {
                group = "verification"
                description = "Validates module structure and dependency rules."
                doLast {
                    val violations = mutableListOf<String>()
                    val allProjects = target.subprojects

                    allProjects.forEach { project ->
                        violations += validateModulePlacement(project)
                        violations += validateDependencyDirection(project)
                        violations += validateApiImplPairing(project, allProjects)
                        violations += validateModelLeafNode(project)
                        violations += validateNoImplLeaks(project)
                    }

                    if (violations.isNotEmpty()) {
                        val report = buildString {
                            appendLine("Architecture Guard: ${violations.size} violation(s) found:")
                            appendLine()
                            violations.forEachIndexed { i, v ->
                                appendLine("  ${i + 1}. $v")
                            }
                            appendLine()
                            appendLine("Fix these violations before building.")
                            appendLine("Run `./gradlew architectureGuard` to re-check.")

                            // Report active suppressions as warnings
                            val suppressions = allProjects.filter {
                                it.extensions.extraProperties.has("architectureGuard.suppress")
                            }
                            if (suppressions.isNotEmpty()) {
                                appendLine()
                                appendLine("Active suppressions (review regularly):")
                                suppressions.forEach { p ->
                                    val rules = p.extensions.extraProperties
                                        .get("architectureGuard.suppress")
                                    appendLine("  ⚠ ${p.path}: $rules")
                                }
                            }
                        }
                        throw GradleException(report)
                    } else {
                        logger.lifecycle("Architecture Guard: all checks passed ✓")
                    }
                }
            }

            // Wire into check lifecycle
            target.tasks.matching { it.name == "check" }.configureEach {
                dependsOn("architectureGuard")
            }
        }
    }
}
```

## Adding Custom Rules

To add a project-specific rule, create a new validation function and add it to the violations list:

```kotlin
// Example: all feature modules must have at least one test file
fun validateFeatureHasTests(project: Project): List<String> {
    if (!project.path.matches(Regex(":feature:[^:]+:impl"))) return emptyList()
    if (shouldSkip(project, "feature-has-tests")) return emptyList()

    val testDir = project.file("src/test/kotlin")
    val androidTestDir = project.file("src/androidTest/kotlin")

    if (!testDir.exists() && !androidTestDir.exists()) {
        return listOf(
            "Module ${project.path} has no test directory. " +
            "Every feature must have at least unit tests for its ViewModel."
        )
    }
    return emptyList()
}

// Example: enforce namespace convention
fun validateNamespace(project: Project): List<String> {
    if (shouldSkip(project, "namespace")) return emptyList()

    val android = project.extensions.findByType(LibraryExtension::class.java) ?: return emptyList()
    val expectedPrefix = "com.example."
    val namespace = android.namespace ?: return listOf("${project.path} has no namespace set.")

    if (!namespace.startsWith(expectedPrefix)) {
        return listOf(
            "Module ${project.path} namespace is '$namespace'. " +
            "Expected prefix: '$expectedPrefix'."
        )
    }
    return emptyList()
}
```
