# 2.0.1 repair

Source baseline: `a22dccb577b5d59b83ac12e9e784b16d919df95d`.

## Corrected defects

- The foreground service attempted to remove and recreate its window as an accessibility overlay without the accessibility-service window token. Failure could leave the island detached while reporting it running. Restore the documented bounded application overlay and remove the unused accessibility service declaration and misleading disclosures.
- Apple parity hold resolved to "open controls" but the service sent TapPrimary/TapSecondary, which the arbiter interpreted as opening the source in that mode. Route resolved expansion to the correct command, including the idle inbox and alert cases.
- ExecuteAction could invoke a retained activity callback or PendingIntent while keyguard was locked. Apply the same lock gate as other direct controls.
- Stale telemetry applied alpha to the entire capsule, exposing the underlying app through the black surface. Dim content inside the opaque background instead.
- The orientation argument was shadowed by a rotation-derived local variable, so landscape presentation depended on cutout rotation rather than actual orientation.
- The idle mini-status clock was remembered once and never advanced. Reuse the wall-clock ticker.
- Collapse scrubbing captured the initial progress and guessed velocity as delta times 60. Read current progress and use timestamped pointer samples.
- Activity-dependent accessibility labels broke stable control lookup. Keep the control label stable and put activity information in stateDescription.
- CI uploaded unsigned release APKs, ignored SDK installation failures, and its emulator job ran JVM tests only. Require signature/alignment checks, preserve reports, and run connected instrumentation plus a release install/launch smoke test. Automatic release-tag rewriting was removed.

## Build and validation

```sh
python3 tools/check-secrets.py
./gradlew :core:test :testDebugUnitTest :lintDebug :app:lintDebug :app:assembleRelease :app:bundleRelease -PislandDevelopmentSigning=true --max-workers=2
./gradlew :app:connectedDebugAndroidTest --max-workers=2
```

The development-signing property is an explicit opt-in for sideload testing. Publisher signing continues to use the four documented ISLAND_KEYSTORE_* / ISLAND_KEY_* environment variables. A locally generated development key is not guaranteed to match previous APKs; signing material is never committed.

Validation status will be completed with the final artifact. Physical-device/OEM compatibility and sustained 120 Hz operation require separate device evidence.
