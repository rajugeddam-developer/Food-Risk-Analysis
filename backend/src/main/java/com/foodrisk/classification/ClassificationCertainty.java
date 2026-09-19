package com.foodrisk.classification;

/**
 * Qualitative certainty level for food intent classification.
 *
 * Prefers honest qualitative categorization over fabricated decimal precision.
 */
public enum ClassificationCertainty {
    HIGH,
    MEDIUM,
    LOW,
    UNKNOWN
}
