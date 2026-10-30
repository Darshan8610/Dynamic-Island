# Build Variants & Flavors

## Flavor Setup

Define flavors in the application convention plugin:

```kotlin
class AndroidApplicationFlavorsConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            extensions.configure<ApplicationExtension> {
                flavorDimensions += "environment"
                productFlavors {
                    create("demo") {
                        dimension = "environment"
                        applicationIdSuffix = ".demo"
                    }
                    create("prod") {
                        dimension = "environment"
                    }
                }
            }
        }
    }
}
```

## Flavor-Specific Source Sets

```
app/src/
├── main/          # Shared code
├── demo/          # Demo-only code
│   └── kotlin/
│       └── DemoAppModule.kt
├── prod/          # Prod-only code
│   └── kotlin/
│       └── ProdAppModule.kt
├── testDemo/      # Tests for demo flavor
└── testProd/      # Tests for prod flavor
```

## Swapping Implementations by Flavor

Use Hilt modules in flavor-specific source sets:

```kotlin
// src/demo/kotlin/di/FlavoredNetworkModule.kt
@Module
@InstallIn(SingletonComponent::class)
abstract class FlavoredNetworkModule {
    @Binds
    abstract fun bindsNetworkDataSource(
        impl: DemoAppNetworkDataSource,  // reads from bundled JSON assets
    ): AppNetworkDataSource
}

// src/prod/kotlin/di/FlavoredNetworkModule.kt
@Module
@InstallIn(SingletonComponent::class)
abstract class FlavoredNetworkModule {
    @Binds
    abstract fun bindsNetworkDataSource(
        impl: RetrofitAppNetwork,  // calls real API
    ): AppNetworkDataSource
}
```

Both files have the same class name and package — Gradle picks the correct one based on the active flavor.

## Demo Data Source Pattern

```kotlin
class DemoAppNetworkDataSource @Inject constructor(
    @ApplicationContext private val context: Context,
    private val json: Json,
) : AppNetworkDataSource {

    override suspend fun get{Items}(): List<Network{Item}> =
        context.assets.open("{items}.json").use { stream ->
            json.decodeFromString(stream.readBytes().decodeToString())
        }
}
```

Place JSON fixture files in `src/demo/assets/`.

## When to Use Flavors

| Use Case | Approach |
|----------|----------|
| Swap network layer for offline dev | `demo` / `prod` flavors |
| Add Firebase/analytics in production only | `prod` flavor source set |
| Different API endpoints per environment | `BuildConfig` fields in flavor config |
| Free vs. paid app | Separate flavor dimension |

## Build Types vs. Flavors

- **Build types** (`debug`, `release`): Control optimization, signing, debugging.
- **Flavors** (`demo`, `prod`): Control behavior, data sources, feature flags.

Both combine into **build variants**: `demoDebug`, `prodRelease`, etc.

## CI Commands by Variant

```bash
# Tests
./gradlew testDemoDebug              # Unit tests for demo
./gradlew connectedDemoDebugAndroidTest  # Instrumentation tests
./gradlew verifyRoborazziDemoDebug   # Screenshot verification

# Lint
./gradlew :app:lintProdRelease       # Lint on production release

# Build
./gradlew assembleProdRelease        # Production APK
./gradlew bundleProdRelease          # Production AAB
```
