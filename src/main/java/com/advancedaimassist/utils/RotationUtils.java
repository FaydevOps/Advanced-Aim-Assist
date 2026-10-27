package com.advancedaimassist.utils;

import net.minecraft.util.math.MathHelper;
import static meteordevelopment.meteorclient.MeteorClient.mc;

public final class RotationUtils {
    private RotationUtils() {}

    /**
     * Applies Minecraft's Greatest Common Divisor (GCD) fix to yaw and pitch deltas.
     * Uses proper angle wrapping and rounding to eliminate off-target errors and floating-point snapping.
     */
    public static double applyGCD(double current, double target) {
        if (mc.options == null) return target;

        float sensitivity = (float) (mc.options.getMouseSensitivity().getValue() * 0.6 + 0.2);
        double gcd = sensitivity * sensitivity * sensitivity * 1.2;
        // Clamp gcd to a sane range to avoid extreme quantization
        if (gcd <= 0.0001 || gcd > 1.0) return target;

        double delta = MathHelper.wrapDegrees(target - current);
        double count = Math.round(delta / gcd);
        return current + count * gcd;
    }

    /**
     * Smoothly steps current rotation towards target rotation by maxStep degrees.
     */
    public static float smoothRotate(float current, float target, float maxStep) {
        float delta = MathHelper.wrapDegrees(target - current);
        if (Math.abs(delta) <= maxStep) {
            return target;
        }
        return current + Math.signum(delta) * maxStep;
    }
}
