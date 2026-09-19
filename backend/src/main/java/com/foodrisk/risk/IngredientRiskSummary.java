package com.foodrisk.risk;

/**
 * High-level counts summarizing evaluated ingredients on product packaging.
 *
 * Strictly informational: does NOT calculate an overall health score or Good/Bad rating.
 */
public record IngredientRiskSummary(
        int totalIngredients,
        int identifiedIngredients,
        int uncertainIngredients,
        int additivesDetected,
        int highAttentionIngredients,
        int moderateAttentionIngredients,
        int lowAttentionIngredients,
        int noConcernIngredients,
        int unknownIngredients
) {}
