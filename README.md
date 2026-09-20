# Island — a Dynamic Island–style notification hub for Android

**Island** turns the top of any Android screen into a live, interactive pill: music transport,
timers and stopwatch, incoming calls, charging and battery, Bluetooth/headphone connections,
downloads, turn-by-turn hints, alarms and any notification — all in one place, animated the way
iPhone users expect, built the way Android requires.

It is a real, working system service — not a mock-up. Events flow from Android's own APIs into a
priority queue and a state machine, and are drawn into a `TYPE_APPLICATION_OVERLAY` window that is
exactly the size of the pill, so the app underneath keeps every touch that is not on the island.

```
Kotlin · Jetpack Compose · Material 3 · minSdk 26 · targetSdk 35 · no analytics, no telemetry
```

---

## Contents

- [What it does](#what-it-does)
- [Install & build](#install--build)
- [First run](#first-run)
- [Permissions — and why each one exists](#permissions--and-why-each-one-exists)
- [Privacy](#privacy)
- [Battery](#battery)
- [Architecture](#architecture)
- [Adding a new event type](#adding-a-new-event-type)
- [Testing](#testing)
- [Troubleshooting / OEM notes](#troubleshooting--oem-notes)
- [Known limits (by design)](#known-limits-by-design)
- [Project layout](#project-layout)

---

## What it does

| Area | Behaviour |
| --- | --- |
| **Notifications** | Any notification can appear in the pill, grouped per app/thread, with the source app's icon and its own action buttons. Media-style notifications are ignored while the media bridge is on, so a track never shows up twice. |
| **Media** | Reads the active `MediaSession` (title, artist, album art, position, duration, capabilities). Position is *derived* from a monotonic sample — Island never polls the session. Collapsed: art + title + a breathing waveform. Expanded: seek bar + transport controls that only appear when the session can actually execute them. |
| **Timers & stopwatch** | Island's own timers: multiple concurrent countdowns, labels, pause/resume/stop/+restart, a stopwatch with laps and tenths. Completion is scheduled through `AlarmManager`, so it fires even if the process was killed, and a notification is always posted as a fallback — the island is never the only alarm. |
| **Calls** | Incoming / active / on-hold / ended from `TelephonyManager` + `TelecomManager`. Answer, end and mute buttons appear only when `ANSWER_PHONE_CALLS` is granted; otherwise the island offers "Open Phone". An active call is `CRITICAL` and is never covered by another event. |
| **Charging & battery** | Plug/unplug edges, wattage (V × A when the platform reports both), temperature, health, full charge, low battery and a user-defined threshold. Edges, not levels: you get one event when charging starts, not one per percent. |
| **Bluetooth / headphones / watch / car** | Connection *changes* only (`ACTION_ACL_CONNECTED`, `ACTION_HEADSET_PLUG`). Island never scans, never enumerates paired devices, and never shows a MAC address. |
| **Alarms** | The next system alarm, with a configurable lead time. A third-party app cannot dismiss another app's alarm, so the island's buttons open the alarm owner instead of pretending. |
| **Downloads / navigation** | Progress notifications are parsed into real progress bars and turn-by-turn hints (maneuver, distance, street, ETA) — from the notification's public fields, never from screen scraping. |
| **Queue & priorities** | Up to 8 simultaneous events, one focused. Transient events (a message) briefly take the display from persistent ones (music) and focus returns afterwards. Swipe to browse the stack; dots show how many events are live. |
| **Gestures** | Tap, double-tap, long-press, swipe left/right/up/down, drag — each one mapped to a behaviour the user chooses in Settings. |
| **Cutout awareness** | Geometry comes from `WindowInsetsCompat.getDisplayCutout()` at runtime. Punch-hole devices get the pill centred over the camera; notches and waterdrops get a safe centred placement; the pill shifts if an unusual cutout would be covered. No device tables, no hard-coded coordinates. |
| **Foldables, tablets, landscape** | Every size is derived from live dp insets and the system font scale. Landscape can adapt, minimize to a dot, or hide — the user's choice. |
| **Appearance** | 8 presets, size/corner/offset/icon/text scales, opacity, blur, theme (pure black / dark grey / dynamic), accent colour, animation speed and style (springy/smooth/snappy), plus full respect for the system "remove animations" setting. |
| **Privacy** | Four notification privacy modes, a separate (stricter) lock-screen mode, per-app rules, sensitive-content hiding, and event history that is **off by default** and metadata-only unless you opt into content. |
| **Developer tools** | Demo mode (sample events in five categories, preview before you send, transport controls inert), live diagnostics, event log, FPS/frame-time/memory screen. |

---

## Install & build

### From a release artifact

```bash
adb install island-release.apk
```

Release builds are minified + resource-shrunk. If `keystore.properties` is absent the release build
falls back to the debug keystore so the APK is installable for evaluation — replace it with a real
upload key before publishing:

```properties
# keystore.properties  (never committed)
storeFile=/absolute/path/to/upload-keystore.jks
storePassword=...
keyAlias=...
keyPassword=...
```

### From source

```bash
./gradlew assembleDebug          # app/build/outputs/apk/debug/app-debug.apk
./gradlew test                   # JVM unit tests (domain, layout, formatting, grouping)
./gradlew lint                   # lint is configured to fail on errors
./gradlew assembleRelease
```

Requirements: JDK 17, Android SDK 35. The debug variant uses the application id suffix `.debug`, so
a debug and a release build can be installed side by side.

CI (GitHub Actions, `.github/workflows/build.yml`) runs `assembleDebug` → `test` → `lint` on every
push, and publishes `ci-logs/` plus the APK artifacts when something fails, so a red build is always
diagnosable from the repository alone.

### Targeting Android 16 / API 36

The project compiles against SDK 35 today. Moving to 36 is a two-line change (`compileSdk`/`targetSdk`
in `app/build.gradle.kts`) plus a toolchain bump: API 36 needs AGP 8.9.1+ and Gradle 8.11.1+. Nothing
in the source uses deprecated window or notification behaviour that changes at 36 — the overlay uses
`TYPE_APPLICATION_OVERLAY`, the foreground service declares `specialUse` with a subtype property, and
edge-to-edge is already the default layout mode.

---

## First run

1. **Overlay permission** — Island explains why it needs "Display over other apps" and opens the
   system screen. Without it nothing can be drawn; the app says so plainly instead of failing
   silently.
2. **Notification access** — optional but recommended. This is the deep Android settings page; the
   onboarding tells you exactly what to tap.
3. **Test the island** — a live preview plus a "send a demo event" button, so you see it working
   before you rely on it.
4. **Done** — Island is switched on and the service starts.

Every step can be skipped. Island is useful with the overlay alone (timers, charging, media).

---

## Permissions — and why each one exists

| Permission | Why | What happens without it |
| --- | --- | --- |
| `SYSTEM_ALERT_WINDOW` | The island window itself. Granted by you, in system settings, never silently. | No island. The dashboard shows a fix button. |
| `BIND_NOTIFICATION_LISTENER_SERVICE` | To see notifications. Enabled by you in system settings. | Notifications, grouped messages and download/navigation parsing are off. Media, timers, charging, Bluetooth and calls keep working. |
| `POST_NOTIFICATIONS` | Island's own notifications: timer completion, the ongoing service notification, permission-recovery notices. | Timers still fire in the island; the fallback notification is not shown. |
| `FOREGROUND_SERVICE` + `FOREGROUND_SERVICE_SPECIAL_USE` | A user-visible overlay drawn above other apps is exactly what Android requires a foreground service for. Declared with `PROPERTY_SPECIAL_USE_FGS_SUBTYPE` explaining the use. | The service cannot run on API 34+. |
| `RECEIVE_BOOT_COMPLETED` | Restores *your own preference* after a reboot. Nothing is scheduled, no job is registered. | Island comes back when you next open the app instead of at boot. |
| `BLUETOOTH_CONNECT` | Names of already-connected audio/wearable devices. | Device events still fire, with a generic label ("Bluetooth audio") instead of a name. |
| `READ_PHONE_STATE` | Incoming/ongoing call state. | The call island is disabled; everything else is untouched. |
| `ANSWER_PHONE_CALLS` | The island's Answer/End/Mute buttons. | Those buttons are not rendered — Island offers "Open Phone" instead. |
| `MODIFY_AUDIO_SETTINGS` | Mute/unmute the microphone during a call. | The mute button is not rendered. |
| `SCHEDULE_EXACT_ALARM` | Timer completion while the screen is off. | Timers complete with an inexact alarm (may be a little late) — they still complete. |
| `VIBRATE` | Optional haptic confirmation for timer completion. | No haptics. View haptics for gestures need no permission. |

**Deliberately not requested:** location, camera, microphone, contacts, call log, SMS, storage,
`QUERY_ALL_PACKAGES`, accessibility services, media projection. Island has no reason to know where
you are, who your contacts are, or what is on your screen.

---

## Privacy

- **No telemetry.** No analytics SDK, no crash reporter, no advertising ID, no network permission.
  The app does not talk to the internet at all.
- **Notification content stays on the device.** It lives in memory only while the notification is
  live, and is never written to disk unless you turn on history *and* history content.
- **Four privacy modes** — Full, Private ("New message"), Icon only, Sensitive hidden — plus a
  separate, stricter mode that applies on the lock screen.
- **Per-app rules.** Any app that has posted a notification can be set to always / never /
  important only / icon only / full preview. The list is built from apps you have actually seen —
  Island never enumerates your installed apps.
- **No identifiers on screen.** Bluetooth MAC addresses are never rendered. Caller handles are shown
  only if you allow it, and only from what the system already published.
- **History is opt-in, bounded, pruned by retention, and never backed up** (see
  `res/xml/backup_rules.xml` and `res/xml/data_extraction_rules.xml`).
- **The event log is in memory only** and redacted before it is written; clearing it is permanent.

---

## Battery

The overlay is only alive while you have Island switched on, and it is engineered to be boring when
nothing is happening:

- **No polling.** Media position, timer countdowns, stopwatch tenths and call durations are
  *derived* from a monotonic timestamp. One shared ticker runs while the island is visible **and**
  only for event types that need it (a running stopwatch ticks at 10 Hz; a charging pill does not
  tick at all).
- **Edges, not levels.** Charging, battery threshold, device connection and call state are emitted on
  change, with de-duplication, so a chatty source cannot animate the island four times in 100 ms.
- **Event coalescing.** The engine batches bursts (progress notifications, metadata updates) into a
  single state change before it reaches the compositor.
- **Monitors follow settings.** Turn off Bluetooth events and the receiver is unregistered; turn off
  media and the session listener is released.
- **One foreground service**, stopped the moment Island is switched off. No `WorkManager` jobs, no
  wake locks, no periodic alarms except the ones your timers actually need.
- **The window is the size of the pill** — no full-screen overlay, no dim, no always-on blur pass.

---

## Architecture

Full detail lives in [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md). The short version:

```
Android APIs ──► data layer ──► IslandEvent ──► IslandEngine ──► IslandUiState ──► Compose overlay
 (listeners,      (monitors,      (one sealed     (actor: priority,   (immutable     (renderers,
  sessions,        factories,      hierarchy)      masking, queue,     snapshot)      gestures,
  broadcasts)      repositories)                   state machine)                     window)
```

- **`domain/`** — pure Kotlin. Models, the engine (actor coroutine), event queue, priority resolver,
  privacy masker, expiration policy, state machine, repository interfaces. No Android imports, so it
  is unit-testable on the JVM in milliseconds.
- **`data/`** — everything that touches Android: the notification listener, media-session bridge,
  battery/Bluetooth/call/alarm monitors, DataStore settings, timer engine, history store, event
  factories.
- **`service/`** — the foreground service, the `WindowManager` wrapper (the *only* place window calls
  live), the event dispatcher and the action runner.
- **`feature/island/`** — the overlay: layout metrics, gestures, container, and one renderer per
  event family.
- **`feature/app/`** — dashboard, settings, customize, per-app rules, timers, history, demo,
  diagnostics, event log, performance.
- **`core/`** — composition root (`AppGraph`), logging, permissions, notifications, design tokens,
  animations, platform helpers.

Design rules the codebase holds itself to:

1. One immutable event model; every source normalises into it.
2. The engine is an actor: commands in, one state snapshot out. No UI thread races.
3. The UI never decides *what* to show, only *how*. Privacy, priority and expiration are domain rules.
4. Nothing is hard-coded per device: geometry comes from live insets and dp math.
5. No abrupt visibility changes: everything animates, and the animation spec respects the user's
   speed, style, "reduce motion" and the system animator scale.
6. Separate responsibilities: the service hosts, the window manager draws, the dispatcher listens,
   the engine decides, the renderers render.
7. Fail soft: every platform call that can throw is wrapped, logged and degraded — a revoked
   permission disables one feature, never the app.

---

## Adding a new event type

1. Add a case to `IslandEventType` and a data class to the `IslandEvent` sealed hierarchy
   (`domain/model/IslandEvent.kt`).
2. Build it from your source in `data/events/IslandEventFactories.kt` (titles from resources, actions
   with `IslandActionKind`).
3. Give it a priority in `PriorityResolver` and an expiration in `ExpirationPolicy`.
4. Add a renderer: one entry in `feature/island/ui/renderers/EventIslands.kt` with three slots —
   collapsed, expanded, minimized — and register it in `defaultIslandRenderers()`.
5. Add a demo entry in `feature/demo/DemoEvents.kt` and a sample row in the demo screen.

Nothing else changes: the queue, the state machine, the window, the gestures and the history all work
against `IslandEvent`, so a new family is picked up automatically.

---

## Testing

```bash
./gradlew test        # JVM unit tests
./gradlew lint        # static analysis (errors fail the build)
```

Unit tests cover the parts where a bug is invisible until it happens on someone else's phone:

- **`EventQueueTest`** — focus rules: priority wins, transient covers persistent and returns focus,
  a CRITICAL call is never covered, coalescing replaces instead of stacking, the 8-event cap holds.
- **`PriorityResolverTest`** — Do Not Disturb thresholds, per-source switches, importance floor.
- **`PrivacyMaskerTest`** — lock-screen mode override, icon-only strips every trace of content,
  sender-marked-sensitive notifications are hidden even in full mode.
- **`IslandStateMachineTest`** — expand/collapse, interaction suspends auto-collapse, disable wins,
  only an explicit enable leaves `DISABLED`, screen-off hides.
- **`ExpirationPolicyTest`** — persistent events never expire by timer; transient ones collapse then
  expire.
- **`IslandEngineTest`** — the actor loop on a virtual clock: submit → render, stack → focus,
  dismiss → empty, disable → stop rendering, screen off/on, cycle, coalesce.
- **`IslandMetricsTest`** — the pill stays inside its design band on a 240dp screen, a 412dp phone, a
  900dp tablet, in landscape, at font scale 1.3, at size scale 0.7 and 1.5; it covers a centred punch
  hole; expanded cards are capped; landscape can hide or minimize.
- **`IslandFormatTest`** — clocks, tenths, percent clamping, wattage, byte units, distance units, ETA.
- **`NotificationGroupingTest`** — buckets, app separation, opt-out, system summaries, ongoing
  notifications never grouping, sibling lookup.
- **`IslandRendererRegistryTest`** — every event family resolves to a renderer, dedicated renderers
  beat the generic fallback.

UI-level behaviour that needs a device (overlay attachment, window flags, real `MediaSession`
interaction) is exercised manually through **Demo mode** and the **Diagnostics** screen, which report
the live state of every capability.

---

## Troubleshooting / OEM notes

The diagnostics screen reports the truth about your device: overlay permission, listener connection,
service state, window attachment, engine phase, cutout geometry, battery-optimisation status.

Xiaomi/Redmi/Poco, Samsung, Huawei, Oppo/Vivo and others aggressively kill background services and
require an extra "autostart" toggle. Island cannot flip those switches for you, but it:

- detects the manufacturer and offers the matching OEM settings screen (when the intent exists),
- posts a recovery notification with a one-tap fix if the overlay permission is revoked,
- restores itself after a reboot (an exemption Android grants) and after a process restart,
- degrades instead of crashing when any single capability disappears.

If the island disappears after a while: open **Settings → Diagnostics → OEM battery settings** and
allow autostart / disable battery saving for Island.

---

## Known limits (by design)

- **Another app's alarm cannot be dismissed or snoozed** by a third-party app. Island opens the alarm
  owner instead of faking it.
- **Caller names** only appear when the dialer publishes them (a call-style notification). Island does
  not read contacts or the call log.
- **Turn-by-turn navigation** comes from the navigation app's own notification. Android offers no
  public API to read another app's navigation state, and Island will not scrape the screen or the
  accessibility tree.
- **Media artwork** is whatever the session publishes. Some players publish none; the island shows a
  music glyph rather than a blank square.
- **The island does not replace notifications.** Timers always post a real notification too; the
  island is a faster surface, not a substitute — a silent overlay must never be the only alarm.

---

## Project layout

```
app/src/main/java/dev/island/
├── MainActivity.kt                 app shell + navigation host
├── core/                           AppGraph, Application, BootReceiver, logging, permissions,
│   └── platform/                   notifications, design tokens, animations, cutout & display
├── domain/                         model, engine (queue, priorities, masking, expiration, state
│                                   machine), repository interfaces — pure Kotlin
├── data/                           notifications, media, device monitors, timers, history, DataStore
├── service/                        IslandService, IslandWindowManager, EventDispatcher, ActionRunner
└── feature/
    ├── island/                     layout metrics, overlay root, container, gestures, renderers,
    │                               widgets, in-app preview
    ├── app/                        dashboard, settings, customize, per-app, timers, history
    └── demo/                       demo events

app/src/test/java/dev/island/       JVM unit tests
docs/ARCHITECTURE.md                the long-form design document
```

---

## License & attribution

Island is an original implementation. It is *inspired by* the interaction pattern popularised by
Apple's Dynamic Island; it contains no Apple assets, no copied artwork, and no proprietary code, and
it is not affiliated with or endorsed by Apple Inc. All icons are Material Symbols via
`androidx.compose.material.icons`, all rendering is Compose.
