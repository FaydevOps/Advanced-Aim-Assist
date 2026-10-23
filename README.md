# Advanced Aim Assist

An advanced aim assist addon for [Meteor Client](https://meteorclient.com), built on the Fabric addon API.

The addon replaces the old Meteor `AimAssist` with a configurable targeting system, several rotation modes and optional anti-detection behaviour.

## Features

- Targets players and hostile mobs (`Monster` entities).
- Range and FOV filtering, plus a direct line-of-sight raycast.
- Target priorities: lowest health, closest distance or smallest angle to the crosshair.
- Sticky targeting with a separate unlock range.
- Three rotation modes: `Instant`, `Smooth` and `Linear`.
- Optional velocity prediction, with a configurable number of ticks.
- Aims at the target's head, body or feet using its bounding box.
- Optional attack-cooldown gate.
- Anti-detection options: random rotation offsets, random pauses and a human-like rotation-speed cap.
- Optional visible camera movement. Disable `move-camera` for silent rotations.

## Requirements

| Component | Version |
| --- | --- |
| Minecraft | 1.21.11 |
| Yarn mappings | 1.21.11+build.3 |
| Fabric Loader | 0.18.2 |
| Meteor Client | 1.21.11-SNAPSHOT |
| Gradle | 9.2.0 |
| JDK | 21 |

The dependency versions are defined in [`gradle/libs.versions.toml`](gradle/libs.versions.toml). This project targets Minecraft 1.21.11 using Yarn mappings. To build for another version, update `minecraft`, `yarn-mappings`, `fabric-loader`, `meteor` and, when necessary, `loom` and the Java version in that file. The code uses Yarn names (`getVelocity()`, `mc.world.getEntities()`, `Vec3d`, etc.).

> Gradle itself must run on JDK 21 or newer. Having only a Java 17 `JAVA_HOME` will fail during Loom configuration.

## Building

Windows:

```powershell
$env:JAVA_HOME = "C:\path\to\jdk-21"
.\gradlew.bat build
```

Linux/macOS:

```bash
export JAVA_HOME=/path/to/jdk-21
./gradlew build
```

Alternatively, pass the JDK directly to Gradle:

```powershell
.\gradlew.bat build -Dorg.gradle.java.home="C:\path\to\jdk-21"
```

The output jar is written to:

```text
build/libs/advanced-aim-assist-1.0.0.jar
```

## Installing and testing

1. Install Minecraft 1.21.11 with Fabric Loader 0.18.2 or newer.
2. Put the Meteor Client jar and `advanced-aim-assist-1.0.0.jar` in your `.minecraft/mods` folder.
3. Launch the Fabric profile.
4. Open the Meteor GUI and enable **Advanced Aim Assist** under **Combat**, or run:

```text
.t advanced-aim-assist
```

To test from the IDE, run the `Minecraft Client` configuration (or `./gradlew runClient`). This launches a development client with Meteor and the addon loaded.

### Cloning this repository

```bash
git clone https://github.com/FaydevOps/meteor-addon-aimassit
cd meteor-addon-aimassit
```

## Settings

### General

| Setting | Default | Description |
| --- | --- | --- |
| `range` | `4.5` | Maximum target distance in blocks (2.0 - 6.0). |
| `fov` | `90` | FOV filter in degrees (30 - 180). `180` disables the filter. |
| `priority` | `LowestHealth` | `LowestHealth`, `Closest` or `Angle`. |
| `ignore-friends` | `true` | Skips players on the Meteor friends list. |
| `ignore-invisible` | `true` | Skips invisible entities. |

### Aim

| Setting | Default | Description |
| --- | --- | --- |
| `aim-speed` | `5.0` | Maximum rotation speed in degrees per tick (1.0 - 20.0). |
| `smoothness` | `0.3` | Interpolation strength for `Smooth` mode (0.0 - 1.0). |
| `prediction` | `true` | Predicts the target's movement. |
| `prediction-ticks` | `3` | Ticks to look ahead (1 - 10). |
| `aim-at` | `Head` | `Head`, `Body` or `Feet`. |
| `rotation-mode` | `Smooth` | `Instant`, `Smooth` or `Linear`. |
| `move-camera` | `true` | Moves the camera with the aim; disable for silent rotations. |

### Targeting

| Setting | Default | Description |
| --- | --- | --- |
| `sticky` | `true` | Keeps the current target until it leaves the unlock range. |
| `unlock-range` | `5.5` | Distance at which a sticky target is released. It is clamped to at least `range + 0.5`. |

### Checks

| Setting | Default | Description |
| --- | --- | --- |
| `require-line-of-sight` | `true` | Only targets entities visible through a block raycast. |
| `require-attack-cooldown` | `false` | Only rotates while the attack cooldown is fully charged. |

### Anti-Detection

| Setting | Default | Description |
| --- | --- | --- |
| `randomize` | `false` | Adds a random offset to the final yaw and pitch. |
| `random-amount` | `0.1` | Maximum random offset in degrees (0.0 - 1.0). |
| `human-like` | `false` | Skips roughly 1 in 10 ticks and caps rotation speed. |

## How it works

| File | Purpose |
| --- | --- |
| `AdvancedAimAssistAddon.java` | Meteor entrypoint. Registers the module in `onInitialize()`. |
| `modules/AdvancedAimAssist.java` | Module settings, target selection, sticky logic and rotation handling. |
| `utils/AimUtils.java` | Aim-point calculation, yaw/pitch math, FOV angle and line-of-sight raycast. |
| `utils/AimTarget.java` | Per-tick target snapshot used for sorting. |
| `utils/AimPart.java` | `Head` / `Body` / `Feet`. |
| `utils/RotationMode.java` | `Instant` / `Smooth` / `Linear`. |
| `utils/TargetPriority.java` | `LowestHealth` / `Closest` / `Angle`. |

The module listens to `TickEvent.Post`. Each tick it:

1. Reuses a valid sticky target, or scans `mc.world.getEntities()`.
2. Filters by entity type, distance, FOV, friends, invisibility and line of sight.
3. Sorts the remaining candidates using the selected priority.
4. Rebuilds the aim point from the target's bounding box and applies velocity prediction.
5. Smooths or steps the rotation according to the selected mode and speed.
6. Applies the result through `Rotations.rotate(...)` and `Rotations.setCamRotation(...)`.

`getVelocity()` returns the entity's movement per tick. Meteor no longer exposes `Utils.loadedEntities()`, so the module uses `mc.world.getEntities()`, the same source as Meteor's `TargetUtils`.

## Notes

- The anti-detection options make the movement less mechanical; they do not bypass server-side anticheat checks.
- Prediction can place the aim point behind cover. With `require-line-of-sight` enabled, that target may be dropped until it is visible again.
- Use this addon only on servers where such modifications are allowed.

## License

This template is available under the CC0 license. Feel free to use it for your own projects.
