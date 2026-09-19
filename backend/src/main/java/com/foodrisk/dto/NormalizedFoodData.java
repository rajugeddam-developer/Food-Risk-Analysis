package com.foodrisk.dto;

import java.util.List;

/**
 * Structured normalized food data produced by Gemini AI normalization.
 *
 * Strict boundary:
 * - Contains only normalized representations of evidence present in the OCR text.
 * - ZERO health risk scores, ZERO Good/Bad/Worst classifications, ZERO human/pet categorization.
 */
public record NormalizedFoodData(
        String productName,
        String servingSize,
        Double servingSizeGrams,
        List<NormalizedIngredient> ingredients,
        NormalizedNutrition nutrition,
        List<String> uncertainties
) {}
