package com.advancedaimassist.utils;

import meteordevelopment.meteorclient.utils.entity.EntityUtils;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.Monster;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

import static meteordevelopment.meteorclient.MeteorClient.mc;

public final class AimUtils {
    private AimUtils() {
    }

    /**
     * Players and hostile mobs are valid targets. In Yarn mappings, hostile mobs
     * implement {@link Monster}.
     */
    public static boolean isValidTargetType(Entity entity) {
        return entity instanceof PlayerEntity || entity instanceof Monster;
    }

    public static boolean isDead(Entity entity) {
        if (!entity.isAlive() || entity.isRemoved()) return true;
        return entity instanceof LivingEntity living && (living.isDead() || living.getHealth() <= 0);
    }

    /**
     * Returns the point to aim at inside the target's bounding box.
     */
    public static Vec3d getAimPoint(Entity entity, AimPart part, boolean prediction, int predictionTicks) {
        Box box = entity.getBoundingBox();

        double x = (box.minX + box.maxX) / 2.0;
        double z = (box.minZ + box.maxZ) / 2.0;

        double y = switch (part) {
            case Head -> box.maxY - 0.1;
            case Body -> (box.minY + box.maxY) / 2.0;
            case Feet -> box.minY + 0.1;
        };

        Vec3d point = new Vec3d(x, y, z);

        if (prediction && predictionTicks > 0) {
            point = point.add(entity.getVelocity().multiply(predictionTicks));
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

    /**
     * Converts a yaw/pitch pair to a unit look vector using Minecraft's coordinate system.
     */
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

    /**
     * Returns the angle in degrees between a look vector and the direction to a point.
     */
    public static double getAngle(Vec3d look, Vec3d from, Vec3d to) {
        Vec3d direction = to.subtract(from);

        if (direction.lengthSquared() < 1.0E-6) return 0.0;

        double dot = MathHelper.clamp(look.dotProduct(direction.normalize()), -1.0, 1.0);
        return Math.toDegrees(Math.acos(dot));
    }

    /**
     * Raycasts from the player's eyes to the supplied point. Blocks with collision are
     * considered solid; fluids are ignored.
     */
    public static boolean hasLineOfSight(Vec3d from, Vec3d to) {
        if (mc.world == null || mc.player == null) return false;

        return mc.world.raycast(new RaycastContext(
            from,
            to,
            RaycastContext.ShapeType.COLLIDER,
            RaycastContext.FluidHandling.NONE,
            mc.player
        )).getType() == HitResult.Type.MISS;
    }

    public static float getHealth(Entity entity) {
        if (entity instanceof LivingEntity living) {
            return living.getHealth() + living.getAbsorptionAmount();
        }

        return 0.0f;
    }

    public static String getName(Entity entity) {
        return EntityUtils.getName(entity);
    }
}
