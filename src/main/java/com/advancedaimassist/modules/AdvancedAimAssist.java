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
import net.minecraft.item.Item;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

import static meteordevelopment.meteorclient.MeteorClient.mc;

/**
 * AdvancedAimAssist (AimBot) - DemonClient v2.2 Crash-Safe & Memory-Hardened Edition
 * Hardened Features:
 * 1. Zero Native Crash 0xc0000409 (GL vertex capacity buffer overrun prevention in renderTargetCircle)
 * 2. Rate-limited clean rotation updates (avoids saturating the Render Thread and matrix buffers)
 * 3. Robust Try-Catch exception isolation in onTick with auto-toggle failsafe
 * 4. Pointer & state clearance on world/server change via GameLeftEvent, GameJoinedEvent, and world validation
 * 5. Strict Additive Pull Assist, Freedom Hitbox Roaming, and Strict Player-Only filtering
 * 6. GCD Sensitivity Grid alignment and NaN/Infinity protocol protection
 */
public class AdvancedAimAssist extends Module {
    private final SettingGroup sgGeneral = settings.createGroup("General");
    private final SettingGroup sgPro = settings.createGroup("Pro Settings");
    private final SettingGroup sgHuman = settings.createGroup("Humanization");
    private final SettingGroup sgTargeting = settings.createGroup("Targeting");
    private final SettingGroup sgMulti = settings.createGroup("Multi-Target");
    private final SettingGroup sgChecks = settings.createGroup("Checks");
    private final SettingGroup sgRender = settings.createGroup("Render");

    public enum AimMode {
        Client,  // Additive pull assist (collaborates with user mouse input)
        Assist,  // Smooth humanized assist
        Silent   // Server packets only, free camera
    }

    public enum MultiTargetMode {
        Closest,
        LowestHealth,
        Crosshair,
        Cycle
    }

    // General Settings (Protocol-Safe AntiCheat-Safe Defaults)
    private final Setting<AimMode> aimMode = sgGeneral.add(new EnumSetting.Builder<AimMode>()
        .name("aim-mode")
        .description("Client: additive pull assist. Assist: smooth humanized. Silent: server rotations only.")
        .defaultValue(AimMode.Client)
        .build()
    );

