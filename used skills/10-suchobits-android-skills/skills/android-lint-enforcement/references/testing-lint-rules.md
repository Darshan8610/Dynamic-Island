# Testing Lint Rules

## Test Setup

Lint tests run as JVM unit tests using the `lint-tests` library:

```kotlin
dependencies {
    testImplementation(libs.lint.tests)
    testImplementation(libs.lint.checks)
    testImplementation(libs.kotlin.test)
}
```

## Testing a Detector

### Design System Detector Test

```kotlin
class DesignSystemDetectorTest {

    @Test
    fun `reports error when using Button directly`() {
        lint().files(
            kotlin(
                """
                package com.example.feature

                import androidx.compose.material3.Button
                import androidx.compose.runtime.Composable

                @Composable
                fun MyScreen() {
                    Button(onClick = {}) { }
                }
                """
            ).indented(),
            COMPOSE_STUBS,
            MATERIAL3_STUBS,
        )
            .issues(DesignSystemDetector.ISSUE)
            .run()
            .expect(
                """
                src/com/example/feature/test.kt:8: Error: Using Button directly. Use {App}Button instead. [DesignSystem]
                    Button(onClick = {}) { }
                    ~~~~~~
                1 errors, 0 warnings
                """.trimIndent()
            )
    }

    @Test
    fun `allows Button inside designsystem module`() {
        lint().files(
            kotlin(
                """
                package com.example.core.designsystem

                import androidx.compose.material3.Button
                import androidx.compose.runtime.Composable

                @Composable
                fun {App}Button(onClick: () -> Unit) {
                    Button(onClick = onClick) { }
                }
                """
            ).indented(),
            COMPOSE_STUBS,
            MATERIAL3_STUBS,
        )
            .issues(DesignSystemDetector.ISSUE)
            .run()
            .expectClean()
    }

    @Test
    fun `reports error when using Icons receiver`() {
        lint().files(
            kotlin(
                """
                package com.example.feature

                import androidx.compose.material.icons.Icons
                import androidx.compose.runtime.Composable

                @Composable
                fun MyIcon() {
                    val icon = Icons.Default.Home
                }
                """
            ).indented(),
            COMPOSE_STUBS,
            ICONS_STUBS,
        )
            .issues(DesignSystemDetector.ISSUE)
            .run()
            .expectErrorCount(1)
    }
}
```

### Test Method Name Detector Test

```kotlin
class TestMethodNameDetectorTest {

    @Test
    fun `warns on test prefix`() {
        lint().files(
            kotlin(
                """
                package com.example

                import org.junit.Test

                class MyTest {
                    @Test
                    fun testSomething() { }
                }
                """
            ).indented(),
            JUNIT_STUBS,
        )
            .issues(TestMethodNameDetector.PREFIX)
            .run()
            .expect(
                """
                src/com/example/test.kt:6: Warning: Test method should not start with test. Rename to something. [TestMethodPrefix]
                    @Test
                    ~~~~~
                0 errors, 1 warnings
                """.trimIndent()
            )
            .expectFixDiffs(
                """
                Fix for src/com/example/test.kt line 6: Remove test prefix:
                @@ -7 +7
                -     fun testSomething() { }
                +     fun something() { }
                """.trimIndent()
            )
    }

    @Test
    fun `allows descriptive names without test prefix`() {
        lint().files(
            kotlin(
                """
                package com.example

                import org.junit.Test

                class MyTest {
                    @Test
                    fun `emits Success when data loads`() { }
                }
                """
            ).indented(),
            JUNIT_STUBS,
        )
            .issues(TestMethodNameDetector.PREFIX)
            .run()
            .expectClean()
    }
}
```

## Creating Test Stubs

Lint tests need stubs for external APIs your detector references:

```kotlin
val COMPOSE_STUBS = kotlin(
    """
    package androidx.compose.runtime

    annotation class Composable
    """
).indented()

val MATERIAL3_STUBS = kotlin(
    """
    package androidx.compose.material3

    import androidx.compose.runtime.Composable

    @Composable fun Button(onClick: () -> Unit, content: @Composable () -> Unit) { }
    @Composable fun MaterialTheme(content: @Composable () -> Unit) { }
    @Composable fun FilterChip(selected: Boolean, onClick: () -> Unit, label: @Composable () -> Unit) { }
    """
).indented()

val JUNIT_STUBS = kotlin(
    """
    package org.junit

    annotation class Test
    """
).indented()
```

## Issue Severity Levels

| Severity | Behavior | Use For |
|----------|----------|---------|
| `ERROR` | Fails the build | Architectural violations (design system bypass) |
| `WARNING` | Reports but doesn't fail | Style issues (test naming) |
| `INFORMATIONAL` | Shows in IDE only | Suggestions |
| `IGNORE` | Suppressed | Disabled rules |

## Quick-Fix API

Provide auto-fixes when possible:

```kotlin
context.report(
    issue = PREFIX,
    scope = node,
    location = context.getLocation(node),
    message = "Remove test prefix",
    quickfixData = fix()
        .replace()
        .name("Remove test prefix")
        .text("testSomething")
        .with("something")
        .build(),
)
```

For group fixes:

```kotlin
fix().alternatives(
    fix().replace().name("Use {App}Button").text("Button").with("{App}Button").build(),
    fix().replace().name("Suppress").text("Button").with("@SuppressLint(\"DesignSystem\") Button").build(),
)
```

## Running Lint Rule Tests

```bash
./gradlew :lint:test
```

## Anti-Patterns

| Don't | Do Instead |
|-------|------------|
| Skip testing lint rules | Test every rule with positive and negative cases |
| Use `Severity.ERROR` for style preferences | Use `WARNING` for style; `ERROR` for architectural rules |
| Hardcode file paths in tests | Use `lint().files(kotlin(...))` API |
| Forget to test the "allowed" case | Verify the detector doesn't flag valid usage |
| Write detectors without quick-fixes | Provide auto-fixes when the correction is unambiguous |
