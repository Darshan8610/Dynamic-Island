---
name: android-lint-enforcement
description: >-
  Coaches writing custom Android Lint rules to enforce architectural boundaries and coding
  standards. Use when creating custom lint detectors, enforcing design system usage, enforcing
  test naming conventions, building an IssueRegistry, publishing lint rules via lintPublish,
  testing lint rules, or configuring Spotless/ktlint for code formatting in an Android project.
  Covers UAST scanning, quick-fixes, severity levels, and CI integration with SARIF reports.
license: Apache-2.0
compatibility: Requires Android Lint API (bundled with AGP)
metadata:
  author: luis-suchobits
  version: "1.0"
  based-on: google/nowinandroid
---

# Android Lint Enforcement — Custom Rules & Code Quality Guide

Patterns for writing custom lint rules that enforce architectural boundaries, coding standards, and design system usage in Android projects. Lint rules catch violations at build time — before code review.

## Why Custom Lint Rules

Static analysis catches what code review misses at scale:
- A developer uses `Button()` from Material instead of `{App}Button()` → lint flags it instantly.
- A test method uses `testFoo()` prefix instead of descriptive naming → auto-fix removes it.
- These rules run on every build, not just when a reviewer remembers to check.

## Lint Module Structure

```
lint/
├── build.gradle.kts
└── src/
    ├── main/
    │   ├── kotlin/com/example/lint/
    │   │   ├── {App}IssueRegistry.kt        # Registers all issues
    │   │   ├── designsystem/
    │   │   │   └── DesignSystemDetector.kt   # Enforces design system usage
    │   │   └── TestMethodNameDetector.kt     # Enforces test naming
    │   └── resources/META-INF/services/
    │       └── com.android.tools.lint.client.api.IssueRegistry
    └── test/
        └── kotlin/com/example/lint/
            ├── designsystem/
            │   └── DesignSystemDetectorTest.kt
            └── TestMethodNameDetectorTest.kt
```

### build.gradle.kts

```kotlin
plugins {
    alias(libs.plugins.kotlin.jvm)  // JVM-only, not Android
}

java {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
}

dependencies {
    compileOnly(libs.kotlin.stdlib)
    compileOnly(libs.lint.api)

    testImplementation(libs.kotlin.test)
    testImplementation(libs.lint.checks)
    testImplementation(libs.lint.tests)
}
```

## Issue Registry

The entry point that registers all custom issues:

```kotlin
class {App}IssueRegistry : IssueRegistry() {

    override val issues: List<Issue> = listOf(
        DesignSystemDetector.ISSUE,
        TestMethodNameDetector.PREFIX,
        TestMethodNameDetector.FORMAT,
    )

    override val api: Int = CURRENT_API
    override val minApi: Int = 12

    override val vendor: Vendor = Vendor(
        vendorName = "{App}",
        feedbackUrl = "https://github.com/{org}/{repo}/issues",
    )
}
```

Register via service provider in `META-INF/services/com.android.tools.lint.client.api.IssueRegistry`:

```
com.example.lint.{App}IssueRegistry
```

## Writing a Design System Detector

Prevents direct use of Material composables, forcing usage through design system wrappers:

```kotlin
class DesignSystemDetector : Detector(), Detector.UastScanner {

    override fun getApplicableMethodNames(): List<String> = METHOD_NAMES.keys.toList()

    override fun getApplicableReferenceNames(): List<String> = RECEIVER_NAMES.keys.toList()

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        val preferredName = METHOD_NAMES[node.methodName] ?: return
        if (isInDesignSystemModule(context)) return

        context.report(
            ISSUE,
            node,
            context.getLocation(node),
            "Using `${node.methodName}` directly. Use `$preferredName` instead.",
        )
    }

    override fun visitReference(
        context: JavaContext,
        ref: UReferenceExpression,
        referenced: PsiElement,
    ) {
        val preferredName = RECEIVER_NAMES[ref.resolvedName] ?: return
        if (isInDesignSystemModule(context)) return

        context.report(
            ISSUE,
            ref,
            context.getLocation(ref),
            "Using `${ref.resolvedName}` directly. Use `$preferredName` instead.",
        )
    }

    private fun isInDesignSystemModule(context: JavaContext): Boolean =
        context.project.name.contains("designsystem")

    companion object {
        val ISSUE = Issue.create(
            id = "DesignSystem",
            briefDescription = "Design system component required",
            explanation = "Use design system wrappers instead of raw Material components " +
                "to ensure consistent styling across the app.",
            category = Category.CUSTOM_LINT_CHECKS,
            priority = 7,
            severity = Severity.ERROR,
            implementation = Implementation(
                DesignSystemDetector::class.java,
                Scope.JAVA_FILE_SCOPE,
            ),
        )

        val METHOD_NAMES = mapOf(
            "MaterialTheme" to "{App}Theme",
            "Button" to "{App}Button",
            "OutlinedButton" to "{App}OutlinedButton",
            "TextButton" to "{App}TextButton",
            "FilterChip" to "{App}FilterChip",
            "NavigationBar" to "{App}NavigationBar",
            "NavigationBarItem" to "{App}NavigationBarItem",
            "NavigationRail" to "{App}NavigationRail",
            "NavigationRailItem" to "{App}NavigationRailItem",
            "TabRow" to "{App}TabRow",
            "Tab" to "{App}Tab",
            "IconToggleButton" to "{App}IconToggleButton",
            "CenterAlignedTopAppBar" to "{App}TopAppBar",
            "SmallTopAppBar" to "{App}TopAppBar",
            "MediumTopAppBar" to "{App}TopAppBar",
            "LargeTopAppBar" to "{App}TopAppBar",
        )

        val RECEIVER_NAMES = mapOf(
            "Icons" to "{App}Icons",
        )
    }
}
```

