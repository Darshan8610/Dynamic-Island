# Dynamic Island

Native Kotlin / Compose camera-adjacent activity capsule and Android host dashboard.

Version 1.3 repairs focus on bounded overlays, fixed opaque capsule sizing,
rotation, media ownership and a host dashboard without accessibility access.
See [repair notes](docs/REPAIR_NOTES.md) for changes and device limitations and
[work plan](docs/WORK_PLAN.md) for scope. Added product features and outstanding roadmap items are in [feature notes](docs/FEATURES.md).

Build with JDK 17 or 21 and Android SDK 36:

```sh
./gradlew :core:test :testDebugUnitTest :app:assembleDebug :lintDebug :app:lintDebug --max-workers=2
```

Unsigned release APK: `app/build/outputs/apk/release/app-release-unsigned.apk`.
Debug APK: `app/build/outputs/apk/debug/app-debug.apk`. The development signature is not
suitable for publishing or updating an app signed with another key.

Completion build: 220 unit tests passed; release APK/AAB and UI/benchmark test artifacts compile. See [test report](docs/TEST_REPORT.md) for the failed emulator infrastructure and unrun device checks. Publisher signing and physical Realme verification remain external.
