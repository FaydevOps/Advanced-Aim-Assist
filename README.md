# Advanced Aim Assist
<img width="120" height="120" alt="logo" src="https://github.com/user-attachments/assets/d9a1c1e3-5c51-41c3-86cc-514698984df3" />

**Legit Edition**

An advanced, pro-grade ghost client aim assist addon for
[Meteor Client](https://meteorclient.com) — engineered for **strict anti-cheat environments** (Grim, Vulcan) and **100% natural, humanized PvP performance**.

[![Minecraft](https://img.shields.io/badge/Minecraft-1.21.11-62B47A?style=flat-square)](https://minecraft.net)
[![Fabric](https://img.shields.io/badge/Fabric%20Loader-0.18.2-DBB69A?style=flat-square)](https://fabricmc.net)
[![Meteor](https://img.shields.io/badge/Meteor-1.21.11--SNAPSHOT-9146FF?style=flat-square)](https://meteorclient.com)
[![JDK](https://img.shields.io/badge/JDK-21-orange?style=flat-square)](https://adoptium.net)
[![License: GPL v3](https://img.shields.io/badge/License-GPLv3-blue.svg?style=flat-square)](LICENSE)

[![GitHub Stars](https://img.shields.io/github/stars/FaydevOps/Advanced-Aim-Assist?style=flat-square&color=yellow)](https://github.com/FaydevOps/Advanced-Aim-Assist/stargazers)
[![GitHub Issues](https://img.shields.io/github/issues/FaydevOps/Advanced-Aim-Assist?style=flat-square)](https://github.com/FaydevOps/Advanced-Aim-Assist/issues)
[![GitHub Last Commit](https://img.shields.io/github/last-commit/FaydevOps/Advanced-Aim-Assist?style=flat-square)](https://github.com/FaydevOps/Advanced-Aim-Assist/commits/main)
[![PayPal](https://img.shields.io/badge/Donate-PayPal-00457C?style=flat-square&logo=paypal&logoColor=white)](https://paypal.me/FaycraxE)

</div>

---

## 📖 Table of Contents

- [Why Advanced Aim Assist?](#-why-advanced-aim-assist)
- [What's New — Multi-Target Update](#-whats-new--multi-target-update)
- [Preview](#-preview)
- [Core Features](#-core-features)
    - [Targeting](#targeting)
    - [Humanization](#humanization)
    - [Anti-Cheat Safety](#anti-cheat-safety)
    - [Integration](#integration)
- [Quick Start](#-quick-start)
- [Requirements](#-requirements)
- [Building](#-building)
- [Installing](#-installing)
- [Configuration Reference](#-configuration-reference)
    - [Core Settings](#core-settings)
    - [Multi-Target Settings](#multi-target-settings)
- [Multi-Target PvP Tips](#-multi-target-pvp-tips)
- [FAQ](#-faq)
- [Troubleshooting](#-troubleshooting)
- [Support the Project](#-support-the-project)
- [Changelog](#-changelog)
- [Contributing](#-contributing)
- [License & Disclaimer](#-license--disclaimer)

---

## 🎯 Why Advanced Aim Assist?

There are many aim assist addons for Meteor. Here's what sets this one apart:

| Feature                            | Typical addons              | **Advanced Aim Assist**                            |
| ---------------------------------- | --------------------------- | -------------------------------------------------- |
| Target selection                   | Closest only                | **Threat-weighted score** (distance / angle / HP)  |
| Rotation output                    | Overwrites camera           | **Additive pull** — coexists with your mouse       |
| Anti-cheat handling                | Optional GCD rounding       | **GCD grid enforced on every delta**               |
| Target switching                   | Instant snap                | **Eased 6-tick blend** (human flick pattern)       |
| Micro-movement                     | White noise jitter          | **Multi-frequency noise** (~0.02°, below AC heuristics) |
| Hitbox targeting                   | Fixed pixel                 | **Organic roaming** inside the BoundingBox         |
| Public API                         | ❌                          | ✅ **`getAimAngles()`** for KillAura & friends    |
| Strict player-only                 | ⚠️ Often includes mobs      | ✅ Only `PlayerEntity`                            |

> [!NOTE]
> This addon is designed as a **legit / ghost client** assist — it never
> overwrites your absolute camera rotation, so it can be used alongside
> normal gameplay without visual fight conflicts.

---

## 🆕 What's New — Multi-Target Update

- **Threat-Based Scoring** — Target selection weighs **distance (40%)**, **view angle (30%)**, **target health (20%)**, plus a **10% proximity bonus** for enemies within 3 blocks. The crosshair locks onto the *most dangerous* enemy, not just the nearest one.
- **Smooth Target Transitions** — Switching targets uses an **ease-in blend over 6 ticks**, mirroring a natural human flick. Overshoot is suppressed during the transition so the crosshair never snaps.
- **Improved Sticky Targeting** — The current target is retained while valid, eliminating suspicious switches.
- **Natural Cycle Mode** — Opt-in target cycling with configurable cooldown and the same eased transition.
- **Smart Prioritization** — Enemies closer than 3 blocks receive a threat-score bonus.

---

## 📸 Preview

<div align="center">

[![Advanced Aim Assist — Demo](https://img.youtube.com/vi/YW-reiVP2Ls/maxresdefault.jpg)](https://youtu.be/YW-reiVP2Ls)

*Click the thumbnail to watch the demo on YouTube*

</div>

---

## ✨ Core Features

### Targeting

- **Strict Player-Only Targeting** — Exclusively targets `PlayerEntity` instances. Mobs, animals, villagers, and NPCs are ignored.
- **Multi-Target Selection** — Threat-scored `Closest` / `Cycle` modes with sticky locking and hysteresis.
- **Euclidean Closest Selection** — Closest opponent by Euclidean distance from player eyes, with unlock-range hysteresis.
- **Freedom Hitbox Roaming** — The aim point drifts organically inside the target's `BoundingBox` (Head / Body / Feet) instead of locking to a fixed pixel.

### Humanization

- **Deep Ease-Out Damping** — Smooth deceleration curve approaching the target with zero micro-pauses.
- **Multi-Frequency Micro-Noise** — Microscopic variation (~`0.02°`), deliberately kept below the detection thresholds of advanced heuristic anti-cheats.
- **Sine Fade-In Target Switch Ramp** — Eliminates hook jerks and micro-jumps on target acquisition.
- **Additive Pull Assist** — Never overwrites absolute camera rotation; the user's mouse and the assist coexist without conflict.

### Anti-Cheat Safety

- **GCD Sensitivity Grid Bypass** — Exact mouse-sensitivity GCD rounding (~`0.0055°`) matching the client-side movement check, applied to **every** smoothed delta before it reaches the player rotation.
- **Manual Mouse Override Threshold** — Instantly yields absolute control back to the user when intentional mouse movement is detected (`mouse-threshold`).

### Integration

- **Public `getAimAngles()` API** — Clean rotation provider for external modules (KillAura, etc.) without duplicating logic.

---

## ⚡ Quick Start

```text
1. Drop Meteor Client + advanced-aim-assist-1.0.0.jar into .minecraft/mods
2. Launch Fabric 1.21.11 profile
3. Enable via GUI (Combat → Advanced Aim Assist) or run .t advanced-aim-assist
```

---

## ✅ Requirements

| Requirement        | Version                 | Notes                                                 |
| ------------------ | ----------------------- | ----------------------------------------------------- |
| **Minecraft**      | `1.21.11`               | Only this version is supported by the current build   |
| **Fabric Loader**  | `0.18.2`                | Older loaders may work but are untested               |
| **Fabric API**     | matching `1.21.11`      | Required by Meteor Client                             |
| **Meteor Client**  | `1.21.11-SNAPSHOT`      | Addon will not load on stable Meteor builds           |
| **JDK**            | `21` (Temurin/Adoptium) | Required to build from source only                    |
| **OS**             | Windows / Linux / macOS | Anything that runs Fabric 1.21.11                     |

> [!IMPORTANT]
> Mismatched Meteor versions are the **#1 cause of startup crashes**. Always
> update Meteor Client *before* updating the addon.

---

## 🔨 Building

```bash
# 1. Clone the repository
git clone https://github.com/FaydevOps/Advanced-Aim-Assist.git
cd Advanced-Aim-Assist

# 2. Verify your JDK
java -version   # must report 21.x

# 3. Build (Linux / macOS)
./gradlew build

# 3. Build (Windows)
gradlew.bat build
```

The compiled addon is written to:

```text
build/libs/advanced-aim-assist-1.0.0.jar
```

Use the **jar without the `-sources` / `-dev` suffix**. If Gradle fails with a
dependency error, run `./gradlew --refresh-dependencies build`.

---

## 📥 Installing

1. Install **Fabric Loader 0.18.2** for Minecraft `1.21.11`.
2. Download **Fabric API** and **Meteor Client** (`1.21.11-SNAPSHOT`).
3. Place all three jars into your mods folder:

   ```text
   .minecraft/mods/
   ├── fabric-api-<version>.jar
   ├── meteor-client-1.21.11-SNAPSHOT.jar
   └── advanced-aim-assist-1.0.0.jar
   ```

4. Launch the **Fabric 1.21.11** profile.
5. Open the Meteor GUI (default `Right Shift`) → **Combat** → **Advanced Aim Assist**, or run:

   ```text
   .t advanced-aim-assist
   ```

> [!TIP]
> If the module does not appear in the GUI, check that the Meteor version in
> the badge at the top of this README matches the jar in your mods folder.

---

## ⚙️ Configuration Reference

Settings are stored in `meteor-client/settings/advanced-aim-assist.json` and can
be edited in the GUI or via chat:

```text
.settings advanced-aim-assist pull-strength 0.3
.settings advanced-aim-assist target-mode Cycle
```

### Core Settings

| Setting             | Type     | Default    | Description                                                                 |
| ------------------- | -------- | ---------- | --------------------------------------------------------------------------- |
| `enabled`           | `bool`   | `false`    | Master toggle for the module                                                |
| `range`             | `double` | `4.5`      | Maximum targeting distance in blocks, measured from the player's eyes       |
| `unlock-range`      | `double` | `5.5`      | Hysteresis — the target is only dropped beyond this distance                |
| `fov`               | `double` | `90.0`     | Maximum angle (degrees) between crosshair and target for acquisition        |
| `target-mode`       | `enum`   | `Closest`  | `Closest` (Euclidean) or `Cycle` (natural rotation between targets)         |
| `sticky`            | `bool`   | `true`     | Keeps the current target while it remains valid (no suspicious switches)    |
| `hitbox-part`       | `enum`   | `Roaming`  | `Head`, `Body`, `Feet`, or `Roaming` (organic drift inside the BoundingBox) |
| `pull-strength`     | `double` | `0.35`     | Fraction of the angular error corrected per tick (additive pull)            |
| `smoothing`         | `double` | `0.75`     | Ease-out damping factor — higher is slower and more natural                 |
| `switch-blend`      | `int`    | `6`        | Ticks used for the eased target-to-target transition                        |
| `micro-noise`       | `double` | `0.02`     | Multi-frequency micro-noise amplitude in degrees                            |
| `gcd-rounding`      | `bool`   | `true`     | Snaps every smoothed delta to the mouse-sensitivity GCD grid                |
| `mouse-threshold`   | `double` | `1.5`      | Manual mouse movement (deg/tick) above which the assist fully yields        |
| `players-only`      | `bool`   | `true`     | Restricts targeting to `PlayerEntity` instances                             |

### Multi-Target Settings

| Setting                   | Type     | Default | Description                                                              |
| ------------------------- | -------- | ------- | ------------------------------------------------------------------------ |
| `multi-target`            | `bool`   | `true`  | Enables threat-based scoring across all valid targets                    |
| `weight-distance`         | `double` | `0.40`  | Weight of Euclidean distance in the threat score                         |
| `weight-angle`            | `double` | `0.30`  | Weight of the view angle between crosshair and target                    |
| `weight-health`           | `double` | `0.20`  | Weight of the target's current health (lower HP = higher threat)         |
| `proximity-bonus`         | `double` | `0.10`  | Bonus added to targets inside `proximity-radius`                         |
| `proximity-radius`        | `double` | `3.0`   | Radius in blocks for the proximity bonus                                 |
| `cycle-cooldown`          | `int`    | `500`   | Milliseconds between automatic target cycles (`target-mode = Cycle`)     |
| `transition-easing`       | `enum`   | `Sine`  | Easing curve for target switches: `Sine`, `Quad`, `Cubic`                |
| `overshoot-suppression`   | `bool`   | `true`  | Damps overshoot during transitions so the crosshair never snaps past      |

> [!NOTE]
> The four weights should sum to `1.0`. Values are normalized internally, so
> partial configurations will not break scoring — they will just be rescaled.

---

## 🎮 Multi-Target PvP Tips

- **Keep `sticky` on.** Constant target flipping is the single most obvious
  signature of an assist. Sticky locking makes your crosshair behave like a
  human who committed to a fight.
- **Lower `pull-strength` in 1v1s.** `0.20 – 0.30` feels natural in duels;
  raise it toward `0.40` only in crowded team fights where tracking matters
  more than subtlety.
- **Prefer `Closest` in open areas**, `Cycle` in box fights or when cleaning up
  low-HP players behind cover.
- **Raise `mouse-threshold` only if you flick a lot.** A value that is too low
  makes the assist feel like it is "fighting" your mouse.
- **Leave `gcd-rounding` enabled at all times** — it is the main reason this
  addon stays under Grim/Vulcan rotation checks.
- **Tune `micro-noise` down, not up.** `0.02°` is already below heuristic
  detection thresholds; increasing it only adds risk without improving feel.
- **Test on a practice server first.** Aim behaviour depends heavily on your
  in-game sensitivity — retune after changing it.

---

## ❓ FAQ

**Does this work on servers with Grim or Vulcan?**
The addon is engineered around strict rotation checks (GCD grid, additive-only
pull, sub-threshold noise), but **no addon can guarantee safety**. Use at your
own risk.

**Will it conflict with my KillAura / other combat modules?**
No. Rotation output is additive and exposes `getAimAngles()`, so external
modules can consume the same angles instead of fighting over the camera.

**Does it target mobs, animals or NPCs?**
No. With `players-only` enabled (default) it exclusively targets
`PlayerEntity` instances.

**Why does the crosshair feel slow to reach the target?**
That is the ease-out damping working as intended. Lower `smoothing` or raise
`pull-strength` slightly if you want a faster pull.

**Can I use it in singleplayer / LAN?**
Yes, it works anywhere the client runs — but it is pointless in singleplayer.

**Does it change my sensitivity or FOV?**
No. It never writes to your options or absolute camera rotation.

**Is there a Fabric version for older Minecraft releases?**
Only `1.21.11` is currently maintained. Older branches are not supported.

**Will my config survive an update?**
Yes — settings are stored by name in `advanced-aim-assist.json`. Renamed
settings fall back to defaults.

---

## 🛠 Troubleshooting

| Symptom                                   | Cause / Fix                                                                 |
| ----------------------------------------- | --------------------------------------------------------------------------- |
| Module missing from the Combat category   | Meteor version mismatch. Update Meteor to `1.21.11-SNAPSHOT`.               |
| Game crashes on launch                    | Duplicate/older addon jar in `mods/`, or wrong Fabric API version.          |
| `NoSuchMethodError` in the log            | Addon built against a different Meteor snapshot than the one installed.     |
| Assist does nothing                       | `range`/`fov` too small, or `players-only` on with no players nearby.       |
| Crosshair jitters noticeably              | Lower `pull-strength`, raise `smoothing`, reduce `micro-noise`.             |
| Assist "fights" your mouse                | Lower `mouse-threshold` so manual input takes over sooner.                  |
| Target keeps switching in team fights     | Enable `sticky`, increase `cycle-cooldown`, or use `Closest` mode.          |
| Rotation flagged by anti-cheat            | Confirm `gcd-rounding` is enabled and `pull-strength` is below `0.40`.      |
| Settings not saving                       | Close the game cleanly; Meteor writes config on shutdown.                   |

**Reporting a bug?** Include: full `latest.log`, Minecraft/Fabric/Meteor
versions, your `advanced-aim-assist.json`, and reproduction steps.

---

## 💖 Support the Project

- ⭐ **Star the repository** — it directly helps visibility.
- 🐛 **Report issues** with logs and steps to reproduce.
- 💡 **Suggest features** via GitHub Discussions.
- ☕ **Donate:** [paypal.me/FaycraxE](https://paypal.me/FaycraxE)

---

## 📜 Changelog

### `1.0.0` — Multi-Target Update
- Added **threat-based target scoring** (distance 40% / angle 30% / HP 20% /
  proximity bonus 10%).
- Added **eased 6-tick target transitions** with overshoot suppression.
- Added **improved sticky targeting** with unlock-range hysteresis.
- Added **natural cycle mode** with configurable cooldown.
- Added **public `getAimAngles()` API** for external modules.
- Reworked **GCD grid enforcement** to apply to every smoothed delta.
- Reworked **micro-noise** to multi-frequency, capped at ~`0.02°`.
- Added **Freedom Hitbox Roaming** (Head / Body / Feet / Roaming).
- Strict player-only targeting enforced.

### `0.9.x` — Pre-release
- Initial aim assist core: additive pull, ease-out damping, manual override.
- Basic closest-target selection.

---

## 🤝 Contributing

1. Fork the repository and create a branch:
   `git checkout -b feature/my-feature`
2. Follow the existing code style (Java 21, 4-space indent, no wildcard imports).
3. Keep changes focused — one feature or fix per pull request.
4. Test against **Grim** and **Vulcan** test servers before submitting rotation changes.
5. Open a PR describing the change, the motivation, and any config additions.

Please **do not** open PRs that add aggressive rotation snapping, silent-aim
behaviour, or anything that breaks the additive-pull design.

---

## 📄 License & Disclaimer

Licensed under the **GNU General Public License v3.0** — see [LICENSE](LICENSE).

```text
This program is free software: you can redistribute it and/or modify it under
the terms of the GNU General Public License as published by the Free Software
Foundation, either version 3 of the License, or (at your option) any later
version.
```

> [!WARNING]
> **Disclaimer:** This project is provided for **educational and research
> purposes only**. Using client modifications on multiplayer servers may violate
> their terms of service and can result in a ban. The authors accept **no
> liability** for account suspensions, bans, or any other consequences arising
> from the use of this software. You are solely responsible for how you use it.
