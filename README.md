<img width="128" height="128" alt="logo" src="https://github.com/user-attachments/assets/57855f35-c6f4-4e9d-b128-d663deb09ad3" />
# Advanced Aim Assist ( Legit Editition

An advanced, pro-grade ghost client aim assist addon for [Meteor Client](https://meteorclient.com), built on the Fabric addon API. Designed specifically for strict anti-cheat bypass (Grim, Vulcan) and 100% natural, humanized PvP combat performance.

## Core Features (DemonClient v2.2 Architecture)

- **Strict Player-Only Targeting:** Exclusively targets real players (`PlayerEntity`), completely ignoring mobs, animals, villagers, and NPCs to prevent crosshair misdirection in survival worlds or farms.
- **Additive Pull Assist (Zero Mouse Conflicts):** Acts as a magnetic "sticky glue" without ever overwriting the player's absolute camera rotation, allowing seamless user mouse collaboration and free cursor movement.
- **Freedom Hitbox Roaming:** Allows the target aim point to orbit and wander organically within the enemy's BoundingBox dimensions (Head, Body, Feet) without locking onto a rigid pixel.
- **Strict Euclidean Closest Target Selection & Hysteresis:** Prioritizes the absolute closest opponent by Euclidean distance from player eyes, with robust sticky locking and unlock range thresholds.
- **Deep Ease-Out Damping & Heavy Inertia:** Smooth deceleration curve approaching the target with zero free-standing micro-pauses or freezes.
- **Ultra-Subtle Multi-Frequency Micro-Noise:** Microscopic human variation noise (centered around `0.02°`) that completely evades advanced heuristic anti-cheats (Grim, Vulcan) without producing artificial visual jitter or shake.
- **GCD Sensitivity Grid Bypass:** Exact Mouse Sensitivity GCD rounding (~0.0055°) to seamlessly bypass strict client-side movement and rotation checks.
- **Manual Mouse Override Threshold:** Instantly yields absolute manual control back to the user if intentional mouse movement is detected (`mouse-threshold`).
- **Sine Fade-In Target Switch Ramp:** Smooth sine-based transition when acquiring new targets to eliminate any sudden hook jerks or micro-jump snaps.
- **Public `getAimAngles()` API:** Clean public rotation provider method enabling external modules like KillAura to integrate seamlessly.

## Requirements

| Component | Version |
| --- | --- |
| Minecraft | 1.21.11 |
| Yarn mappings | 1.21.11+build.3 |
| Fabric Loader | 0.18.2 |
| Meteor Client | 1.21.11-SNAPSHOT |
| Gradle | 9.2.0 |
| JDK | 21 |

## Building

Windows:

```powershell
.\gradlew.bat build -Dorg.gradle.java.home="C:\path\to\jdk-21"
```

Linux/macOS:

```bash
./gradlew build
```

The output jar is written to:

```text
build/libs/advanced-aim-assist-1.0.0.jar
```

## Installing and Testing

1. Install Minecraft 1.21.11 with Fabric Loader 0.18.2 or newer.
2. Put the Meteor Client jar and `advanced-aim-assist-1.0.0.jar` in your `.minecraft/mods` folder.
3. Launch the Fabric profile.
4. Open the Meteor GUI and enable **Advanced Aim Assist** under **Combat**, or run `.t advanced-aim-assist`.

## License

This project is available under the CC0 license. Feel free to use it for your own projects.
