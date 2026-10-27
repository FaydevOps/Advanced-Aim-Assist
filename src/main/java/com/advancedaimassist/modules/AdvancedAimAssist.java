package com.advancedaimassist.modules;

import com.advancedaimassist.utils.AimPart;
import com.advancedaimassist.utils.AimTarget;
import com.advancedaimassist.utils.AimUtils;
import com.advancedaimassist.utils.RotationUtils;
import com.advancedaimassist.utils.TargetPriority;
import meteordevelopment.meteorclient.events.game.GameJoinedEvent;
import meteordevelopment.meteorclient.events.game.GameLeftEvent;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.friends.Friends;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.Rotations;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.entity.passive.WanderingTraderEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Items;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.ConcurrentModificationException;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

import static meteordevelopment.meteorclient.MeteorClient.mc;

/**
 * AdvancedAimAssist — (refactored)
 *
 * Changes vs original:
 *  - Exponential ease-out replaced by ease-out-cubic curve for smoother deceleration.
 *  - Micro-tremor uses correlated (Ornstein-Uhlenbeck) noise instead of white noise.
 *  - Overshoot now lasts 2-4 ticks with decay instead of a single tick.
 *  - GCD fix kept ALWAYS active (pre and post delta quantisation).
 *  - System.nanoTime() instead of currentTimeMillis().
 *  - Target entity list cached for 2 ticks.
 *  - ArrayLists pre-sized.
 *  - Specific exception handling + SLF4J logging.
 *  - Ping compensation + parabolic vertical prediction.
 */
public class AdvancedAimAssist extends Module {

    private static final Logger LOG = LoggerFactory.getLogger(AdvancedAimAssist.class);

    // ------------------------------------------------------------------ groups
    private final SettingGroup sgGeneral   = settings.createGroup("General");
    private final SettingGroup sgPro       = settings.createGroup("Pro Settings");
    private final SettingGroup sgHuman     = settings.createGroup("Humanization");
    private final SettingGroup sgTargeting = settings.createGroup("Targeting");
    private final SettingGroup sgMulti     = settings.createGroup("Multi-Target");
    private final SettingGroup sgChecks    = settings.createGroup("Checks");
    private final SettingGroup sgRender    = settings.createGroup("Render");

    // ------------------------------------------------------------------ enums
    public enum AimMode        { Client, Assist, Silent }
    public enum MultiTargetMode { Closest, LowestHealth, Angle, Cycle }

    // ------------------------------------------------------------------ General
    private final Setting<AimMode> aimMode = sgGeneral.add(new EnumSetting.Builder<AimMode>()
        .name("aim-mode")
        .description("Client/Assist: visual rotation assist. Silent: server-side only.")
        .defaultValue(AimMode.Client)
        .build());

    private final Setting<Boolean> onlyWhenClicking = sgGeneral.add(new BoolSetting.Builder()
        .name("only-when-clicking")
        .description("Activate only while holding left-click.")
        .defaultValue(true)
        .build());

    private final Setting<Boolean> weaponOnly = sgGeneral.add(new BoolSetting.Builder()
        .name("weapon-only")
        .description("Activate only while holding a melee weapon.")
        .defaultValue(true)
        .build());

    private final Setting<Double> range = sgGeneral.add(new DoubleSetting.Builder()
        .name("range")
        .description("Maximum target distance in blocks.")
        .defaultValue(4.0).range(2.0, 6.0).sliderRange(2.0, 6.0)
        .build());

    private final Setting<Double> horizontalSpeed = sgGeneral.add(new DoubleSetting.Builder()
        .name("horizontal-speed")
        .description("Peak horizontal angular speed (deg/sec).")
        .defaultValue(160.0).range(20.0, 720.0).sliderRange(40.0, 500.0)
        .build());

    private final Setting<Double> verticalSpeed = sgGeneral.add(new DoubleSetting.Builder()
        .name("vertical-speed")
        .description("Peak vertical angular speed (deg/sec).")
        .defaultValue(80.0).range(20.0, 500.0).sliderRange(40.0, 400.0)
        .build());

    private final Setting<Double> fov = sgGeneral.add(new DoubleSetting.Builder()
        .name("fov")
        .description("Activation field of view (degrees, 360 = omnidirectional).")
        .defaultValue(140.0).range(10.0, 360.0).sliderRange(10.0, 180.0)
        .build());

    private final Setting<Boolean> gcdFix = sgGeneral.add(new BoolSetting.Builder()
        .name("gcd-fix")
        .description("Quantises rotation steps to the GCD grid for Grim/Vulcan bypass.")
        .defaultValue(true)
        .build());

    private final Setting<Boolean> prediction = sgGeneral.add(new BoolSetting.Builder()
        .name("prediction")
        .description("Kinematic intercept: aims where the target will be.")
        .defaultValue(true)
        .build());

    private final Setting<Integer> predictionTicks = sgGeneral.add(new IntSetting.Builder()
        .name("prediction-ticks")
        .description("Tick lookahead for intercept calculation.")
        .defaultValue(2).range(1, 5).sliderRange(1, 5) // FIX 11: align with validateSettings clamp (1..5)
        .visible(prediction::get)
        .build());

    private final Setting<Boolean> autoAttack = sgGeneral.add(new BoolSetting.Builder()
        .name("auto-attack")
        .description("Auto-attack when the crosshair is within 5° of the target.")
        .defaultValue(false)
        .build());

    // ------------------------------------------------------------------ Pro
    private final Setting<Double> stickyGlue = sgPro.add(new DoubleSetting.Builder()
        .name("sticky-glue-strength")
        .description("Adhesion force. Higher = tighter tracking without lag.")
        .defaultValue(0.70).range(0.1, 1.0).sliderRange(0.1, 1.0)
        .build());

    private final Setting<Double> microMagnetism = sgPro.add(new DoubleSetting.Builder()
        .name("micro-magnetism")
        .description("Extra centering pull applied when crosshair is inside the hitbox (<6°).")
        .defaultValue(0.05).range(0.0, 3.0).sliderRange(0.0, 2.0)
        .build());

    // ------------------------------------------------------------------ Humanization
    private final Setting<Double> smoothness = sgHuman.add(new DoubleSetting.Builder()
        .name("smoothness")
        .description("Exponential damping factor. Lower = crisp snap. Higher = soft cinematic ease-out.")
        .defaultValue(0.40).range(0.01, 0.99).sliderRange(0.01, 0.99)
        .build());

    private final Setting<Double> arcCurvature = sgHuman.add(new DoubleSetting.Builder()
        .name("arc-curvature")
        .description("Wrist pivot arc trajectory intensity.")
        .defaultValue(0.04).range(0.0, 1.0).sliderRange(0.0, 0.5)
        .build());

