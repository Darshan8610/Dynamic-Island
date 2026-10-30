# Convention Plugins (Build Logic)

## Why Convention Plugins

Convention plugins eliminate duplicated Gradle configuration across modules. Instead of copying SDK versions, Compose settings, and Hilt setup into every `build.gradle.kts`, each module applies a single plugin ID.

## Directory Structure

```
build-logic/
└── convention/
    ├── build.gradle.kts              # Plugin project configuration
    └── src/main/kotlin/
        ├── AndroidApplicationConventionPlugin.kt
        ├── AndroidLibraryConventionPlugin.kt
        ├── AndroidFeatureImplConventionPlugin.kt
        ├── AndroidFeatureApiConventionPlugin.kt
        ├── AndroidLibraryComposeConventionPlugin.kt
        ├── AndroidRoomConventionPlugin.kt
        ├── HiltConventionPlugin.kt
        ├── JvmLibraryConventionPlugin.kt
        └── helpers/
            ├── KotlinAndroid.kt      # Shared Kotlin/Android config
            └── ComposeConfig.kt      # Shared Compose compiler config
```

Include in `settings.gradle.kts`:

```kotlin
pluginManagement {
    includeBuild("build-logic")
}
```

## Plugin Registration

Register plugins in `build-logic/convention/build.gradle.kts`:

```kotlin
plugins {
    `kotlin-dsl`
}

dependencies {
    compileOnly(libs.android.gradlePlugin)
    compileOnly(libs.kotlin.gradlePlugin)
    compileOnly(libs.compose.gradlePlugin)
    compileOnly(libs.ksp.gradlePlugin)
    compileOnly(libs.room.gradlePlugin)
}

gradlePlugin {
    plugins {
        register("androidApplication") {
            id = "{appId}.android.application"
            implementationClass = "AndroidApplicationConventionPlugin"
        }
        register("androidLibrary") {
            id = "{appId}.android.library"
            implementationClass = "AndroidLibraryConventionPlugin"
        }
        register("androidFeatureImpl") {
            id = "{appId}.android.feature.impl"
            implementationClass = "AndroidFeatureImplConventionPlugin"
        }
        register("androidFeatureApi") {
            id = "{appId}.android.feature.api"
            implementationClass = "AndroidFeatureApiConventionPlugin"
        }
        register("androidLibraryCompose") {
            id = "{appId}.android.library.compose"
            implementationClass = "AndroidLibraryComposeConventionPlugin"
        }
        register("androidRoom") {
            id = "{appId}.android.room"
            implementationClass = "AndroidRoomConventionPlugin"
        }
        register("hilt") {
            id = "{appId}.hilt"
            implementationClass = "HiltConventionPlugin"
        }
        register("jvmLibrary") {
            id = "{appId}.jvm.library"
            implementationClass = "JvmLibraryConventionPlugin"
        }
    }
}
```

## Plugin Implementations

### Android Library

The base plugin applied by most modules:

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
                // Auto-prefix resources by module path: core:ui → core_ui_
                resourcePrefix = path.drop(1)
                    .replace(':', '_')
                    .replace('-', '_') + "_"
            }
        }
    }
}
```

### Shared Kotlin/Android Config

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

private fun Project.configureKotlin() {
    tasks.withType<KotlinCompile>().configureEach {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
            freeCompilerArgs.addAll(
                "-opt-in=kotlinx.coroutines.ExperimentalCoroutinesApi",
            )
        }
    }
}
```

### Feature Impl Plugin

Auto-applies Hilt, lifecycle, and navigation dependencies:

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
                add("testImplementation", libs.findLibrary("kotlin.test").get())
            }
        }
    }
}
```

### Feature API Plugin

Minimal — just exports the NavKey:

```kotlin
class AndroidFeatureApiConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply {
                apply("{appId}.jvm.library")
                apply("org.jetbrains.kotlin.plugin.serialization")
            }

            dependencies {
                add("implementation", project(":core:navigation"))
                add("implementation", project(":core:model"))
            }
        }
    }
}
```

### Compose Plugin

```kotlin
class AndroidLibraryComposeConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("org.jetbrains.kotlin.plugin.compose")

            extensions.configure<LibraryExtension> {
                buildFeatures {
                    compose = true
                }
            }
        }
    }
}
```

### Hilt Plugin

```kotlin
class HiltConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply {
                apply("com.google.devtools.ksp")
                apply("com.google.dagger.hilt.android")
            }

            dependencies {
                add("implementation", libs.findLibrary("hilt.android").get())
                add("ksp", libs.findLibrary("hilt.compiler").get())
            }
        }
    }
}
```

### Room Plugin

```kotlin
class AndroidRoomConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply {
                apply("{appId}.android.library")
                apply("com.google.devtools.ksp")
                apply("androidx.room")
            }

            extensions.configure<RoomExtension> {
                schemaDirectory("$projectDir/schemas")
            }

            dependencies {
                add("implementation", libs.findLibrary("room.runtime").get())
                add("implementation", libs.findLibrary("room.ktx").get())
                add("ksp", libs.findLibrary("room.compiler").get())
            }
        }
    }
}
```

## Version Catalog (libs.versions.toml)

Centralize all dependency versions:

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

[libraries]
android-gradlePlugin = { group = "com.android.tools.build", name = "gradle", version.ref = "androidGradlePlugin" }
kotlin-gradlePlugin = { group = "org.jetbrains.kotlin", name = "kotlin-gradle-plugin", version.ref = "kotlin" }
hilt-android = { group = "com.google.dagger", name = "hilt-android", version.ref = "hilt" }
hilt-compiler = { group = "com.google.dagger", name = "hilt-compiler", version.ref = "hilt" }
room-runtime = { group = "androidx.room", name = "room-runtime", version.ref = "room" }
room-compiler = { group = "androidx.room", name = "room-compiler", version.ref = "room" }
room-ktx = { group = "androidx.room", name = "room-ktx", version.ref = "room" }

[plugins]
android-application = { id = "com.android.application", version.ref = "androidGradlePlugin" }
kotlin-android = { id = "org.jetbrains.kotlin.android", version.ref = "kotlin" }
hilt = { id = "com.google.dagger.hilt.android", version.ref = "hilt" }
ksp = { id = "com.google.devtools.ksp", version.ref = "ksp" }
```

## Usage in Feature Modules

After setup, a feature `impl` module's `build.gradle.kts` is minimal:

```kotlin
plugins {
    alias(libs.plugins.{appId}.android.feature.impl)
}

android {
    namespace = "com.example.feature.{feature}.impl"
}

dependencies {
    implementation(projects.feature.{feature}.api)
    implementation(projects.core.data)
}
```

## Anti-Patterns

| Don't | Do Instead |
|-------|------------|
| Copy SDK versions into every module | Configure once in convention plugins |
| Use `buildSrc` for convention plugins | Use an included build (`build-logic/`) — it avoids cache invalidation |
| Hardcode dependency versions in plugins | Reference the version catalog via `libs` accessor |
| Create one mega-plugin that does everything | Compose small, focused plugins (`Hilt` + `Compose` + `Library`) |
| Apply plugins modules don't need | Feature API modules don't need Compose or Hilt |
