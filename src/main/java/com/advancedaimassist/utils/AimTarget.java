package com.advancedaimassist.utils;

import net.minecraft.entity.Entity;
import net.minecraft.util.math.Vec3d;

/**
 * A snapshot of a candidate target. It is rebuilt every tick because entities move.
 *
 * @param entity   the target entity
 * @param point    the current aim point used for filtering (before prediction)
 * @param distance distance from the player's eyes to {@code point}
 * @param angle    angle in degrees between the current look direction and {@code point}
 * @param health   total health including absorption
 */
public record AimTarget(Entity entity, Vec3d point, double distance, double angle, float health) {
}
