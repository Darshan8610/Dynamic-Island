# Build Performance

## gradle.properties

```properties
# Memory
org.gradle.jvmargs=-Xmx4g -XX:+UseParallelGC

# Parallelism
org.gradle.parallel=true
org.gradle.workers.max=4

# Caching
org.gradle.configuration-cache=true
org.gradle.caching=true

# KSP (Hilt, Room annotation processing)
ksp.incremental=true
ksp.incremental.log=true

# Kotlin
kotlin.code.style=official
```

## Key Optimizations

### Configuration Cache

Caches the result of the configuration phase so subsequent builds skip it entirely. Enable with `org.gradle.configuration-cache=true`. Requires all plugins to be configuration-cache compatible.

### Parallel Execution

`org.gradle.parallel=true` builds independent modules simultaneously. The more modules with no dependency relationship, the more parallelism.

### Build Cache

`org.gradle.caching=true` reuses outputs from previous builds (local) or CI (remote). Task outputs are cached by input hash.

### KSP over KAPT

KSP (Kotlin Symbol Processing) is faster than KAPT because it doesn't generate Java stubs. Use KSP for Hilt, Room, and any processor that supports it:

```kotlin
// Convention plugin
pluginManager.apply("com.google.devtools.ksp")
dependencies {
    add("ksp", libs.findLibrary("hilt.compiler").get())
    // NOT: add("kapt", ...)
}
```

### Modularization Impact

More modules = more parallelism but more configuration overhead. Sweet spot: 15-30 modules for a medium app. Each module should have a clear purpose — don't create modules just for parallelism.

## Diagnosing Slow Builds

### Build Scan

```bash
./gradlew build --scan
```

Generates a Gradle Enterprise build scan showing:
- Task execution timeline
- Cache hit rates
- Configuration time
- Dependency resolution time

### Profile

```bash
./gradlew build --profile
```

Generates an HTML report in `build/reports/profile/`.

### Common Bottlenecks

| Symptom | Cause | Fix |
|---------|-------|-----|
| Long configuration phase | Too many `buildSrc` changes | Use included builds (`build-logic/`) |
| Low cache hit rate | Non-deterministic task inputs | Fix absolute paths, timestamps in outputs |
| KAPT slow | Stub generation overhead | Migrate to KSP |
| Full rebuild on small change | Broad API module changes | Minimize api module surface |
| OOM during build | Insufficient heap | Increase `-Xmx` in `gradle.properties` |

## Managed Devices for Testing

```kotlin
fun configureManagedDevices(extension: CommonExtension<*, *, *, *, *, *>) {
    extension.testOptions.managedDevices.localDevices {
        create("pixel6Api33") {
            device = "Pixel 6"
            apiLevel = 33
            systemImageSource = "aosp"
        }
    }
}
```

Run tests on managed devices:

```bash
./gradlew pixel6Api33DemoDebugAndroidTest
```

## Dependency Guard

Tracks dependency changes to prevent accidental version bumps or new transitive deps:

```bash
./gradlew dependencyGuardBaseline  # Record baseline
./gradlew dependencyGuard          # Check for changes
```