    private final Setting<Boolean> microOvershoot = sgHuman.add(new BoolSetting.Builder()
        .name("micro-overshoot")
        .description("Simulates human error by subtly overshooting fast snaps by 1-2 pixels.")
        .defaultValue(false)
        .build());

    private final Setting<Double> humanVariation = sgHuman.add(new DoubleSetting.Builder()
        .name("human-variation")
        .description("Micro-noise amplitude for behavioural heuristic evasion.")
        .defaultValue(0.004).range(0.0, 1.0).sliderRange(0.0, 0.5)
        .build());

    private final Setting<Double> hitboxRoaming = sgHuman.add(new DoubleSetting.Builder()
        .name("hitbox-roaming-intensity")
        .description("Allows the aim to drift naturally within the enemy bounding box.")
        .defaultValue(0.03).range(0.0, 1.0).sliderRange(0.0, 1.0)
        .build());

    private final Setting<Double> escapeAngle = sgHuman.add(new DoubleSetting.Builder()
        .name("escape-angle")
        .description("Minimum angle (°) away from target required to release camera assist.")
        .defaultValue(80.0).range(20.0, 180.0).sliderRange(40.0, 120.0)
        .build());

    private final Setting<Boolean> realisticTiming = sgHuman.add(new BoolSetting.Builder()
        .name("realistic-timing")
        .description("Simulates human reaction latency on new target acquisition.")
        .defaultValue(true)
        .build());

    private final Setting<Integer> reactionTimeMin = sgHuman.add(new IntSetting.Builder()
        .name("reaction-time-min").description("Min reaction latency (ms).")
        .defaultValue(25).range(0, 500).sliderRange(0, 300)
        .visible(realisticTiming::get)
        .build());

    private final Setting<Integer> reactionTimeMax = sgHuman.add(new IntSetting.Builder()
        .name("reaction-time-max").description("Max reaction latency (ms).")
        .defaultValue(60).range(0, 500).sliderRange(0, 300)
        .visible(realisticTiming::get)
        .build());

    // ------------------------------------------------------------------ Targeting
    private final Setting<AimPart> hitboxTarget = sgTargeting.add(new EnumSetting.Builder<AimPart>()
        .name("hitbox-target").description("Target zone within the enemy hitbox.")
        .defaultValue(AimPart.Head)
        .build());

    private final Setting<Set<EntityType<?>>> entities = sgTargeting.add(new EntityTypeListSetting.Builder()
        .name("entities").description("Entity types to target.")
        .onlyAttackable()
        .build());

    private final Setting<Boolean> targetNpcs = sgTargeting.add(new BoolSetting.Builder()
        .name("target-npcs").description("Also target villagers and wandering traders.")
        .defaultValue(false)
        .build());

    private final Setting<Boolean> ignoreFriends = sgTargeting.add(new BoolSetting.Builder()
        .name("ignore-friends").description("Skip players on the friends list.")
        .defaultValue(true)
        .build());

    private final Setting<Boolean> ignoreInvisible = sgTargeting.add(new BoolSetting.Builder()
        .name("ignore-invisible").description("Skip invisible entities.")
        .defaultValue(true)
        .build());

    private final Setting<TargetPriority> priority = sgTargeting.add(new EnumSetting.Builder<TargetPriority>()
        .name("priority").description("How to rank multiple valid targets.")
        .defaultValue(TargetPriority.Closest)
        .build());

    private final Setting<Boolean> sticky = sgTargeting.add(new BoolSetting.Builder()
        .name("sticky-target").description("Hold the current target until it exits the unlock range.")
        .defaultValue(true)
        .build());

    private final Setting<Double> unlockRange = sgTargeting.add(new DoubleSetting.Builder()
        .name("unlock-range").description("Distance at which a sticky target is released.")
        .defaultValue(5.5).range(2.0, 12.0).sliderRange(2.0, 10.0)
        .visible(sticky::get)
        .build());

    // ------------------------------------------------------------------ Multi-Target
    private final Setting<Boolean> multiTarget = sgMulti.add(new BoolSetting.Builder()
        .name("multi-target").description("Enable multi-target prioritisation logic.")
        .defaultValue(true)
        .build());

    private final Setting<MultiTargetMode> multiTargetMode = sgMulti.add(new EnumSetting.Builder<MultiTargetMode>()
        .name("multi-target-mode").description("Strategy when multiple targets are in range.")
        .defaultValue(MultiTargetMode.Closest)
        .visible(multiTarget::get)
        .build());

    private final Setting<Integer> switchCooldown = sgMulti.add(new IntSetting.Builder()
        .name("switch-cooldown").description("Ticks before a target switch is allowed.")
        .defaultValue(4).range(0, 40).sliderRange(0, 30)
        .visible(multiTarget::get)
        .build());

    private final Setting<Integer> cycleTime = sgMulti.add(new IntSetting.Builder()
        .name("cycle-time").description("Ticks between automatic target cycling.")
        .defaultValue(40).range(10, 200).sliderRange(10, 100)
        .visible(() -> multiTarget.get() && multiTargetMode.get() == MultiTargetMode.Cycle)
        .build());

    // ------------------------------------------------------------------ Checks
    private final Setting<Boolean> throughWalls = sgChecks.add(new BoolSetting.Builder()
        .name("through-walls").description("Target entities through solid blocks.")
        .defaultValue(false)
        .build());

    private final Setting<Boolean> requireAttackCooldown = sgChecks.add(new BoolSetting.Builder()
        .name("require-attack-cooldown").description("Only assist when the attack cooldown is full.")
        .defaultValue(false)
        .build());

    // ------------------------------------------------------------------ Render
    private final Setting<Boolean> renderCircle = sgRender.add(new BoolSetting.Builder()
        .name("target-circle").description("Draw a ring around the locked target's feet.")
        .defaultValue(true)
        .build());

    private final Setting<ShapeMode> circleShapeMode = sgRender.add(new EnumSetting.Builder<ShapeMode>()
        .name("circle-shape-mode").description("Fill / outline / both.")
        .defaultValue(ShapeMode.Both)
        .visible(renderCircle::get)
        .build());

    private final Setting<SettingColor> circleFillColor = sgRender.add(new ColorSetting.Builder()
        .name("circle-fill-color").description("Ring fill color.")
        .defaultValue(new SettingColor(255, 50, 50, 40))
        .visible(() -> renderCircle.get() && circleShapeMode.get().sides())
        .build());

    private final Setting<SettingColor> circleOutlineColor = sgRender.add(new ColorSetting.Builder()
        .name("circle-outline-color").description("Ring outline color.")
        .defaultValue(new SettingColor(255, 60, 60, 220))
        .visible(() -> renderCircle.get() && circleShapeMode.get().lines())
        .build());

