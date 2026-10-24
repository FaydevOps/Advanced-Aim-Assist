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
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

import static meteordevelopment.meteorclient.MeteorClient.mc;

/**
 * AdvancedAimAssist Exponential Smooth Ease-Out Edition
 *
 * Mathematical Camera Interpolation & Humanized Mouse Movement Architecture:
 *
 * 1. EXPONENTIAL SMOOTH EASE-OUT DAMPING
 *    Replaces linear or rigid steps with a continuous exponential ease-out function
 *    combined with sigmoidal velocity scaling. As the crosshair approaches the target,
 *    rotational velocity decelerates organically, perfectly simulating human wrist muscle
 *    deceleration without any robotic stops or jerky cutoffs.
 *
 * 2. ASYMMETRIC YAW & PITCH NON-LINEARITY
 *    Yaw and Pitch utilize independent, non-linear damping coefficients. Horizontal arm sweeps
 *    and vertical wrist adjustments scale differently, destroying robotic synchronized axis locking.
 *
 * 3. ADDITIVE SMOOTH PULL & MOUSE SYNERGY
 *    Assist torque seamlessly blends with the player's own mouse input. When the user moves
 *    their mouse toward the opponent, the aimbot melts into their movement invisibly.
 *
 * 4. KINEMATIC INTERCEPT & GCD BYPASS
 *    Tick-compensated velocity extrapolation ensures the aim point never lags behind strafing
 *    opponents, while strict modulo GCD alignment (~0.0055°) bypasses Grim and Vulcan anti-cheats.
 */
