# Screenshot Testing with Roborazzi

## Setup

### Dependencies

```kotlin
// In module build.gradle.kts
plugins {
    alias(libs.plugins.roborazzi)
}

dependencies {
    testImplementation(libs.roborazzi)
    testImplementation(libs.roborazzi.accessibility)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.compose.ui.test)
}
```

### Default Options

```kotlin
val DefaultRoborazziOptions = RoborazziOptions(
    compareOptions = RoborazziOptions.CompareOptions(
        changeThreshold = 0f,  // Pixel-perfect matching
    ),
    recordOptions = RoborazziOptions.RecordOptions(
        resizeScale = 0.5,  // Half-size PNGs to save space
    ),
)
```

## Multi-Theme / Multi-Device Capture

The power of screenshot testing is capturing all theme and device combinations automatically:

```kotlin
fun captureMultiTheme(
    name: String,
    content: @Composable () -> Unit,
) {
    val devices = listOf(
        DeviceConfig(name = "phone", width = 640, height = 360, dpi = 480),
        DeviceConfig(name = "foldable", width = 673, height = 841, dpi = 480),
        DeviceConfig(name = "tablet", width = 1280, height = 800, dpi = 480),
    )

    val themes = listOf(
        ThemeConfig(name = "light", darkTheme = false, dynamicColor = false),
        ThemeConfig(name = "dark", darkTheme = true, dynamicColor = false),
        ThemeConfig(name = "light_dynamic", darkTheme = false, dynamicColor = true),
        ThemeConfig(name = "dark_dynamic", darkTheme = true, dynamicColor = true),
    )

    devices.forEach { device ->
        themes.forEach { theme ->
            composeTestRule.setContent {
                AppTheme(darkTheme = theme.darkTheme, useDynamicColor = theme.dynamicColor) {
                    content()
                }
            }
            composeTestRule.onRoot().captureRoboImage(
                "${name}_${device.name}_${theme.name}.png",
                roborazziOptions = DefaultRoborazziOptions,
            )
        }
    }
}
```

## Component Screenshot Tests

```kotlin
class {App}ButtonScreenshotTests {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun button_default() {
        captureMultiTheme("button_default") {
            {App}Button(onClick = {}, text = { Text("Click me") })
        }
    }

    @Test
    fun button_with_icon() {
        captureMultiTheme("button_icon") {
            {App}Button(
                onClick = {},
                text = { Text("Save") },
                leadingIcon = { Icon({App}Icons.Bookmark, contentDescription = null) },
            )
        }
    }

    @Test
    fun button_disabled() {
        captureMultiTheme("button_disabled") {
            {App}Button(onClick = {}, enabled = false, text = { Text("Disabled") })
        }
    }
}
```

## Screen Screenshot Tests

```kotlin
class {Feature}ScreenScreenshotTests {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun {feature}Screen_loading() {
        composeTestRule.setContent {
            AppTheme {
                {Feature}Screen(
                    uiState = {Feature}UiState.Loading,
                    onAction = {},
                )
            }
        }
        composeTestRule.onRoot().captureRoboImage()
    }

    @Test
    fun {feature}Screen_success() {
        composeTestRule.setContent {
            AppTheme {
                {Feature}Screen(
                    uiState = {Feature}UiState.Success(
                        items = listOf(
                            test{Item}(id = "1", title = "First Item"),
                            test{Item}(id = "2", title = "Second Item"),
                        ),
                    ),
                    onAction = {},
                )
            }
        }
        composeTestRule.onRoot().captureRoboImage()
    }

    @Test
    fun {feature}Screen_empty() {
        composeTestRule.setContent {
            AppTheme {
                {Feature}Screen(
                    uiState = {Feature}UiState.Success(items = emptyList()),
                    onAction = {},
                )
            }
        }
        composeTestRule.onRoot().captureRoboImage()
    }

    @Test
    fun {feature}Screen_error() {
        composeTestRule.setContent {
            AppTheme {
                {Feature}Screen(
                    uiState = {Feature}UiState.Error("Network unavailable"),
                    onAction = {},
                )
            }
        }
        composeTestRule.onRoot().captureRoboImage()
    }
}
```

## Accessibility Checks

Integrate accessibility validation into screenshot tests:

```kotlin
val accessibilityOptions = RoborazziOptions(
    accessibilityOptions = RoborazziOptions.AccessibilityOptions(
        checker = RoborazziATFAccessibilityChecker(
            preset = CheckResultCategory.LATEST,
        ),
    ),
)
```

This flags accessibility issues (missing content descriptions, insufficient contrast) alongside visual diffs.

## Screenshot Storage

```
src/test/screenshots/
├── button_default_phone_light.png
├── button_default_phone_dark.png
├── button_default_foldable_light.png
├── button_default_tablet_light.png
├── {feature}Screen_loading.png
├── {feature}Screen_success.png
└── ...
```

## CI Workflow

```yaml
# Verify screenshots haven't changed
- name: Verify screenshots
  run: ./gradlew verifyRoborazziDemoDebug

# On failure, record new baselines and upload diff images
- name: Record screenshots (on PR)
  if: failure()
  run: ./gradlew recordRoborazziDemoDebug

- name: Upload comparison images
  if: failure()
  uses: actions/upload-artifact@v4
  with:
    name: screenshot-diffs
    path: '**/build/outputs/roborazzi/*_compare.png'
```

## When to Add Screenshot Tests

| Scenario | Add Screenshot? |
|----------|----------------|
| New design system component | Yes — all states, themes, devices |
| New feature screen | Yes — Loading, Success, Error, Empty states |
| Bug fix that changes rendering | Yes — prevent regression |
| Internal refactor (no visual change) | No — unit tests suffice |
| ViewModel logic change | No — unit test the ViewModel |

## Anti-Patterns

| Don't | Do Instead |
|-------|------------|
| Screenshot-test business logic | Unit-test it; screenshot only the visual output |
| Test only one theme | Capture light, dark, and dynamic color |
| Test only phone size | Include foldable and tablet |
| Skip error/empty states | They're the most likely to regress |
| Check in huge uncompressed PNGs | Use `resizeScale = 0.5` |
