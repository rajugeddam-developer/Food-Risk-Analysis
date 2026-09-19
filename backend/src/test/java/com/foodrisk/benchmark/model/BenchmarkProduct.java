package com.foodrisk.benchmark.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

/**
 * Benchmark record representing a real-world packaged food product label.
 * Every product has a stable ID, independent ground truth, and packaging notes.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record BenchmarkProduct(
        String productId,
        String productName,
        String category,
        String source,
        String labelLanguage,
        String packagingCondition,
        boolean ingredientPanelVisible,
        boolean nutritionPanelVisible,
        String dataSufficiency, // "SUFFICIENT", "PARTIALLY_SUFFICIENT", "INSUFFICIENT"
        String groundTruthOcrIngredients,
        String groundTruthOcrNutrition,
        String simulatedOcrIngredients,
        String simulatedOcrNutrition,
        List<String> groundTruthIngredients,
        List<String> groundTruthAdditives,
        GroundTruthNutrition groundTruthNutrition,
        List<String> expectedFindings,
        String expectedScoreEligibility, // "RATED", "UNRATED", "PARTIALLY_RATED"
        String notes
) {}
