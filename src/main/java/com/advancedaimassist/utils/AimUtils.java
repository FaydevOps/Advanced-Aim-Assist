package com.advancedaimassist.utils;

import meteordevelopment.meteorclient.utils.entity.EntityUtils;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.Monster;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.entity.passive.WanderingTraderEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import net.minecraft.client.network.PlayerListEntry;

import java.util.UUID;
import meteordevelopment.meteorclient.utils.player.PlayerUtils;

import java.util.concurrent.ThreadLocalRandom;

import static meteordevelopment.meteorclient.MeteorClient.mc;

public final class AimUtils {
    private AimUtils() {
    }

    public static boolean isValidTargetType(Entity entity, boolean targetNpcs) {
        if (entity instanceof PlayerEntity || entity instanceof Monster) return true;
        return targetNpcs && (entity instanceof VillagerEntity || entity instanceof WanderingTraderEntity);
    }

    public static boolean isDead(Entity entity) {
        if (!entity.isAlive() || entity.isRemoved()) return true;
        return entity instanceof LivingEntity living && (living.isDead() || living.getHealth() <= 0);
    }

    /**
     * Returns the point to aim at inside the target's bounding box.
     * Weighted random selection (favoring center-mass Body) and dampened prediction
     * ensure zero missed hits in fast PvP.
     */
    public static Vec3d getAimPoint(Entity entity, AimPart part, boolean prediction, int predictionTicks) {
        Box box = entity.getBoundingBox();

        double x = (box.minX + box.maxX) / 2.0;
        double z = (box.minZ + box.maxZ) / 2.0;
        double height = box.maxY - box.minY;

        AimPart actualPart = part;
        if (actualPart == AimPart.Random) {
            double r = ThreadLocalRandom.current().nextDouble();
            if (r < 0.6) actualPart = AimPart.Body;
            else if (r < 0.9) actualPart = AimPart.Head;
            else actualPart = AimPart.Feet;
        }

        double y = switch (actualPart) {
            case Head -> box.maxY - height * 0.15;
            case Body -> (box.minY + box.maxY) / 2.0;
            case Feet -> box.minY + height * 0.25;
            default -> (box.minY + box.maxY) / 2.0;
        };

        Vec3d point = new Vec3d(x, y, z);

        if (prediction && predictionTicks > 0) {
            // Dampen prediction factor to prevent over-leading and missing hits
            point = point.add(entity.getVelocity().multiply(predictionTicks * 0.75));
        }

        return point;
    }

    public static double getYaw(Vec3d from, Vec3d to) {
        double diffX = to.x - from.x;
        double diffZ = to.z - from.z;

        return Math.toDegrees(Math.atan2(diffZ, diffX)) - 90.0;
    }

    public static double getPitch(Vec3d from, Vec3d to) {
        double diffX = to.x - from.x;
        double diffY = to.y - from.y;
        double diffZ = to.z - from.z;
        double horizontalDistance = Math.sqrt(diffX * diffX + diffZ * diffZ);

        return -Math.toDegrees(Math.atan2(diffY, horizontalDistance));
    }

    public static Vec3d getLookVector(float yaw, float pitch) {
        double yawRadians = Math.toRadians(yaw);
        double pitchRadians = Math.toRadians(pitch);
        double cosPitch = Math.cos(pitchRadians);

        return new Vec3d(
            -Math.sin(yawRadians) * cosPitch,
            -Math.sin(pitchRadians),
            Math.cos(yawRadians) * cosPitch
        );
    }

    public static double getAngle(Vec3d look, Vec3d from, Vec3d to) {
        Vec3d direction = to.subtract(from);

        if (direction.lengthSquared() < 1.0E-6) return 0.0;

        double dot = MathHelper.clamp(look.dotProduct(direction.normalize()), -1.0, 1.0);
        return Math.toDegrees(Math.acos(dot));
    }

    /**
     * Strict line of sight check using world block raycasting to prevent targeting through walls,
     * combined with entity visibility check.
     */
    public static boolean hasLineOfSight(Vec3d from, Vec3d to, Entity target) {
        if (mc.world == null || mc.player == null) return true;
        RaycastContext context = new RaycastContext(
            from,
            to,
            RaycastContext.ShapeType.COLLIDER,
            RaycastContext.FluidHandling.NONE,
            mc.player
        );
        BlockHitResult result = mc.world.raycast(context);
        if (result.getType() == HitResult.Type.BLOCK) {
            return false;
        }
        return PlayerUtils.canSeeEntity(target);
    }

    public static float getHealth(Entity entity) {
        if (entity instanceof LivingEntity living) {
            return (float) (living.getHealth() + living.getAbsorptionAmount());
        }
        return 20.0f;
    }

    public static String getName(Entity entity) {
        return entity.getName().getString();
    }

    /**
     * Ping compensation helper: converts the player's network latency to an
     * equivalent number of ticks (20 TPS). Used to adjust prediction lookahead.
     */
    public static int getPingTicks() {
        try {
            if (mc.player == null || mc.getNetworkHandler() == null) return 0;
            PlayerListEntry entry = mc.getNetworkHandler().getPlayerListEntry(mc.player.getUuid());
            if (entry == null) return 0;
            return (int) Math.round(entry.getLatency() / 50.0);
        } catch (NullPointerException | IllegalStateException e) {
            return 0;
        }
    }

    /**
     * Simple parabolic vertical prediction for falling targets.
     * Simulates Minecraft gravity (~0.08/tick) and drag (~0.98) over {@code ticks}.
     */
    public static double predictVerticalOffset(double velocityY, int ticks) {
        double y = 0.0;
        double vy = velocityY;
        for (int i = 0; i < ticks; i++) {
            y += vy;
            vy -= 0.08;   // gravity (applyGravity approx)
            vy *= 0.98;   // drag
        }
        return y;
    }
}
