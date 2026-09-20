# Island — Architecture

This document describes how Island is built and *why*. It is the reference for anyone extending the
app: the rules in [Design rules](#design-rules) are enforced by the code, and the tests in
[Testing strategy](#testing-strategy) exist to keep them enforced.

---

## 1. Goals and constraints

Island is a system-level overlay: it draws above other apps, reacts to events the app itself did not
produce, and must never become a source of jank, battery drain or privacy leakage. Four constraints
shaped every decision:

1. **Android does not give you a Dynamic Island.** There is no API for it. The island is a
   `TYPE_APPLICATION_OVERLAY` window that Island sizes, positions and animates itself, driven by
   public data sources (`NotificationListenerService`, `MediaSessionManager`, `TelephonyManager`,
   `BatteryManager`, Bluetooth broadcast intents, `AlarmManager`).
2. **The window must not steal touches.** A full-screen overlay would break the app underneath. The
   window is exactly the size of the pill/card, positioned at its current bounds, and re-laid out on
   every animation frame only when the bounds actually change.
3. **Decisions must be testable and race-free.** Priority, masking, expiration and phase transitions
   are pure functions in `domain/`, with no Android imports, driven by a single actor coroutine.
4. **Nothing about the device may be assumed.** No hard-coded resolutions, no per-model tables. All
   geometry derives from live insets, dp and the system font scale.

---

## 2. Layer map

```
┌─────────────────────────────────────────────────────────────────────────────┐
│ feature/app         MainActivity · dashboard · settings · customize ·        │
│ (Compose UI)        per-app rules · timers · history · demo · diagnostics    │
├─────────────────────────────────────────────────────────────────────────────┤
│ feature/island      IslandOverlayRoot · IslandContainer · gestures ·         │
│ (overlay UI)        renderers (one per event family) · widgets · preview     │
├─────────────────────────────────────────────────────────────────────────────┤
│ service             IslandService (FGS) · IslandWindowManager ·              │
│                     EventDispatcher · IslandActionRunner · TimerActionReceiver│
├─────────────────────────────────────────────────────────────────────────────┤
│ domain              model · engine (actor) · EventQueue · PriorityResolver · │
│ (pure Kotlin)       PrivacyMasker · ExpirationPolicy · IslandStateMachine ·  │
│                     repository interfaces                                    │
├─────────────────────────────────────────────────────────────────────────────┤
│ data                notification listener · media bridge · battery/bluetooth/ │
│                     call/alarm monitors · timer engine · history store ·     │
│                     DataStore settings · event factories · artwork & icons   │
├─────────────────────────────────────────────────────────────────────────────┤
│ core                AppGraph (composition root) · logging · permissions ·    │
│                     notifications · design tokens · animation · platform     │
└─────────────────────────────────────────────────────────────────────────────┘
```

Dependency direction is strictly downward. `domain` imports nothing from Android; `data` implements
`domain.repository` interfaces; `service` owns platform side effects; UI reads state and sends
commands. `core.AppGraph` is the only place that knows how to build the whole object graph, and it is
initialised by `IslandApplication` so the service and the activity share one instance.

---

## 3. The event model

Everything that can appear in the island is one `IslandEvent` (sealed hierarchy, `domain/model`):

```
IslandEvent.Notification | Media | Call | Charging | Battery | Bluetooth |
            Timer | Stopwatch | Alarm | Navigation | Download | System | Custom
```

Each event carries an `IslandEventMeta` (id, type, priority, timestamps, title, subtitle, icon key,
optional progress, actions, `persistent`, source package/label, `coalesceKey`) plus a typed payload
(`MediaInfo`, `TimerInfo`, `CallInfo`, …). Consequences of this shape:

- **Normalisation at the edge.** Sources differ wildly (a `StatusBarNotification`, a
  `MediaController`, a broadcast `Intent`); they are converted once, in `data/events`, into the same
  model. Everything downstream sees one type.
- **`coalesceKey` is what prevents animation spam.** Progress notifications and media metadata
  updates reuse the same key, so the queue *replaces* rather than appends; the engine reports
  `wasUpdate` and skips the intro animation.
- **`persistent` distinguishes status from interruption.** Music, timers, navigation and downloads
  stay in the mini island; messages, calls and charging events get a full intro then auto-collapse.
- **Actions are declarative.** `IslandAction(kind, …)` is data, not a lambda, so it survives
  process boundaries (pending intents in notifications), can be masked by privacy mode, and can be
  executed from the overlay, from a notification action, or from the app UI by the same runner.

---

## 4. The engine: an actor, not an object graph

`IslandEngine` is a single coroutine acting as a serialiser:

```
commands ──► Channel ──► drain loop ──► queue reduce ──► mask ──► state machine ──► StateFlow
submit()                 (coalesce            (EventQueue)  (PrivacyMasker)  (IslandStateMachine)  uiState
dismiss()                 window)
expand()/collapse()
setEnabled()
cycleEvent()/pin()
device + settings flows
```

Why an actor:

- **One writer.** Every mutation happens on the engine's dispatcher, so there is no locking and no
  ordering ambiguity between "notification arrived" and "user tapped".
- **Coalescing window.** Bursts (a download posting 40 progress updates) are merged before they reach
  the compositor; the UI observes a smooth single change.
- **Snapshot out.** `uiState: StateFlow<IslandUiState>` is immutable, so Compose recomposes on
  actual change and the overlay never reads half-updated state.
- **Testability.** `IslandEngineTest` drives the real loop with a `TestIslandClock` and
  `runTest`/`advanceTimeBy`, asserting the observable contract instead of internals.

Expiration is time-based, not loop-based: `ExpirationPolicy` yields a `TimeoutPolicy(collapseAfterMs,
removeAfterMs, isPersistent)` per event, and the engine schedules against the monotonic clock. Nothing
polls "are we done yet?".

---

## 5. Queue, priority, privacy

Three pure functions decide *what* is shown; the engine only sequences them.

**`EventQueue.reduce(stack, command) → QueueResult`** (upsert / remove / clear / focus / cycle):
higher priority takes focus; at equal priority a transient event takes the display from a persistent
one (the classic "music + WhatsApp") and focus returns when it leaves; a `CRITICAL` persistent event
(an active call) is never covered; equal priority otherwise keeps the current focus for stability;
the stack is capped at `MAX_EVENTS = 8` so a pathological producer cannot grow it.

**`PriorityResolver`** maps an event + settings to a priority, and answers `shouldShow(...)`:
`BACKGROUND` never shows; under Do Not Disturb the threshold is `CRITICAL`, or `HIGH` when the user
allows critical events during DND; per-source switches (media, calls, charging, battery, devices) and
the per-app notification rules and the importance floor are applied here — in one place, not scattered
through the UI.

**`PrivacyMasker`** applies the effective mode (`notifications.privacyMode`, or
`privacy.lockScreenMode` when the screen is locked) and returns a *new* event with content removed:
`FULL` keeps everything, `PRIVATE` drops title/text/actions, `ICON_ONLY` additionally drops action
counts, `SENSITIVE_HIDDEN` downgrades notifications the sender marked private. Masking happens in the
domain, before the event ever reaches a renderer or the history store — so a leak is structurally
impossible rather than a matter of remembering to check in the UI.

---

## 6. State machine and animation

`IslandPhase`: `IDLE · COLLAPSED · EXPANDING · EXPANDED · INTERACTING · COLLAPSING · TRANSIENT ·
PINNED · DISABLED`.

`IslandTrigger`: `EventArrived(priority, persistent, isUpdate, focusChanged) · IntroElapsed ·
AutoCollapseElapsed · ExpandRequested · CollapseRequested · InteractionStarted · InteractionEnded ·
AnimationCompleted · UserPinned · UserUnpinned · EventRemoved · QueueCleared · Disabled · Enabled ·
ScreenTurnedOff · ScreenTurnedOn`.

`IslandStateMachine.next(current, trigger, context)` is **total**: every phase/trigger pair yields a
defined phase. `TransitionContext` carries only the facts the table needs
(`hasEvents`, `hasPersistentEvents`, `hasTransientEvents`, `expandedByUser`, `pinnedByUser`), which is
what keeps the machine a pure function.

Representative flows:

```
transient   IDLE → EventArrived → TRANSIENT → AutoCollapseElapsed → COLLAPSING → AnimationCompleted → IDLE
persistent  IDLE → EventArrived → EXPANDING → AnimationCompleted → EXPANDED → IntroElapsed
                → COLLAPSING → AnimationCompleted → COLLAPSED (mini island)
over music  COLLAPSED → EventArrived(transient) → PINNED → AutoCollapseElapsed → COLLAPSING → COLLAPSED
```

Hard overrides are evaluated first: `Disabled` always wins, `ScreenTurnedOff` always yields `IDLE`,
and nothing except `Enabled`/`ScreenTurnedOn` leaves `DISABLED`.

**Animation.** The renderer owns the interpolation between collapsed and expanded bounds; the machine
only names the phase. `IslandAnimation` exposes typed specs (`expand`, `collapse`, `bounce`, `morph`,
`slide`, `crossfade`, `fade`, `progress`) whose durations come from the design tokens
(`IslandMotion`) scaled by the user's `animationSpeed` and `animationStyle`, clamped when
`reduceMotion` is on or the system animator scale is zero. Every visibility change goes through an
animation; there is no `visible = true` jump anywhere, and the animation fallback timer guarantees the
machine cannot get stuck in `EXPANDING` if a frame callback is lost.

---

## 7. Window management

`service/IslandWindowManager` is the **only** file that talks to `WindowManager`:

- `canDrawOverlays()` — `Settings.canDrawOverlays()`, the single source of truth for the permission.
- `attach(view, bounds)` — `TYPE_APPLICATION_OVERLAY`, `FLAG_NOT_FOCUSABLE | FLAG_LAYOUT_NO_LIMITS |
  FLAG_HARDWARE_ACCELERATED`, no dim, no blur behind, `PixelFormat.TRANSLUCENT`, gravity top|start so
  coordinates are absolute and predictable in landscape.
- `updateBounds(...)` — `updateViewLayout` with the animated `WindowManager.LayoutParams`; called only
  when width/height/x/y actually change, which is what keeps resizing cheap during a morph.
- `setVisible(...)` — visibility is driven by the phase (`IDLE`/`DISABLED` → `GONE`), so the window
  costs nothing while idle but stays attached (re-attaching is slow and flickers).
- `detach()` — on service stop and on permission loss; `lastError` is surfaced to diagnostics.

Composition lives in `OverlayComposeOwners`: one `ComposeView` per attached window, with its own
`ViewTreeLifecycleOwner`/`ViewModelStoreOwner`/`SavedStateRegistryOwner` so the overlay survives
configuration changes and does not depend on the activity being alive.

`IslandService` is the host: `specialUse` foreground service (subtype property declared in the
manifest), started only while Island is switched on, `START_STICKY` so the system may restore it, and
on API 29–33 it calls `startForeground` without a type (passing `SPECIAL_USE` there fails validation).
It hosts the engine, the dispatcher and the window manager — and nothing else. Timers live in their own
engine; listening lives in the dispatcher; deciding lives in the domain.

---

## 8. Data sources

| Source | Mechanism | Notes |
| --- | --- | --- |
| Notifications | `IslandNotificationListener` (`NotificationListenerService`) | Reads title/text/extras/group/actions/importance; forwards to `NotificationRepository`. `requestRebind` on reconnect so the island survives a listener restart. Media-style notifications are skipped while the media bridge is active. |
| Media | `MediaSessionManager` active sessions + `MediaController` callbacks | Title/artist/album/duration/position/artwork/capabilities. Position is stored as `(positionMs, sampledAtElapsedMs)` and derived with `SystemClock.elapsedRealtime()`, so the seek bar moves without polling the session. Artwork is decoded off the main thread and cached by `ArtworkCache`. |
| Timers / stopwatch | `TimerEngine` + `AlarmManager` | Multiple concurrent timers, pause/resume/stop/restart, stopwatch with laps. Completion scheduled via an exact alarm (inexact fallback) *and* a notification; the island is never the only alert. `TimerActionReceiver` handles alarm/toggle/stop/dismiss/restart even if the process was killed. |
| Battery / charging | `BatteryManager` broadcasts + sticky `ACTION_BATTERY_CHANGED` | Edge-triggered: plug/unplug, low battery, user threshold, full. Wattage is derived from voltage × current when both are reported; temperature in tenths of a degree. |
| Bluetooth / headphones | `ACTION_ACL_CONNECTED`/`DISCONNECTED`, `ACTION_HEADSET_PLUG` | Connection changes only. No scanning, no paired-device enumeration, no MAC addresses rendered. Names only when `BLUETOOTH_CONNECT` is granted. |
| Calls | `TelephonyManager` listen + call-style notifications | State from `CallState`; answer/end/mute via `TelecomManager` only when `ANSWER_PHONE_CALLS` is granted, otherwise the action opens the dialer. |
| Alarms | `AlarmManager.getNextAlarmClock()` | Next alarm plus a configurable lead time. Third-party apps cannot dismiss another app's alarm, so actions open the alarm owner. |
| Downloads / navigation | Parsed from notification extras | Progress fraction, ETA, maneuver/distance/street. Public fields only — no accessibility scraping. |
| Settings | DataStore (protobuf-free, JSON/Preferences) | `SettingsRepository` exposes a `Flow` and suspend `update`; `IslandSettings` is immutable, so partial updates are `copy()` transforms recorded by tests. |
| History | `FileEventHistoryRepository` | **Off by default**, metadata-only unless content is enabled, retention-pruned on write, excluded from backup, exported through `FileProvider` on explicit user action. |

Each monitor implements the same lifecycle contract — `start()`/`stop()` and an `applySettings(prev,
next)` — and `EventDispatcher` registers/unregisters them in response to settings changes. A disabled
feature is not merely filtered at render time; its receiver is unregistered and its listener released.

---

## 9. Overlay UI

`feature/island`:

- **`layout/IslandMetrics`** — pure geometry. `IslandLayoutInput` (screen dp, safe insets, cutout
  rect/kind, landscape, font scale, position, offsets, landscape behaviour, appearance) goes in;
  `IslandBounds(widthDp, heightDp, xDp, yDp, cornerRadiusDp, visible, minimized)` comes out, for the
  collapsed pill (`collapsed(input)`), the expanded card (`expanded(input, type)`) and per-type heights
  (`expandedHeightDp(type, appearance)`). Rules: the pill stays inside a design band (min/max width and
  height constants), never wider than `screen − margin`, centred unless a cutout would be covered
  (then it shifts), `AUTO_CUTOUT` places the pill over a centred punch hole, user scale and font scale
  widen rather than clip, expanded width is capped on large screens, and landscape can `ADAPT`,
  `MINIMIZE` (a dot) or `HIDE`. Because it is a pure function over dp, one set of unit tests covers
  every device shape.
- **`IslandOverlayRoot` / `IslandContainer`** — measure, animate bounds, publish the window rect back to
  the window manager, host the renderer, and draw the surface (`IslandColors.islandSurfaceColor`,
  elevation, optional blur) with the animated corner radius.
- **`IslandGestures` + `IslandGestureRouter`** — pointer input is mapped to user-configurable actions
  (`TapAction`, `DoubleTapAction`, `LongPressAction`, `HorizontalSwipeAction`, `VerticalSwipeAction`)
  and translated into engine commands (`beginInteraction`, `cycleEvent`, `dismissFocused`, `expand`,
  `collapse`, `endInteraction`, `performAction`). Touch slop and velocity thresholds come from
  `ViewConfiguration`, not magic numbers; haptics honour the setting and require no permission.
- **`renderers/`** — an `IslandRenderer` per family with `canRender(event)`, `order`, and slots for
  collapsed / expanded / minimized content, plus a generic renderer at `order = 1000` as the fallback.
  Orders are fixed constants (Call 5, Media 10, Alarm 20, Navigation 25, Timer 30, Stopwatch 35,
  Charging 40, Battery 45, Device 50, Download 55, Notification 60, System 70, Custom 75) so the
  resolution is deterministic and testable. `IslandRenderMode` selects the slot.
- **`widgets/`** — shared primitives (artwork, waveform, progress ring/bar, icon set, formatted
  labels via `IslandFormat`). Formatting is centralised so every renderer shows the same clock,
  percent, wattage, distance and byte strings — and so those strings are unit-tested.
- **`IslandPreview`** — renders a real event with the real renderers inside the app UI (dashboard,
  demo, customize) so settings previews and demo rows are pixel-identical to the overlay. It sets the
  phase explicitly (`COLLAPSED`/`EXPANDED`) so `isRendering` is true without a window.

---

## 10. App UI and composition root

`feature/app` screens are plain Compose with a small shared kit (`IslandScaffold`, `SectionHeader`,
`SettingsCard`, `ToggleRow`, `ClickRow`, `EnumChoiceRow`, `SliderRow`, `StatusChip`, `EmptyState`,
`PermissionRow`, `DiagnosticRow`). `rememberSettingsUpdate()` turns a screen-local control into a
`(IslandSettings) -> IslandSettings` transform, so every screen writes settings the same way and the
dispatcher reacts to the resulting flow change.

`core/AppGraph` builds everything lazily and exposes the singletons the service and the activity
share (engine, dispatcher, repositories, monitors, renderer registry, logger, permission launcher,
artwork/icon providers, cutout & display providers, plus `StateFlow`s for notification access, service
running and overlay attachment). It is the only composition root — no DI framework, no reflection, no
generated code, so cold start stays cheap and the graph is readable in one file.

Permissions are handled through `PermissionKey` + `PermissionStatus` (granted/required/affects feature/
severity) and `PermissionIntents`, which produce the right system screen per API level and per OEM
(`oemBatteryIntents(context)` returns intents that actually resolve). The UI never claims a permission
is granted; it reads status, explains the consequence of missing it, and offers a fix.

---

## 11. Battery and performance budget

| Concern | Mechanism |
| --- | --- |
| No polling | Derived time (media position, timer countdown, stopwatch tenths, call duration) from `SystemClock.elapsedRealtime()`; one shared ticker that runs only while the island is visible and only for types that need it. |
| No duplicate animations | Event coalescing window + `coalesceKey` replacement + `isUpdate` suppressing intro animations. |
| Edge-triggered sources | Charging, battery threshold, device connection, call state emit on change with de-duplication. |
| Minimal window work | `updateViewLayout` only when bounds change; window sized to the pill; no dim, no always-on blur. |
| Idle cost | Overlay hidden and no ticker in `IDLE`; monitors unregistered when their setting is off; one foreground service, stopped when Island is off; no `WorkManager`, no wake locks. |
| Measurement | The Perf screen reports FPS from a `Choreographer` callback, heap usage, and engine statistics, so a regression is visible in-app rather than anecdotal. |

Budgets the code aims at: collapsed pill ≤ 210 dp wide, expanded card ≤ 420 dp, animations within the
design-token durations, no frame in which the window is resized more than once.

---

## 12. Failure modes and degradation

Island is built to fail one feature at a time, never as a whole:

- **Overlay permission revoked** → engine `Disabled(OVERLAY_PERMISSION_MISSING)`, window detached, a
  recovery notification with a one-tap fix, dashboard shows the reason.
- **Notification listener disconnected** → notifications stop, everything else continues; the listener
  requests a rebind and diagnostics report the connection state.
- **Media access denied / no session** → media renderer idle; `accessDenied` is surfaced so the UI can
  explain rather than show nothing.
- **Missing `BLUETOOTH_CONNECT` / `READ_PHONE_STATE` / `ANSWER_PHONE_CALLS` / exact alarms** → the
  specific action or label is not rendered; the event still appears with reduced fidelity.
- **OEM killed the service** → `START_STICKY`, boot receiver, and a permission/service recovery path;
  diagnostics offers the OEM battery screens.
- **Any platform call throwing** → wrapped, logged through `IslandLogger` (in-memory, redacted ring
  buffer), and degraded; the event log screen exists so a user can show you what happened without
  Island shipping a crash reporter.

---

## 13. Design rules

1. One immutable event model; normalise at the edge.
2. The engine is an actor: commands in, one snapshot out.
3. UI decides *how*, domain decides *what* (priority, privacy, expiration).
4. Never hard-code device dimensions; derive from insets and dp.
5. No abrupt visibility changes; every transition animates, and respects reduce-motion / animator scale.
6. One responsibility per component: service hosts, window manager draws, dispatcher listens, engine
   decides, renderers render.
7. Fail soft and explain: degrade one feature, log it, tell the user how to fix it.
8. No telemetry, no analytics, no network, no identifier persisted anywhere.
9. Never expose sensitive content by default; masking happens in the domain.
10. Never make the overlay the only alert; real notifications back it up.

---

## 14. Testing strategy

- **JVM unit tests** (`app/src/test`) for everything that can be pure: engine actor loop (virtual
  clock), queue/focus rules, priority + DND, privacy masking, state machine, expiration, geometry,
  formatting, notification grouping, renderer resolution. These are the invariants users feel and
  they run in milliseconds.
- **Lint** configured to fail on errors, with a `lint.xml` that documents intentional suppressions.
- **CI** (`.github/workflows/build.yml`) runs assemble (debug + release) → unit tests → lint →
  instrumented-test compile on every push; a green run commits the APKs to `dist/`, a red run commits
  `ci-logs/`, so every build is reproducible and diagnosable from the repository alone. A second,
  manual job runs the instrumented suite on an API 36 emulator.
- **Manual, in-app**: Demo mode drives real events through the real pipeline (with inert controls),
  and Diagnostics/Event log/Perf expose live capability state, the redacted event log and frame
  timing. This is deliberately used for the parts that cannot be unit-tested honestly — overlay
  attachment, window flags, real `MediaSession` behaviour, OEM quirks.
- **Instrumented tests** (`app/src/androidTest`) for the parts a JVM cannot answer: app launch and
  object-graph construction on a real device (`MainActivitySmokeTest`), geometry checked against the
  actual display at every user scale and orientation (`IslandGeometryOnDeviceTest`), and the real
  renderers driven through `IslandPreview` with content asserted in the semantics tree
  (`IslandRendererInstrumentedTest`). CI compiles them on every push (`assembleDebugAndroidTest`) and
  runs them on an emulator through the manual `ui-tests` job, because booting an AVD costs more than
  the entire JVM build and a push should not wait for it.
- **Still manual by choice**: overlay attachment and window flags (they need `SYSTEM_ALERT_WINDOW`,
  which no emulator grants without user action), live `MediaSession` interaction (needs a real player
  holding audio focus) and OEM power-management behaviour. Demo mode and the Diagnostics screen exist
  to make those checks fast and honest instead of anecdotal.
- **Next**: gesture-threshold tests (drag/swipe velocity against `ViewConfiguration`), which need the
  overlay permission and therefore a granted-permission emulator snapshot rather than a plain AVD.

---

## 15. Extending Island

- **New event family** — see README "Adding a new event type": model + factory + priority + expiration
  + renderer + demo entry. No engine, window or gesture changes.
- **New gesture** — add the enum value, a settings row, and one branch in `IslandGestureRouter` that
  emits an existing engine command.
- **New setting** — add the field to the relevant `IslandSettings` sub-model (it is immutable, so
  defaults are explicit), a UI row using `rememberSettingsUpdate()`, and react to it in
  `EventDispatcher.applySettings` or `IslandMetrics` — never by reaching into the engine.
- **New source** — implement a monitor with `start/stop/applySettings`, register it in the dispatcher,
  and build events with `IslandEventFactories`. The engine, queue, renderers and window need no change.

---

## 16. Deliberate omissions

- **No screen reading / accessibility-tree scraping** to detect other apps' state. It is fragile,
  invasive and a Play policy risk.
- **No `QUERY_ALL_PACKAGES`.** Per-app rules are built from apps that have actually posted a
  notification.
- **No background location, contacts, call log or SMS access**, even though they would let Island show
  richer call/navigation info.
- **No widget/notification-only "lite" mode.** A notification cannot be an interactive, animated pill
  above other apps; pretending otherwise would produce a worse product.
- **No plugin system.** The renderer registry is closed and ordered on purpose: determinism and
  reviewability beat extensibility for a system overlay.
