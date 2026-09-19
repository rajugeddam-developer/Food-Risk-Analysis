package com.foodrisk.nutrition;

/**
 * Result of comparing an observed or normalized nutrient value against an authoritative reference threshold.
 */
public enum ComparisonStatus {
    WITHIN_REFERENCE,
    ABOVE_REFERENCE,
    BELOW_REFERENCE,
    NO_REFERENCE,
    INSUFFICIENT_DATA
}
