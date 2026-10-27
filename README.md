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
| Public API                         | ❌                          | ✅ **\`getAimAngles()\`** for KillAura & friends    |
| Strict player-only                 | ⚠️ Often includes mobs      | ✅ Only \`PlayerEntity\`                            |

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

- **Strict Player-Only Targeting** — Exclusively targets \`PlayerEntity\` instances. Mobs, animals, villagers, and NPCs are ignored.
- **Multi-Target Selection** — Threat-scored \`Closest\` / \`Cycle\` modes with sticky locking and hysteresis.
- **Euclidean Closest Selection** — Closest opponent by Euclidean distance from player eyes, with unlock-range hysteresis.
- **Freedom Hitbox Roaming** — The aim point drifts organically inside the target's \`BoundingBox\` (Head / Body / Feet) instead of locking to a fixed pixel.

### Humanization

- **Deep Ease-Out Damping** — Smooth deceleration curve approaching the target with zero micro-pauses.
- **Multi-Frequency Micro-Noise** — Microscopic variation (~\`0.02°\`), deliberately kept below the detection thresholds of advanced heuristic anti-cheats.
- **Sine Fade-In Target Switch Ramp** — Eliminates hook jerks and micro-jumps on target acquisition.
- **Additive Pull Assist** — Never overwrites absolute camera rotation; the user's mouse and the assist coexist without conflict.

### Anti-Cheat Safety

- **GCD Sensitivity Grid Bypass** — Exact mouse-sensitivity GCD rounding (~\`0.0055°\`) matching the client-side movement check, applied to **every** smoothed delta before it reaches the player rotation.
- **Manual Mouse Override Threshold** — Instantly yields absolute control back to the user when intentional mouse movement is detected (\`mouse-threshold\`).

### Integration

- **Public \`getAimAngles()\` API** — Clean rotation provider for external modules (KillAura, etc.) without duplicating logic.

---

## ⚡ Quick Start

```text
1. Drop Meteor Client + advanced-aim-assist-1.0.0.jar into .minecraft/mods
2. Launch Fabric 1.21.11 profile
3. Enable via GUI (Combat → Advanced Aim Assist) or run .t advanced-aim-assist
