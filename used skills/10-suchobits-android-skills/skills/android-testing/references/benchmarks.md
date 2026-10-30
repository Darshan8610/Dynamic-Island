# Benchmarks & Baseline Profiles

## Macro Benchmarks

Measure real-world performance (frame timing, startup speed) on a physical device or emulator.

### Setup

```kotlin
// benchmarks/build.gradle.kts
plugins {
    alias(libs.plugins.baselineprofile)
    alias(libs.plugins.{appId}.android.test)
}

android {
    namespace = "com.example.benchmarks"
    targetProjectPath = ":app"

    testOptions.managedDevices.localDevices {
        create("pixel6Api33") {
            device = "Pixel 6"
            apiLevel = 33
            systemImageSource = "aosp"
        }
    }
}
```

### Scroll Benchmark

```kotlin
@RunWith(AndroidJUnit4ClassRunner::class)
class Scroll{Feature}Benchmark {

    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    @Test
    fun scrollFeed_compilationNone() = scrollFeed(CompilationMode.None())

    @Test
    fun scrollFeed_compilationPartial() = scrollFeed(CompilationMode.Partial())

    @Test
    fun scrollFeed_compilationFull() = scrollFeed(CompilationMode.Full())

    private fun scrollFeed(compilationMode: CompilationMode) =
        benchmarkRule.measureRepeated(
            packageName = PACKAGE_NAME,
            metrics = listOf(FrameTimingMetric()),
            compilationMode = compilationMode,
            iterations = 10,
            startupMode = StartupMode.WARM,
            setupBlock = {
                pressHome()
                startActivityAndWait()
            },
        ) {
            // Navigate to the feature screen
            device.findObject(By.text("{Feature}")).click()
            device.waitForIdle()

            // Scroll down and back up
            val list = device.findObject(By.scrollable(true))
            list.setGestureMargin(device.displayWidth / 5)
            list.fling(Direction.DOWN)
            device.waitForIdle()
            list.fling(Direction.UP)
            device.waitForIdle()
        }
}
```

### Compilation Modes

| Mode | What It Measures |
|------|-----------------|
| `None()` | Worst case — no JIT or AOT compilation |
| `Partial()` | With baseline profile applied (realistic first launch) |
| `Full()` | Fully optimized (best case after extended use) |

Testing all three shows the baseline profile's impact on real performance.

## Baseline Profiles

Baseline profiles tell the Android runtime which code paths to AOT-compile at install time, improving startup and scroll performance.

### Generating Profiles

```kotlin
class {Feature}BaselineProfile {

    @get:Rule
    val baselineProfileRule = BaselineProfileRule()

    @Test
    fun generate() = baselineProfileRule.collect(
        packageName = PACKAGE_NAME,
        includeInStartupProfile = true,
    ) {
        // Exercise the critical user journey
        pressHome()
        startActivityAndWait()

        // Navigate to the feature
        device.findObject(By.text("{Feature}")).click()
        device.waitForIdle()

        // Scroll through content
        val list = device.findObject(By.scrollable(true))
        list?.fling(Direction.DOWN)
        device.waitForIdle()
    }
}
```

### Startup Profile

```kotlin
class StartupBaselineProfile {

    @get:Rule
    val baselineProfileRule = BaselineProfileRule()

    @Test
    fun generate() = baselineProfileRule.collect(
        packageName = PACKAGE_NAME,
        includeInStartupProfile = true,
        profileBlock = MacrobenchmarkScope::startActivityAndAllowNotifications,
    )
}
```

The `includeInStartupProfile = true` flag also generates a **DEX layout optimization** profile for faster class loading.

## UIAutomator Helpers

```kotlin
fun MacrobenchmarkScope.startActivityAndAllowNotifications() {
    startActivityAndWait()

    // Dismiss notification permission dialog if shown (API 33+)
    val allowButton = device.findObject(By.text("Allow"))
    allowButton?.click()
    device.waitForIdle()
}

fun UiDevice.waitForObject(selector: BySelector, timeout: Long = 5_000): UiObject2 {
    if (hasObject(selector)) return findObject(selector)
    waitForObject(Until.hasObject(selector), timeout)
    return findObject(selector)
}

fun UiDevice.scrollDownTo(selector: BySelector) {
    val scrollable = findObject(By.scrollable(true)) ?: return
    scrollable.setGestureMargin(displayWidth / 5)
    while (!hasObject(selector)) {
        scrollable.scroll(Direction.DOWN, 1f)
    }
}
```

## Running Benchmarks

```bash
# Generate baseline profiles
./gradlew :benchmarks:pixel6Api33BenchmarkAndroidTest

# Run specific benchmark
./gradlew :benchmarks:connectedBenchmarkAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.example.benchmarks.Scroll{Feature}Benchmark

# Apply generated profiles to app
./gradlew :app:generateBaselineProfile
```

## CI Integration

Run benchmarks less frequently than tests (nightly or on release branches):

```yaml
# Weekly benchmark run
- name: Run benchmarks
  run: ./gradlew :benchmarks:pixel6Api33BenchmarkAndroidTest
  
- name: Upload benchmark results
  uses: actions/upload-artifact@v4
  with:
    name: benchmark-results
    path: '**/build/outputs/connected_android_test_additional_output/**/*.json'
```

## Anti-Patterns

| Don't | Do Instead |
|-------|------------|
| Run benchmarks on every PR | Run nightly or on release branches |
| Only test `CompilationMode.None()` | Test all three modes to measure profile impact |
| Skip startup profile | It's the highest-impact optimization |
| Use Thread.sleep in benchmarks | Use `device.waitForIdle()` or `waitForObject()` |
| Benchmark on emulator for absolute numbers | Use physical devices; emulator is OK for relative comparison |