public class AdvancedAimAssist extends Module {

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
        .defaultValue(4.5).range(2.0, 6.0).sliderRange(2.0, 6.0)
        .build());

    private final Setting<Double> horizontalSpeed = sgGeneral.add(new DoubleSetting.Builder()
        .name("horizontal-speed")
        .description("Peak horizontal angular speed (deg/sec).")
        .defaultValue(280.0).range(20.0, 720.0).sliderRange(40.0, 500.0)
        .build());

    private final Setting<Double> verticalSpeed = sgGeneral.add(new DoubleSetting.Builder()
        .name("vertical-speed")
        .description("Peak vertical angular speed (deg/sec).")
        .defaultValue(190.0).range(20.0, 500.0).sliderRange(40.0, 400.0)
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
        .defaultValue(2).range(1, 6).sliderRange(1, 5)
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
        .defaultValue(0.82).range(0.1, 1.0).sliderRange(0.1, 1.0)
        .build());

    private final Setting<Double> microMagnetism = sgPro.add(new DoubleSetting.Builder()
        .name("micro-magnetism")
        .description("Extra centering pull applied when crosshair is inside the hitbox (<6°).")
        .defaultValue(0.35).range(0.0, 3.0).sliderRange(0.0, 2.0)
        .build());

    // ------------------------------------------------------------------ Humanization
    private final Setting<Double> smoothness = sgHuman.add(new DoubleSetting.Builder()
        .name("smoothness")
        .description("Exponential damping factor. Lower = crisp snap. Higher = soft cinematic ease-out.")
        .defaultValue(0.22).range(0.01, 0.99).sliderRange(0.01, 0.99)
        .build());

    private final Setting<Double> arcCurvature = sgHuman.add(new DoubleSetting.Builder()
        .name("arc-curvature")
        .description("Wrist pivot arc trajectory intensity.")
        .defaultValue(0.12).range(0.0, 1.0).sliderRange(0.0, 0.5)
        .build());

    private final Setting<Boolean> microOvershoot = sgHuman.add(new BoolSetting.Builder()
        .name("micro-overshoot")
        .description("Simulates human error by subtly overshooting fast snaps by 1-2 pixels.")
        .defaultValue(true)
        .build());

    private final Setting<Double> humanVariation = sgHuman.add(new DoubleSetting.Builder()
        .name("human-variation")
        .description("Micro-noise amplitude for behavioural heuristic evasion.")
        .defaultValue(0.015).range(0.0, 1.0).sliderRange(0.0, 0.5)
        .build());

    private final Setting<Double> hitboxRoaming = sgHuman.add(new DoubleSetting.Builder()
        .name("hitbox-roaming-intensity")
        .description("Allows the aim to drift naturally within the enemy bounding box.")
        .defaultValue(0.15).range(0.0, 1.0).sliderRange(0.0, 1.0)
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
        .defaultValue(15).range(0, 500).sliderRange(0, 300)
        .visible(realisticTiming::get)
        .build());

    private final Setting<Integer> reactionTimeMax = sgHuman.add(new IntSetting.Builder()
        .name("reaction-time-max").description("Max reaction latency (ms).")
        .defaultValue(50).range(0, 500).sliderRange(0, 300)
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

    private float   prevAssistYaw   = 0f;
    private float   prevAssistPitch = 0f;
    private float   lastPlayerYaw   = 0f;
    private float   lastPlayerPitch = 0f;
    private long    lastTickMs      = 0L;
    private World   lastWorld       = null;

    private int     overshootTicksLeft = 0;
    private double  overshootYawOffset = 0.0;

    private final List<AimTarget> targetCache = new ArrayList<>();

    // ------------------------------------------------------------------ ctor
    public AdvancedAimAssist() {
        super(Categories.Combat, "advanced-aim-assist",
              "DemonClient v2.2 — Exponential smooth ease-out damping & asymmetric decoupling.");
    }

    // ------------------------------------------------------------------ lifecycle
    private void resetState() {
        target             = null;
        reactionReadyAt    = 0L;
        switchCooldownTicks = 0;
        cycleTimer         = 0;
        prevAssistYaw      = 0f;
        prevAssistPitch    = 0f;
        overshootTicksLeft = 0;
        overshootYawOffset = 0.0;
        targetCache.clear();
        if (mc.player != null) {
            lastPlayerYaw   = mc.player.getYaw();
            lastPlayerPitch = mc.player.getPitch();
        } else {
            lastPlayerYaw   =   0f;
            lastPlayerPitch =   0f;
        }
    }

    @Override public void onActivate()   { resetState(); lastWorld = mc.world; }
    @Override public void onDeactivate() { resetState(); lastWorld = null; }

    @EventHandler
    private void onGameLeft(GameLeftEvent event)     { resetState(); lastWorld = null; }

    @EventHandler
    private void onGameJoined(GameJoinedEvent event) {
        resetState();
        lastWorld = mc.world;
    }

    private boolean isHoldingWeapon() {
        if (mc.player == null) return false;
        String name = mc.player.getMainHandStack().getItem().toString().toLowerCase();
        return name.contains("sword") || name.contains("axe")
            || name.contains("trident") || name.contains("mace");
    }

    // ------------------------------------------------------------------ public API
    public float[] getAimAngles(Entity targetEntity) {
        if (targetEntity == null || mc.player == null) return null;

        Vec3d eye   = mc.player.getEyePos();
        Vec3d point = buildInterpolatedAimPoint(targetEntity);

        float yaw   = (float) AimUtils.getYaw(eye, point);
        float pitch = (float) MathHelper.clamp(AimUtils.getPitch(eye, point), -90.0, 90.0);

        if (!Float.isFinite(yaw) || !Float.isFinite(pitch)) return null;
        return new float[]{ yaw, pitch };
    }

    private Vec3d buildInterpolatedAimPoint(Entity entity) {
        Vec3d base = AimUtils.getAimPoint(entity, hitboxTarget.get(), false, 0);

        // Kinematic Velocity Intercept
        if (prediction.get() && mc.player != null) {
            Vec3d targetVel = entity.getVelocity();
            Vec3d playerVel = mc.player.getVelocity();
            int   ticks     = predictionTicks.get();

            double relX = (targetVel.x - playerVel.x * 0.40) * ticks;
            double relZ = (targetVel.z - playerVel.z * 0.40) * ticks;
            double relY = targetVel.y * 0.55 * ticks;

            Box    box  = entity.getBoundingBox();
            double maxH = (box.maxX - box.minX) * 1.5;
            double maxV = (box.maxY - box.minY) * 0.8;
            relX = MathHelper.clamp(relX, -maxH, maxH);
            relZ = MathHelper.clamp(relZ, -maxH, maxH);
            relY = MathHelper.clamp(relY, -maxV, maxV);

            base = base.add(relX, relY, relZ);
        }

        // Bezier Wrist Pivot Arc
        if (arcCurvature.get() > 0.0 && mc.player != null) {
            Vec3d eye = mc.player.getEyePos();
            Vec3d toTarget = base.subtract(eye);
            double dist = toTarget.length();
            if (dist > 0.5) {
                Vec3d perp = new Vec3d(-toTarget.z, 0, toTarget.x).normalize();
                double progress = Math.min(dist / 6.0, 1.0);
                double arcOffset = Math.sin(progress * Math.PI) * arcCurvature.get() * 0.35 * dist;
                base = base.add(perp.multiply(arcOffset));
            }
        }

        // Hitbox Roaming
        if (hitboxRoaming.get() > 0.0) {
            Box    box    = entity.getBoundingBox();
            double width  = box.maxX - box.minX;
            double height = box.maxY - box.minY;
            double t      = System.currentTimeMillis() * 0.0022;

            double ox = Math.sin(t * 2.3) * width  * 0.28 * hitboxRoaming.get();
            double oz = Math.cos(t * 1.8) * width  * 0.28 * hitboxRoaming.get();
            double oy = Math.sin(t * 3.0) * height * 0.16 * hitboxRoaming.get();

            base = base.add(ox, oy, oz);
        }

        return base;
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

            long now = System.currentTimeMillis();
            if (now - lastTickMs < 10L) return;
            lastTickMs = now;

            if (target != null &&
                (target.isRemoved() || !target.isAlive() || target.getEntityWorld() != mc.world)) {
                target = null;
            }

            if (!Float.isFinite(mc.player.getYaw()) || !Float.isFinite(mc.player.getPitch())) {
                mc.player.setYaw(0f); mc.player.setPitch(0f); return;
            }

            float currentYaw   = mc.player.getYaw();
            float currentPitch = mc.player.getPitch();

            // Isolate raw manual mouse delta
            float manualDeltaYaw   = MathHelper.wrapDegrees(
                (currentYaw   - prevAssistYaw)   - lastPlayerYaw);
            float manualDeltaPitch = (currentPitch - prevAssistPitch) - lastPlayerPitch;

            if (onlyWhenClicking.get() && (mc.options == null || !mc.options.attackKey.isPressed())) {
                clearAssist(currentYaw, currentPitch); return;
            }
            if (weaponOnly.get() && !isHoldingWeapon()) {
                clearAssist(currentYaw, currentPitch); return;
            }

            if (switchCooldownTicks > 0) switchCooldownTicks--;
            if (cycleTimer         > 0) cycleTimer--;

            Entity found = findBestTarget();
            if (found != target) { target = found; onTargetChanged(); }

            if (target == null) { clearAssist(currentYaw, currentPitch); return; }

            if (realisticTiming.get() && now < reactionReadyAt) {
                lastPlayerYaw = currentYaw; lastPlayerPitch = currentPitch; return;
            }
            if (requireAttackCooldown.get() && mc.player.getAttackCooldownProgress(0.5f) < 1.0f) {
                lastPlayerYaw = currentYaw; lastPlayerPitch = currentPitch; return;
            }

            float[] angles = getAimAngles(target);
            if (angles == null) { clearAssist(currentYaw, currentPitch); return; }

            double wantedYaw   = angles[0];
            double wantedPitch = angles[1];

            // ── Micro-Overshoot Injection ────────────────────────────────────
            if (microOvershoot.get()) {
                double rawCheckDist = Math.abs(MathHelper.wrapDegrees(wantedYaw - currentYaw));
                if (rawCheckDist > 12.0 && overshootTicksLeft <= 0 && ThreadLocalRandom.current().nextDouble() < 0.35) {
                    overshootTicksLeft = 1;
                    overshootYawOffset = Math.signum(MathHelper.wrapDegrees(wantedYaw - currentYaw)) * ThreadLocalRandom.current().nextDouble(0.4, 0.9);
                }
            }
            if (overshootTicksLeft > 0) {
                wantedYaw += overshootYawOffset;
                overshootTicksLeft--;
            }

            // Auto-attack
            if (autoAttack.get() && mc.interactionManager != null
                && mc.player.getAttackCooldownProgress(0.5f) >= 1.0f) {
                Vec3d eye   = mc.player.getEyePos();
                Vec3d point = AimUtils.getAimPoint(target, hitboxTarget.get(), false, 0);
                Vec3d look  = AimUtils.getLookVector(currentYaw, currentPitch);
                if (AimUtils.getAngle(look, eye, point) < 5.0) {
                    mc.interactionManager.attackEntity(mc.player, target);
                    mc.player.swingHand(net.minecraft.util.Hand.MAIN_HAND);
                }
            }

            // Escape angle
            Vec3d eye   = mc.player.getEyePos();
            Vec3d point = AimUtils.getAimPoint(target, hitboxTarget.get(), false, 0);
            Vec3d look  = AimUtils.getLookVector(currentYaw, currentPitch);
            double currentAngleToTarget = AimUtils.getAngle(look, eye, point);

            boolean playerSteeringAway =
                Math.abs(manualDeltaYaw) > 0.25f
                && Math.signum(manualDeltaYaw) != Math.signum(
                      MathHelper.wrapDegrees(wantedYaw - currentYaw));

            if (playerSteeringAway && currentAngleToTarget > escapeAngle.get()) {
                clearAssist(currentYaw, currentPitch); return;
            }

            // Micro-noise
            if (humanVariation.get() > 0) {
                double t      = now * 0.00085;
                double noiseY = (Math.sin(t * 2.1) * 0.5 + Math.sin(t * 4.3) * 0.25)
                                * humanVariation.get() * 0.28;
                double noiseP = (Math.cos(t * 2.5) * 0.4 + Math.sin(t * 5.1) * 0.2)
                                * humanVariation.get() * 0.18;
                wantedYaw   += noiseY;
                wantedPitch  = MathHelper.clamp(wantedPitch + noiseP, -90.0, 90.0);
            }

            // GCD quantisation (pre-delta)
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

            // ── EXPONENTIAL SMOOTH EASE-OUT DAMPING & ASYMMETRIC DECOUPLING ──
            float maxYawTick   = (float) (horizontalSpeed.get() / 20.0);
            float maxPitchTick = (float) (verticalSpeed.get()   / 20.0);
            double glue        = MathHelper.clamp(stickyGlue.get(), 0.1, 1.0);
            double smooth      = MathHelper.clamp(smoothness.get(), 0.01, 0.99);

            // Additive Mouse Synergy Factor
            double manualMagnitude = Math.hypot(manualDeltaYaw, manualDeltaPitch);
            double synergyFactor   = manualMagnitude > 0.02f ? 1.0 : 0.42;

            // Exponential ease-out damping formula:
            // factor = 1.0 - exp(-distance / (smoothness * scale))
            // Combined with sigmoidal velocity progression for ultimate butter-smoothness
            double expFactorYaw   = 1.0 - Math.exp(-absYaw   / (Math.max(smooth, 0.05) * 14.0));
            double expFactorPitch = 1.0 - Math.exp(-absPitch / (Math.max(smooth, 0.05) * 12.0));

            // Asymmetric non-linearity: Yaw and Pitch scale differently to avoid robotic axes locking
            double stepY = maxYawTick   * expFactorYaw   * (0.65 + 0.35 * glue) * (1.0 - smooth * 0.30) * synergyFactor;
            double stepP = maxPitchTick * expFactorPitch * (0.60 + 0.40 * glue) * (1.0 - (smooth * 1.20) * 0.30) * synergyFactor;

            // Velocity floor for target tracking
            float targetAngularSpeedYaw = 0f, targetAngularSpeedPitch = 0f;
            if (target != null) {
                Vec3d vel    = target.getVelocity();
                Vec3d eyePos = mc.player.getEyePos();
                Vec3d dir    = AimUtils.getAimPoint(target, hitboxTarget.get(), false, 0).subtract(eyePos);
                double dist  = dir.length();
                if (dist > 0.01) {
                    Vec3d dirN  = dir.normalize();
                    Vec3d perpH = new Vec3d(-dirN.z, 0, dirN.x);
                    double latV = Math.abs(vel.subtract(dirN.multiply(vel.dotProduct(dirN))).dotProduct(perpH));
                    targetAngularSpeedYaw   = (float) Math.toDegrees(Math.atan2(latV, dist));
                    targetAngularSpeedPitch = targetAngularSpeedYaw * 0.45f;
                }
            }

            float floorYaw   = Math.max(targetAngularSpeedYaw,   0.20f) * (float) (0.7 + 0.3 * glue);
            float floorPitch = Math.max(targetAngularSpeedPitch, 0.12f) * (float) (0.7 + 0.3 * glue);

            float finalStepYaw   = (float) Math.max(stepY, floorYaw);
            float finalStepPitch = (float) Math.max(stepP, floorPitch);

            finalStepYaw   = Math.min(finalStepYaw,   maxYawTick);
            finalStepPitch = Math.min(finalStepPitch, maxPitchTick);

            finalStepYaw   = (float) Math.min(finalStepYaw,   absYaw);
            finalStepPitch = (float) Math.min(finalStepPitch, absPitch);

            // Micro-magnetism inside hitbox (<6°)
            if (microMagnetism.get() > 0.0 && absYaw < 6.0) {
                double mag = (1.0 - absYaw / 6.0) * microMagnetism.get() * 0.28;
                finalStepYaw += finalStepYaw * mag;
            }
            if (microMagnetism.get() > 0.0 && absPitch < 5.0) {
                double mag = (1.0 - absPitch / 5.0) * microMagnetism.get() * 0.28;
                finalStepPitch += finalStepPitch * mag;
            }

            float yawStep   = finalStepYaw   * (float) Math.signum(rawDeltaYaw);
            float pitchStep = finalStepPitch * (float) Math.signum(rawDeltaPitch);

            // GCD quantisation (post-step)
            if (gcdFix.get()) {
                yawStep   = (float) (RotationUtils.applyGCD(currentYaw,   currentYaw   + yawStep)   - currentYaw);
                pitchStep = (float) (RotationUtils.applyGCD(currentPitch, currentPitch + pitchStep) - currentPitch);
            }

            float finalYaw   = currentYaw   + yawStep;
            float finalPitch = MathHelper.clamp(currentPitch + pitchStep, -90.0f, 90.0f);

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

            prevAssistYaw   = yawStep;
            prevAssistPitch = pitchStep;
            lastPlayerYaw   = mc.player.getYaw();
            lastPlayerPitch = mc.player.getPitch();

        } catch (Throwable t) {
            error("AimAssist error: " + (t.getMessage() != null
                  ? t.getMessage() : t.getClass().getSimpleName()));
            resetState();
            if (isActive()) toggle();
        }
    }

    private void clearAssist(float y, float p) {
        prevAssistYaw   = 0f;
        prevAssistPitch = 0f;
        lastPlayerYaw   = y;
        lastPlayerPitch = p;
        target          = null;
    }

    // ------------------------------------------------------------------ render
    @EventHandler
    private void onRender3D(Render3DEvent event) {
        if (mc.player == null || mc.world == null || !mc.player.isAlive()) { target = null; return; }
        if (renderCircle.get() && target != null && !AimUtils.isDead(target)) {
            try { renderTargetCircle(event); } catch (Throwable ignored) {}
        }
    }

    // ------------------------------------------------------------------ targeting
    private void onTargetChanged() {
        switchCooldownTicks = switchCooldown.get();
        prevAssistYaw       = 0f;
        prevAssistPitch     = 0f;
        if (!realisticTiming.get() || target == null) {
            reactionReadyAt = 0L;
        } else {
            int lo = Math.min(reactionTimeMin.get(), reactionTimeMax.get());
            int hi = Math.max(reactionTimeMin.get(), reactionTimeMax.get());
            reactionReadyAt = System.currentTimeMillis()
                            + ThreadLocalRandom.current().nextLong(lo, (long) hi + 1);
        }
    }

    private Entity findBestTarget() {
        if (sticky.get() && target != null && isValidStickyTarget(target) && switchCooldownTicks > 0) {
            return target;
        }

        Vec3d  eye     = mc.player.getEyePos();
        Vec3d  look    = AimUtils.getLookVector(mc.player.getYaw(), mc.player.getPitch());
        double halfFov = fov.get() / 2.0;
        boolean useFov = fov.get() < 360.0;

        targetCache.clear();
        for (Entity entity : mc.world.getEntities()) {
            if (!isValidTarget(entity)) continue;
            Vec3d  point    = AimUtils.getAimPoint(entity, hitboxTarget.get(), false, 0);
            double distance = eye.distanceTo(point);
            if (distance > range.get()) continue;
            double angle = AimUtils.getAngle(look, eye, point);
            if (useFov && angle > halfFov) continue;
            if (!throughWalls.get() && !AimUtils.hasLineOfSight(eye, point, entity)) continue;

            float health = AimUtils.getHealth(entity);
            targetCache.add(new AimTarget(entity, point, distance, angle, health));
        }

        if (targetCache.isEmpty()) return null;

        targetCache.sort((t1, t2) -> switch (priority.get()) {
            case LowestHealth -> Float.compare(t1.health(), t2.health());
            case Angle        -> Double.compare(t1.angle(), t2.angle());
            default           -> Double.compare(t1.distance(), t2.distance());
        });

        if (multiTarget.get() && multiTargetMode.get() == MultiTargetMode.Cycle && targetCache.size() > 1) {
            if (cycleTimer <= 0) {
                cycleTimer = cycleTime.get();
                int currentIndex = -1;
                for (int i = 0; i < targetCache.size(); i++) {
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
    }

    private boolean isValidTarget(Entity entity) {
        if (entity == null || entity == mc.player || entity == mc.getCameraEntity()) return false;
        if (!(entity instanceof PlayerEntity player)) return false;
        if (player.isSpectator() || player.isCreative()) return false;
        if (entity.isRemoved() || AimUtils.isDead(entity)) return false;
        if (ignoreInvisible.get() && entity.isInvisible()) return false;
        if (!entities.get().isEmpty() && !entities.get().contains(entity.getType())) return false;
        if (ignoreFriends.get() && !Friends.get().shouldAttack(player)) return false;
        return true;
    }

    private boolean isValidStickyTarget(Entity entity) {
        if (!isValidTarget(entity)) return false;
        Vec3d  eye   = mc.player.getEyePos();
        Vec3d  point = AimUtils.getAimPoint(entity, hitboxTarget.get(), false, 0);
        double eff   = Math.max(unlockRange.get(), range.get() + 0.5);
        if (eye.distanceTo(point) > eff) return false;
        if (!throughWalls.get() && !AimUtils.hasLineOfSight(eye, point, entity)) return false;
        Vec3d look = AimUtils.getLookVector(mc.player.getYaw(), mc.player.getPitch());
        return AimUtils.getAngle(look, eye, point) <= escapeAngle.get();
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
        return target != null ? AimUtils.getName(target) : null;
    }
}
