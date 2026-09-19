package com.foodrisk.nutrition;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Complete structured nutrition analysis result produced by Milestone M9.
 */
public record NutritionAnalysisResult(
        UUID sessionId,
        DataCompleteness dataCompleteness,
        NutritionBasis declaredBasis,
        Double servingSizeGrams,
        List<NutritionFinding> findings,
        List<String> positiveIndicators,
        List<String> attentionIndicators,
        List<String> missingNutrients,
        List<String> referenceSources,
        String nutritionReferenceVersion,
        Instant evaluatedAt
) {}
