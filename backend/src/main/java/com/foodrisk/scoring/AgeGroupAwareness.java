package com.foodrisk.scoring;

import java.util.List;

/**
 * Contextual, evidence-based food awareness assessment for a demographic age group.
 *
 * Strictly non-medical: conveys attention factors grounded in authoritative reference benchmarks
 * (e.g. WHO sugar guidelines for children, sodium considerations for older adults) without clinical assertions.
 */
public record AgeGroupAwareness(
        AgeGroup ageGroup,
        AgeAttentionLevel attentionLevel,
        String summary,
        List<String> contributingFactors,
        List<String> sourceIds
) {}