    private final Setting<Boolean> onlyWhenClicking = sgGeneral.add(new BoolSetting.Builder()
        .name("only-when-clicking")
        .description("Aim assist only activates when holding left-click (Legit Ghost style).")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> weaponOnly = sgGeneral.add(new BoolSetting.Builder()
        .name("weapon-only")
        .description("Only activates when holding a weapon (sword, axe, mace, trident).")
        .defaultValue(true)
        .build()
    );

    private final Setting<Double> range = sgGeneral.add(new DoubleSetting.Builder()
        .name("range")
        .description("Maximum distance in blocks.")
        .defaultValue(4.5)
        .range(2.0, 6.0)
        .sliderRange(2.0, 6.0)
        .build()
    );

    private final Setting<Double> horizontalSpeed = sgGeneral.add(new DoubleSetting.Builder()
        .name("horizontal-speed")
        .description("Horizontal rotation speed (deg/sec).")
        .defaultValue(150.0)
        .range(10.0, 360.0)
        .sliderRange(10.0, 360.0)
        .build()
    );

    private final Setting<Double> verticalSpeed = sgGeneral.add(new DoubleSetting.Builder()
        .name("vertical-speed")
        .description("Vertical rotation speed (deg/sec).")
        .defaultValue(95.0)
        .range(10.0, 360.0)
        .sliderRange(10.0, 360.0)
        .build()
    );

    private final Setting<Double> maxAbsoluteSpeed = sgGeneral.add(new DoubleSetting.Builder()
        .name("max-absolute-speed")
        .description("Hard ceiling for angular speed per tick.")
        .defaultValue(18.0)
        .range(5.0, 40.0)
        .sliderRange(5.0, 35.0)
        .build()
    );

    private final Setting<Double> fov = sgGeneral.add(new DoubleSetting.Builder()
        .name("fov")
        .description("Field of view restriction.")
        .defaultValue(180.0)
        .range(10.0, 360.0)
        .sliderRange(10.0, 360.0)
        .build()
    );

    private final Setting<Boolean> keepRotation = sgGeneral.add(new BoolSetting.Builder()
        .name("keep-rotation")
        .description("Eliminates visual camera micro-jitter.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> gcdFix = sgGeneral.add(new BoolSetting.Builder()
        .name("gcd-fix")
        .description("Applies mouse sensitivity GCD rounding to bypass Grim and Vulcan.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> prediction = sgGeneral.add(new BoolSetting.Builder()
        .name("prediction")
        .description("Kinematic target position estimation.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> predictionTicks = sgGeneral.add(new IntSetting.Builder()
        .name("prediction-ticks")
        .description("Ticks ahead to predict.")
        .defaultValue(2)
        .range(1, 10)
        .sliderRange(1, 10)
        .visible(prediction::get)
        .build()
    );

    private final Setting<Boolean> autoAttack = sgGeneral.add(new BoolSetting.Builder()
        .name("auto-attack")
        .description("Automatically attacks when locked on target.")
        .defaultValue(false)
        .build()
    );

    // Pro Settings (Sticky-Glue & Adherence)
    private final Setting<Double> stickyGlue = sgPro.add(new DoubleSetting.Builder()
        .name("sticky-glue-strength")
        .description("DemonClient sticky adherence factor.")
        .defaultValue(0.60)
        .range(0.1, 1.0)
        .sliderRange(0.1, 1.0)
        .build()
    );

    private final Setting<Boolean> strafeCompensation = sgPro.add(new BoolSetting.Builder()
        .name("strafe-compensation")
        .description("Instantly compensates for enemy strafing direction changes.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Double> microMagnetism = sgPro.add(new DoubleSetting.Builder()
        .name("micro-magnetism")
        .description("Subtle magnetic pull toward hitbox center.")
        .defaultValue(0.3)
        .range(0.0, 3.0)
        .sliderRange(0.0, 3.0)
        .build()
    );

    // Humanization Settings (Deep Damping, Ultra-Subtle Noise & Manual Override)
    private final Setting<Double> smoothness = sgHuman.add(new DoubleSetting.Builder()
        .name("smoothness")
        .description("Deep smoothing and gradual ease-out factor.")
        .defaultValue(0.72)
        .range(0.01, 0.99)
        .sliderRange(0.01, 0.99)
        .build()
    );

    private final Setting<Double> humanVariation = sgHuman.add(new DoubleSetting.Builder()
        .name("human-variation")
        .description("Ultra-subtle microscopic noise intensity (zero artificial jitter).")
        .defaultValue(0.02)
        .range(0.0, 1.0)
        .sliderRange(0.0, 1.0)
        .build()
    );

    private final Setting<Double> hitboxRoaming = sgHuman.add(new DoubleSetting.Builder()
        .name("hitbox-roaming-intensity")
        .description("Freedom Hitbox wandering intensity (allows free cursor movement inside enemy bounding box).")
        .defaultValue(0.15)
        .range(0.0, 1.0)
        .sliderRange(0.0, 1.0)
        .build()
    );

    private final Setting<Double> mouseThreshold = sgHuman.add(new DoubleSetting.Builder()
        .name("mouse-threshold")
        .description("Manual mouse override threshold.")
        .defaultValue(1.2)
        .range(0.0, 5.0)
        .sliderRange(0.0, 5.0)
        .build()
    );

    private final Setting<Boolean> realisticTiming = sgHuman.add(new BoolSetting.Builder()
        .name("realistic-timing")
        .description("Human reaction time latency.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> reactionTimeMin = sgHuman.add(new IntSetting.Builder()
        .name("reaction-time-min")
        .description("Min reaction ms.")
        .defaultValue(30)
        .range(0, 500)
        .sliderRange(0, 300)
        .visible(realisticTiming::get)
        .build()
    );

    private final Setting<Integer> reactionTimeMax = sgHuman.add(new IntSetting.Builder()
        .name("reaction-time-max")
        .description("Max reaction ms.")
        .defaultValue(90)
        .range(0, 500)
        .sliderRange(0, 300)
        .visible(realisticTiming::get)
        .build()
    );

    // Targeting Settings
    private final Setting<AimPart> hitboxTarget = sgTargeting.add(new EnumSetting.Builder<AimPart>()
        .name("hitbox-target")
        .description("Hitbox target zone.")
        .defaultValue(AimPart.Head)
        .build()
    );

    private final Setting<Set<EntityType<?>>> entities = sgTargeting.add(new EntityTypeListSetting.Builder()
        .name("entities")
        .description("Allowed entities.")
        .onlyAttackable()
        .build()
    );

    private final Setting<Boolean> targetNpcs = sgTargeting.add(new BoolSetting.Builder()
        .name("target-npcs")
        .description("Target villagers/traders.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> ignoreFriends = sgTargeting.add(new BoolSetting.Builder()
        .name("ignore-friends")
        .description("Ignore friends.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> ignoreInvisible = sgTargeting.add(new BoolSetting.Builder()
        .name("ignore-invisible")
        .description("Ignore invisible.")
        .defaultValue(true)
        .build()
    );

    private final Setting<TargetPriority> priority = sgTargeting.add(new EnumSetting.Builder<TargetPriority>()
        .name("priority")
        .description("Target sorting priority.")
        .defaultValue(TargetPriority.Closest)
        .build()
    );

    private final Setting<Boolean> sticky = sgTargeting.add(new BoolSetting.Builder()
        .name("sticky-target")
        .description("Sticky lock hysteresis.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Double> unlockRange = sgTargeting.add(new DoubleSetting.Builder()
        .name("unlock-range")
        .description("Unlock distance.")
        .defaultValue(5.5)
        .range(2.0, 12.0)
        .sliderRange(2.0, 12.0)
        .visible(sticky::get)
        .build()
    );

    private final Setting<Double> releaseAngle = sgTargeting.add(new DoubleSetting.Builder()
        .name("release-angle")
        .description("Release angle when looking away.")
        .defaultValue(85.0)
        .range(10.0, 180.0)
        .sliderRange(10.0, 180.0)
        .build()
    );

    // Multi-Target Advanced Settings
    private final Setting<Boolean> multiTarget = sgMulti.add(new BoolSetting.Builder()
        .name("multi-target")
        .description("Enables advanced handling when multiple opponents are in range.")
        .defaultValue(true)
        .build()
    );

    private final Setting<MultiTargetMode> multiTargetMode = sgMulti.add(new EnumSetting.Builder<MultiTargetMode>()
        .name("multi-target-mode")
        .description("Strategy for switching between multiple opponents.")
        .defaultValue(MultiTargetMode.Closest)
        .visible(multiTarget::get)
        .build()
    );

    private final Setting<Integer> switchCooldown = sgMulti.add(new IntSetting.Builder()
        .name("switch-cooldown")
        .description("Ticks to wait before switching target.")
        .defaultValue(8)
        .range(0, 40)
        .sliderRange(0, 30)
        .visible(multiTarget::get)
        .build()
    );

    private final Setting<Integer> cycleTime = sgMulti.add(new IntSetting.Builder()
        .name("cycle-time")
        .description("Ticks between automatic target cycling.")
        .defaultValue(40)
        .range(10, 200)
        .sliderRange(10, 100)
        .visible(() -> multiTarget.get() && multiTargetMode.get() == MultiTargetMode.Cycle)
        .build()
    );

    // Checks Settings
    private final Setting<Boolean> throughWalls = sgChecks.add(new BoolSetting.Builder()
        .name("through-walls")
        .description("Allows aiming through walls.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> requireAttackCooldown = sgChecks.add(new BoolSetting.Builder()
        .name("require-attack-cooldown")
        .description("Attack cooldown check.")
        .defaultValue(false)
        .build()
    );

    // Render Settings
    private final Setting<Boolean> renderCircle = sgRender.add(new BoolSetting.Builder()
        .name("target-circle")
        .description("Target feet ring.")
        .defaultValue(true)
        .build()
    );

    private final Setting<ShapeMode> circleShapeMode = sgRender.add(new EnumSetting.Builder<ShapeMode>()
        .name("circle-shape-mode")
        .description("Ring shape mode.")
        .defaultValue(ShapeMode.Both)
        .visible(renderCircle::get)
        .build()
    );

    private final Setting<SettingColor> circleFillColor = sgRender.add(new ColorSetting.Builder()
        .name("circle-fill-color")
        .description("Fill color.")
        .defaultValue(new SettingColor(255, 50, 50, 40))
        .visible(() -> renderCircle.get() && circleShapeMode.get().sides())
        .build()
    );

    private final Setting<SettingColor> circleOutlineColor = sgRender.add(new ColorSetting.Builder()
        .name("circle-outline-color")
        .description("Outline color.")
        .defaultValue(new SettingColor(255, 60, 60, 220))
        .visible(() -> renderCircle.get() && circleShapeMode.get().lines())
        .build()
    );

    private final Setting<Double> circleRadius = sgRender.add(new DoubleSetting.Builder()
        .name("circle-radius")
        .description("Radius.")
        .defaultValue(0.55)
        .range(0.2, 2.0)
        .sliderRange(0.2, 2.0)
        .visible(renderCircle::get)
        .build()
    );

    private final Setting<Integer> circleSegments = sgRender.add(new IntSetting.Builder()
        .name("circle-segments")
        .description("Segments.")
        .defaultValue(32)
        .range(8, 64)
        .sliderRange(8, 48)
        .visible(renderCircle::get)
        .build()
    );

    private final Setting<Double> circleHeight = sgRender.add(new DoubleSetting.Builder()
        .name("circle-height")
        .description("Height.")
        .defaultValue(0.02)
        .range(0.0, 0.5)
        .sliderRange(0.0, 0.2)
        .visible(renderCircle::get)
        .build()
    );

    // State Variables & Caches
    private Entity target;
    private long reactionReadyAt;
    private int switchCooldownTicks;
    private int cycleTimer;
    private float previousStepYaw = 0f;
    private float previousStepPitch = 0f;
    private int targetSwitchProgressTicks = 0;
    private float lastPlayerYaw = 0f;
    private float lastPlayerPitch = 0f;
    private long lastTickTime = 0L;
    private World lastWorld = null;
    private final List<Entity> candidateCache = new ArrayList<>();

    public AdvancedAimAssist() {
        super(Categories.Combat, "advanced-aim-assist", "DemonClient v2.2 Crash-Safe & Memory-Hardened Edition.");
    }

    /**
     * Resets all internal targeting and momentum state cleanly.
     */
    private void resetState() {
        target = null;
        reactionReadyAt = 0;
        switchCooldownTicks = 0;
        cycleTimer = 0;
        targetSwitchProgressTicks = 15;
        previousStepYaw = 0f;
        previousStepPitch = 0f;
        candidateCache.clear();
        if (mc.player != null) {
            lastPlayerYaw = mc.player.getYaw();
            lastPlayerPitch = mc.player.getPitch();
        } else {
            lastPlayerYaw = 0f;
            lastPlayerPitch = 0f;
        }
    }

    @Override
    public void onActivate() {
        resetState();
        if (mc.world != null) {
            lastWorld = mc.world;
        }
    }

    @Override
    public void onDeactivate() {
        resetState();
        lastWorld = null;
    }

    @EventHandler
    private void onGameLeft(GameLeftEvent event) {
        resetState();
        lastWorld = null;
    }

    @EventHandler
    private void onGameJoined(GameJoinedEvent event) {
        resetState();
        if (mc.world != null) {
            lastWorld = mc.world;
        }
    }

    private boolean isHoldingWeapon() {
        if (mc.player == null) return false;
        String name = mc.player.getMainHandStack().getItem().toString().toLowerCase();
        return name.contains("sword") ||
               name.contains("axe") ||
               name.contains("trident") ||
               name.contains("mace");
    }

    /**
     * Public API method for KillAura and external combat modules with Freedom Hitbox Roaming.
     */
    public float[] getAimAngles(Entity targetEntity) {
        if (targetEntity == null || mc.player == null) return null;
        Vec3d eye = mc.player.getEyePos();
        Vec3d point = AimUtils.getAimPoint(targetEntity, hitboxTarget.get(), prediction.get(), predictionTicks.get());

        // Freedom Hitbox Roaming: Allows organic cursor navigation and wandering inside the enemy BoundingBox
        if (hitboxRoaming.get() > 0.0) {
            Box box = targetEntity.getBoundingBox();
            if (box != null) {
                double width = box.maxX - box.minX;
                double height = box.maxY - box.minY;
                double time = System.currentTimeMillis() * 0.002;
                double offsetX = Math.sin(time * 2.1) * (width * 0.35) * hitboxRoaming.get();
                double offsetZ = Math.cos(time * 1.7) * (width * 0.35) * hitboxRoaming.get();
                double offsetY = Math.sin(time * 2.8) * (height * 0.2) * hitboxRoaming.get();
                point = point.add(offsetX, offsetY, offsetZ);
            }
        }

        if (strafeCompensation.get() && targetEntity instanceof LivingEntity living) {
            point = point.add(living.getVelocity().multiply(0.4));
        }
        float yaw = (float) AimUtils.getYaw(eye, point);
        float pitch = (float) MathHelper.clamp(AimUtils.getPitch(eye, point), -90.0, 90.0);

        // Protocol Safety Check: Prevent NaN or Infinite angles from generating invalid packets
        if (Float.isNaN(yaw) || Float.isInfinite(yaw) || Float.isNaN(pitch) || Float.isInfinite(pitch)) {
            return null;
        }

        return new float[]{yaw, pitch};
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        try {
            if (mc.player == null || mc.world == null || !mc.player.isAlive()) {
                resetState();
                return;
            }

            // World change / dimension transfer detection
            if (mc.world != lastWorld) {
                lastWorld = mc.world;
                resetState();
                return;
            }

            // Rate limiter: prevent redundant high-frequency rotation computations that saturate render matrix buffers
            long now = System.currentTimeMillis();
            if (now - lastTickTime < 10L) {
                return;
            }
            lastTickTime = now;

            // Target validity check (entity dead, despawned, removed or world changed)
            if (target != null && (target.isRemoved() || !target.isAlive() || target.getEntityWorld() != mc.world)) {
                target = null;
            }

            // Protocol Safety Check: Validate current player view angles
            if (Float.isNaN(mc.player.getYaw()) || Float.isInfinite(mc.player.getYaw()) ||
                Float.isNaN(mc.player.getPitch()) || Float.isInfinite(mc.player.getPitch())) {
                mc.player.setYaw(0f);
                mc.player.setPitch(0f);
                return;
            }

            // Manual Mouse Override Check using mouseThreshold
            float currentYaw = mc.player.getYaw();
            float currentPitch = mc.player.getPitch();
            if (mouseThreshold.get() > 0.0) {
                float playerDeltaYaw = Math.abs(MathHelper.wrapDegrees(currentYaw - lastPlayerYaw));
                float playerDeltaPitch = Math.abs(currentPitch - lastPlayerPitch);
                if (playerDeltaYaw > mouseThreshold.get() + Math.abs(previousStepYaw) ||
                    playerDeltaPitch > mouseThreshold.get() + Math.abs(previousStepPitch)) {
                    lastPlayerYaw = currentYaw;
                    lastPlayerPitch = currentPitch;
                    return;
                }
            }

            if (onlyWhenClicking.get() && (mc.options == null || !mc.options.attackKey.isPressed())) {
                target = null;
                lastPlayerYaw = currentYaw;
                lastPlayerPitch = currentPitch;
                return;
            }

            if (weaponOnly.get() && !isHoldingWeapon()) {
                target = null;
                lastPlayerYaw = currentYaw;
                lastPlayerPitch = currentPitch;
                return;
            }

            if (switchCooldownTicks > 0) switchCooldownTicks--;
            if (cycleTimer > 0) cycleTimer--;
            if (targetSwitchProgressTicks < 15) targetSwitchProgressTicks++;

            Entity found = findBestTarget();
            if (found != target) {
                target = found;
                onTargetChanged();
            }

            if (target == null) {
                lastPlayerYaw = currentYaw;
                lastPlayerPitch = currentPitch;
                return;
            }

            if (realisticTiming.get() && System.currentTimeMillis() < reactionReadyAt) {
                lastPlayerYaw = currentYaw;
                lastPlayerPitch = currentPitch;
                return;
            }

            if (requireAttackCooldown.get() && mc.player.getAttackCooldownProgress(0.5f) < 1.0f) {
                lastPlayerYaw = currentYaw;
                lastPlayerPitch = currentPitch;
                return;
            }

            float[] targetAngles = getAimAngles(target);
            if (targetAngles == null) {
                lastPlayerYaw = currentYaw;
                lastPlayerPitch = currentPitch;
                return;
            }
            double targetYaw = targetAngles[0];
            double targetPitch = targetAngles[1];

            Vec3d eye = mc.player.getEyePos();
            Vec3d point = AimUtils.getAimPoint(target, hitboxTarget.get(), prediction.get(), predictionTicks.get());

            if (autoAttack.get() && mc.interactionManager != null && mc.player.getAttackCooldownProgress(0.5f) >= 1.0f) {
                Vec3d look = AimUtils.getLookVector(mc.player.getYaw(), mc.player.getPitch());
                if (AimUtils.getAngle(look, eye, point) < 5.0) {
                    mc.interactionManager.attackEntity(mc.player, target);
                    mc.player.swingHand(net.minecraft.util.Hand.MAIN_HAND);
                }
            }

            if (releaseAngle.get() < 180.0) {
                Vec3d look = AimUtils.getLookVector(mc.player.getYaw(), mc.player.getPitch());
                if (AimUtils.getAngle(look, eye, point) > releaseAngle.get()) {
                    target = null;
                    lastPlayerYaw = currentYaw;
                    lastPlayerPitch = currentPitch;
                    return;
                }
            }

            // === Ultra-Subtle Micro-Noise (Heuristic Evasion) ===
            if (humanVariation.get() > 0) {
                double time = System.currentTimeMillis() * 0.0008;
                double noiseYaw = (Math.sin(time * 2.1) * 0.5 + Math.sin(time * 4.3) * 0.25) * humanVariation.get() * 0.35;
                double noisePitch = (Math.cos(time * 2.5) * 0.4 + Math.sin(time * 5.1) * 0.2) * humanVariation.get() * 0.25;
                targetYaw += noiseYaw;
                targetPitch = MathHelper.clamp(targetPitch + noisePitch, -90.0, 90.0);
            }

            // === GCD Fix & Sub-Pixel Deadzone (Grim & Vulcan Sensitivity Grid Bypass) ===
            if (gcdFix.get()) {
                targetYaw = RotationUtils.applyGCD(mc.player.getYaw(), targetYaw);
                targetPitch = RotationUtils.applyGCD(mc.player.getPitch(), targetPitch);
            }

            double rawDeltaYaw = MathHelper.wrapDegrees(targetYaw - mc.player.getYaw());
            double rawDeltaPitch = targetPitch - mc.player.getPitch();

            // GCD Sub-Pixel Deadzone to eliminate flicker/oscillation
            if (gcdFix.get() && mc.options != null) {
                float sensitivity = (float) (mc.options.getMouseSensitivity().getValue() * 0.6 + 0.2);
                double gcd = sensitivity * sensitivity * sensitivity * 1.2;
                if (Math.abs(rawDeltaYaw) < gcd * 0.5) rawDeltaYaw = 0.0;
                if (Math.abs(rawDeltaPitch) < gcd * 0.5) rawDeltaPitch = 0.0;
            }

            double absYaw = Math.abs(rawDeltaYaw);
            double absPitch = Math.abs(rawDeltaPitch);

            if (absYaw == 0.0 && absPitch == 0.0) {
                previousStepYaw = 0f;
                previousStepPitch = 0f;
                lastPlayerYaw = mc.player.getYaw();
                lastPlayerPitch = mc.player.getPitch();
                return;
            }

            float baseStepH = (float) (horizontalSpeed.get() / 20.0);
            float baseStepV = (float) (verticalSpeed.get() / 20.0);

            // Deep Ease-Out Curve for Anti-Cheat Heuristic Evasion
            double adaptSmooth = smoothness.get();
            double easeYaw = Math.pow(MathHelper.clamp(absYaw / 45.0, 0.04, 1.0), 1.3 + adaptSmooth * 1.5);
            double easePitch = Math.pow(MathHelper.clamp(absPitch / 30.0, 0.04, 1.0), 1.3 + adaptSmooth * 1.5);

            float rawStepYaw = (float) (baseStepH * easeYaw * stickyGlue.get());
            float rawStepPitch = (float) (baseStepV * easePitch * stickyGlue.get());

            // Target Switch Progressive Sine Fade-In Ramp (Zero Hook Jerks & Micro-Jumps)
            float switchRamp = (float) Math.sin((Math.min(15.0f, targetSwitchProgressTicks) / 15.0f) * (Math.PI / 2.0));
            rawStepYaw *= switchRamp;
            rawStepPitch *= switchRamp;

            // Gentle Minimum Step (Zero Freezing, Ultra-Smooth)
            float minStepYaw = Math.max(0.4f, (float)(absYaw * 0.28));
            float minStepPitch = Math.max(0.4f, (float)(absPitch * 0.28));
            rawStepYaw = Math.max(minStepYaw, rawStepYaw);
            rawStepPitch = Math.max(minStepPitch, rawStepPitch);

            // Heavy Natural Momentum (28% Carry-Through Inertia for Soft, Organic Feel)
            float stepYaw = previousStepYaw * 0.28f + rawStepYaw * 0.72f;
            float stepPitch = previousStepPitch * 0.28f + rawStepPitch * 0.72f;
            previousStepYaw = stepYaw;
            previousStepPitch = stepPitch;

            // Strict Anti-Cheat Safe Max Absolute Speed Ceiling (18.0 per tick)
            float maxAbs = maxAbsoluteSpeed.get().floatValue();
            stepYaw = Math.min(maxAbs, Math.max(0.05f, stepYaw));
            stepPitch = Math.min(maxAbs, Math.max(0.05f, stepPitch));

            if (microMagnetism.get() > 0 && absYaw < 9.0) {
                stepYaw *= (float) (1.0 + microMagnetism.get() * 0.1);
            }

            // === PROTOCOL-SAFE ADDITIVE PULL ASSIST APPLICATION ===
            if (aimMode.get() == AimMode.Client || aimMode.get() == AimMode.Assist) {
                float yawStep = Math.min((float) absYaw, stepYaw) * (float) Math.signum(rawDeltaYaw);
                float pitchStep = Math.min((float) absPitch, stepPitch) * (float) Math.signum(rawDeltaPitch);

                if (gcdFix.get()) {
                    yawStep = (float) (RotationUtils.applyGCD(mc.player.getYaw(), mc.player.getYaw() + yawStep) - mc.player.getYaw());
                    pitchStep = (float) (RotationUtils.applyGCD(mc.player.getPitch(), mc.player.getPitch() + pitchStep) - mc.player.getPitch());
                }

                float finalYaw = mc.player.getYaw() + yawStep;
                float finalPitch = MathHelper.clamp(mc.player.getPitch() + pitchStep, -90.0f, 90.0f);

                // Strict Protocol Validation & Threshold Guard
                if (!Float.isNaN(finalYaw) && !Float.isInfinite(finalYaw) &&
                    !Float.isNaN(finalPitch) && !Float.isInfinite(finalPitch)) {
                    if (Math.abs(yawStep) > 0.0001f || Math.abs(pitchStep) > 0.0001f) {
                        mc.player.setYaw(finalYaw);
                        mc.player.setPitch(finalPitch);
                    }
                }
            } else {
                float yawStep = Math.min((float) absYaw, stepYaw) * (float) Math.signum(rawDeltaYaw);
                float pitchStep = Math.min((float) absPitch, stepPitch) * (float) Math.signum(rawDeltaPitch);
                float finalYaw = (float) Rotations.serverYaw + yawStep;
                float finalPitch = MathHelper.clamp((float) Rotations.serverPitch + pitchStep, -90.0f, 90.0f);

                if (!Float.isNaN(finalYaw) && !Float.isInfinite(finalYaw) &&
                    !Float.isNaN(finalPitch) && !Float.isInfinite(finalPitch)) {
                    if (Math.abs(yawStep) > 0.0001f || Math.abs(pitchStep) > 0.0001f) {
                        Rotations.rotate(finalYaw, finalPitch);
                    }
                }
            }

            lastPlayerYaw = mc.player.getYaw();
            lastPlayerPitch = mc.player.getPitch();
        } catch (Throwable t) {
            // Failsafe exception isolation: disable module gracefully to prevent client crash
            error("Disabling AdvancedAimAssist due to runtime error: " + (t.getMessage() != null ? t.getMessage() : t.getClass().getSimpleName()));
            resetState();
            if (isActive()) {
                toggle();
            }
        }
    }

    @EventHandler
    private void onRender3D(Render3DEvent event) {
        if (mc.player == null || mc.world == null || !mc.player.isAlive()) {
            target = null;
            return;
        }

        if (renderCircle.get() && target != null && !AimUtils.isDead(target)) {
            try {
                renderTargetCircle(event);
            } catch (Throwable ignored) {
                // Buffer/matrix protection against native OpenGL crash
            }
        }
    }

    private void onTargetChanged() {
        switchCooldownTicks = switchCooldown.get();
        targetSwitchProgressTicks = 0;
        previousStepYaw = 0f;
        previousStepPitch = 0f;
        if (!realisticTiming.get() || target == null) {
            reactionReadyAt = 0;
        } else {
            int min = Math.min(reactionTimeMin.get(), reactionTimeMax.get());
            int max = Math.max(reactionTimeMin.get(), reactionTimeMax.get());
            reactionReadyAt = System.currentTimeMillis() + ThreadLocalRandom.current().nextLong(min, max + 1L);
        }
    }

    private Entity findBestTarget() {
        if (sticky.get() && target != null && isValidStickyTarget(target)) {
            if (switchCooldownTicks > 0) {
                return target;
            }
        }

        Vec3d eye = mc.player.getEyePos();
        Vec3d look = AimUtils.getLookVector(mc.player.getYaw(), mc.player.getPitch());
        double halfFov = fov.get() / 2.0;
        boolean useFov = fov.get() < 360.0;

        candidateCache.clear();

        for (Entity entity : mc.world.getEntities()) {
            if (!isValidTarget(entity)) continue;

            Vec3d point = AimUtils.getAimPoint(entity, hitboxTarget.get(), prediction.get(), predictionTicks.get());
            double distance = eye.distanceTo(point);
            if (distance > range.get()) continue;

            double angle = AimUtils.getAngle(look, eye, point);
            if (useFov && angle > halfFov) continue;

            if (!throughWalls.get() && !AimUtils.hasLineOfSight(eye, point, entity)) continue;

            candidateCache.add(entity);
        }

        if (candidateCache.isEmpty()) {
            return null;
        }

        candidateCache.sort((e1, e2) -> {
            Vec3d p1 = AimUtils.getAimPoint(e1, hitboxTarget.get(), false, 0);
            Vec3d p2 = AimUtils.getAimPoint(e2, hitboxTarget.get(), false, 0);
            double d1 = eye.distanceTo(p1);
            double d2 = eye.distanceTo(p2);
            if (priority.get() == TargetPriority.Closest) {
                return Double.compare(d1, d2);
            } else if (priority.get() == TargetPriority.LowestHealth) {
                return Float.compare(AimUtils.getHealth(e1), AimUtils.getHealth(e2));
            } else {
                double a1 = AimUtils.getAngle(look, eye, p1);
                double a2 = AimUtils.getAngle(look, eye, p2);
                return Double.compare(a1, a2);
            }
        });

        return candidateCache.get(0);
    }

    /**
     * Strict Player-Only Validation Filter (Ignores mobs, animals, and non-player entities entirely)
     */
    private boolean isValidTarget(Entity entity) {
        if (entity == null || entity == mc.player || entity == mc.getCameraEntity()) return false;
        
        // Strict player check: discard anything that is not a real PlayerEntity immediately
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
        Vec3d eye = mc.player.getEyePos();
        Vec3d point = AimUtils.getAimPoint(entity, hitboxTarget.get(), false, 0);
        double effectiveUnlockRange = Math.max(unlockRange.get(), range.get() + 0.5);

        if (eye.distanceTo(point) > effectiveUnlockRange) return false;
        if (!throughWalls.get() && !AimUtils.hasLineOfSight(eye, point, entity)) return false;
        return true;
    }

    /**
     * Render target circle with guaranteed buffer capacity protection against native crash 0xc0000409.
     */
    private void renderTargetCircle(Render3DEvent event) {
        if (event == null || event.renderer == null || target == null || target.isRemoved() || !target.isAlive()) return;
        if (mc.world == null || target.getEntityWorld() != mc.world) {
            target = null;
            return;
        }

        Box box = target.getBoundingBox();
        if (box == null) return;
        double x = (box.minX + box.maxX) / 2.0;
        double z = (box.minZ + box.maxZ) / 2.0;
        double y = box.minY + circleHeight.get();

        int segments = MathHelper.clamp(circleSegments.get(), 8, 64);
        double radius = circleRadius.get();
        ShapeMode mode = circleShapeMode.get();

        Color fill = circleFillColor.get();
        Color outline = circleOutlineColor.get();

        if (mode.sides() && fill != null && fill.a > 0) {
            // Capacity protection: 1 center + (segments + 1) vertices = segments + 2. Allocate segments + 8 vertices, segments * 3 + 12 indices.
            event.renderer.triangles.ensureCapacity(segments + 8, segments * 3 + 12);
            int center = event.renderer.triangles.vec3(x, y, z).color(fill).next();
            int previous = -1;

            for (int i = 0; i <= segments; i++) {
                double angle = 2.0 * Math.PI * i / segments;
                int vertex = event.renderer.triangles
                    .vec3(x + Math.cos(angle) * radius, y, z + Math.sin(angle) * radius)
                    .color(fill).next();
                if (previous != -1) event.renderer.triangles.triangle(center, previous, vertex);
                previous = vertex;
            }
        }

        if (mode.lines() && outline != null && outline.a > 0) {
            // Capacity protection: allocate segments + 8 vertices, (segments + 2) * 2 + 12 indices.
            event.renderer.lines.ensureCapacity(segments + 8, (segments + 2) * 2 + 12);
            int first = -1, previous = -1;

            for (int i = 0; i <= segments; i++) {
                double angle = 2.0 * Math.PI * i / segments;
                int vertex = event.renderer.lines
                    .vec3(x + Math.cos(angle) * radius, y, z + Math.sin(angle) * radius)
                    .color(outline).next();
                if (previous == -1) first = vertex;
                else event.renderer.lines.line(previous, vertex);
                previous = vertex;
            }
            if (previous != -1 && first != -1) {
                event.renderer.lines.line(previous, first);
            }
        }
    }

    @Override
    public String getInfoString() {
        return target != null ? AimUtils.getName(target) : null;
    }
}
