# Code Formatting (Spotless + ktlint)

## Spotless Setup

Spotless applies ktlint formatting and license headers consistently across the project.

### Plugin Application

```kotlin
// build-logic/convention — apply to all modules

fun Project.configureSpotless() {
    pluginManager.apply("com.diffplug.spotless")

    extensions.configure<SpotlessExtension> {
        kotlin {
            target("**/*.kt")
            targetExclude("**/build/**/*.kt")
            ktlint(libs.findVersion("ktlint").get().toString())
                .editorConfigOverride(mapOf("android" to "true"))
            licenseHeaderFile(rootProject.file("spotless/copyright.kt"))
        }
        kotlinGradle {
            target("**/*.kts")
            targetExclude("**/build/**/*.kts")
            ktlint(libs.findVersion("ktlint").get().toString())
            licenseHeaderFile(rootProject.file("spotless/copyright.kts"), "(^(?![\\/ ]\\*).*$)")
        }
    }
}

// For modules with XML resources
fun Project.configureSpotlessAndroid() {
    configureSpotless()
    extensions.configure<SpotlessExtension> {
        format("xml") {
            target("**/*.xml")
            targetExclude("**/build/**/*.xml")
            licenseHeaderFile(rootProject.file("spotless/copyright.xml"), "(<[^!?])")
        }
    }
}
```

### License Header Template

```kotlin
// spotless/copyright.kt
/*
 * Copyright {year} {organization}
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * ...
 */
```

## .editorconfig

Configure ktlint rules project-wide:

```ini
[*.{kt,kts}]
# Disable rules that conflict with project conventions
ktlint_standard_backing-property-naming = disabled
ktlint_standard_class-signature = disabled
ktlint_standard_function-signature = disabled

# Allow Composable and Test functions to break naming rules
ktlint_function_naming_ignore_when_annotated_with = Composable, Test

# Enable trailing commas
ij_kotlin_allow_trailing_comma = true
ij_kotlin_allow_trailing_comma_on_call_site = true
```

## Running Spotless

```bash
# Check formatting (CI — fails on violations)
./gradlew spotlessCheck

# Apply formatting (development — auto-fixes)
./gradlew spotlessApply
```

## CI Integration

```yaml
- name: Check formatting
  run: ./gradlew spotlessCheck
```

Run spotlessCheck before lint and tests — formatting issues are cheap to fix and shouldn't block CI on more expensive checks.

## Combining Lint + Spotless + Formatting

The full code quality pipeline:

```bash
# 1. Format check (fastest)
./gradlew spotlessCheck

# 2. Lint (custom rules + Android lint)
./gradlew :app:lintProdRelease :lint:lint

# 3. Custom lint rule tests
./gradlew :lint:test

# 4. Unit tests
./gradlew testDemoDebug

# 5. Screenshot verification
./gradlew verifyRoborazziDemoDebug
```

## SARIF Reports for GitHub

Enable SARIF output for GitHub security tab integration:

```kotlin
lint {
    sarifReport = true
}
```

Upload in CI:

```yaml
- name: Upload SARIF
  uses: github/codeql-action/upload-sarif@v3
  with:
    sarif_file: app/build/reports/lint-results-prodRelease.sarif
```

This shows lint violations directly in pull request annotations.
