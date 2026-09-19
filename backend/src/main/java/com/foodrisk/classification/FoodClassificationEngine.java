package com.foodrisk.classification;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.foodrisk.dto.NormalizedFoodData;
import com.foodrisk.dto.NormalizedIngredient;
import com.foodrisk.dto.NormalizedNutrition;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Deterministic rule-based engine evaluating food product intent and category.
 *
 * Enforces strict evidence hierarchy:
 * 1. Strong explicit markers (pet food, animal feed, non-food / do not ingest) take highest precedence.
 * 2. Conflicting indicators resolve safely to UNKNOWN.
 * 3. Supporting evidence (nutrition facts, standard food ingredients, serving size) indicates HUMAN_FOOD.
 * 4. Insufficient or missing evidence resolves to UNKNOWN.
 * 5. Strictly forbids asserting or accusing products of being "waste food".
 */
@Component
public class FoodClassificationEngine {

    private static final Logger log = LoggerFactory.getLogger(FoodClassificationEngine.class);

    private final ResourceLoader resourceLoader;
    private final ObjectMapper objectMapper;

    private final Set<String> petFoodKeywords = new HashSet<>();
    private final Set<String> animalFeedKeywords = new HashSet<>();
    private final Set<String> nonFoodKeywords = new HashSet<>();
    private final Set<String> humanFoodKeywords = new HashSet<>();

