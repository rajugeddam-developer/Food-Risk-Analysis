package com.foodrisk.dto;

import jakarta.validation.constraints.Size;

/**
 * Request payload containing raw OCR text to be normalized by Gemini AI.
 *
 * Ephemeral input: original image binaries are never received or passed here.
 */
public record NormalizeRequest(
        @Size(max = 10000, message = "Ingredient text exceeds maximum character limit")
        String ingredientText,

        @Size(max = 10000, message = "Nutrition text exceeds maximum character limit")
        String nutritionText
) {
    public boolean hasContent() {
        return (ingredientText != null && !ingredientText.isBlank()) ||
               (nutritionText != null && !nutritionText.isBlank());
    }
}
