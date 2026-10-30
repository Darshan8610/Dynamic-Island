# Dynamic Island

Native Kotlin / Compose camera-adjacent activity capsule for Android 9 and later.

Current repair source: **2.0.1** · [repair details](docs/V2_0_1_REPAIR.md).

[Historical v1.5 APK](https://github.com/Darshan8610/Dynamic-Island/raw/refs/heads/main/deliverables/v1.5/dynamic-island-v1.5.apk)
· [Source and reports](deliverables/v1.5)

Tap the capsule to expand music controls in place: artwork, track/artist,
play/pause, previous/next and seeking when the player supports them. Close with
the chevron, an upward swipe on the header or a tap outside. The compact capsule
keeps the same opaque black size across music and notifications.

The island uses display-over-other-apps permission and a foreground service.
Notification access supplies media and alerts. Accessibility access is not used.
Android retains priority for system bars, protected screens and apps that hide
overlays. On ColorOS/realme UI, allow background activity in the app's battery
settings if the service is stopped by the phone.

Build with JDK 17/21 and Android SDK 36:

```sh
./gradlew :core:test :testDebugUnitTest :app:assembleRelease :app:bundleRelease :lintDebug :app:lintDebug -PislandDevelopmentSigning=true --max-workers=2
```

The command explicitly signs the release variant with a development key for sideload
testing. Keep that key outside source control to support future updates; a different
key cannot update an existing installation. Publisher signing can be supplied through
the environment variables documented in the [release checklist](docs/RELEASE_CHECKLIST.md).
Without publisher credentials or the development-signing opt-in, release output is unsigned.

See [v1.5 changes](docs/V1_5_CHANGES.md), [validation](docs/TEST_REPORT.md) and
[compatibility](docs/COMPATIBILITY_MATRIX.md) for verification and device limits.
