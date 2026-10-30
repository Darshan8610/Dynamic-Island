# Version Catalog (libs.versions.toml)

## File Location

`gradle/libs.versions.toml` — Gradle's built-in dependency management format.

## Structure

```toml
[versions]
kotlin = "2.1.0"
androidGradlePlugin = "8.8.0"
composeBom = "2024.12.01"
hilt = "2.53.1"
room = "2.7.0"
ksp = "2.1.0-1.0.29"
coroutines = "1.10.1"
retrofit = "2.11.0"
kotlinxSerialization = "1.7.3"
kotlinxDatetime = "0.6.1"
coil = "2.7.0"
roborazzi = "1.56.0"
ktlint = "1.4.0"

[libraries]
# Gradle plugins (used in build-logic)
android-gradlePlugin = { group = "com.android.tools.build", name = "gradle", version.ref = "androidGradlePlugin" }
kotlin-gradlePlugin = { group = "org.jetbrains.kotlin", name = "kotlin-gradle-plugin", version.ref = "kotlin" }
compose-gradlePlugin = { group = "org.jetbrains.kotlin", name = "compose-compiler-gradle-plugin", version.ref = "kotlin" }
ksp-gradlePlugin = { group = "com.google.devtools.ksp", name = "com.google.devtools.ksp.gradle.plugin", version.ref = "ksp" }
room-gradlePlugin = { group = "androidx.room", name = "room-gradle-plugin", version.ref = "room" }

# AndroidX Compose
androidx-compose-bom = { group = "androidx.compose", name = "compose-bom", version.ref = "composeBom" }
androidx-compose-ui = { group = "androidx.compose.ui", name = "ui" }
androidx-compose-material3 = { group = "androidx.compose.material3", name = "material3" }
androidx-compose-ui-tooling-preview = { group = "androidx.compose.ui", name = "ui-tooling-preview" }
androidx-compose-ui-test = { group = "androidx.compose.ui", name = "ui-test-junit4" }
androidx-compose-ui-testManifest = { group = "androidx.compose.ui", name = "ui-test-manifest" }

# AndroidX Lifecycle
androidx-lifecycle-runtimeCompose = { group = "androidx.lifecycle", name = "lifecycle-runtime-compose", version.ref = "lifecycle" }
androidx-lifecycle-viewModelCompose = { group = "androidx.lifecycle", name = "lifecycle-viewmodel-compose", version.ref = "lifecycle" }

# Hilt
hilt-android = { group = "com.google.dagger", name = "hilt-android", version.ref = "hilt" }
hilt-compiler = { group = "com.google.dagger", name = "hilt-compiler", version.ref = "hilt" }
hilt-android-testing = { group = "com.google.dagger", name = "hilt-android-testing", version.ref = "hilt" }
androidx-hilt-navigation-compose = { group = "androidx.hilt", name = "hilt-navigation-compose", version.ref = "hiltNavigationCompose" }

# Room
room-runtime = { group = "androidx.room", name = "room-runtime", version.ref = "room" }
room-compiler = { group = "androidx.room", name = "room-compiler", version.ref = "room" }
room-ktx = { group = "androidx.room", name = "room-ktx", version.ref = "room" }

# Network
retrofit = { group = "com.squareup.retrofit2", name = "retrofit", version.ref = "retrofit" }
kotlinx-serialization-json = { group = "org.jetbrains.kotlinx", name = "kotlinx-serialization-json", version.ref = "kotlinxSerialization" }
okhttp = { group = "com.squareup.okhttp3", name = "okhttp", version.ref = "okhttp" }

# Coroutines
kotlinx-coroutines-android = { group = "org.jetbrains.kotlinx", name = "kotlinx-coroutines-android", version.ref = "coroutines" }
kotlinx-coroutines-test = { group = "org.jetbrains.kotlinx", name = "kotlinx-coroutines-test", version.ref = "coroutines" }

# Testing
kotlin-test = { group = "org.jetbrains.kotlin", name = "kotlin-test", version.ref = "kotlin" }

# Lint
lint-api = { group = "com.android.tools.lint", name = "lint-api", version.ref = "androidGradlePlugin" }
lint-checks = { group = "com.android.tools.lint", name = "lint-checks", version.ref = "androidGradlePlugin" }
lint-tests = { group = "com.android.tools.lint", name = "lint-tests", version.ref = "androidGradlePlugin" }

[plugins]
android-application = { id = "com.android.application", version.ref = "androidGradlePlugin" }
android-library = { id = "com.android.library", version.ref = "androidGradlePlugin" }
kotlin-android = { id = "org.jetbrains.kotlin.android", version.ref = "kotlin" }
kotlin-jvm = { id = "org.jetbrains.kotlin.jvm", version.ref = "kotlin" }
kotlin-serialization = { id = "org.jetbrains.kotlin.plugin.serialization", version.ref = "kotlin" }
compose-compiler = { id = "org.jetbrains.kotlin.plugin.compose", version.ref = "kotlin" }
hilt = { id = "com.google.dagger.hilt.android", version.ref = "hilt" }
ksp = { id = "com.google.devtools.ksp", version.ref = "ksp" }
room = { id = "androidx.room", version.ref = "room" }
roborazzi = { id = "io.github.takahirom.roborazzi", version.ref = "roborazzi" }
```

## Accessing from Convention Plugins

In `build-logic/convention/build.gradle.kts`, declare Gradle plugin dependencies as `compileOnly`:

```kotlin
dependencies {
    compileOnly(libs.android.gradlePlugin)
    compileOnly(libs.kotlin.gradlePlugin)
    compileOnly(libs.compose.gradlePlugin)
    compileOnly(libs.ksp.gradlePlugin)
    compileOnly(libs.room.gradlePlugin)
}
```

Inside plugin code, access the catalog:

```kotlin
// Extension property to access the version catalog
val Project.libs get(): VersionCatalog =
    extensions.getByType<VersionCatalogsExtension>().named("libs")

// Usage
dependencies {
    add("implementation", libs.findLibrary("hilt.android").get())
    add("ksp", libs.findLibrary("hilt.compiler").get())
}
```

## Usage in Module build.gradle.kts

```kotlin
plugins {
    alias(libs.plugins.{appId}.android.library)
    alias(libs.plugins.{appId}.hilt)
}

dependencies {
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    implementation(projects.core.model)
}
```

## Compose BOM

Use a BOM to align all Compose library versions:

```kotlin
dependencies {
    val bom = libs.androidx.compose.bom
    implementation(platform(bom))
    androidTestImplementation(platform(bom))

    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
}
```

## Adding a New Dependency

1. Add the version to `[versions]` (if not already present).
2. Add the library to `[libraries]` with `version.ref`.
3. Use `libs.{library.name}` in `build.gradle.kts` or convention plugins.
4. If it's a Gradle plugin, add to `[plugins]` too.

## Anti-Patterns

| Don't | Do Instead |
|-------|------------|
| Hardcode versions in `build.gradle.kts` | Use `libs.versions.toml` |
| Duplicate dependency declarations across modules | Add common deps in convention plugins |
| Use `implementation(platform(...))` for non-BOM deps | Only BOM libraries use platform() |
| Mix `classpath` and `plugins {}` blocks | Use `plugins {}` with version catalog aliases |
