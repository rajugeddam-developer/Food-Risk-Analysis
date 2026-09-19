package com.foodrisk.dto;

import java.util.List;

/**
 * Normalized nutritional breakdown structured from OCR nutrition table text.
 *
 * Missing values are kept strictly as null (never fabricated as zeros).
 */
public record NormalizedNutrition(
        String basis,
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
        List<String> rawEntries
) {}
