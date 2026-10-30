---
name: android-build-logic
description: >-
  Coaches Gradle convention plugins, version catalogs, build variants, and build performance
  for multi-module Android projects. Use when creating convention plugins, configuring
  build-logic, setting up libs.versions.toml, managing build flavors, optimizing Gradle
  build speed, configuring KSP, or structuring an Android project's build system.
  Covers included builds, plugin registration, and shared Kotlin/Compose/Hilt/Room config.
license: Apache-2.0
compatibility: Requires Gradle 8+, Kotlin, Android Gradle Plugin
metadata:
  author: luis-suchobits
  version: "1.0"
  based-on: google/nowinandroid
---

# Android Build Logic — Convention Plugins & Gradle Guide

Patterns for structuring Gradle build configuration in multi-module Android projects using convention plugins, version catalogs, and build variants. All examples use `{appId}` as the plugin namespace placeholder.

## Why Convention Plugins

Every Android module needs SDK versions, Kotlin config, Compose setup, lint rules. Without convention plugins, this configuration is duplicated across 20+ `build.gradle.kts` files. Convention plugins centralize it so each module's build file is 5-10 lines.

## Directory Structure

```
build-logic/
└── convention/
    ├── build.gradle.kts
    └── src/main/kotlin/
        ├── AndroidApplicationConventionPlugin.kt
        ├── AndroidLibraryConventionPlugin.kt
        ├── AndroidFeatureImplConventionPlugin.kt
        ├── AndroidFeatureApiConventionPlugin.kt
        ├── AndroidLibraryComposeConventionPlugin.kt
        ├── AndroidRoomConventionPlugin.kt
        ├── HiltConventionPlugin.kt
        ├── JvmLibraryConventionPlugin.kt
        ├── AndroidTestConventionPlugin.kt
        ├── AndroidLintConventionPlugin.kt
        └── helpers/
            ├── KotlinAndroid.kt
            └── ComposeConfig.kt
```

Register as an included build in the root `settings.gradle.kts`:

```kotlin
pluginManagement {
    includeBuild("build-logic")
}
```

## Plugin Catalog

| Plugin ID | Applied To | What It Configures |
|-----------|-----------|-------------------|
| `{appId}.android.application` | `app/` | SDK targets, lint, dependency guard, managed devices |
| `{appId}.android.library` | All `core:*` modules | SDK targets, resource prefixing by module path |
| `{appId}.android.feature.impl` | `feature:*/impl` | Auto-applies Hilt, lifecycle, Compose, navigation deps |
| `{appId}.android.feature.api` | `feature:*/api` | Minimal — serialization plugin, navigation + model deps |
| `{appId}.android.library.compose` | Any module with Compose UI | Compose compiler plugin + buildFeatures |
| `{appId}.android.room` | `core:database` | Room plugin, KSP, schema directory |
| `{appId}.hilt` | Any module with DI | Hilt plugin, KSP, hilt-android + hilt-compiler |
| `{appId}.jvm.library` | Pure Kotlin modules | Kotlin JVM plugin, Java target |
| `{appId}.android.test` | `benchmarks/` | Android test plugin, managed devices |
| `{appId}.android.lint` | All Android modules | XML + SARIF reports, checkDependencies |

## Creating a Convention Plugin

### 1. Implement the Plugin

```kotlin
class AndroidLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            with(pluginManager) {
                apply("com.android.library")
                apply("org.jetbrains.kotlin.android")
            }
            extensions.configure<LibraryExtension> {
                configureKotlinAndroid(this)
                defaultConfig.targetSdk = 36
                resourcePrefix = path.drop(1)
                    .replace(':', '_')
                    .replace('-', '_') + "_"
            }
        }
    }
}
```

### 2. Register It

In `build-logic/convention/build.gradle.kts`:

```kotlin
gradlePlugin {
    plugins {
        register("androidLibrary") {
            id = "{appId}.android.library"
            implementationClass = "AndroidLibraryConventionPlugin"
        }
    }
}
```

### 3. Use It

In any module's `build.gradle.kts`:

```kotlin
plugins {
    alias(libs.plugins.{appId}.android.library)
}
android { namespace = "com.example.core.data" }
```

## Shared Configuration Helpers

Extract repeated logic into extension functions:

```kotlin
// helpers/KotlinAndroid.kt
internal fun Project.configureKotlinAndroid(
    extension: CommonExtension<*, *, *, *, *, *>,
) {
    extension.apply {
        compileSdk = 36
        defaultConfig {
            minSdk = 26
            testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        }
        compileOptions {
            sourceCompatibility = JavaVersion.VERSION_17
            targetCompatibility = JavaVersion.VERSION_17
            isCoreLibraryDesugaringEnabled = true
        }
    }
    dependencies {
        add("coreLibraryDesugaring", libs.findLibrary("android.desugarJdkLibs").get())
    }
    configureKotlin()
}
```

## Plugin Composition

Plugins compose — a feature impl plugin applies library + compose + hilt:

```kotlin
class AndroidFeatureImplConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply {
                apply("{appId}.android.library")
                apply("{appId}.android.library.compose")
                apply("{appId}.hilt")
            }
            dependencies {
                add("implementation", project(":core:ui"))
                add("implementation", project(":core:designsystem"))
                add("implementation", project(":core:navigation"))
                add("implementation", project(":core:model"))
                add("implementation", libs.findLibrary("androidx.lifecycle.runtimeCompose").get())
                add("implementation", libs.findLibrary("androidx.lifecycle.viewModelCompose").get())
                add("implementation", libs.findLibrary("androidx.hilt.navigation.compose").get())
                add("testImplementation", project(":core:testing"))
            }
        }
    }
}
```

## Version Catalog

For detailed version catalog setup and dependency management, see the [version catalog reference](references/version-catalog.md).

## Build Variants & Flavors

For build flavor configuration (demo/prod), see the [build variants reference](references/build-variants.md).

## Build Performance

For Gradle optimization settings, see the [performance reference](references/performance.md).
