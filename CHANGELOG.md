# CHANGELOG — Advanced-Aim-Assist (DemonClient v2.2)

## Refactor (humanization + performance + robustness)

### Humanization
- Replaced exponential ease-out damping with an ease-out-cubic curve for smoother
  deceleration as the crosshair approaches the target.
- Micro-tremor now uses correlated (Ornstein-Uhlenbeck) noise instead of
  deterministic white-ish sine noise, avoiding artificial jitter patterns.
- Overshoot now lasts 2-4 ticks with a decaying offset instead of a single tick.
- GCD fix remains ALWAYS active (pre- and post-delta quantisation untouched).

### Performance
- `System.currentTimeMillis()` replaced by `System.nanoTime()` in the tick loop,
  hitbox roaming and reaction timing.
- World entity list cached for 2 ticks (`targetEntityCache`) to reduce per-tick
  iteration and allocations.
- `ArrayList` instances pre-sized (targetCache=16, targetEntityCache=32).
- Indexed `for` loops instead of streams in the hot path.

### Robustness
- `catch (Throwable)` replaced with specific catches: `NullPointerException`,
  `IllegalStateException`, `ConcurrentModificationException` (plus a guarded
  `Exception` fallback that logs instead of crashing).
- SLF4J `Logger` (Meteor's logger) used instead of `printStackTrace`/`error()`.
- Settings validated/clamped on activate and on game join (smoothness, range,
  predictionTicks, speeds).

### Prediction
- Ping compensation: effective prediction ticks = base + round(latency/50ms),
  clamped to [0,5], via `AimUtils.getPingTicks()`.
- Parabolic vertical prediction using `entity.getVelocity().y` and a gravity/drag
  simulation (`AimUtils.predictVerticalOffset`) instead of linear `vy * ticks`.

### Compatibility
- No new dependencies; settings names/structure unchanged.
- Meteor API usage guarded with null checks (network handler, options).

### Warnings ⚠️
- Ping compensation increases lookahead on high latency; on servers with strict
  aim/rotation checks this can look like over-leading and may raise detection
  risk. Keep `prediction-ticks` low on those servers.
- Correlated tremor + overshoot add extra rotation noise; combined with a very
  low `smoothness` this can produce visible micro-jitter. Tune `human-variation`
  and `micro-overshoot` conservatively.
- GCD fix is never disabled; do not toggle it off, or Grim/Vulcan will flag.

## Hit-registration improvements (rotations)
- Final aim point clamped inside the target hitbox (inner margin) so attack raycasts connect.
- Overshoot gated: only on large snaps far from target; disabled when already on-target.
- Hitbox roaming amplitude reduced to avoid drifting outside the box.
- Micro-magnetism strengthened and extended to <8°/<6° for reliable centering.
- Final micro-snap closes the remaining gap when <1.5° so hits land (⚠️ tiny snap, GCD-quantised).
- Auto-attack now uses the predicted aim point and a 4° threshold.
- Vertical prediction clamp relaxed (0.9 vs 0.8) and vertical speed kept lower than horizontal.

## Visual naturalness (less detectable)
- Tracking lag: aim point smoothed toward target instead of instant snap.
- Path curvature: wrist-arc perpendicular offset varies over time (curved paths).
- Prediction inaccuracy: small random error added to predicted position.
- Per-tick speed jitter (±15%) on step magnitude.
- Fatigue simulation: after 2s of tracking, accuracy slightly degrades.
- Aim point reset on target switch (no carry-over lag).
- Micro-snap leaves a tiny residual so the crosshair never looks perfectly locked.

## Ghost aim (visual naturalness)
- EMA smoothing on final rotation output (alpha 0.45-0.65) for seamless transitions.
- Breathing simulation: slow vertical oscillation mimicking human breathing.
- Organic drift: slow circular movement around the aim point (hand instability).
- Tracking lag factor increased (0.85-0.95) for smoother following.
- Prediction inaccuracy reduced (0.2x) for less erratic movement.
- Tremor amplitude reduced (0.12 sigma, 0.5 contribution) for subtlety.
- Speed jitter reduced (±4%) for stable speed.
- Path curvature reduced (0.03x) for gentler arcs.
- Hitbox roaming reduced (0.05/0.03) for less drift.
- Micro-snap increased (85-95%) to close gaps without residual jitter.

## Anti-cheat (UniversoCraft / Grim / Vulcan) hardening
- Max rotation per tick reduced (defaults: yaw 200°/s, pitch 140°/s) and jerk limit added (2.0° yaw, 1.4° pitch per tick).
- Prediction dampened (0.85 horizontal, 0.40 vertical) to avoid over-leading flags.
- Overshoot reduced (probability 0.15, 2 ticks, smaller magnitude).
- Hitbox roaming reduced (0.10/0.06).
- Micro-magnetism reduced (0.22 multiplier, default 0.20).
- Skip-tick (8%) to mimic human pauses.
- Micro-snap reduced to 50-65% of remaining gap.
- Tremor amplitude reduced (sigma 0.25x).
- Speed jitter reduced (±8%).
- Reaction time defaults increased (40-90ms).
- Range default reduced to 4.0.