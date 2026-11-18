# PulsePad

PulsePad reads a Bluetooth game controller and replays your mappings as touch
gestures in any app. It does this without root, without ADB, and without a PC.

The only mechanism Android offers for that is an accessibility service, and
PulsePad is built entirely around that one mechanism.

## What PulsePad is, and what it is not

PulsePad is a **gesture-replay engine**. When you press a mapped controller
button, PulsePad asks the system to play a tap, hold or swipe at a screen position
you chose.

PulsePad is **not** a virtual gamepad. No gamepad event is ever synthesised, so a
game that reads gamepad input directly will not see anything PulsePad does. That is
a hard limit of doing this without root, and the UI states it wherever a user might
reasonably expect otherwise.

## Latency, stated honestly

`dispatchGesture` describes a stroke and lets the system play it out. It is not an
injected event, so there is a real delay between the button press and the effect:

- Expect roughly **50-120 ms** end to end.
- The figure varies with the device and the Android version.
- The Tuning screen shows an estimate for your current injection interval and
  labels it as an estimate, because measuring it for real would require a target app
  that reports when it saw the touch.

Nothing in the app calls this "low latency", and no screen promises virtual-gamepad
behaviour.

## Architecture

```
Bluetooth controller
        |
        v
+-----------------------------+        +----------------------------+
| PulsePadAccessibilityService|        |   InputCaptureService      |
|  onKeyEvent / onMotionEvent |        |  InputDeviceListener       |
|  (global capture)           |        |  (connection watching)     |
+-------------+---------------+        +-------------+--------------+
              |                                      |
              +------------------+-------------------+
                                 v
                      ControllerInputBus (SharedFlow)
                                 |
              +------------------+---------------------+
              |                                        |
              v                                        v
   ControllerInputRepository                InjectionCoordinator
   (live highlight state)                   (arming + safety rule)
                                                      |
                                                      v
                                       AccessibilityInjectionEngine
                                       (GestureDescription + dispatchGesture)
```

Capture and injection are deliberately separate. The accessibility service only
*captures*; it never decides to inject. That keeps the rule "never inject unless
armed" in exactly one place, and it means the foreground capture path needs no
changes to gain injection.

### Layers

| Layer | Contents |
|---|---|
| `domain/model` | Controllers, inputs, mappings, capabilities. No Android types. |
| `domain/engine` | Transform pipeline, gesture planning, profile codec. Pure Kotlin, unit tested. |
| `data/bluetooth` | Discovery and connection, with the HID-host and generic-input paths. |
| `data/persistence` | Room profile store. |
| `data/repository` | The live mapping configuration, and saved profiles. |
| `service/accessibility` | The only injection path, plus the arming coordinator. |
| `presentation` | Compose + Material 3 UI, MVVM over the repositories. |

The transform pipeline is `RawInput -> Deadzone -> ResponseCurve -> Mapping ->
OutputAction`, and the same code runs in tests and on the device.

### Key decisions

- **One JSON format for storage and export.** `ProfileCodec` is used by both, so a
  profile saved on the device is byte-identical to the one the user shares.
- **Mapping coordinates are normalised (0..1).** A profile made on a phone targets
  the same part of the screen on a tablet; pixels are resolved at dispatch time.
- **One live configuration.** `MappingRepository` is the single source of truth for
  both the UI and the injection engine, so there is no separate "apply" step.
- **Room uses destructive migration.** A profile is a convenience artefact and the
  JSON export is the durable copy, so a schema change rebuilds rather than risking a
  bricked database.

## Amazon Fire OS

Fire OS is a first-class target, not an afterthought. The app is designed around
its restrictions:

- **No Google Play Services.** Nothing in the app depends on GMS. The release
  runtime classpath contains no `com.google.android.gms` or `play-services`
  artefact, and the merged manifest declares none. `BluetoothAdapter`,
  `BluetoothProfile` and `InputDevice` are all standard framework APIs.
- **`BluetoothHidHost` may be restricted.** If `getProfileProxy` returns null, the
  app falls back to enumerating bonded devices filtered by peripheral device class,
  and to reading controllers through the standard input system. The Devices screen
  says which path is in use.
