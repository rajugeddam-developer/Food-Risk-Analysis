package com.foodrisk.service;

import com.foodrisk.dto.NormalizedIngredient;
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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
        return normalize(sessionId, request, List.of());
    }

    public NormalizedFoodData normalize(UUID sessionId, NormalizeRequest request, List<GeminiClient.ImagePayload> images) {
        if (request == null) {
            throw new IllegalArgumentException("NormalizeRequest cannot be null");
        }

        boolean ingBlank = request.ingredientText() == null || request.ingredientText().isBlank();
        boolean nutBlank = request.nutritionText() == null || request.nutritionText().isBlank();
        boolean hasImages = images != null && !images.isEmpty();

        if (ingBlank && nutBlank && !hasImages) {
            throw new IllegalArgumentException("At least one OCR text section (ingredients or nutrition) must be provided.");
        }

        // Validate session is active and not expired
        FoodAnalysisSession session = sessionService.getActiveSession(sessionId);

        NormalizedFoodData rawNormalized;
        try {
            log.info("Starting AI normalization for session {} (images: {})", sessionId, (images != null ? images.size() : 0));
            rawNormalized = (images != null && !images.isEmpty())
                    ? geminiClient.normalizeWithImages(request.ingredientText(), request.nutritionText(), images)
                    : geminiClient.normalize(request.ingredientText(), request.nutritionText());
        } catch (Exception ex) {
            log.warn("Gemini AI normalization failed for session {}: {}. Activating resilient deterministic fallback parser.",
                    sessionId, ex.getMessage());
            rawNormalized = parseDeterministicFallback(request.ingredientText(), request.nutritionText());
        }

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
                data.productMatchVerified(),
                data.mismatchReason(),
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

    private NormalizedFoodData parseDeterministicFallback(String ingredientText, String nutritionText) {
        List<NormalizedIngredient> ingredients = new ArrayList<>();
        List<String> uncertainties = new ArrayList<>();
        uncertainties.add("Normalized using deterministic rule-based parser fallback.");

        if (ingredientText != null && !ingredientText.isBlank()) {
            String cleaned = ingredientText
                    .replaceAll("(?i)^.*ingredients[:\\s]*", "")
                    .replaceAll("[()\\[\\]{}]", " ")
                    .trim();

            String[] rawTokens = cleaned.split("[,;•\n\r]+");
            Pattern additivePattern = Pattern.compile("(?i)(?:INS|E)[\s-]*([0-9]{3,4}[a-z]?)");

            for (String token : rawTokens) {
                String trimmed = token.trim();
                if (trimmed.length() >= 2 && !trimmed.matches("(?i)^[0-9%\\s]+$")) {
                    Matcher m = additivePattern.matcher(trimmed);
                    boolean isAdditive = m.find();
                    String additiveCode = isAdditive ? "INS " + m.group(1).toUpperCase() : null;

                    ingredients.add(new NormalizedIngredient(
                            capitalizeWords(trimmed),
                            trimmed,
                            isAdditive,
                            additiveCode,
                            false
                    ));
                }
            }
        }

        NormalizedNutrition nutrition = null;
        if (nutritionText != null && !nutritionText.isBlank()) {
            Double energy = extractNutrient(nutritionText, "(?i)(?:energy|calories|kcal)[:\\s]*([0-9]+(?:\\.[0-9]+)?)");
            Double protein = extractNutrient(nutritionText, "(?i)protein[:\\s]*([0-9]+(?:\\.[0-9]+)?)");
            Double carbs = extractNutrient(nutritionText, "(?i)(?:carbohydrate|carbs)[:\\s]*([0-9]+(?:\\.[0-9]+)?)");
            Double sugars = extractNutrient(nutritionText, "(?i)(?:total\\s+sugars?|sugars?)[:\\s]*([0-9]+(?:\\.[0-9]+)?)");
            Double addedSugars = extractNutrient(nutritionText, "(?i)added\\s+sugars?[:\\s]*([0-9]+(?:\\.[0-9]+)?)");
            Double totalFat = extractNutrient(nutritionText, "(?i)(?:total\\s+fat|fat)[:\\s]*([0-9]+(?:\\.[0-9]+)?)");
            Double satFat = extractNutrient(nutritionText, "(?i)saturated\\s+fat[:\\s]*([0-9]+(?:\\.[0-9]+)?)");
            Double transFat = extractNutrient(nutritionText, "(?i)trans\\s+fat[:\\s]*([0-9]+(?:\\.[0-9]+)?)");
            Double fiber = extractNutrient(nutritionText, "(?i)(?:dietary\\s+fiber|fiber)[:\\s]*([0-9]+(?:\\.[0-9]+)?)");
            
            Double sodium = extractNutrient(nutritionText, "(?i)sodium[:\\s]*([0-9]+(?:\\.[0-9]+)?)\\s*mg");
            if (sodium == null) {
                Double sodiumG = extractNutrient(nutritionText, "(?i)sodium[:\\s]*([0-9]+(?:\\.[0-9]+)?)\\s*g");
                if (sodiumG != null) {
                    sodium = sodiumG * 1000.0;
                }
            }
            if (sodium == null) {
                Double saltG = extractNutrient(nutritionText, "(?i)salt[:\\s]*([0-9]+(?:\\.[0-9]+)?)");
                if (saltG != null) {
                    sodium = saltG * 400.0; // standard estimation of sodium in table salt
                }
            }

            nutrition = new NormalizedNutrition(
                    "per 100g",
                    energy,
                    protein,
                    carbs,
                    sugars,
                    addedSugars,
                    totalFat,
                    satFat,
                    transFat,
                    sodium,
                    fiber,
                    List.of()
            );
        }

        Double servingGrams = extractNutrient(nutritionText != null ? nutritionText : "", "(?i)serving\\s*size[:\\s]*([0-9]+(?:\\.[0-9]+)?)\\s*g");
        String servingSize = servingGrams != null ? servingGrams + "g" : null;

        return new NormalizedFoodData(
                "Packaged Food Product",
                servingSize,
                servingGrams,
                ingredients,
                nutrition,
                uncertainties
        );
    }

    private Double extractNutrient(String text, String regex) {
        Matcher m = Pattern.compile(regex).matcher(text);
        if (m.find()) {
            try {
                return Double.parseDouble(m.group(1));
            } catch (NumberFormatException ignored) {}
        }
        return null;
    }

    private String capitalizeWords(String input) {
        String[] words = input.toLowerCase().split("\\s+");
        StringBuilder sb = new StringBuilder();
        for (String w : words) {
            if (!w.isBlank()) {
                sb.append(Character.toUpperCase(w.charAt(0)))
                  .append(w.substring(1))
                  .append(" ");
            }
        }
        return sb.toString().trim();
    }
}
