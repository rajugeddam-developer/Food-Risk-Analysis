package com.foodrisk.service;

import com.foodrisk.dto.NormalizeRequest;
import com.foodrisk.dto.NormalizedFoodData;
import com.foodrisk.dto.NormalizedNutrition;
import com.foodrisk.entity.FoodAnalysisSession;
import com.foodrisk.exception.NormalizationException;
import com.foodrisk.gemini.GeminiClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Service orchestrating AI normalization of raw OCR text and validating untrusted model output.
 *
 * Enforces Milestone M6 boundaries:
 * - Validates input OCR text and session expiration.
 * - Invokes Gemini with strict prompt guardrails.
 * - Validates output schema: strictly rejects negative values and impossible physical metrics.
 * - ZERO risk scoring or medical evaluations.
 */
@Service
public class FoodNormalizationService {

    private static final Logger log = LoggerFactory.getLogger(FoodNormalizationService.class);

    private final GeminiClient geminiClient;
    private final AnalysisSessionService sessionService;

    public FoodNormalizationService(GeminiClient geminiClient, AnalysisSessionService sessionService) {
        this.geminiClient = geminiClient;
        this.sessionService = sessionService;
    }

    public NormalizedFoodData normalize(UUID sessionId, NormalizeRequest request) {
        if (request == null || !request.hasContent()) {
            throw new IllegalArgumentException("At least one OCR text section (ingredients or nutrition) must be provided for normalization.");
        }

        // Validate session is active and not expired
        FoodAnalysisSession session = sessionService.getActiveSession(sessionId);

        log.info("Starting AI normalization for session {}", sessionId);
        NormalizedFoodData rawNormalized = geminiClient.normalize(request.ingredientText(), request.nutritionText());

        // Validate untrusted model output
        NormalizedFoodData validatedData = validateNormalizedData(rawNormalized);
        log.info("Successfully validated normalized food data for session {}", sessionId);

        return validatedData;
    }

    public NormalizedFoodData validateNormalizedData(NormalizedFoodData data) {
        if (data == null) {
            throw new NormalizationException("AI_MALFORMED_OUTPUT", "Received null normalization response from AI engine.");
        }

        List<String> uncertainties = new ArrayList<>(data.uncertainties() != null ? data.uncertainties() : List.of());

        // Validate serving size
        if (data.servingSizeGrams() != null && data.servingSizeGrams() < 0) {
            throw new NormalizationException("AI_INVALID_NUTRITION", "Invalid negative serving size grams: " + data.servingSizeGrams());
        }

        // Validate nutrition facts
        NormalizedNutrition nutrition = data.nutrition();
        if (nutrition != null) {
            checkNonNegative("Energy (kcal)", nutrition.energyKcal());
            checkNonNegative("Protein (g)", nutrition.proteinG());
            checkNonNegative("Carbohydrate (g)", nutrition.carbohydrateG());
            checkNonNegative("Total Sugars (g)", nutrition.totalSugarsG());
            checkNonNegative("Added Sugars (g)", nutrition.addedSugarsG());
            checkNonNegative("Total Fat (g)", nutrition.totalFatG());
            checkNonNegative("Saturated Fat (g)", nutrition.saturatedFatG());
            checkNonNegative("Trans Fat (g)", nutrition.transFatG());
            checkNonNegative("Sodium (mg)", nutrition.sodiumMg());
            checkNonNegative("Fiber (g)", nutrition.fiberG());

            // Check impossible physical values (e.g. > 100g per 100g basis)
            String basis = nutrition.basis() != null ? nutrition.basis().toLowerCase() : "";
            if (basis.contains("100g") || basis.contains("100 g")) {
                if (nutrition.totalFatG() != null && nutrition.totalFatG() > 100.0) {
                    throw new NormalizationException("AI_INVALID_NUTRITION", "Physically impossible total fat (>100g per 100g): " + nutrition.totalFatG());
                }
                if (nutrition.proteinG() != null && nutrition.proteinG() > 100.0) {
                    throw new NormalizationException("AI_INVALID_NUTRITION", "Physically impossible protein (>100g per 100g): " + nutrition.proteinG());
                }
                if (nutrition.carbohydrateG() != null && nutrition.carbohydrateG() > 100.0) {
                    throw new NormalizationException("AI_INVALID_NUTRITION", "Physically impossible carbohydrate (>100g per 100g): " + nutrition.carbohydrateG());
                }
            }

            // Flag added sugars exceeding total sugars as uncertainty
            if (nutrition.addedSugarsG() != null && nutrition.totalSugarsG() != null &&
                    nutrition.addedSugarsG() > nutrition.totalSugarsG()) {
                uncertainties.add("Added sugars (" + nutrition.addedSugarsG() + "g) exceeds total sugars (" + nutrition.totalSugarsG() + "g) in packaging evidence.");
            }
        }

        return new NormalizedFoodData(
                data.productName(),
                data.servingSize(),
                data.servingSizeGrams(),
                data.ingredients() != null ? data.ingredients() : List.of(),
                nutrition,
                uncertainties
        );
    }

    private void checkNonNegative(String nutrientName, Double value) {
        if (value != null && value < 0) {
            throw new NormalizationException("AI_INVALID_NUTRITION", "Invalid negative value for nutrient " + nutrientName + ": " + value);
        }
    }
}
