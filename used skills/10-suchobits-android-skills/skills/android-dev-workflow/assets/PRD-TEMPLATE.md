# PRD: {Feature Name}

## Problem Statement

{1-2 sentences describing the user problem this feature solves.}

## User Stories

- [ ] As a {user}, I can {action} so that {benefit}
- [ ] As a {user}, I can {action} so that {benefit}

## Screens

### {Feature} List Screen

**Success state:**
- Shows a list of {items} sorted by {criteria}
- Each item displays: {title, subtitle, image, etc.}
- Tapping an item navigates to the detail screen

**Loading state:**
- Shows a centered loading indicator

**Empty state:**
- Shows an icon + message: "{No items found}"
- Optional action button: "{Add first item}"

**Error state:**
- Shows error message with retry button

### {Feature} Detail Screen

**Success state:**
- Shows {item} details: {fields}
- Action buttons: {bookmark, share, etc.}

**Loading state:**
- Shows a centered loading indicator

## Data

### Models

- `{Item}`: {id, title, description, imageUrl, createdAt, ...}
- `{Category}`: {id, name}
- Relationship: {Item} belongs to one {Category}

### Data Source

- Network: {API endpoint or describe the data source}
- Local: Room database (offline-first)
- Sync: {describe sync strategy — delta, full, manual refresh}

## Navigation

- Entry point: {bottom nav tab / deep link / button in another screen}
- List → Detail: tap item card
- Detail → Back: system back / toolbar back

## Edge Cases

- [ ] What happens with no network on first launch?
- [ ] What happens when the list has 1000+ items?
- [ ] What happens when an item is deleted while being viewed?
- [ ] What happens when sync fails mid-way?

## Out of Scope

- {Thing we explicitly won't build in this iteration}
- {Another thing}

## Acceptance Criteria

- [ ] List screen shows items from local database
- [ ] Detail screen shows full item details
- [ ] Offline: app works with no network after first sync
- [ ] Pull-to-refresh triggers sync
- [ ] Architecture guard passes
- [ ] Unit tests for ViewModel (Loading, Success, Error states)
- [ ] Screenshot tests for all screen states

---

## Tasks (Agent-Executable)

<!-- Break into vertical slices. Each task = one Ralph iteration. -->

### Slice 1: Data Foundation
- [ ] Create `{Item}` domain model in `core:model`
- [ ] Create `{Item}Entity` + `{Item}Dao` in `core:database`
- [ ] Create `{Item}Repository` interface + `OfflineFirst{Item}Repository` in `core:data`
- [ ] Create `Network{Item}` + mapping extensions
- [ ] Add DI bindings in DataModule

### Slice 2: Feature Scaffold (Tracer Bullet)
- [ ] Create `{Feature}NavKey` in `feature:{feature}:api`
- [ ] Create `{Feature}ViewModel` + `{Feature}UiState` in `feature:{feature}:impl`
- [ ] Create `{Feature}Screen` (success state only) + `{Feature}Route`
- [ ] Wire navigation in app module (EntryProvider)
- [ ] Verify: app builds, navigates to feature, shows data

### Slice 3: Tests + Error States
- [ ] Create `Test{Item}Repository` fake in `core:testing`
- [ ] Create `{Feature}ViewModelTest` (Loading, Success, Error)
- [ ] Add Loading/Error/Empty states to `{Feature}Screen`
- [ ] Run all feedback loops: architectureGuard, tests, lint

### Slice 4: Detail Screen
- [ ] Create `{Feature}DetailNavKey` in api module
- [ ] Create `{Feature}DetailViewModel` + `{Feature}DetailUiState`
- [ ] Create `{Feature}DetailScreen`
- [ ] Wire list → detail navigation
- [ ] Add ViewModel test for detail

### Slice 5: Sync + Offline
- [ ] Implement `syncWith()` in repository
- [ ] Add to SyncWorker
- [ ] Pull-to-refresh in list screen
- [ ] Offline banner integration

### Slice 6: Polish
- [ ] Screenshot tests (all states × light/dark)
- [ ] Edge case handling (from Edge Cases section above)
- [ ] Accessibility (content descriptions, focus order)