## Writing a Test Method Name Detector

Enforces naming conventions for test methods:

```kotlin
class TestMethodNameDetector : Detector(), Detector.UastScanner {

    override fun getApplicableUastTypes(): List<Class<out UElement>> =
        listOf(UAnnotation::class.java)

    override fun createUastHandler(context: JavaContext): UElementHandler =
        object : UElementHandler() {
            override fun visitAnnotation(node: UAnnotation) {
                if (node.qualifiedName != "org.junit.Test") return

                val method = node.uastParent as? UMethod ?: return
                val name = method.name

                // Rule 1: No "test" prefix (unit tests)
                if (name.startsWith("test")) {
                    val fixedName = name.removePrefix("test").removePrefix("_")
                        .replaceFirstChar { it.lowercaseChar() }
                    context.report(
                        PREFIX,
                        node,
                        context.getLocation(node),
                        "Test method should not start with `test`. Rename to `$fixedName`.",
                        fix().replace().name("Remove test prefix")
                            .text(name).with(fixedName).build(),
                    )
                }

                // Rule 2: BDD format for instrumentation tests
                if (isInAndroidTest(context) && !name.matches(BDD_PATTERN)) {
                    context.report(
                        FORMAT,
                        node,
                        context.getLocation(node),
                        "Instrumented test should use `given_when_then` or `when_then` format.",
                    )
                }
            }
        }

    private fun isInAndroidTest(context: JavaContext): Boolean =
        context.file.path.contains("androidTest")

    companion object {
        private val BDD_PATTERN = Regex("[^\\W_]+(_[^\\W_]+){1,2}")

        val PREFIX = Issue.create(
            id = "TestMethodPrefix",
            briefDescription = "Redundant test prefix",
            explanation = "JUnit @Test annotation already marks this as a test. " +
                "The `test` prefix adds noise.",
            category = Category.TESTING,
            priority = 5,
            severity = Severity.WARNING,
            implementation = Implementation(
                TestMethodNameDetector::class.java,
                Scope.JAVA_FILE_SCOPE,
            ),
        )

        val FORMAT = Issue.create(
            id = "TestMethodFormat",
            briefDescription = "Test naming format",
            explanation = "Instrumented tests should follow `when_then` or " +
                "`given_when_then` naming for readability.",
            category = Category.TESTING,
            priority = 5,
            severity = Severity.WARNING,
            implementation = Implementation(
                TestMethodNameDetector::class.java,
                Scope.JAVA_FILE_SCOPE,
            ),
        )
    }
}
```

## Publishing Lint Rules

Make custom rules available to all modules via the design system module:

```kotlin
// core/designsystem/build.gradle.kts
dependencies {
    lintPublish(projects.lint)
}
```

Any module that depends on `core:designsystem` (which is all feature modules) automatically gets the lint rules enforced.

## Lint Convention Plugin

Apply lint config to all Android modules:

```kotlin
class AndroidLintConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            when {
                pluginManager.hasPlugin("com.android.application") ->
                    configure<ApplicationExtension> { lint { configure() } }
                pluginManager.hasPlugin("com.android.library") ->
                    configure<LibraryExtension> { lint { configure() } }
                else -> {
                    pluginManager.apply("com.android.lint")
                    configure<LintExtension> { configure() }
                }
            }
        }
    }
}

private fun Lint.configure() {
    xmlReport = true
    sarifReport = true       // GitHub security integration
    checkDependencies = true
    disable += "GradleDependency"
}
```

For detailed testing and code formatting patterns, see the reference guides:

- [Testing Lint Rules](references/testing-lint-rules.md)
- [Code Formatting (Spotless + ktlint)](references/code-formatting.md)
