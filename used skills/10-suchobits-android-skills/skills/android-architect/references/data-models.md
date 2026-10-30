# Data Model Patterns

## Three-Tier Model Architecture

Each data entity exists as three separate classes, one per layer:

```
Network{Item}  →  {Item}Entity  →  {Item}
  (API)           (Database)       (Domain/UI)
```

This decouples layers so a backend API change doesn't ripple into your UI, and a schema migration doesn't affect network parsing.

## Network Model

Lives in `core:network`. Mirrors the API response shape exactly.

```kotlin
// core/network/model/Network{Item}.kt

@Serializable
data class Network{Item}(
    val id: String,
    val title: String,
    val description: String,
    val imageUrl: String,
    val categoryId: String,
    val createdAt: String,
    val tags: List<String> = emptyList(),
)
```

Rules:
- Use `@Serializable` (kotlinx.serialization) for JSON parsing.
- Match the API field names exactly — use `@SerialName` only when Kotlin naming conflicts.
- Default values for optional fields (`= emptyList()`, `= null`).
- No business logic — this is a pure data carrier.

## Database Entity

Lives in `core:database`. Mirrors the Room table schema.

```kotlin
// core/database/model/{Item}Entity.kt

@Entity(tableName = "{items}")
data class {Item}Entity(
    @PrimaryKey
    val id: String,
    val title: String,
    val description: String,
    val imageUrl: String,
    val categoryId: String,
    val createdAt: Instant,
    val tags: List<String>,
)
```

Rules:
- Use `@Entity` with an explicit `tableName`.
- Use `@PrimaryKey` on the unique identifier.
- Use Room type converters for complex types (`List<String>`, `Instant`, enums).
- Use `@ColumnInfo(name = "...")` only when the column name differs from the property name.

### Type Converters

```kotlin
class {Item}TypeConverters {
    @TypeConverter
    fun fromStringList(value: List<String>): String =
        Json.encodeToString(value)

    @TypeConverter
    fun toStringList(value: String): List<String> =
        Json.decodeFromString(value)

    @TypeConverter
    fun fromInstant(value: Instant): Long = value.toEpochMilliseconds()

    @TypeConverter
    fun toInstant(value: Long): Instant = Instant.fromEpochMilliseconds(value)
}
```

### Cross-Reference Tables

For many-to-many relationships (e.g., items ↔ categories):

```kotlin
@Entity(
    tableName = "{item}_category_cross_ref",
    primaryKeys = ["{item}Id", "categoryId"],
    foreignKeys = [
        ForeignKey(entity = {Item}Entity::class, parentColumns = ["id"], childColumns = ["{item}Id"]),
        ForeignKey(entity = CategoryEntity::class, parentColumns = ["id"], childColumns = ["categoryId"]),
    ],
)
data class {Item}CategoryCrossRef(
    val {item}Id: String,
    val categoryId: String,
)
```

### Full-Text Search (FTS)

For search features, add an FTS entity shadowing the main table:

```kotlin
@Fts4(contentEntity = {Item}Entity::class)
@Entity(tableName = "{item}s_fts")
data class {Item}FtsEntity(
    @ColumnInfo(name = "rowid")
    @PrimaryKey val rowId: Int,
    val title: String,
    val description: String,
)
```

Query using `MATCH`:

```kotlin
@Query("SELECT rowid FROM {item}s_fts WHERE {item}s_fts MATCH :query")
fun search{Items}(query: String): Flow<List<Int>>
```

## Domain Model

Lives in `core:model`. The model the rest of the app works with.

```kotlin
// core/model/data/{Item}.kt

data class {Item}(
    val id: String,
    val title: String,
    val description: String,
    val imageUrl: String,
    val category: Category,
    val createdAt: Instant,
    val tags: List<String>,
)
```

Rules:
- No annotations — this is a plain Kotlin data class.
- Use domain types (`Category` object, not `categoryId: String`) when the relationship is always resolved.
- This is the only model that UI composables and ViewModels see.

## Mapping Extensions

Place mapping functions next to the source model.

### Network → Entity

```kotlin
// In core/database/model/{Item}Entity.kt (or a sibling Mapping.kt)

fun Network{Item}.asEntity() = {Item}Entity(
    id = id,
    title = title,
    description = description,
    imageUrl = imageUrl,
    categoryId = categoryId,
    createdAt = Instant.parse(createdAt),
    tags = tags,
)
```

### Entity → Domain

```kotlin
// In core/database/model/{Item}Entity.kt

fun {Item}Entity.asExternalModel() = {Item}(
    id = id,
    title = title,
    description = description,
    imageUrl = imageUrl,
    category = /* resolve from joined data or pass separately */,
    createdAt = createdAt,
    tags = tags,
)
```

### Naming Convention

| Direction | Extension Name |
|-----------|---------------|
| Network → Entity | `asEntity()` |
| Entity → Domain | `asExternalModel()` |
| Domain → Entity (rare) | `toEntity()` |

## Room DAO

```kotlin
@Dao
interface {Item}Dao {

    @Query("SELECT * FROM {items} ORDER BY created_at DESC")
    fun get{Item}Entities(): Flow<List<{Item}Entity>>

    @Query("SELECT * FROM {items} WHERE id = :id")
    fun get{Item}Entity(id: String): Flow<{Item}Entity>

    @Query("SELECT * FROM {items} WHERE category_id = :categoryId")
    fun get{Item}EntitiesByCategory(categoryId: String): Flow<List<{Item}Entity>>

    @Upsert
    suspend fun upsert{Items}(entities: List<{Item}Entity>)

    @Query("DELETE FROM {items} WHERE id IN (:ids)")
    suspend fun delete{Items}(ids: List<String>)
}
```

Rules:
- Read queries return `Flow<T>` for reactive updates.
- Use `@Upsert` instead of separate insert + update.
- Write operations are `suspend` functions.
- Use `@Transaction` for queries involving cross-reference joins.

## Network Data Source

```kotlin
// core/network/AppNetworkDataSource.kt

interface AppNetworkDataSource {
    suspend fun get{Items}(): List<Network{Item}>
    suspend fun get{Items}(ids: List<String>): List<Network{Item}>
    suspend fun get{Item}ChangeList(after: Int): List<NetworkChangeList>
}

// Retrofit implementation
class RetrofitAppNetwork @Inject constructor(
    private val api: AppApi,
) : AppNetworkDataSource {

    override suspend fun get{Items}(): List<Network{Item}> =
        api.get{Items}().data

    override suspend fun get{Items}(ids: List<String>): List<Network{Item}> =
        api.get{Items}(ids = ids).data
}
```

### Demo Data Source (for development)

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

Use build flavors to swap implementations: `demo` flavor uses bundled JSON, `prod` uses Retrofit.

## Anti-Patterns

| Don't | Do Instead |
|-------|------------|
| Reuse `Network{Item}` as Room entity | Create separate `{Item}Entity` |
| Add UI formatting to models (`displayDate: String`) | Format in the composable or a UI-layer mapper |
| Put nullable fields in domain models when they're always present | Only use nullability when genuinely optional |
| Store derived data in the database | Compute it from base fields in mapping extensions |
| Hardcode JSON keys in DAO queries | JSON belongs in network models; DAO queries use column names |
