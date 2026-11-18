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

Release builds are signed with the debug key so `assembleRelease` runs in CI
without a keystore; a production build overrides `signingConfig` with a real
configuration.

## Permissions

| Permission | Why |
|---|---|
| `BLUETOOTH_CONNECT`, `BLUETOOTH_SCAN` (API 31+); `BLUETOOTH`, `BLUETOOTH_ADMIN` (API 28-30) | See and connect to the controller. |
| `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_CONNECTED_DEVICE` | Keep input capture alive while you are in another app. |
| `POST_NOTIFICATIONS` | The ongoing notices for capture and for an armed engine. |
| `BIND_ACCESSIBILITY_SERVICE` (service-side) | The injection path. Only the user can grant this. |

PulsePad cannot enable its own accessibility service. Onboarding and the Devices
banner deep-link to the system settings screen; the user toggles it.

## Project layout

```
app/src/main/java/com/pulsepad/controller/
  data/bluetooth        discovery, HID host proxy, generic input fallback
  data/persistence      Room entities, DAO, database
  data/repository       live configuration, saved profiles, input state
  domain/model          controllers, inputs, mappings, capabilities
  domain/engine         transform pipeline, gesture planner, profile codec
  presentation          Compose UI, navigation, theme, view models
  service/accessibility capture + injection engine + arming coordinator
  service/input         foreground capture service, input device watcher
  util                  capability checks, logging, notifications, permissions
app/src/main/assets/models   bundled controller models
scripts/                     model generation and repository tooling
```