- **Accessibility services may be refused** (reported on Fire OS 6 builds). The app
  detects this and shows a non-blocking explanation on the Tuning screen: mapping
  outside PulsePad is unavailable, but the app still works as a 3D configuration
  preview. It does not crash and does not loop permission prompts.
- **OpenGL ES 3.0 may be missing.** SceneView needs GLES 3.0. When
  `ActivityManager.deviceConfigurationInfo.reqGlEsVersion < 0x30000`, the studio
  swaps the 3D scene for a 2D vector schematic that still supports node selection
  and live highlighting. There is no blank screen and no crash.

PulsePad requires Android 9 (API 28) or newer. Older Fire OS 6 devices show a
compatibility notice rather than failing later.

## The 3D studio

The studio renders the three supported controller families (DualShock 4,
DualSense, Xbox Wireless) with SceneView, and highlights the control you press in
real time.

`assets/models/` ships three `.glb` files generated by
`scripts/generate_controller_models.py`. That script is standard-library-only and
reproducible; run it to regenerate the assets.

**Replacing the models.** The bundled models are generated low-poly placeholders,
not licensed artwork. To swap in a higher-quality model:

1. Obtain a `.glb` you have the right to ship.
2. Place it at `app/src/main/assets/models/<name>.glb`, keeping one of the three
   existing filenames.
3. Keep the node names the app expects; `ControllerModelContractTest` checks that
   each asset exists, is a valid GLB container, and declares the controls the
   schematic and the highlight layer address by name.
4. Run `./gradlew :app:testDebugUnitTest` to confirm the contract still holds.

If a model fails to load at runtime, the studio falls back to the 2D schematic for
that model and stops retrying, so a bad asset degrades rather than crashes.

## Mapping and tuning

- Tap any control on the model (or the 2D schematic) to open its mapping sheet.
- Assign **tap**, **hold**, **swipe**, **macro**, **turbo** or **disabled**.
- Tune the sticks with a deadzone slider and a response curve, with a live graph.
- Set the trigger activation threshold and maximum output.
- The Tuning tab controls the injection interval, the turbo rate, and the master
  arming switch.

## Building

```bash
export ANDROID_HOME=/path/to/android-sdk
./gradlew :app:assembleDebug          # debug APK
./gradlew :app:assembleRelease        # release APK (R8 + resource shrinking)
./gradlew :app:testDebugUnitTest      # unit tests
```

Release builds use a stable signing key when one is supplied and fall back to the
debug key otherwise, so `assembleRelease` runs anywhere without setup.

### Release signing and versioning

Two things matter for a build that users install and then update in place:

* **The signing key must be stable.** An APK signed with a different key cannot
  update an installed copy, so the release build reads its key from the
  environment and falls back to the debug key only when none is supplied. In CI,
  set the repository secrets `PULSEPAD_KEYSTORE_BASE64` (a base64 `.jks`) plus
  `PULSEPAD_KEYSTORE_PASSWORD`, `PULSEPAD_KEY_ALIAS` and `PULSEPAD_KEY_PASSWORD`.
  Without them, CI still builds and uploads APKs, but those APKs are signed with
  an ephemeral key and cannot update one another.
* **The version must match the tag.** A tagged build passes
  `-PversionName` / `-PversionCode`, so the published APK reports the version its
  tag names. The in-app updater compares its own `versionName` against the newest
  release tag; if the two disagreed the app would offer an update that is really
  itself.

```bash
PULSEPAD_KEYSTORE_PATH=/path/to/release.jks \
PULSEPAD_KEYSTORE_PASSWORD=... \
PULSEPAD_KEY_ALIAS=... \
PULSEPAD_KEY_PASSWORD=... \
./gradlew :app:assembleRelease -PversionName=1.2.0 -PversionCode=3
```

## Continuous integration

Two workflows run on every push and pull request.

`build.yml` produces the APKs. Its `integrity` job runs first and the `build` job
`needs` it, so **the APK is not assembled while the repository integrity check
reports an error** - the gate is real, not a parallel report. On a `v*` tag the
release APK is attached to a GitHub release, which is what the in-app updater
looks for.

`ci.yml` is the fast half: it inspects every tracked file without a JDK or the
Android SDK, so a pull request gets a verdict in seconds.

### The repository integrity check

`scripts/check_repo_integrity.py` scans **every tracked file** for the classes of
defect a compiler and a linter do not catch:

