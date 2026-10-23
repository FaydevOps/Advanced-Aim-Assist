package com.advancedaimassist.modules;

import com.advancedaimassist.utils.AimPart;
import com.advancedaimassist.utils.AimTarget;
import com.advancedaimassist.utils.AimUtils;
import com.advancedaimassist.utils.RotationMode;
import com.advancedaimassist.utils.TargetPriority;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.friends.Friends;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.Rotations;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.util.concurrent.ThreadLocalRandom;

import static meteordevelopment.meteorclient.MeteorClient.mc;

public class AdvancedAimAssist extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgAim = settings.createGroup("Aim");
    private final SettingGroup sgTargeting = settings.createGroup("Targeting");
    private final SettingGroup sgChecks = settings.createGroup("Checks");
    private final SettingGroup sgAntiDetection = settings.createGroup("Anti-Detection");

    // General

    private final Setting<Double> range = sgGeneral.add(new DoubleSetting.Builder()
        .name("range")
        .description("Maximum distance in blocks at which targets are considered.")
        .defaultValue(4.5)
        .range(2.0, 6.0)
        .sliderRange(2.0, 6.0)
        .build()
    );

    private final Setting<Double> fov = sgGeneral.add(new DoubleSetting.Builder()
        .name("fov")
        .description("Field of view in degrees used to filter targets. 180 disables the FOV filter.")
        .defaultValue(90.0)
        .range(30.0, 180.0)
        .sliderRange(30.0, 180.0)
        .build()
    );

    private final Setting<TargetPriority> priority = sgGeneral.add(new EnumSetting.Builder<TargetPriority>()
        .name("priority")
        .description("How multiple valid targets are compared.")
        .defaultValue(TargetPriority.LowestHealth)
        .build()
    );

    private final Setting<Boolean> ignoreFriends = sgGeneral.add(new BoolSetting.Builder()
        .name("ignore-friends")
        .description("Never targets players on your friends list.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> ignoreInvisible = sgGeneral.add(new BoolSetting.Builder()
        .name("ignore-invisible")
        .description("Never targets invisible entities.")
        .defaultValue(true)
        .build()
    );

    // Aim

    private final Setting<Double> aimSpeed = sgAim.add(new DoubleSetting.Builder()
        .name("aim-speed")
        .description("Maximum rotation speed in degrees per tick.")
        .defaultValue(5.0)
        .range(1.0, 20.0)
        .sliderRange(1.0, 20.0)
        .build()
    );

    private final Setting<Double> smoothness = sgAim.add(new DoubleSetting.Builder()
        .name("smoothness")
        .description("Interpolation factor for Smooth mode. Higher values make the aim softer.")
        .defaultValue(0.3)
        .range(0.0, 1.0)
        .sliderRange(0.0, 1.0)
        .build()
    );

    private final Setting<Boolean> prediction = sgAim.add(new BoolSetting.Builder()
        .name("prediction")
        .description("Aims ahead of moving targets based on their velocity.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> predictionTicks = sgAim.add(new IntSetting.Builder()
        .name("prediction-ticks")
        .description("How many ticks ahead to predict.")
        .defaultValue(3)
        .range(1, 10)
        .sliderRange(1, 10)
        .visible(prediction::get)
        .build()
    );

    private final Setting<AimPart> aimAt = sgAim.add(new EnumSetting.Builder<AimPart>()
        .name("aim-at")
        .description("Which part of the target's hitbox to aim at.")
        .defaultValue(AimPart.Head)
        .build()
    );

    private final Setting<RotationMode> rotationMode = sgAim.add(new EnumSetting.Builder<RotationMode>()
        .name("rotation-mode")
        .description("How the rotation is applied to the player.")
        .defaultValue(RotationMode.Smooth)
        .build()
    );

    private final Setting<Boolean> moveCamera = sgAim.add(new BoolSetting.Builder()
        .name("move-camera")
        .description("Moves your camera with the aim. Disable this for silent rotations.")
        .defaultValue(true)
        .build()
    );

    // Targeting

    private final Setting<Boolean> sticky = sgTargeting.add(new BoolSetting.Builder()
        .name("sticky")
        .description("Keeps the current target until it leaves the unlock range.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Double> unlockRange = sgTargeting.add(new DoubleSetting.Builder()
        .name("unlock-range")
        .description("Distance at which a sticky target is released. It is clamped to at least range + 0.5.")
        .defaultValue(5.5)
        .range(2.0, 12.0)
        .sliderRange(2.0, 12.0)
        .visible(sticky::get)
        .build()
    );

    // Checks

    private final Setting<Boolean> requireLineOfSight = sgChecks.add(new BoolSetting.Builder()
        .name("require-line-of-sight")
        .description("Only aims at targets that are visible through a direct raycast.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> requireAttackCooldown = sgChecks.add(new BoolSetting.Builder()
        .name("require-attack-cooldown")
        .description("Only rotates while the attack cooldown is fully charged.")
        .defaultValue(false)
        .build()
    );

    // Anti-Detection

    private final Setting<Boolean> randomize = sgAntiDetection.add(new BoolSetting.Builder()
        .name("randomize")
        .description("Adds a small random offset to the final rotation.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Double> randomAmount = sgAntiDetection.add(new DoubleSetting.Builder()
        .name("random-amount")
        .description("Maximum random offset in degrees for yaw and pitch.")
        .defaultValue(0.1)
        .range(0.0, 1.0)
        .sliderRange(0.0, 1.0)
        .visible(randomize::get)
        .build()
    );

    private final Setting<Boolean> humanLike = sgAntiDetection.add(new BoolSetting.Builder()
        .name("human-like")
        .description("Adds random pauses and limits the rotation speed to look less robotic.")
        .defaultValue(false)
        .build()
    );

    private Entity target;
    private double aimYaw;
    private double aimPitch;

    public AdvancedAimAssist() {
        super(Categories.Combat, "advanced-aim-assist", "An advanced aim assist with multiple targeting, rotation and anti-detection modes.");
    }

    @Override
    public void onActivate() {
        target = null;

        if (mc.player != null) {
            aimYaw = mc.player.getYaw();
            aimPitch = mc.player.getPitch();
        }
    }

    @Override
    public void onDeactivate() {
        target = null;
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player == null || mc.world == null) return;

        if (!mc.player.isAlive()) {
            target = null;
            return;
        }

        target = findTarget();
        if (target == null) return;

        // Keep tracking the target while the cooldown recovers, but do not rotate.
        if (requireAttackCooldown.get() && mc.player.getAttackCooldownProgress(0.5f) < 1.0f) return;

        // 1 in 10 ticks is skipped when human-like movement is enabled.
        if (humanLike.get() && ThreadLocalRandom.current().nextInt(10) == 0) return;

        Vec3d eye = mc.player.getEyePos();
        Vec3d point = AimUtils.getAimPoint(target, aimAt.get(), prediction.get(), predictionTicks.get());

        double targetYaw = AimUtils.getYaw(eye, point);
        double targetPitch = MathHelper.clamp(AimUtils.getPitch(eye, point), -90.0, 90.0);

        if (randomize.get()) {
            double amount = randomAmount.get();
            targetYaw += ThreadLocalRandom.current().nextDouble(-amount, amount);
            targetPitch += ThreadLocalRandom.current().nextDouble(-amount, amount);
        }

        // wrapDegrees makes the yaw take the shortest path instead of spinning around.
        double deltaYaw = MathHelper.wrapDegrees(targetYaw - aimYaw);
        double deltaPitch = targetPitch - aimPitch;

        double stepYaw;
        double stepPitch;

        switch (rotationMode.get()) {
            case Instant -> {
                stepYaw = deltaYaw;
                stepPitch = deltaPitch;
            }
            case Smooth -> {
                // A factor of exactly zero would freeze the aim, so keep a small minimum.
                double factor = Math.max(0.01, 1.0 - smoothness.get());
                double maxStep = aimSpeed.get();

                stepYaw = MathHelper.clamp(deltaYaw * factor, -maxStep, maxStep);
                stepPitch = MathHelper.clamp(deltaPitch * factor, -maxStep, maxStep);
            }
            case Linear -> {
                double maxStep = aimSpeed.get();

                stepYaw = MathHelper.clamp(deltaYaw, -maxStep, maxStep);
                stepPitch = MathHelper.clamp(deltaPitch, -maxStep, maxStep);
            }
            default -> throw new IllegalStateException("Unexpected rotation mode: " + rotationMode.get());
        }

        if (humanLike.get()) {
            double maxStep = Math.max(1.0, Math.min(30.0, aimSpeed.get() * 1.5));

            stepYaw = MathHelper.clamp(stepYaw, -maxStep, maxStep);
            stepPitch = MathHelper.clamp(stepPitch, -maxStep, maxStep);
        }

        aimYaw = MathHelper.wrapDegrees(aimYaw + stepYaw);
        aimPitch = MathHelper.clamp(aimPitch + stepPitch, -90.0, 90.0);

        if (moveCamera.get()) {
            mc.player.setYaw((float) aimYaw);
            mc.player.setPitch((float) aimPitch);
        }

        // Rotations handles the server-side rotation. move-camera above also updates the local camera.
        Rotations.rotate(aimYaw, aimPitch);
        Rotations.setCamRotation(aimYaw, aimPitch);
    }

    private Entity findTarget() {
        if (sticky.get() && target != null && isValidStickyTarget(target)) {
            return target;
        }

        Vec3d eye = mc.player.getEyePos();
        Vec3d look = AimUtils.getLookVector(mc.player.getYaw(), mc.player.getPitch());
        double halfFov = fov.get() / 2.0;
        boolean useFov = fov.get() < 179.9;

        AimTarget best = null;

        // Meteor no longer exposes Utils.loadedEntities(); this is the same source TargetUtils uses.
        for (Entity entity : mc.world.getEntities()) {
            if (!isValidTarget(entity)) continue;

            // Filter on the entity's current position. Prediction is only used for the final rotation,
            // so a fast-moving target is not dropped just because its predicted point is farther away.
            Vec3d point = AimUtils.getAimPoint(entity, aimAt.get(), false, 0);

            double distance = eye.distanceTo(point);
            if (distance > range.get()) continue;

            double angle = AimUtils.getAngle(look, eye, point);
            if (useFov && angle > halfFov) continue;

            if (requireLineOfSight.get() && !AimUtils.hasLineOfSight(eye, point)) continue;

            AimTarget candidate = new AimTarget(entity, point, distance, angle, AimUtils.getHealth(entity));
            if (best == null || isBetter(candidate, best)) best = candidate;
        }

        return best != null ? best.entity() : null;
    }

    private boolean isBetter(AimTarget candidate, AimTarget current) {
        return switch (priority.get()) {
            case LowestHealth -> {
                int health = Float.compare(candidate.health(), current.health());
                yield health != 0 ? health < 0 : candidate.distance() < current.distance();
            }
            case Closest -> {
                int distance = Double.compare(candidate.distance(), current.distance());
                yield distance != 0 ? distance < 0 : candidate.angle() < current.angle();
            }
            case Angle -> {
                int angle = Double.compare(candidate.angle(), current.angle());
                yield angle != 0 ? angle < 0 : candidate.distance() < current.distance();
            }
        };
    }

    private boolean isValidTarget(Entity entity) {
        if (entity == null || entity == mc.player || entity == mc.getCameraEntity()) return false;
        if (!AimUtils.isValidTargetType(entity)) return false;
        if (AimUtils.isDead(entity)) return false;
        if (ignoreInvisible.get() && entity.isInvisible()) return false;

        if (entity instanceof PlayerEntity player) {
            if (player.isSpectator() || player.isCreative()) return false;
            if (ignoreFriends.get() && !Friends.get().shouldAttack(player)) return false;
        }

        return true;
    }

    private boolean isValidStickyTarget(Entity entity) {
        if (!isValidTarget(entity)) return false;

        Vec3d eye = mc.player.getEyePos();
        Vec3d point = AimUtils.getAimPoint(entity, aimAt.get(), false, 0);
        double effectiveUnlockRange = Math.max(unlockRange.get(), range.get() + 0.5);

        if (eye.distanceTo(point) > effectiveUnlockRange) return false;
        if (requireLineOfSight.get() && !AimUtils.hasLineOfSight(eye, point)) return false;

        return true;
    }

    @Override
    public String getInfoString() {
        return target != null ? AimUtils.getName(target) : null;
    }
}
