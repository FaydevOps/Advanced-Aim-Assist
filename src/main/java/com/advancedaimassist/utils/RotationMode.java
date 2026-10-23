package com.advancedaimassist.utils;

/**
 * How the module moves the player's rotation towards the target.
 */
public enum RotationMode {
    /** Snaps directly to the target rotation. */
    Instant,
    /** Interpolates towards the target and caps the step by the configured aim speed. */
    Smooth,
    /** Moves towards the target at a constant number of degrees per tick. */
    Linear
}
