package com.foodrisk.classification;

/**
 * Machine-readable reason codes explaining why a particular product category was assigned.
 */
public enum ClassificationReasonCode {
    EXPLICIT_HUMAN_FOOD_MARKER,
    EXPLICIT_PET_FOOD_MARKER,
    EXPLICIT_ANIMAL_FEED_MARKER,
    EXPLICIT_NON_FOOD_MARKER,
    CONFLICTING_EVIDENCE,
    INSUFFICIENT_INFORMATION,
    UNKNOWN_PRODUCT
}