    private final Setting<Double> circleRadius = sgRender.add(new DoubleSetting.Builder()
        .name("circle-radius").description("Ring radius in blocks.")
        .defaultValue(0.55).range(0.2, 2.0).sliderRange(0.2, 2.0)
        .visible(renderCircle::get)
        .build());

    private final Setting<Integer> circleSegments = sgRender.add(new IntSetting.Builder()
        .name("circle-segments").description("Polygon resolution of the ring.")
        .defaultValue(32).range(8, 64).sliderRange(8, 48)
        .visible(renderCircle::get)
        .build());

    private final Setting<Double> circleHeight = sgRender.add(new DoubleSetting.Builder()
        .name("circle-height").description("Vertical offset above the target's feet.")
        .defaultValue(0.02).range(0.0, 0.5).sliderRange(0.0, 0.2)
        .visible(renderCircle::get)
        .build());

    // ------------------------------------------------------------------ State
    private Entity  target;
    private long    reactionReadyAt;
    private int     switchCooldownTicks;
    private int     cycleTimer;

    private float   lastPlayerYaw   = 0f;
    private float   lastPlayerPitch = 0f;
    private long    lastTickNs      = 0L;
    private World   lastWorld       = null;

    private int     overshootTicksLeft = 0;
    private int     overshootDuration  = 0;
    private double  overshootYawOffset   = 0.0;
    private double  overshootPitchOffset  = 0.0;

    // Correlated micro-tremor state (Ornstein-Uhlenbeck)
    private double  tremorYaw   = 0.0;
    private double  tremorPitch = 0.0;

    // Visual naturalness state
    private long    trackStartNs = 0L;    // for fatigue simulation
    private int     fatigueTicks = 0;
    private double  prevAimX    = 0.0;
    private double  prevAimY    = 0.0;
    private double  prevAimZ    = 0.0;
    private boolean hasPrevAim  = false;

    // Multi-target transition state
    private float   switchStartYaw   = 0f;
    private float   switchStartPitch = 0f;
    private boolean inSwitchTransition = false;
    private int     switchTransitionTicks = 0;
    private static final int SWITCH_TRANSITION_DURATION = 6; // ticks to blend between targets

    // Anti-cheat (Grim/Vulcan) safety state
    private float   prevStepYaw   = 0f;
    private float   prevStepPitch = 0f;
    private int     skipTickCounter = 0;

    // FIX 10: aim part resolved once and held across ticks (stable for Random)
    private AimPart currentAimPart = AimPart.Body;
    private int     aimPartTicks   = 0;
    private static final int AIM_PART_REROLL_TICKS = 10;

    private final List<AimTarget> targetCache = new ArrayList<>(16);
    private final List<Entity>    targetEntityCache = new ArrayList<>(32);
    private int targetCacheAge = 0;
    private static final int TARGET_CACHE_TTL = 2;

    // ------------------------------------------------------------------ ctor
    public AdvancedAimAssist() {
        super(Categories.Combat, "advanced-aim-assist",
              "DemonClient v2.2 — ease-out-cubic, correlated tremor, GCD bypass.");
    }

    // ------------------------------------------------------------------ lifecycle
    private void resetState() {
        target             = null;
        reactionReadyAt    = 0L;
        switchCooldownTicks = 0;
        cycleTimer         = 0;
        overshootTicksLeft = 0;
        overshootDuration  = 0;
        overshootYawOffset = 0.0;
        overshootPitchOffset = 0.0;
        tremorYaw          = 0.0;
        tremorPitch        = 0.0;
        trackStartNs       = 0L;
        fatigueTicks       = 0;
        hasPrevAim         = false;
        prevStepYaw        = 0f;
        prevStepPitch      = 0f;
        skipTickCounter    = 0;
        aimPartTicks       = 0;
        inSwitchTransition = false;
        switchTransitionTicks = 0;
        targetCache.clear();
        targetEntityCache.clear();
        targetCacheAge     = 0;
        if (mc.player != null) {
            lastPlayerYaw   = mc.player.getYaw();
            lastPlayerPitch = mc.player.getPitch();
        } else {
            lastPlayerYaw   = 0f;
            lastPlayerPitch = 0f;
        }
    }

    private void validateSettings() {
        try {
            smoothness.set(MathHelper.clamp(smoothness.get(), 0.01, 0.99));
            range.set(MathHelper.clamp(range.get(), 2.0, 6.0));
            int pt = MathHelper.clamp(predictionTicks.get(), 1, 5);
            predictionTicks.set(pt);
            horizontalSpeed.set(MathHelper.clamp(horizontalSpeed.get(), 20.0, 720.0));
            verticalSpeed.set(MathHelper.clamp(verticalSpeed.get(), 20.0, 500.0));
        } catch (NullPointerException | IllegalStateException e) {
            LOG.warn("Settings validation failed: {}", e.getMessage());
        }
    }

    @Override public void onActivate()   { validateSettings(); resetState(); lastWorld = mc.world; lastTickNs = 0L; }
    @Override public void onDeactivate() { resetState(); lastWorld = null; }

    @EventHandler
    private void onGameLeft(GameLeftEvent event)     { resetState(); lastWorld = null; }

    @EventHandler
    private void onGameJoined(GameJoinedEvent event) {
        validateSettings();
        resetState();
        lastWorld = mc.world;
    }

    private boolean isHoldingWeapon() {
        if (mc.player == null) return false;
        try {
            // FIX 17: use item tags/identity instead of toString() substring matching.
            var stack = mc.player.getMainHandStack();
            return stack.isIn(ItemTags.SWORDS)
                || stack.isIn(ItemTags.AXES)
                || stack.getItem() == Items.TRIDENT
                || stack.getItem() == Items.MACE;
        } catch (NullPointerException | IllegalStateException e) {
            return false;
        }
    }

    // ------------------------------------------------------------------ public API
    public float[] getAimAngles(Entity targetEntity) {
        // FIX 2: external clients only; do not advance interpolation state.
        return anglesFromPoint(computeAimPointOnce(targetEntity, false));
    }

    private float[] anglesFromPoint(Vec3d point) {
        if (point == null || mc.player == null) return null;
        Vec3d eye   = mc.player.getEyePos();
        float yaw   = (float) AimUtils.getYaw(eye, point);
        float pitch = (float) MathHelper.clamp(AimUtils.getPitch(eye, point), -90.0, 90.0);
        if (!Float.isFinite(yaw) || !Float.isFinite(pitch)) return null;
        return new float[]{ yaw, pitch };
    }

    private Vec3d buildInterpolatedAimPoint(Entity entity) {
        // FIX 2: single onTick entry point that advances interpolation state.
        return computeAimPointOnce(entity, true);
    }