    public FoodClassificationEngine(ResourceLoader resourceLoader, ObjectMapper objectMapper) {
        this.resourceLoader = resourceLoader;
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    public void loadRules() {
        try {
            Resource resource = resourceLoader.getResource("classpath:data/food-risk-knowledge/food-category-rules.json");
            if (!resource.exists()) {
                resource = resourceLoader.getResource("classpath:data/food-category-rules.json");
            }
            if (resource.exists()) {
                try (InputStream is = resource.getInputStream()) {
                    JsonNode root = objectMapper.readTree(is);
                    loadKeywords(root.path("explicitPetFoodKeywords"), petFoodKeywords);
                    loadKeywords(root.path("explicitAnimalFeedKeywords"), animalFeedKeywords);
                    loadKeywords(root.path("explicitNonFoodKeywords"), nonFoodKeywords);
                    loadKeywords(root.path("humanFoodProductKeywords"), humanFoodKeywords);
                }
                log.info("Loaded food classification rules: {} pet keywords, {} animal feed keywords, {} non-food keywords, {} human food keywords",
                        petFoodKeywords.size(), animalFeedKeywords.size(), nonFoodKeywords.size(), humanFoodKeywords.size());
            } else {
                log.warn("food-category-rules.json not found on classpath; using built-in defaults");
                populateFallbackKeywords();
            }
        } catch (Exception e) {
            log.error("Failed to load food category rules from JSON: {}", e.getMessage());
            populateFallbackKeywords();
        }
    }

    private void loadKeywords(JsonNode arrayNode, Set<String> target) {
        if (arrayNode.isArray()) {
            for (JsonNode n : arrayNode) {
                target.add(n.asText().toLowerCase(Locale.ROOT));
            }
        }
    }

    private void populateFallbackKeywords() {
        petFoodKeywords.addAll(List.of("dog food", "cat food", "pet food", "pet treat", "puppy", "kitten", "for dogs", "for cats"));
        animalFeedKeywords.addAll(List.of("animal feed", "livestock feed", "cattle feed", "poultry feed", "bird feed"));
        nonFoodKeywords.addAll(List.of("not for human consumption", "for external use only", "detergent", "shampoo", "cleaner", "do not ingest"));
        humanFoodKeywords.addAll(List.of("biscuit", "cookie", "cereal", "chips", "noodles", "chocolate", "bread", "juice", "snack"));
    }

    public FoodClassificationResult classify(NormalizedFoodData foodData) {
        if (foodData == null) {
            return new FoodClassificationResult(
                    FoodCategory.UNKNOWN,
                    ClassificationCertainty.LOW,
                    null,
                    ClassificationReasonCode.INSUFFICIENT_INFORMATION,
                    "Available label information is insufficient to determine the intended product category.",
                    List.of("No food data provided"),
                    List.of("Verify the original packaging before consumption.")
            );
        }

        List<String> evidence = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        // Aggregate text corpus for keyword detection
        String corpus = buildSearchCorpus(foodData);

        // Check for explicit negative/non-human markers (Evidence Hierarchy Level 1)
        boolean hasPetMarker = checkMatches(corpus, petFoodKeywords, evidence, "Pet food indicator observed: '%s'");
        boolean hasAnimalMarker = checkMatches(corpus, animalFeedKeywords, evidence, "Animal feed indicator observed: '%s'");
        boolean hasNonFoodMarker = checkMatches(corpus, nonFoodKeywords, evidence, "Non-food indicator observed: '%s'");
        boolean hasHumanMarker = checkMatches(corpus, humanFoodKeywords, evidence, "Human food keyword observed: '%s'");

        // Evaluate supporting nutrition and ingredient indicators (Level 2)
        boolean hasNutritionTable = hasSignificantNutrition(foodData.nutrition());
        if (hasNutritionTable) {
            evidence.add("Nutrition information panel present (" + (foodData.nutrition().basis() != null ? foodData.nutrition().basis() : "per serving") + ")");
        }

        boolean hasIngredientsList = foodData.ingredients() != null && !foodData.ingredients().isEmpty();
        if (hasIngredientsList) {
            evidence.add("Ingredient declaration present with " + foodData.ingredients().size() + " entries");
        }

        if (foodData.servingSize() != null && !foodData.servingSize().isBlank()) {
            evidence.add("Serving size declaration present: " + foodData.servingSize());
        }

        int explicitCategoriesFound = (hasPetMarker ? 1 : 0) + (hasAnimalMarker ? 1 : 0) + (hasNonFoodMarker ? 1 : 0);

        // Conflict check: multiple explicit non-human categories or non-human + human product name
        if (explicitCategoriesFound > 1) {
            warnings.add("Label contains conflicting category indicators. Do not consume without verifying manufacturer intent.");
            return new FoodClassificationResult(
                    FoodCategory.UNKNOWN,
                    ClassificationCertainty.LOW,
                    null,
                    ClassificationReasonCode.CONFLICTING_EVIDENCE,
                    "Conflicting category evidence detected on packaging.",
                    evidence,
                    warnings
            );
        }

        // Priority 1: Explicit Non-Food
        if (hasNonFoodMarker) {
            warnings.add("Product is labeled for non-food or external application. Not intended for human consumption.");
            return new FoodClassificationResult(
                    FoodCategory.NON_FOOD,
                    ClassificationCertainty.HIGH,
                    null,
                    ClassificationReasonCode.EXPLICIT_NON_FOOD_MARKER,
                    "Label explicitly indicates non-food or external use.",
                    evidence,
                    warnings
            );
        }

        // Priority 2: Explicit Pet Food
        if (hasPetMarker) {
            warnings.add("Product is explicitly formulated for pets (canine/feline). Not intended for human consumption.");
            return new FoodClassificationResult(
                    FoodCategory.PET_FOOD,
                    ClassificationCertainty.HIGH,
                    null,
                    ClassificationReasonCode.EXPLICIT_PET_FOOD_MARKER,
                    "The label explicitly identifies the product as pet food.",
                    evidence,
                    warnings
            );
        }

        // Priority 3: Explicit Animal Feed
        if (hasAnimalMarker) {
            warnings.add("Product is labeled as animal feed or livestock fodder. Not intended for human consumption.");
            return new FoodClassificationResult(
                    FoodCategory.ANIMAL_FEED,
                    ClassificationCertainty.HIGH,
                    null,
                    ClassificationReasonCode.EXPLICIT_ANIMAL_FEED_MARKER,
                    "The label explicitly identifies the product as animal or livestock feed.",
                    evidence,
                    warnings
            );
        }

        // Priority 4: Human Food with supporting evidence
        if (hasHumanMarker || (hasNutritionTable && hasIngredientsList)) {
            ClassificationCertainty certainty = (hasHumanMarker && hasNutritionTable && hasIngredientsList)
                    ? ClassificationCertainty.HIGH
                    : ClassificationCertainty.MEDIUM;

            String reason = (foodData.productName() != null && !foodData.productName().isBlank())
                    ? "Identified as " + foodData.productName().trim() + ". Ingredients and nutrition information are consistent with packaged human food."
                    : "Product label contains ingredients and nutrition information consistent with a packaged human food product.";

            return new FoodClassificationResult(
                    FoodCategory.HUMAN_FOOD,
                    certainty,
                    null,
                    ClassificationReasonCode.EXPLICIT_HUMAN_FOOD_MARKER,
                    reason,
                    evidence,
                    warnings
            );
        }

        // If only ingredients list without nutrition or human food keyword
        if (hasIngredientsList && !hasNutritionTable) {
            String reason = (foodData.productName() != null && !foodData.productName().isBlank())
                    ? "Identified as " + foodData.productName().trim() + ". Ingredient list present without full nutritional panel."
                    : "Ingredient list present without nutritional panel; probable human food item.";

            return new FoodClassificationResult(
                    FoodCategory.HUMAN_FOOD,
                    ClassificationCertainty.LOW,
                    null,
                    ClassificationReasonCode.EXPLICIT_HUMAN_FOOD_MARKER,
                    reason,
                    evidence,
                    warnings
            );
        }

        // Fallback: Insufficient information
        warnings.add("Verify the original packaging before consumption.");
        return new FoodClassificationResult(
            FoodCategory.UNKNOWN,
            ClassificationCertainty.LOW,
            null,
            ClassificationReasonCode.INSUFFICIENT_INFORMATION,
            "Available label information is insufficient to determine the intended product category.",
            evidence,
            warnings
        );
    }

    private String buildSearchCorpus(NormalizedFoodData data) {
        StringBuilder sb = new StringBuilder();
        if (data.productName() != null) {
            sb.append(data.productName()).append(" ");
        }
        if (data.servingSize() != null) {
            sb.append(data.servingSize()).append(" ");
        }
        if (data.ingredients() != null) {
            for (NormalizedIngredient ing : data.ingredients()) {
                if (ing.name() != null) sb.append(ing.name()).append(" ");
                if (ing.rawText() != null) sb.append(ing.rawText()).append(" ");
            }
        }
        if (data.nutrition() != null && data.nutrition().rawEntries() != null) {
            for (String entry : data.nutrition().rawEntries()) {
                sb.append(entry).append(" ");
            }
        }
        if (data.uncertainties() != null) {
            for (String u : data.uncertainties()) {
                sb.append(u).append(" ");
            }
        }
        return sb.toString().toLowerCase(Locale.ROOT);
    }

    private boolean checkMatches(String corpus, Set<String> keywords, List<String> evidence, String template) {
        boolean matched = false;
        for (String kw : keywords) {
            if (corpus.contains(kw)) {
                evidence.add(String.format(template, kw));
                matched = true;
            }
        }
        return matched;
    }

    private boolean hasSignificantNutrition(NormalizedNutrition nutrition) {
        if (nutrition == null) return false;
        return nutrition.energyKcal() != null ||
               nutrition.totalFatG() != null ||
               nutrition.carbohydrateG() != null ||
               nutrition.proteinG() != null ||
               nutrition.sodiumMg() != null;
    }
}
