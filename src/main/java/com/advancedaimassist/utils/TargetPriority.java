package com.advancedaimassist.utils;

/**
 * How the module chooses between multiple valid targets.
 */
public enum TargetPriority {
    /** Targets the entity with the lowest total health. */
    LowestHealth,
    /** Targets the entity closest to the player's eyes. */
    Closest,
    /** Targets the entity closest to the current crosshair. */
    Angle
}