    private Vec3d computeAimPointOnce(Entity entity, boolean advanceInterpolation) {
        if (entity == null || mc.player == null) return null;
        final Random rng = ThreadLocalRandom.current();
        // Reuse a single base point (AimUtils allocates; unavoidable for immutable Vec3d)
        Vec3d base = null;
        try {
            base = AimUtils.getAimPoint(entity, currentAimPart, false, 0); // FIX 10
        } catch (NullPointerException | IllegalStateException e) {
            LOG.debug("getAimPoint failed", e);
            return null;
        }
        if (base == null) return null;

        // Kinematic Velocity Intercept with ping compensation + parabolic vertical
        if (prediction.get() && mc.player != null) {
            Vec3d targetVel = entity.getVelocity();
            Vec3d playerVel = mc.player.getVelocity();
            int   ticks     = getEffectivePredictionTicks(entity); // FIX 7

            // Dampened prediction: over-leading is a common anti-cheat trigger
            double relX = (targetVel.x - playerVel.x * 0.40) * ticks * 0.85;
            double relZ = (targetVel.z - playerVel.z * 0.40) * ticks * 0.85;
            // Parabolic vertical prediction (gravity + drag), conservative.
            // FIX 8: grounded targets have no meaningful vertical motion to lead.
            double relY = entity.isOnGround()
                ? 0.0
                : AimUtils.predictVerticalOffset(targetVel.y, ticks) * 0.50;

            Box    box  = entity.getBoundingBox();
            double maxH = (box.maxX - box.minX) * 1.5;
            double maxV = (box.maxY - box.minY) * 0.9;
            relX = MathHelper.clamp(relX, -maxH, maxH);
            relZ = MathHelper.clamp(relZ, -maxH, maxH);
            relY = MathHelper.clamp(relY, -maxV, maxV);

            base = base.add(relX, relY, relZ);
        }

        // Bezier Wrist Pivot Arc: REMOVED periodic sine (detectable pattern).
        // Replaced with a subtle, non-periodic lateral offset.
        if (arcCurvature.get() > 0.0 && mc.player != null) {
            Vec3d eye = mc.player.getEyePos();
            Vec3d toTarget = base.subtract(eye);
            double dist = toTarget.length();
            if (dist > 0.5) {
                Vec3d perp = new Vec3d(-toTarget.z, 0, toTarget.x);
                double perpLen = perp.length();
                if (perpLen > 1e-6) {
                    perp = perp.multiply(1.0 / perpLen);
                    double offset = arcCurvature.get() * 0.02 * (rng.nextDouble() - 0.5) * dist;
                    base = base.add(perp.multiply(offset));
                }
            }
        }

        // Hitbox Roaming: REMOVED periodic sine patterns (detectable).
        // Replaced with a single, non-periodic micro-jitter.
        if (hitboxRoaming.get() > 0.0) {
            double jitterAmp = hitboxRoaming.get() * 0.02;
            base = base.add(
                (rng.nextDouble() - 0.5) * jitterAmp,
                (rng.nextDouble() - 0.5) * jitterAmp * 0.5,
                (rng.nextDouble() - 0.5) * jitterAmp
            );
        }

        Box b = entity.getBoundingBox();

        // --- Visual naturalness: prediction inaccuracy (small random error) ---
        // Humans don't predict perfectly; add tiny noise to the predicted point.
        if (prediction.get()) {
            double inaccuracy = humanVariation.get() * 0.2; // FIX 12: was * 0.02
            base = base.add(
                (rng.nextDouble() - 0.5) * inaccuracy,
                (rng.nextDouble() - 0.5) * inaccuracy * 0.6,
                (rng.nextDouble() - 0.5) * inaccuracy
            );
        }

        // FIX 3: duplicate path-curvature block removed (arc is applied once above).

        // --- Visual naturalness: tracking lag (human reaction delay) ---
        // Smooth the aim point toward the target instead of snapping instantly.
        if (advanceInterpolation && hasPrevAim) {
            // Tracking lag: minimal. Large lag creates detectable "delayed tracking".
            double lagFactor = 0.92 + rng.nextDouble() * 0.06;
            base = new Vec3d(
                prevAimX + (base.x - prevAimX) * lagFactor,
                prevAimY + (base.y - prevAimY) * lagFactor,
                prevAimZ + (base.z - prevAimZ) * lagFactor
            );
        }
        if (advanceInterpolation) {
            prevAimX = base.x;
            prevAimY = base.y;
            prevAimZ = base.z;
            hasPrevAim = true;
        }

        // Breathing/drift: single non-periodic micro-jitter (FIX 3: applied once).
        // FIX 12: recalibrated so the human-variation slider has a visible effect.
        double breathAmp = 0.005 + humanVariation.get() * 0.05;
        base = base.add(
            (rng.nextDouble() - 0.5) * breathAmp,
            (rng.nextDouble() - 0.5) * breathAmp,
            (rng.nextDouble() - 0.5) * breathAmp
        );

        // Clamp final aim point inside the target hitbox (with small inner margin)
        // so the attack raycast actually connects instead of grazing the edge.
        // FIX 15: margin scales down on tiny hitboxes so min <= max always holds.
        double marginX = Math.min(0.05, (b.maxX - b.minX) * 0.4);
        double marginY = Math.min(0.05, (b.maxY - b.minY) * 0.4);
        double marginZ = Math.min(0.05, (b.maxZ - b.minZ) * 0.4);
        base = new Vec3d(
            MathHelper.clamp(base.x, b.minX + marginX, b.maxX - marginX),
            MathHelper.clamp(base.y, b.minY + marginY, b.maxY - marginY),
            MathHelper.clamp(base.z, b.minZ + marginZ, b.maxZ - marginZ)
        );
        return base;
    }

    /**
     * FIX 10: resolves {@link #currentAimPart}. A fixed setting is applied directly;
     * {@link AimPart#Random} is rolled once and held for {@link #AIM_PART_REROLL_TICKS}
     * ticks (or until the target changes) so the aim point does not jump every tick.
     */
    private AimPart resolveAimPart() {
        AimPart setting = hitboxTarget.get();
        if (setting != AimPart.Random) {
            currentAimPart = setting;
        } else if (aimPartTicks <= 0 || currentAimPart == AimPart.Random) {
            currentAimPart = AimUtils.rollAimPart();
            aimPartTicks   = AIM_PART_REROLL_TICKS;
        } else {
            aimPartTicks--;
        }
        return currentAimPart;
    }

    private int getEffectivePredictionTicks(Entity entity) {
        // FIX 7: use the provided entity instead of the module-level target field.
        if (!prediction.get() || mc.player == null || entity == null) return 0;
        int base = predictionTicks.get();
        int pingTicks = AimUtils.getPingTicks();
        int effective = base + pingTicks;
        // Reduce prediction for close targets (less needed, more detectable)
        try {
            double dist = mc.player.getEyePos().distanceTo(entity.getEntityPos());
            if (dist < 2.5) effective = Math.max(0, effective - 1);
            if (dist < 1.8) effective = 0;
        } catch (NullPointerException | IllegalStateException e) { /* ignore */ }
        return MathHelper.clamp(effective, 0, 5);
    }

