package com.foodrisk.nutrition;

import java.math.BigDecimal;
import java.util.List;

/**
 * Detailed evaluation of an individual nutrient extracted from packaging evidence.
 *
 * Preserves both original declared values and normalized comparison metrics for full auditability.
 */
public record NutritionFinding(
        NutrientType nutrient,
        BigDecimal observedValue,
        String observedUnit,
        NutritionBasis declaredBasis,
        Double servingSizeGrams,
        BigDecimal normalizedValue,
        NutritionBasis normalizedBasis,
        BigDecimal referenceValue,
        String referenceUnit,
        NutritionBasis referenceBasis,
        ReferenceType referenceType,
        NutrientValueState valueState,
        ComparisonStatus status,
        NutritionSeverity severity,
        String reason,
        List<String> sourceIds
) {}
