---
name: android-compose-designsystem
description: >-
  Coaches building a Material 3 design system module for Jetpack Compose Android apps.
  Use when creating a design system, theming with Material 3, defining color tokens,
  typography scales, reusable UI components, dark/light mode support, dynamic color,
  or wrapping Material components in app-specific composables. Covers component libraries,
  icon systems, and design system enforcement via lint.
license: Apache-2.0
compatibility: Requires Jetpack Compose, Material 3
metadata:
  author: luis-suchobits
  version: "1.0"
  based-on: google/nowinandroid
---

# Android Compose Design System — Component Library Guide

Patterns for building a centralized Material 3 design system module that all feature modules share. This ensures visual consistency, simplifies theming, and makes design changes propagate app-wide from a single source.

## Module Structure

```
core/designsystem/
├── build.gradle.kts
└── src/main/kotlin/com/example/core/designsystem/
    ├── theme/
    │   ├── Color.kt          # Color tokens
    │   ├── Type.kt           # Typography scale
    │   ├── Theme.kt          # AppTheme composable
    │   └── Tint.kt           # Tint/accent colors
    ├── component/
    │   ├── {App}Button.kt
    │   ├── {App}FilterChip.kt
    │   ├── {App}TopAppBar.kt
    │   ├── {App}NavigationBar.kt
    │   ├── {App}IconToggleButton.kt
    │   ├── {App}TabRow.kt
    │   └── ...
    └── icon/
        └── {App}Icons.kt     # Centralized icon definitions
```

The module depends on Compose and Material 3 — nothing else. No business logic, no data layer dependencies.

## Theme Setup

### Color Tokens

```kotlin
// theme/Color.kt

// Define semantic colors, not raw hex values
val LightColors = lightColorScheme(
    primary = Color(0xFF006D3B),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFF96F7B5),
    secondary = Color(0xFF4F6354),
    surface = Color(0xFFFBFDF8),
    error = Color(0xFFBA1A1A),
    // ... complete scheme
)

val DarkColors = darkColorScheme(
    primary = Color(0xFF7ADB9B),
    onPrimary = Color(0xFF00391A),
    primaryContainer = Color(0xFF00522B),
    secondary = Color(0xFFB6CCB9),
    surface = Color(0xFF191C1A),
    error = Color(0xFFFFB4AB),
    // ... complete scheme
)
```

### Typography Scale

```kotlin
// theme/Type.kt

val AppTypography = Typography(
    displayLarge = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 57.sp,
        lineHeight = 64.sp,
        letterSpacing = (-0.25).sp,
    ),
    headlineMedium = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 28.sp,
        lineHeight = 36.sp,
    ),
    bodyLarge = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.5.sp,
    ),
    labelSmall = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.5.sp,
    ),
    // ... complete scale
)
```

### Theme Composable

```kotlin
// theme/Theme.kt

@Composable
fun AppTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    useDynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        useDynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColors
        else -> LightColors
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = AppTypography,
        content = content,
    )
}
```

## Wrapping Material Components

Wrap every Material component your app uses. This creates an indirection layer that lets you:
- Apply app-wide defaults (padding, shape, colors)
- Swap underlying implementations without touching features
- Enforce usage via custom lint rules

### Button

```kotlin
@Composable
fun {App}Button(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    text: @Composable () -> Unit,
    leadingIcon: @Composable (() -> Unit)? = null,
) {
    Button(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        contentPadding = if (leadingIcon != null) {
            ButtonDefaults.ButtonWithIconContentPadding
        } else {
            ButtonDefaults.ContentPadding
        },
    ) {
        if (leadingIcon != null) {
            Box(Modifier.sizeIn(maxHeight = ButtonDefaults.IconSize)) {
                leadingIcon()
            }
            Spacer(Modifier.width(ButtonDefaults.IconSpacing))
        }
        text()
    }
}
```

### Top App Bar

```kotlin
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun {App}TopAppBar(
    title: String,
    modifier: Modifier = Modifier,
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
    colors: TopAppBarColors = TopAppBarDefaults.centerAlignedTopAppBarColors(),
) {
    CenterAlignedTopAppBar(
        title = { Text(text = title) },
        navigationIcon = navigationIcon,
        actions = actions,
        colors = colors,
        modifier = modifier,
    )
}
```

### Navigation Bar

```kotlin
@Composable
fun {App}NavigationBar(
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    NavigationBar(
        modifier = modifier,
        contentColor = {App}NavigationDefaults.contentColor(),
        tonalElevation = 0.dp,
        content = content,
    )
}

@Composable
fun RowScope.{App}NavigationBarItem(
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    alwaysShowLabel: Boolean = true,
    icon: @Composable () -> Unit,
    selectedIcon: @Composable () -> Unit = icon,
    label: @Composable (() -> Unit)? = null,
) {
    NavigationBarItem(
        selected = selected,
        onClick = onClick,
        icon = if (selected) selectedIcon else icon,
        modifier = modifier,
        enabled = enabled,
        label = label,
        alwaysShowLabel = alwaysShowLabel,
    )
}
```

## Centralized Icon System

```kotlin
object {App}Icons {
    val Home = Icons.Rounded.Home
    val HomeBorder = Icons.Outlined.Home
    val Search = Icons.Rounded.Search
    val Settings = Icons.Rounded.Settings
    val Bookmark = Icons.Rounded.Bookmark
    val BookmarkBorder = Icons.Outlined.BookmarkBorder
    val ArrowBack = Icons.AutoMirrored.Rounded.ArrowBack
    val Close = Icons.Rounded.Close
    val MoreVert = Icons.Default.MoreVert
}
```

Features reference `{App}Icons.Home` instead of `Icons.Rounded.Home` directly. This lets you swap icon sets without touching feature code.

## Usage in Feature Modules

```kotlin
// feature/{feature}/impl/{Feature}Screen.kt

@Composable
internal fun {Feature}Screen(
    uiState: {Feature}UiState,
    onAction: ({Feature}Action) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Use design system components, not Material directly
    Scaffold(
        topBar = {
            {App}TopAppBar(title = "{Feature}")
        },
    ) { padding ->
        // Use design system theme colors
        Text(
            text = "Hello",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(padding),
        )
    }
}
```

## Previews & Testing

### Component Previews

```kotlin
@Preview(name = "Light")
@Preview(name = "Dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun {App}ButtonPreview() {
    AppTheme {
        {App}Button(onClick = {}, text = { Text("Click me") })
    }
}
```

### Multi-Theme Screenshot Testing

```kotlin
fun captureMultiTheme(content: @Composable () -> Unit) {
    val themes = listOf(
        "light_default" to false to false,
        "dark_default" to true to false,
        "light_dynamic" to false to true,
        "dark_dynamic" to true to true,
    )
    themes.forEach { (name, dark, dynamic) ->
        composeTestRule.setContent {
            AppTheme(darkTheme = dark, useDynamicColor = dynamic) { content() }
        }
        composeTestRule.onRoot().captureRoboImage("${name}.png")
    }
}
```

For lint enforcement of design system usage, see the [android-lint-enforcement](../android-lint-enforcement/SKILL.md) skill.

For detailed component patterns, see the [components reference](references/components.md).