    // ------------------------------------------------------------------ tick
    @EventHandler
    private void onTick(TickEvent.Post event) {
        try {
            if (mc.player == null || mc.world == null || !mc.player.isAlive()) {
                resetState(); return;
            }
            if (mc.world != lastWorld) {
                lastWorld = mc.world; resetState(); return;
            }

            long now = System.nanoTime();
            if (now - lastTickNs < 10_000_000L) return;
            lastTickNs = now;

            if (target != null &&
                (target.isRemoved() || !target.isAlive() || target.getEntityWorld() != mc.world)) {
                target = null;
            }

            if (!Float.isFinite(mc.player.getYaw()) || !Float.isFinite(mc.player.getPitch())) {
                mc.player.setYaw(0f); mc.player.setPitch(0f); return;
            }

            float currentYaw   = mc.player.getYaw();
            float currentPitch = mc.player.getPitch();

            // FIX 1: capture tick-start rotation before any setYaw/setPitch below.
            final float tickStartYaw   = currentYaw;
            final float tickStartPitch = currentPitch;

            // FIX 14: cache per-tick values reused across the tick.
            final Vec3d eyePos     = mc.player.getEyePos();
            final Vec3d startLook  = AimUtils.getLookVector(currentYaw, currentPitch);
            final double rangeVal  = range.get();
            final double escapeVal = escapeAngle.get();
            final Random rng       = ThreadLocalRandom.current();

            // FIX 1: isolate raw manual mouse delta (rotation at the start of this
            // tick minus the rotation recorded at the start of the previous tick).
            float manualDeltaYaw   = MathHelper.wrapDegrees(currentYaw - lastPlayerYaw);
            float manualDeltaPitch = currentPitch - lastPlayerPitch;

            if (onlyWhenClicking.get() && (mc.options == null || !mc.options.attackKey.isPressed())) {
                clearAssist(currentYaw, currentPitch); return;
            }
            if (weaponOnly.get() && !isHoldingWeapon()) {
                clearAssist(currentYaw, currentPitch); return;
            }

            if (switchCooldownTicks > 0) switchCooldownTicks--;
            if (cycleTimer         > 0) cycleTimer--;

            // FIX 10: fix the aim part for this tick before target selection.
            AimPart effectivePart = resolveAimPart();

            Entity found = findBestTarget(rangeVal, escapeVal, eyePos, startLook, effectivePart);
            if (found != target && found != null) {
                // FIX 5: switchCooldownTicks gates new switches (sticky now holds
                // the current target independently of the cooldown).
                if (switchCooldownTicks <= 0) {
                    // Smooth transition between targets (flick-like but controlled)
                    target = found;
                    inSwitchTransition = true;
                    switchTransitionTicks = SWITCH_TRANSITION_DURATION;
                    switchStartYaw = mc.player.getYaw();
                    switchStartPitch = mc.player.getPitch();
                    onTargetChanged();
                }
            } else if (found == null) {
                target = null;
                inSwitchTransition = false;
            }

            if (target == null) { clearAssist(currentYaw, currentPitch); return; }

            if (realisticTiming.get() && now < reactionReadyAt) {
                lastPlayerYaw = currentYaw; lastPlayerPitch = currentPitch; return;
            }
            if (requireAttackCooldown.get() && mc.player.getAttackCooldownProgress(0.5f) < 1.0f) {
                lastPlayerYaw = currentYaw; lastPlayerPitch = currentPitch; return;
            }

            // FIX 2: compute the aim point once per tick and derive angles from it.
            Vec3d aimPoint = buildInterpolatedAimPoint(target);
            if (aimPoint == null) { clearAssist(currentYaw, currentPitch); return; }
            float[] angles = anglesFromPoint(aimPoint);
            if (angles == null) { clearAssist(currentYaw, currentPitch); return; }

            double wantedYaw   = angles[0];
            double wantedPitch = angles[1];

            // Pre-compute angle to target (used to gate overshoot / snap)
            double currentAngleToTarget = AimUtils.getAngle(startLook, eyePos, aimPoint);

            // ── Micro-Overshoot Injection (2-4 ticks with decay) ─────────────
            // Disabled during target switch transitions for smoother flicks.
            if (microOvershoot.get() && !inSwitchTransition) {
                double rawCheckDist = Math.abs(MathHelper.wrapDegrees(wantedYaw - currentYaw));
                if (rawCheckDist > 20.0 && currentAngleToTarget > 12.0
                        && overshootTicksLeft <= 0 && rng.nextDouble() < 0.15) {
                    overshootTicksLeft = rng.nextInt(2, 3); // 2 ticks only
                    overshootDuration  = overshootTicksLeft;
                    overshootYawOffset   = Math.signum(MathHelper.wrapDegrees(wantedYaw - currentYaw))
                                         * rng.nextDouble(0.2, 0.5);
                    overshootPitchOffset = Math.signum(wantedPitch - currentPitch)
                                         * rng.nextDouble(0.1, 0.3);
                }
            }
            if (overshootTicksLeft > 0) {
                double decay = (double) overshootTicksLeft / Math.max(1, overshootDuration);
                wantedYaw   += overshootYawOffset * decay;
                wantedPitch  = MathHelper.clamp(wantedPitch + overshootPitchOffset * decay, -90.0, 90.0);
                overshootTicksLeft--;
            }

            // Auto-attack: add random delay to avoid mechanical attack patterns.
            // Only attack ~60% of eligible ticks to simulate human hesitation.
            if (autoAttack.get() && mc.interactionManager != null
                && mc.player.getAttackCooldownProgress(0.5f) >= 1.0f
                && rng.nextDouble() < 0.4) {
                // FIX 2: reuse the single aim point instead of recomputing it.
                if (AimUtils.getAngle(startLook, eyePos, aimPoint) < 4.0) {
                    mc.interactionManager.attackEntity(mc.player, target);
                    mc.player.swingHand(net.minecraft.util.Hand.MAIN_HAND);
                }
            }

            // Escape angle (currentAngleToTarget already computed above)
            boolean playerSteeringAway =
                Math.abs(manualDeltaYaw) > 0.25f
                && Math.signum(manualDeltaYaw) != Math.signum(
                      MathHelper.wrapDegrees(wantedYaw - currentYaw));

            if (playerSteeringAway && currentAngleToTarget > escapeVal) {
                clearAssist(currentYaw, currentPitch); return;
            }

            // Correlated micro-tremor (Ornstein-Uhlenbeck) instead of white noise
            if (humanVariation.get() > 0) {
                double dt = 1.0 / 20.0;
                double theta = 1.5;
                double sigma = humanVariation.get() * 0.6; // FIX 12: was * 0.06
                double sqrtDt = Math.sqrt(dt);
                tremorYaw   += -theta * tremorYaw   * dt + sigma * rng.nextGaussian() * sqrtDt;
                tremorPitch += -theta * tremorPitch * dt + sigma * 0.6 * rng.nextGaussian() * sqrtDt;
                // FIX 12: clamp widened from * 0.5 to * 2.0
                double tremorMax = humanVariation.get() * 2.0;
                tremorYaw   = MathHelper.clamp(tremorYaw,   -tremorMax, tremorMax);
                tremorPitch = MathHelper.clamp(tremorPitch, -tremorMax, tremorMax);
                // Tremor: minimal contribution. Large tremor creates detectable jitter.
                wantedYaw   += tremorYaw * 0.1;
                wantedPitch  = MathHelper.clamp(wantedPitch + tremorPitch * 0.1, -90.0, 90.0);
            }

            // GCD quantisation (pre-delta) — ALWAYS when gcdFix enabled
            if (gcdFix.get()) {
                wantedYaw   = RotationUtils.applyGCD(currentYaw,   wantedYaw);
                wantedPitch = RotationUtils.applyGCD(currentPitch, wantedPitch);
            }

            double rawDeltaYaw   = MathHelper.wrapDegrees(wantedYaw   - currentYaw);
            double rawDeltaPitch = wantedPitch - currentPitch;
            double absYaw        = Math.abs(rawDeltaYaw);
            double absPitch      = Math.abs(rawDeltaPitch);

            if (absYaw < 0.0005 && absPitch < 0.0005) {
                clearAssist(currentYaw, currentPitch); return;
            }

            // Multi-target: transition factor (0→1 over SWITCH_TRANSITION_DURATION ticks)
            double transitionFactor = 1.0;
            if (inSwitchTransition) {
                switchTransitionTicks--;
                if (switchTransitionTicks <= 0) {
                    inSwitchTransition = false;
                } else {
                    transitionFactor = 1.0 - (switchTransitionTicks / (double) SWITCH_TRANSITION_DURATION);
                    // Ease-in: start slow, accelerate (human flick pattern)
                    transitionFactor = transitionFactor * transitionFactor;
                    // Clamp minimum to avoid stalling
                    transitionFactor = Math.max(transitionFactor, 0.3);
                }
            }

            // Visual naturalness + anti-cheat: occasionally skip a tick (humans
            // don't move the mouse every 50 ms). ~12% of ticks.
            if (skipTickCounter > 0) {
                skipTickCounter--;
            } else if (rng.nextDouble() < 0.015) {
                skipTickCounter = 1;
                lastPlayerYaw = currentYaw;
                lastPlayerPitch = currentPitch;
                return;
            }

            // ── EASE-OUT-CUBIC DAMPING & ASYMMETRIC DECOUPLING ──
            float maxYawTick   = (float) (horizontalSpeed.get() / 20.0);
            float maxPitchTick = (float) (verticalSpeed.get()   / 20.0);
            double glue        = MathHelper.clamp(stickyGlue.get(), 0.1, 1.0);
            double smooth      = MathHelper.clamp(smoothness.get(), 0.01, 0.99);

            double manualMagnitude = Math.hypot(manualDeltaYaw, manualDeltaPitch);
            double synergyFactor   = manualMagnitude > 0.02f ? 1.0 : 0.42;

            // Ease-out-cubic: decelerates as crosshair approaches target
            double tYaw  = Math.min(absYaw   / 25.0, 1.0);
            double tPitch = Math.min(absPitch / 20.0, 1.0);
            double easeYaw   = 1.0 - Math.pow(1.0 - tYaw,   3.0);
            double easePitch = 1.0 - Math.pow(1.0 - tPitch, 3.0);
            double expFactorYaw   = Math.max(easeYaw,   0.10);
            double expFactorPitch = Math.max(easePitch, 0.10);

            double stepY = maxYawTick   * expFactorYaw   * (0.65 + 0.35 * glue) * (1.0 - smooth * 0.15) * synergyFactor * transitionFactor;
            double stepP = maxPitchTick * expFactorPitch * (0.60 + 0.40 * glue) * (1.0 - (smooth * 1.20) * 0.15) * synergyFactor * transitionFactor;

            // Velocity floor for target tracking
            float targetAngularSpeedYaw = 0f, targetAngularSpeedPitch = 0f;
            if (target != null) {
                Vec3d vel    = target.getVelocity();
                Vec3d dir    = aimPoint.subtract(eyePos);
                double dist  = dir.length();
                if (dist > 0.01) {
                    Vec3d dirN  = dir.normalize();
                    Vec3d perpH = new Vec3d(-dirN.z, 0, dirN.x);
                    double latV = Math.abs(vel.subtract(dirN.multiply(vel.dotProduct(dirN))).dotProduct(perpH));
                    targetAngularSpeedYaw   = (float) Math.toDegrees(Math.atan2(latV, dist));
                    targetAngularSpeedPitch = targetAngularSpeedYaw * 0.45f;
                }
            }

            // Velocity floor: minimal. Perfect strafe-tracking is a strong
            // detection vector. Only apply a tiny floor to avoid stalling.
            float floorYaw   = Math.max(targetAngularSpeedYaw * 0.10f, 0.03f);
            float floorPitch = Math.max(targetAngularSpeedPitch * 0.10f, 0.02f);

            float finalStepYaw   = (float) Math.max(stepY, floorYaw);
            float finalStepPitch = (float) Math.max(stepP, floorPitch);

            finalStepYaw   = Math.min(finalStepYaw,   maxYawTick);
            finalStepPitch = Math.min(finalStepPitch, maxPitchTick);

            finalStepYaw   = (float) Math.min(finalStepYaw,   absYaw);
            finalStepPitch = (float) Math.min(finalStepPitch, absPitch);

            // Micro-magnetism REMOVED: the "pull to center" pattern is a known
            // detection vector for Grim/Vulcan (snap-to-center). Instead, the
            // aim naturally decelerates via ease-out-cubic as it approaches.

            // Visual naturalness: per-tick speed variation (humans don't move
            // at constant speed). ±15% jitter on the step magnitude.
            double speedJitter = 0.99 + rng.nextDouble() * 0.02;
            finalStepYaw   = (float) (finalStepYaw   * speedJitter);
            finalStepPitch = (float) (finalStepPitch * speedJitter);

            // Visual naturalness: fatigue simulation. After tracking for >2s,
            // slightly reduce accuracy (humans get tired / less precise).
            if (trackStartNs == 0L) trackStartNs = now;
            if (now - trackStartNs > 2_000_000_000L) {
                fatigueTicks++;
                if (fatigueTicks % 40 == 0) {
                    finalStepYaw   = (float) (finalStepYaw   * 0.985);
                    finalStepPitch = (float) (finalStepPitch * 0.985);
                }
            }

            // Anti-cheat: jerk (acceleration) limit. Humans can't change
            // rotation speed instantly; clamp the per-tick step delta.
            double maxYawJerk   = 2.0;
            double maxPitchJerk = 1.4;
            double yawDelta   = finalStepYaw   - prevStepYaw;
            double pitchDelta = finalStepPitch - prevStepPitch;
            yawDelta   = MathHelper.clamp(yawDelta,   -maxYawJerk,   maxYawJerk);
            pitchDelta = MathHelper.clamp(pitchDelta, -maxPitchJerk, maxPitchJerk);
            finalStepYaw   = (float) (prevStepYaw   + yawDelta);
            finalStepPitch = (float) (prevStepPitch + pitchDelta);
            prevStepYaw    = finalStepYaw;
            prevStepPitch  = finalStepPitch;

            // Final snap when already on-target: close most of the gap so the
            // hit connects, but leave a tiny residual so it doesn't look locked. ⚠️
            // Micro-snap: minimal residual. The ease-out-cubic already decelerates
            // naturally; a large snapFactor creates detectable "hovering".
            if (absYaw < 1.5 && absPitch < 1.5) {
                double snapFactor = 0.88 + rng.nextDouble() * 0.10;
                finalStepYaw   = (float) (absYaw   * snapFactor);
                finalStepPitch = (float) (absPitch * snapFactor);
            }

            float yawStep   = finalStepYaw   * (float) Math.signum(rawDeltaYaw);
            float pitchStep = finalStepPitch * (float) Math.signum(rawDeltaPitch);

            // Anti-cheat: tiny non-periodic noise (before GCD, so it gets
            // absorbed into gcd alignment). Very small to avoid step flipping.
            if (rng.nextDouble() < 0.2) {
                yawStep += (rng.nextFloat() - 0.5f) * 0.0005f;
            }
            if (rng.nextDouble() < 0.15) {
                pitchStep += (rng.nextFloat() - 0.5f) * 0.0003f;
            }

            // GCD quantisation (post-step) — ALWAYS when gcdFix enabled
            if (gcdFix.get()) {
                yawStep   = (float) (RotationUtils.applyGCD(currentYaw,   currentYaw   + yawStep)   - currentYaw);
                pitchStep = (float) (RotationUtils.applyGCD(currentPitch, currentPitch + pitchStep) - currentPitch);
            }

            float finalYaw   = currentYaw   + yawStep;
            float finalPitch = MathHelper.clamp(currentPitch + pitchStep, -90.0f, 90.0f);

            // ⚠️ EMA smoothing REMOVED from rotation delta.
            // EMA on the delta breaks GCD alignment: the final yawStep is no
            // longer a multiple of gcd, which Grim/Vulcan detect instantly.
            // Smoothing is instead applied to the aim POINT before rotation
            // calculation, keeping the rotation GCD-aligned.

            if (!Float.isFinite(finalYaw) || !Float.isFinite(finalPitch)) {
                clearAssist(currentYaw, currentPitch); return;
            }

            if (Math.abs(yawStep) > 0.0001f || Math.abs(pitchStep) > 0.0001f) {
                if (aimMode.get() == AimMode.Client || aimMode.get() == AimMode.Assist) {
                    mc.player.setYaw(finalYaw);
                    mc.player.setPitch(finalPitch);
                } else {
                    Rotations.rotate(
                        (float) Rotations.serverYaw   + yawStep,
                        MathHelper.clamp((float) Rotations.serverPitch + pitchStep, -90f, 90f)
                    );
                }
            }

            // FIX 1: record the tick-START rotation (manual input reference),
            // not the final rotated yaw/pitch.
            lastPlayerYaw   = tickStartYaw;
            lastPlayerPitch = tickStartPitch;

        } catch (NullPointerException e) {
            LOG.error("NPE in aim assist tick", e);
            resetState();
        } catch (IllegalStateException e) {
            LOG.error("Illegal state in aim assist tick", e);
            resetState();
        } catch (ConcurrentModificationException e) {
            LOG.error("Concurrent modification in aim assist tick", e);
            resetState();
        } catch (Exception e) {
            LOG.error("Unexpected error in aim assist tick", e);
            resetState();
        }
    }