| Rule | Catches |
|---|---|
| `unfinished-work` | `TODO`, `FIXME`, `XXX`, `HACK`, `WIP` and similar markers left in source. |
| `orphaned-source` | A class, object or top-level function nothing references - code that was written but never wired up. |
| `unused-resource` / `undefined-resource` | Strings, colors and drawables that nothing points at, or references to resources that do not exist. |
| `unwired-route` | A navigation destination that no screen can reach. |
| `manifest-components` | A manifest entry whose class does not exist. |
| `forbidden-dependency` | Shizuku, root helpers, Magisk, or Google Play Services creeping into a build file. |
| `hardcoded-secret` | Tokens, private keys and credentials committed to the tree. |
| `glyph-in-resources` | Emoji or pictographs in user-facing text, where a vector icon belongs. |
| `aapt-unsafe-entity` | `&apos;`-style entities that AAPT2 rejects. |
| `truncated-source`, `malformed-xml`, `malformed-python`, `malformed-asset` | Truncated or malformed files, including `.glb` headers. |
| `empty-file` | A file committed with no content. |
| `debug-leftover` | Debug-only code paths left reachable in a release build. |
| `build-config` | Version and SDK configuration that has drifted out of range. |

Run it locally with `python3 scripts/check_repo_integrity.py`; it exits non-zero
on the first class of error. `--list-rules` prints every rule id, and `--json`
emits machine-readable findings.

A finding can be waived, but only with a reason, and only for one rule on one
path: an inline `integrity:allow` comment on the line (or the line above), or an
entry in `scripts/integrity-allowlist.txt`. A waiver that no longer matches
anything is itself an error, so the allowlist cannot rot into a blanket.

## Updates

The overflow menu on the Devices screen opens an update screen. It reads the
GitHub releases API, compares the newest release tag against the installed
`versionName`, and - when a newer build exists and carries an `.apk` asset -
downloads it with the system `DownloadManager` and hands it to the package
installer. No browser round-trip is needed.

The check is on demand, never automatic: the app is fully usable offline, and a
background network call on every launch would be surprising. Details that the
screen states plainly:

* Installing requires the one-time "install unknown apps" permission, which
  Android will not grant at runtime; the screen links straight to that setting.
* The downloaded build must be signed with the same key as the installed app.
  A build signed with a different key will be rejected by the installer.
* Pre-releases are skipped unless the user opts in.

The release APKs that `build.yml` publishes are signed with the debug key, so
they are suitable for sideloading and for the updater. A store-distributed build
would be signed with its own key and would update through the store instead.

## Permissions

| Permission | Why |
|---|---|
| `BLUETOOTH_CONNECT`, `BLUETOOTH_SCAN` (API 31+); `BLUETOOTH`, `BLUETOOTH_ADMIN` (API 28-30) | See and connect to the controller. |
| `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_CONNECTED_DEVICE` | Keep input capture alive while you are in another app. |
| `POST_NOTIFICATIONS` | The ongoing notices for capture and for an armed engine. |
| `INTERNET` | The update check. Read-only, unauthenticated, and optional. |
| `REQUEST_INSTALL_PACKAGES` | Hand a downloaded update to the system installer. The user grants this in settings. |
| `BIND_ACCESSIBILITY_SERVICE` (service-side) | The injection path. Only the user can grant this. |

PulsePad cannot enable its own accessibility service. Onboarding and the Devices
banner deep-link to the system settings screen; the user toggles it.

## Project layout

```
app/src/main/java/com/pulsepad/controller/
  data/bluetooth        discovery, HID host proxy, generic input fallback
  data/persistence      Room entities, DAO, database
  data/repository       live configuration, saved profiles, input state, updates
  data/update           GitHub release client, APK download and install
  domain/model          controllers, inputs, mappings, capabilities, versions
  domain/engine         transform pipeline, gesture planner, profile codec, releases
  presentation          Compose UI, navigation, theme, view models
  service/accessibility capture + injection engine + arming coordinator
  service/input         foreground capture service, input device watcher
  util                  capability checks, logging, notifications, permissions
app/src/main/assets/models   bundled controller models
scripts/                     model generation, repository tooling, integrity gate
.github/workflows/           APK build (with the integrity gate) and fast checks
```
