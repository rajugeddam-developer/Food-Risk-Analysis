package com.foodrisk.classification;

import java.util.List;

/**
 * Result of food product intent and category evaluation.
 *
 * @param category assigned category (HUMAN_FOOD, PET_FOOD, ANIMAL_FEED, NON_FOOD, or UNKNOWN)
 * @param certainty qualitative confidence (HIGH, MEDIUM, LOW, UNKNOWN)
 * @param confidence numerical confidence if calibrated, otherwise null
 * @param reasonCode machine-readable code explaining the classification basis
 * @param reason human-readable explanatory rationale
 * @param evidence list of supporting label facts observed
 * @param warnings advisory warnings if product is non-human or ambiguous
 */
public record FoodClassificationResult(
        FoodCategory category,
        ClassificationCertainty certainty,
        Double confidence,
        ClassificationReasonCode reasonCode,
        String reason,
        List<String> evidence,
        List<String> warnings
) {}
