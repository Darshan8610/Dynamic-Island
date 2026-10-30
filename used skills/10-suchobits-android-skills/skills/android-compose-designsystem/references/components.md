# Design System Components Reference

## Component Mapping

Every Material 3 component your app uses should have a design system wrapper:

| Material Component | Design System Wrapper | Why Wrap |
|---|---|---|
| `Button` | `{App}Button` | Standard padding, icon layout |
| `OutlinedButton` | `{App}OutlinedButton` | Consistent outline styling |
| `TextButton` | `{App}TextButton` | Typography defaults |
| `FilterChip` | `{App}FilterChip` | Selection styling |
| `NavigationBar` | `{App}NavigationBar` | Custom elevation, colors |
| `NavigationBarItem` | `{App}NavigationBarItem` | Selected/unselected icon swap |
| `NavigationRail` | `{App}NavigationRail` | Tablet layout defaults |
| `NavigationRailItem` | `{App}NavigationRailItem` | Icon behavior |
| `TabRow` | `{App}TabRow` | Tab indicator styling |
| `Tab` | `{App}Tab` | Typography |
| `CenterAlignedTopAppBar` | `{App}TopAppBar` | Consistent title, nav icon |
| `IconToggleButton` | `{App}IconToggleButton` | Toggle animation, colors |
| `Icons.*` | `{App}Icons.*` | Centralized icon set |

## Filter Chip

```kotlin
@Composable
fun {App}FilterChip(
    selected: Boolean,
    onSelectedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    label: @Composable () -> Unit,
) {
    FilterChip(
        selected = selected,
        onClick = { onSelectedChange(!selected) },
        label = {
            ProvideTextStyle(value = MaterialTheme.typography.labelSmall) {
                label()
            }
        },
        modifier = modifier,
        enabled = enabled,
        leadingIcon = if (selected) {
            { Icon(imageVector = {App}Icons.Check, contentDescription = null, modifier = Modifier.size(FilterChipDefaults.IconSize)) }
        } else null,
        shape = CircleShape,
    )
}
```

## Icon Toggle Button

```kotlin
@Composable
fun {App}IconToggleButton(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: @Composable () -> Unit,
    checkedIcon: @Composable () -> Unit = icon,
) {
    IconToggleButton(
        checked = checked,
        onCheckedChange = onCheckedChange,
        modifier = modifier,
        enabled = enabled,
    ) {
        if (checked) checkedIcon() else icon()
    }
}
```

## Tag / Label Component

Custom components not in Material but common in apps:

```kotlin
@Composable
fun {App}Tag(
    text: String,
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.secondaryContainer,
    contentColor: Color = MaterialTheme.colorScheme.onSecondaryContainer,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(4.dp),
        color = containerColor,
        contentColor = contentColor,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
        )
    }
}
```

## Loading Indicator

```kotlin
@Composable
fun {App}LoadingIndicator(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator(modifier = Modifier.testTag("loading_indicator"))
    }
}
```

## Empty State

```kotlin
@Composable
fun {App}EmptyState(
    message: String,
    modifier: Modifier = Modifier,
    icon: ImageVector = {App}Icons.Search,
    action: @Composable (() -> Unit)? = null,
) {
    Column(
        modifier = modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        if (action != null) {
            Spacer(Modifier.height(16.dp))
            action()
        }
    }
}
```

## Dynamic Color Support

Dynamic color extracts a palette from the user's wallpaper (Android 12+):

```kotlin
@Composable
fun AppTheme(
    useDynamicColor: Boolean = true,
    darkTheme: Boolean = isSystemInDarkTheme(),
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
    // ...
}
```

Let users toggle dynamic color in settings:

```kotlin
// In UserData / preferences
data class UserData(
    val useDynamicColor: Boolean = true,
    val darkThemeConfig: DarkThemeConfig = DarkThemeConfig.FOLLOW_SYSTEM,
)

enum class DarkThemeConfig { FOLLOW_SYSTEM, LIGHT, DARK }
```

## Adaptive Layouts

### Responsive Component Sizing

```kotlin
@Composable
fun {App}Card(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val configuration = LocalConfiguration.current
    val cardWidth = when {
        configuration.screenWidthDp >= 840 -> 400.dp  // Tablet
        configuration.screenWidthDp >= 600 -> 320.dp  // Foldable
        else -> Modifier.fillMaxWidth()                // Phone
    }

    Card(
        modifier = modifier.then(if (cardWidth is Dp) Modifier.width(cardWidth) else cardWidth as Modifier),
        content = content,
    )
}
```

## Anti-Patterns

| Don't | Do Instead |
|-------|------------|
| Use `MaterialTheme` in features | Use `AppTheme` |
| Reference `Icons.Default.*` in features | Use `{App}Icons.*` |
| Call `Button()` from Material directly | Use `{App}Button()` |
| Hardcode colors (`Color(0xFF...)`) in features | Use `MaterialTheme.colorScheme.*` |
| Define text styles inline | Use `MaterialTheme.typography.*` |
| Duplicate component styling across features | Add it to the design system module |