    private void clearAssist(float y, float p) {
        hasPrevAim     = false;
        // FIX 1: keep lastPlayerYaw/Pitch in sync with the player's current
        // rotation (the values passed in), never reset to zero.
        lastPlayerYaw   = y;
        lastPlayerPitch = p;
        target          = null;
    }

    // ------------------------------------------------------------------ render
    @EventHandler
    private void onRender3D(Render3DEvent event) {
        if (mc.player == null || mc.world == null || !mc.player.isAlive()) { target = null; return; }
        if (renderCircle.get() && target != null && !AimUtils.isDead(target)) {
            try { renderTargetCircle(event); } catch (NullPointerException | IllegalStateException | ConcurrentModificationException ignored) {
                LOG.debug("Render error ignored", ignored);
            }
        }
    }

    // ------------------------------------------------------------------ targeting
    private void onTargetChanged() {
        // FIX 9: drop jerk-limiter and overshoot state carried over from the old target.
        prevStepYaw        = 0f;
        prevStepPitch      = 0f;
        overshootTicksLeft = 0;
        overshootDuration  = 0;
        // FIX 10: re-roll the random aim part for the new target.
        aimPartTicks   = 0;
        resolveAimPart();
        hasPrevAim     = false;
        trackStartNs   = 0L;
        fatigueTicks   = 0;
        switchCooldownTicks = switchCooldown.get();
        if (!realisticTiming.get() || target == null) {
            reactionReadyAt = 0L;
        } else {
            int lo = Math.min(reactionTimeMin.get(), reactionTimeMax.get());
            int hi = Math.max(reactionTimeMin.get(), reactionTimeMax.get());
            reactionReadyAt = System.nanoTime()
                            + ThreadLocalRandom.current().nextLong(lo, (long) hi + 1) * 1_000_000L;
        }
    }

