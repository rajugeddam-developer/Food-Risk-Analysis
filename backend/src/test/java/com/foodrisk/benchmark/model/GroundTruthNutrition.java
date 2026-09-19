package com.foodrisk.benchmark.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Independently transcribed nutrition table ground truth from physical packaged food.
 * Missing/undeclared fields are strictly null (never fabricated as zeros).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record GroundTruthNutrition(
        String basis, // "PER_100G", "PER_100ML", "PER_SERVING"
        Double servingSizeGrams,
        Double energyKcal,
        Double proteinG,
        Double carbohydrateG,
        Double totalSugarsG,
        Double addedSugarsG,
        Double totalFatG,
        Double saturatedFatG,
        Double transFatG,
        Double sodiumMg,
        Double fiberG,
        Double saltG
) {}
