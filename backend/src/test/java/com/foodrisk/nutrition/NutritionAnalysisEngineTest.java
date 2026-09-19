package com.foodrisk.nutrition;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.foodrisk.dto.NormalizedFoodData;
import com.foodrisk.dto.NormalizedIngredient;
import com.foodrisk.dto.NormalizedNutrition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.DefaultResourceLoader;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class NutritionAnalysisEngineTest {

    private NutritionAnalysisEngine engine;

    @BeforeEach
    void setUp() {
        JsonNutritionRuleRepository repository = new JsonNutritionRuleRepository(
                new DefaultResourceLoader(),
                new ObjectMapper()
        );
        repository.initialize();
        engine = new NutritionAnalysisEngine(repository);
    }

    @Test
    @DisplayName("Evaluate per-100g basis with high sugar and trans fat")
    void testEvaluatePer100gHighSugarAndTransFat() {
        UUID sessionId = UUID.randomUUID();
        // NormalizedNutrition: basis, energyKcal, proteinG, carbohydrateG, totalSugarsG, addedSugarsG, totalFatG, saturatedFatG, transFatG, sodiumMg, fiberG, rawEntries
        NormalizedNutrition nutrition = new NormalizedNutrition(
                "per 100g",
                450.0, // energyKcal
                5.0,   // proteinG
                60.0,  // carbohydrateG
                30.0,  // totalSugarsG (> 22.5g -> HIGH)
                25.0,  // addedSugarsG (> 10g -> HIGH)
                20.0,  // totalFatG
                10.0,  // saturatedFatG (> 5g -> HIGH)
                2.5,   // transFatG (> 2.0g -> HIGH, exceeds regulatory limit)
                600.0, // sodiumMg (> 600mg -> HIGH)
                1.0,   // fiberG
                List.of()
        );

        NormalizedFoodData foodData = new NormalizedFoodData(
                "Chocolate Cookie",
                null,
                null,
                List.of(new NormalizedIngredient("Sugar", "Sugar", false, null, false)),
                nutrition,
                List.of()
        );

        NutritionAnalysisResult result = engine.evaluate(sessionId, foodData);

        assertEquals(sessionId, result.sessionId());
        assertEquals(DataCompleteness.COMPLETE, result.dataCompleteness());
        assertEquals(NutritionBasis.PER_100G, result.declaredBasis());
        assertNull(result.servingSizeGrams());

        // Verify attention flags
        assertFalse(result.attentionIndicators().isEmpty(), "Should contain attention flags");
        assertTrue(result.attentionIndicators().stream().anyMatch(f -> f.contains("Elevated sugar")), "Should flag elevated sugar");
        assertTrue(result.attentionIndicators().stream().anyMatch(f -> f.contains("trans fatty acids")), "Should flag trans fats");
        assertTrue(result.attentionIndicators().stream().anyMatch(f -> f.contains("Elevated sodium")), "Should flag high sodium");

        // Verify trans fat finding has REGULATORY_LIMIT
        NutritionFinding transFatFinding = result.findings().stream()
                .filter(f -> f.nutrient() == NutrientType.TRANS_FAT)
                .findFirst()
                .orElseThrow();
        assertEquals(ComparisonStatus.ABOVE_REFERENCE, transFatFinding.status());
        assertEquals(NutritionSeverity.HIGH, transFatFinding.severity());
        assertEquals(ReferenceType.PRODUCT_CLASSIFICATION_THRESHOLD, transFatFinding.referenceType());
        assertEquals(NutrientValueState.DETECTED_VALUE, transFatFinding.valueState());
    }

    @Test
    @DisplayName("Evaluate per-serving basis with explicit servingSizeGrams scales mathematically to 100g")
    void testEvaluatePerServingWithServingSizeGrams() {
        UUID sessionId = UUID.randomUUID();
        // Serving size = 50g. Declared sugar = 15g per 50g -> 30g per 100g (HIGH)
        NormalizedNutrition nutrition = new NormalizedNutrition(
                "per serving (50g)",
                200.0, // energyKcal
                3.0,   // proteinG
                35.0,  // carbohydrateG
                15.0,  // totalSugarsG (15g in 50g -> 30g in 100g -> HIGH)
                10.0,  // addedSugarsG
                5.0,   // totalFatG
                2.0,   // saturatedFatG
                0.0,   // transFatG
                100.0, // sodiumMg
                2.0,   // fiberG
                List.of()
        );

        NormalizedFoodData foodData = new NormalizedFoodData(
                "Granola Bar",
                "50g",
                50.0,
                List.of(new NormalizedIngredient("Oats", "Oats", false, null, false)),
                nutrition,
                List.of()
        );

        NutritionAnalysisResult result = engine.evaluate(sessionId, foodData);

        assertEquals(NutritionBasis.PER_SERVING, result.declaredBasis());
        assertEquals(50.0, result.servingSizeGrams());

        NutritionFinding sugarFinding = result.findings().stream()
                .filter(f -> f.nutrient() == NutrientType.TOTAL_SUGARS)
                .findFirst()
                .orElseThrow();

        assertEquals(new BigDecimal("15.00"), sugarFinding.observedValue());
        assertEquals(new BigDecimal("30.00"), sugarFinding.normalizedValue());
        assertEquals(NutritionBasis.PER_100G, sugarFinding.normalizedBasis());
        assertEquals(ComparisonStatus.ABOVE_REFERENCE, sugarFinding.status());
        assertEquals(NutritionSeverity.HIGH, sugarFinding.severity());
    }

    @Test
    @DisplayName("Evaluate per-serving without servingSizeGrams returns INSUFFICIENT_DATA and does not guess")
    void testEvaluatePerServingWithoutServingSizeGrams() {
        UUID sessionId = UUID.randomUUID();
        NormalizedNutrition nutrition = new NormalizedNutrition(
                "per serving",
                150.0, // energyKcal
                2.0,   // proteinG
                20.0,  // carbohydrateG
                12.0,  // totalSugarsG
                null,  // addedSugarsG
                4.0,   // totalFatG
                1.5,   // saturatedFatG
                0.0,   // transFatG
                150.0, // sodiumMg
                1.0,   // fiberG
                List.of()
        );

        NormalizedFoodData foodData = new NormalizedFoodData(
                "Snack Bag",
                "1 pouch",
                null, // No gram weight!
                List.of(),
                nutrition,
                List.of()
        );

        NutritionAnalysisResult result = engine.evaluate(sessionId, foodData);

        assertEquals(NutritionBasis.PER_SERVING, result.declaredBasis());
        assertNull(result.servingSizeGrams());

        NutritionFinding sugarFinding = result.findings().stream()
                .filter(f -> f.nutrient() == NutrientType.TOTAL_SUGARS)
                .findFirst()
                .orElseThrow();

        assertEquals(ComparisonStatus.INSUFFICIENT_DATA, sugarFinding.status());
        assertNull(sugarFinding.normalizedValue());
        assertEquals(NutritionBasis.UNKNOWN, sugarFinding.normalizedBasis());
        assertTrue(sugarFinding.reason().contains("Serving size in grams was not declared"));
    }

    @Test
    @DisplayName("Missing nutrients are NOT assumed to be 0 and receive NOT_DECLARED state")
    void testMissingNutrientsNotZero() {
        UUID sessionId = UUID.randomUUID();
        NormalizedNutrition nutrition = new NormalizedNutrition(
                "per 100g",
                120.0, // energyKcal
                3.0,   // proteinG
                25.0,  // carbohydrateG
                null,  // totalSugarsG
                null,  // addedSugarsG
                null,  // totalFatG
                null,  // saturatedFatG
                null,  // transFatG
                null,  // sodiumMg
                null,  // fiberG
                List.of()
        );

        NormalizedFoodData foodData = new NormalizedFoodData(
                "Plain Biscuit",
                null,
                null,
                List.of(),
                nutrition,
                List.of()
        );

        NutritionAnalysisResult result = engine.evaluate(sessionId, foodData);

        assertEquals(DataCompleteness.PARTIAL, result.dataCompleteness());
        assertTrue(result.missingNutrients().contains("TOTAL_FAT"));
        assertTrue(result.missingNutrients().contains("TRANS_FAT"));
        assertTrue(result.missingNutrients().contains("SODIUM"));

        NutritionFinding sodiumFinding = result.findings().stream()
                .filter(f -> f.nutrient() == NutrientType.SODIUM)
                .findFirst()
                .orElseThrow();

        assertEquals(NutrientValueState.NOT_DECLARED, sodiumFinding.valueState());
        assertNull(sodiumFinding.observedValue());
        assertNull(sodiumFinding.normalizedValue());
        assertEquals(ComparisonStatus.INSUFFICIENT_DATA, sodiumFinding.status());
    }

    @Test
    @DisplayName("Explicit zero is marked EXPLICIT_ZERO and does not trigger attention flags")
    void testExplicitZero() {
        UUID sessionId = UUID.randomUUID();
        NormalizedNutrition nutrition = new NormalizedNutrition(
                "per 100g",
                50.0, // energyKcal
                2.0,  // proteinG
                10.0, // carbohydrateG
                0.0,  // totalSugarsG (explicit 0)
                0.0,  // addedSugarsG
                0.0,  // totalFatG
                0.0,  // saturatedFatG
                0.0,  // transFatG (explicit 0)
                50.0, // sodiumMg (50mg <= 120mg -> low sodium)
                0.0,  // fiberG
                List.of()
        );

        NormalizedFoodData foodData = new NormalizedFoodData(
                "Diet Broth",
                null,
                null,
                List.of(),
                nutrition,
                List.of()
        );

        NutritionAnalysisResult result = engine.evaluate(sessionId, foodData);

        NutritionFinding transFatFinding = result.findings().stream()
                .filter(f -> f.nutrient() == NutrientType.TRANS_FAT)
                .findFirst()
                .orElseThrow();

        assertEquals(NutrientValueState.EXPLICIT_ZERO, transFatFinding.valueState());
        assertEquals(ComparisonStatus.WITHIN_REFERENCE, transFatFinding.status());
        assertEquals(NutritionSeverity.LOW, transFatFinding.severity());
        assertEquals(new BigDecimal("0.00"), transFatFinding.observedValue());

        // Verify low sodium positive indicator
        assertTrue(result.positiveIndicators().stream().anyMatch(p -> p.contains("Low sodium")));
    }

    @Test
    @DisplayName("Positive nutrient indicators are extracted for fibre and protein")
    void testBeneficialNutrientPositiveIndicators() {
        UUID sessionId = UUID.randomUUID();
        NormalizedNutrition nutrition = new NormalizedNutrition(
                "per 100g",
                300.0, // energyKcal
                12.0,  // proteinG (> 6g benchmark)
                50.0,  // carbohydrateG
                4.0,   // totalSugarsG (<= 5g benchmark -> low sugar)
                0.0,   // addedSugarsG
                5.0,   // totalFatG
                1.0,   // saturatedFatG
                0.0,   // transFatG
                100.0, // sodiumMg (<= 120mg -> low sodium)
                6.0,   // fiberG (> 3g benchmark)
                List.of()
        );

        NormalizedFoodData foodData = new NormalizedFoodData(
                "High Fibre Bread",
                null,
                null,
                List.of(),
                nutrition,
                List.of()
        );

        NutritionAnalysisResult result = engine.evaluate(sessionId, foodData);

        assertTrue(result.positiveIndicators().stream().anyMatch(p -> p.contains("dietary fibre")));
        assertTrue(result.positiveIndicators().stream().anyMatch(p -> p.contains("dietary protein")));
        assertTrue(result.positiveIndicators().stream().anyMatch(p -> p.contains("Low sodium")));
        assertTrue(result.positiveIndicators().stream().anyMatch(p -> p.contains("Low total sugar")));
    }

    @Test
    @DisplayName("Null foodData returns INSUFFICIENT completeness result")
    void testNullFoodData() {
        UUID sessionId = UUID.randomUUID();
        NutritionAnalysisResult result = engine.evaluate(sessionId, null);

        assertEquals(DataCompleteness.INSUFFICIENT, result.dataCompleteness());
        assertTrue(result.findings().isEmpty());
        assertFalse(result.attentionIndicators().isEmpty());
    }
}