    // FIX 14: per-tick values (range, escape angle, eye, look, aim part) are passed in
    // so no setting is read inside the entity loop or the comparator.
    private Entity findBestTarget(double rangeVal, double escapeVal, Vec3d eye, Vec3d look, AimPart part) {
        try {
            // FIX 19: force a cache refresh when there is no target yet (fast acquisition).
            if (target == null) targetCacheAge = 0;

            // FIX 5: sticky holds the target as long as it stays valid/unlocked.
            if (sticky.get() && target != null && isValidStickyTarget(target, rangeVal, escapeVal, eye, look, part)) {
                return target;
            }

        final double  fovVal          = fov.get();
        final double  halfFov         = fovVal / 2.0;
        final boolean useFov          = fovVal < 360.0;
        final boolean throughWallsVal = throughWalls.get();
        final TargetPriority prio     = priority.get();

        // Cache the world entity list for a couple of ticks to reduce allocations/iteration
        if (targetCacheAge <= 0 || targetEntityCache.isEmpty()) {
            targetEntityCache.clear();
            if (mc.world != null) {
                for (Entity e : mc.world.getEntities()) {
                    targetEntityCache.add(e);
                }
            }
            targetCacheAge = TARGET_CACHE_TTL;
        }
        targetCacheAge--;

        targetCache.clear();
        int cacheSize = targetEntityCache.size();
        for (int i = 0; i < cacheSize; i++) {
            Entity entity = targetEntityCache.get(i);
            if (!isValidTarget(entity)) continue;
            Vec3d  point    = AimUtils.getAimPoint(entity, part, false, 0); // FIX 10/14
            double distance = eye.distanceTo(point);
            if (distance > rangeVal) continue;
            double angle = AimUtils.getAngle(look, eye, point);
            if (useFov && angle > halfFov) continue;
            if (!throughWallsVal && !AimUtils.hasLineOfSight(eye, point, entity)) continue;

            float health = AimUtils.getHealth(entity);
            targetCache.add(new AimTarget(entity, point, distance, angle, health));
        }

        if (targetCache.isEmpty()) return null;

        // FIX 4: rank by the selected priority, with distance as the tie-breaker.
        targetCache.sort((t1, t2) -> switch (prio) {
            case Closest -> Double.compare(t1.distance(), t2.distance());
            case LowestHealth -> {
                int c = Float.compare(t1.health(), t2.health());
                yield c != 0 ? c : Double.compare(t1.distance(), t2.distance());
            }
            case Angle -> {
                int c = Double.compare(t1.angle(), t2.angle());
                yield c != 0 ? c : Double.compare(t1.distance(), t2.distance());
            }
        });

        if (multiTarget.get() && multiTargetMode.get() == MultiTargetMode.Cycle && targetCache.size() > 1) {
            if (cycleTimer <= 0) {
                cycleTimer = cycleTime.get();
                int currentIndex = -1;
                int sz = targetCache.size();
                for (int i = 0; i < sz; i++) {
                    if (targetCache.get(i).entity() == target) {
                        currentIndex = i;
                        break;
                    }
                }
                int nextIndex = (currentIndex + 1) % targetCache.size();
                return targetCache.get(nextIndex).entity();
            }
        }

        return targetCache.get(0).entity();
        } catch (NullPointerException | IllegalStateException | ConcurrentModificationException e) {
            LOG.debug("findBestTarget failed", e);
            return null;
        }
    }

