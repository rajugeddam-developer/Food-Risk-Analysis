package com.foodrisk.nutrition;

import java.math.BigDecimal;

/**
 * Definition of a verified regulatory or dietary reference rule loaded from JSON.
 */
public record NutritionRuleDefinition(
        NutrientType nutrient,
        NutritionBasis basis,
        BigDecimal threshold,
        String unit,
        ReferenceType referenceType,
        String jurisdiction,
        String sourceId,
        String source,
        String sourceUrl,
        String effectiveDate,
        String lastVerified,
        NutritionSeverity severity,
        String notes
) {}