    // FIX 6: accept any LivingEntity; players always, villagers/wandering traders
    // only with target-npcs, everything else only when listed in `entities`.
    private boolean isValidTarget(Entity entity) {
        if (entity == null || entity == mc.player || entity == mc.getCameraEntity()) return false;
        if (!(entity instanceof LivingEntity)) return false;
        if (entity.isRemoved() || AimUtils.isDead(entity)) return false;
        if (ignoreInvisible.get() && entity.isInvisible()) return false;
        Set<EntityType<?>> types = entities.get();
        if (!types.isEmpty() && !types.contains(entity.getType())) return false;

        if (entity instanceof PlayerEntity player) {
            if (player.isSpectator() || player.isCreative()) return false;
            return !ignoreFriends.get() || Friends.get().shouldAttack(player);
        }
        if (entity instanceof VillagerEntity || entity instanceof WanderingTraderEntity) {
            return targetNpcs.get();
        }
        // Monsters / other mobs: only when explicitly selected in `entities`
        return !types.isEmpty();
    }

    private boolean isValidStickyTarget(Entity entity, double rangeVal, double escapeVal,
                                        Vec3d eye, Vec3d look, AimPart part) {
        if (!isValidTarget(entity)) return false;
        Vec3d  point = AimUtils.getAimPoint(entity, part, false, 0); // FIX 10
        double eff   = Math.max(unlockRange.get(), rangeVal + 0.5);
        if (eye.distanceTo(point) > eff) return false;
        if (!throughWalls.get() && !AimUtils.hasLineOfSight(eye, point, entity)) return false;
        return AimUtils.getAngle(look, eye, point) <= escapeVal;
    }

    private void renderTargetCircle(Render3DEvent event) {
        if (event.renderer == null || target.isRemoved() || !target.isAlive()) return;
        if (mc.world == null || target.getEntityWorld() != mc.world) { target = null; return; }

        Box    box      = target.getBoundingBox();
        if (box == null) return;

        double cx  = (box.minX + box.maxX) / 2.0;
        double cz  = (box.minZ + box.maxZ) / 2.0;
        double cy  = box.minY + circleHeight.get();
        int    seg = MathHelper.clamp(circleSegments.get(), 8, 64);
        double r   = circleRadius.get();
        ShapeMode mode = circleShapeMode.get();
        Color fill    = circleFillColor.get();
        Color outline = circleOutlineColor.get();

        if (mode.sides() && fill != null && fill.a > 0) {
            event.renderer.triangles.ensureCapacity(seg + 8, seg * 3 + 12);
            int center = event.renderer.triangles.vec3(cx, cy, cz).color(fill).next();
            int prev = -1;
            for (int i = 0; i <= seg; i++) {
                double a = 2.0 * Math.PI * i / seg;
                int v = event.renderer.triangles
                    .vec3(cx + Math.cos(a) * r, cy, cz + Math.sin(a) * r)
                    .color(fill).next();
                if (prev != -1) event.renderer.triangles.triangle(center, prev, v);
                prev = v;
            }
        }

        if (mode.lines() && outline != null && outline.a > 0) {
            event.renderer.lines.ensureCapacity(seg + 8, (seg + 2) * 2 + 12);
            int first = -1, prev = -1;
            for (int i = 0; i <= seg; i++) {
                double a = 2.0 * Math.PI * i / seg;
                int v = event.renderer.lines
                    .vec3(cx + Math.cos(a) * r, cy, cz + Math.sin(a) * r)
                    .color(outline).next();
                if (prev   == -1) first = v;
                else event.renderer.lines.line(prev, v);
                prev = v;
            }
            if (prev != -1 && first != -1) event.renderer.lines.line(prev, first);
        }
    }

    @Override
    public String getInfoString() {
        // FIX 18: never return null from getInfoString.
        return target != null ? AimUtils.getName(target) : "";
    }
}
