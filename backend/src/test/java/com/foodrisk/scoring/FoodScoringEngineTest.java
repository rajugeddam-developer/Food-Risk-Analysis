package com.foodrisk.scoring;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.foodrisk.classification.ClassificationCertainty;
import com.foodrisk.classification.ClassificationReasonCode;
import com.foodrisk.classification.FoodCategory;
import com.foodrisk.classification.FoodClassificationResult;
import com.foodrisk.nutrition.ComparisonStatus;
import com.foodrisk.nutrition.DataCompleteness;
import com.foodrisk.nutrition.NutrientType;
import com.foodrisk.nutrition.NutrientValueState;
import com.foodrisk.nutrition.NutritionAnalysisResult;
import com.foodrisk.nutrition.NutritionBasis;
import com.foodrisk.nutrition.NutritionFinding;
import com.foodrisk.nutrition.NutritionSeverity;
import com.foodrisk.nutrition.ReferenceType;
import com.foodrisk.risk.EvidenceStatus;
import com.foodrisk.risk.IngredientRiskAnalysisResult;
import com.foodrisk.risk.IngredientRiskItem;
import com.foodrisk.risk.IngredientRiskLevel;
import com.foodrisk.risk.IngredientRiskSummary;
import com.foodrisk.risk.RegulatoryStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.DefaultResourceLoader;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class FoodScoringEngineTest {

    private FoodScoringEngine scoringEngine;

    @BeforeEach
    void setUp() {
        JsonScoringRuleRepository repository = new JsonScoringRuleRepository(
                new DefaultResourceLoader(),
                new ObjectMapper()
        );
        repository.initialize();
        scoringEngine = new FoodScoringEngine(repository);
    }

    @Test
    @DisplayName("Clean whole food receives GOOD_CHOICE status (score >= 80) with positive bonuses")
    void testCleanFoodGoodChoice() {
        UUID sessionId = UUID.randomUUID();
        FoodClassificationResult classification = new FoodClassificationResult(
                FoodCategory.HUMAN_FOOD,
                ClassificationCertainty.HIGH,
                0.95,
                ClassificationReasonCode.EXPLICIT_HUMAN_FOOD_MARKER,
                "Whole grain food",
                List.of(),
                List.of()
        );

        IngredientRiskSummary summary = new IngredientRiskSummary(1, 1, 0, 0, 0, 0, 0, 1, 0);
        IngredientRiskAnalysisResult ingredientRisk = new IngredientRiskAnalysisResult(
                sessionId,
                summary,
                List.of(new IngredientRiskItem(
                        "Rolled Oats", "Rolled Oats", IngredientRiskLevel.NO_CONCERN,
                        List.of(), List.of("FSSAI"), EvidenceStatus.SUPPORTED, null, null, RegulatoryStatus.PERMITTED
                )),
                Instant.now()
        );

        NutritionAnalysisResult nutrition = new NutritionAnalysisResult(
                sessionId,
                DataCompleteness.COMPLETE,
                NutritionBasis.PER_100G,
                null,
                List.of(
                        new NutritionFinding(NutrientType.FIBRE, new BigDecimal("10.0"), "g", NutritionBasis.PER_100G, null, new BigDecimal("10.0"), NutritionBasis.PER_100G, new BigDecimal("3.0"), "g", NutritionBasis.PER_100G, ReferenceType.NUTRITION_REFERENCE, NutrientValueState.DETECTED_VALUE, ComparisonStatus.WITHIN_REFERENCE, NutritionSeverity.LOW, "Good fibre", List.of("CODEX")),
                        new NutritionFinding(NutrientType.PROTEIN, new BigDecimal("13.0"), "g", NutritionBasis.PER_100G, null, new BigDecimal("13.0"), NutritionBasis.PER_100G, new BigDecimal("6.0"), "g", NutritionBasis.PER_100G, ReferenceType.NUTRITION_REFERENCE, NutrientValueState.DETECTED_VALUE, ComparisonStatus.WITHIN_REFERENCE, NutritionSeverity.LOW, "Good protein", List.of("CODEX")),
                        new NutritionFinding(NutrientType.SODIUM, new BigDecimal("10.0"), "mg", NutritionBasis.PER_100G, null, new BigDecimal("10.0"), NutritionBasis.PER_100G, new BigDecimal("120.0"), "mg", NutritionBasis.PER_100G, ReferenceType.DIETARY_GUIDELINE, NutrientValueState.DETECTED_VALUE, ComparisonStatus.WITHIN_REFERENCE, NutritionSeverity.LOW, "Low sodium", List.of("WHO"))
                ),
                List.of("Good source of dietary fibre", "Meaningful dietary protein contribution", "Low sodium content"),
                List.of(),
                List.of(),
                List.of("WHO", "CODEX"),
                "2026.09",
                Instant.now()
        );

        FoodRiskAssessment assessment = scoringEngine.assess(sessionId, classification, ingredientRisk, nutrition);

        assertNotNull(assessment.overallScore());
        assertEquals(100, assessment.overallScore(), "Clean food with positive bonuses should cap at max 100");
        assertEquals(OverallFoodStatus.GOOD_CHOICE, assessment.overallStatus());
        assertEquals(HumanConsumptionStatus.HUMAN_FOOD, assessment.humanConsumptionStatus());
        assertEquals(AssessmentReliability.HIGH, assessment.assessmentReliability());
        assertTrue(assessment.positiveIndicators().stream().anyMatch(p -> p.contains("dietary fibre")));
    }

    @Test
    @DisplayName("Anti-double-counting: Sugar overlap rule applies single -20 penalty instead of stacking individual penalties")
    void testAntiDoubleCountingSugarOverlap() {
        UUID sessionId = UUID.randomUUID();
        FoodClassificationResult classification = new FoodClassificationResult(
                FoodCategory.HUMAN_FOOD,
                ClassificationCertainty.HIGH,
                0.90,
                ClassificationReasonCode.EXPLICIT_HUMAN_FOOD_MARKER,
                "Processed biscuit",
                List.of(),
                List.of()
        );

        // Ingredient has Sugar flagged as moderate attention
        IngredientRiskAnalysisResult ingredientRisk = new IngredientRiskAnalysisResult(
                sessionId,
                new IngredientRiskSummary(1, 0, 0, 1, 0, 0, 0, 0, 1),
                List.of(new IngredientRiskItem(
                        "Refined Sugar", "Refined Sugar", IngredientRiskLevel.MODERATE_ATTENTION,
                        List.of("High caloric density"), List.of("FSSAI"), EvidenceStatus.SUPPORTED, null, null, RegulatoryStatus.PERMITTED
                )),
                Instant.now()
        );

        // Nutrition has high total sugar (30g/100g)
        NutritionAnalysisResult nutrition = new NutritionAnalysisResult(
                sessionId,
                DataCompleteness.COMPLETE,
                NutritionBasis.PER_100G,
                null,
                List.of(new NutritionFinding(
                        NutrientType.TOTAL_SUGARS, new BigDecimal("30.0"), "g", NutritionBasis.PER_100G, null,
                        new BigDecimal("30.0"), NutritionBasis.PER_100G, new BigDecimal("22.5"), "g",
                        NutritionBasis.PER_100G, ReferenceType.DIETARY_GUIDELINE, NutrientValueState.DETECTED_VALUE,
                        ComparisonStatus.ABOVE_REFERENCE, NutritionSeverity.HIGH, "High sugar", List.of("WHO")
                )),
                List.of(),
                List.of("Elevated sugar content"),
                List.of(),
                List.of("WHO"),
                "2026.09",
                Instant.now()
        );

        FoodRiskAssessment assessment = scoringEngine.assess(sessionId, classification, ingredientRisk, nutrition);

        assertNotNull(assessment.overallScore());
        // Baseline 100 - 20 (sugar overlap) = 80
        assertEquals(80, assessment.overallScore());
        assertEquals(OverallFoodStatus.GOOD_CHOICE, assessment.overallStatus());

        // Verify breakdown contains SUGAR_OVERLAP and NOT separate penalties
        assertTrue(assessment.scoreBreakdown().stream().anyMatch(s -> s.factor().equals("SUGAR_OVERLAP")));
        assertFalse(assessment.scoreBreakdown().stream().anyMatch(s -> s.factor().equals("MODERATE_ATTENTION_ADDITIVE")));
        assertFalse(assessment.scoreBreakdown().stream().anyMatch(s -> s.factor().equals("HIGH_SUGAR_NUTRITION")));
    }

    @Test
    @DisplayName("Category Priority: Pet food receives null score and NOT_INTENDED_FOR_HUMAN_CONSUMPTION")
    void testCategoryPriorityPetFood() {
        UUID sessionId = UUID.randomUUID();
        FoodClassificationResult classification = new FoodClassificationResult(
                FoodCategory.PET_FOOD,
                ClassificationCertainty.HIGH,
                0.98,
                ClassificationReasonCode.EXPLICIT_PET_FOOD_MARKER,
                "Dog food kibble",
                List.of("pet food label"),
                List.of("Not for human consumption")
        );

        IngredientRiskAnalysisResult ingredientRisk = new IngredientRiskAnalysisResult(
                sessionId,
                new IngredientRiskSummary(1, 1, 0, 0, 0, 0, 0, 1, 0),
                List.of(),
                Instant.now()
        );

        NutritionAnalysisResult nutrition = new NutritionAnalysisResult(
                sessionId,
                DataCompleteness.COMPLETE,
                NutritionBasis.PER_100G,
                null,
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                "2026.09",
                Instant.now()
        );

        FoodRiskAssessment assessment = scoringEngine.assess(sessionId, classification, ingredientRisk, nutrition);

        assertNull(assessment.overallScore(), "Pet food overall score must strictly be null");
        assertEquals(OverallFoodStatus.NOT_INTENDED_FOR_HUMAN_CONSUMPTION, assessment.overallStatus());
        assertEquals(HumanConsumptionStatus.NOT_INTENDED_FOR_HUMAN_CONSUMPTION, assessment.humanConsumptionStatus());
        assertTrue(assessment.keyConcerns().stream().anyMatch(c -> c.contains("pet food")));
        assertTrue(assessment.awarenessGuidance().stream().anyMatch(g -> g.contains("DO NOT CONSUME")));
    }

    @Test
    @DisplayName("Category Priority: Non-food item receives null score and NOT_INTENDED_FOR_HUMAN_CONSUMPTION")
    void testCategoryPriorityNonFood() {
        UUID sessionId = UUID.randomUUID();
        FoodClassificationResult classification = new FoodClassificationResult(
                FoodCategory.NON_FOOD,
                ClassificationCertainty.HIGH,
                0.99,
                ClassificationReasonCode.EXPLICIT_NON_FOOD_MARKER,
                "Dishwashing detergent",
                List.of(),
                List.of()
        );

        FoodRiskAssessment assessment = scoringEngine.assess(sessionId, classification, null, null);

        assertNull(assessment.overallScore(), "Non-food item overall score must strictly be null");
        assertEquals(OverallFoodStatus.NOT_INTENDED_FOR_HUMAN_CONSUMPTION, assessment.overallStatus());
        assertEquals(HumanConsumptionStatus.NOT_INTENDED_FOR_HUMAN_CONSUMPTION, assessment.humanConsumptionStatus());
    }

    @Test
    @DisplayName("Banned additive triggers HIGH_ATTENTION status with heavy penalty")
    void testBannedAdditiveHighAttention() {
        UUID sessionId = UUID.randomUUID();
        FoodClassificationResult classification = new FoodClassificationResult(
                FoodCategory.HUMAN_FOOD,
                ClassificationCertainty.HIGH,
                0.90,
                ClassificationReasonCode.EXPLICIT_HUMAN_FOOD_MARKER,
                "Bread loaf",
                List.of(),
                List.of()
        );

        IngredientRiskAnalysisResult ingredientRisk = new IngredientRiskAnalysisResult(
                sessionId,
                new IngredientRiskSummary(1, 0, 0, 0, 1, 1, 0, 0, 1),
                List.of(new IngredientRiskItem(
                        "INS 924a", "Potassium Bromate", IngredientRiskLevel.HIGH_ATTENTION,
                        List.of("Banned due to health concerns"), List.of("FSSAI"), EvidenceStatus.SUPPORTED,
                        "Banned under FSSAI regulations", null, RegulatoryStatus.BANNED
                )),
                Instant.now()
        );

        // Nutrition has high sodium
        NutritionAnalysisResult nutrition = new NutritionAnalysisResult(
                sessionId,
                DataCompleteness.COMPLETE,
                NutritionBasis.PER_100G,
                null,
                List.of(new NutritionFinding(
                        NutrientType.SODIUM, new BigDecimal("800.0"), "mg", NutritionBasis.PER_100G, null,
                        new BigDecimal("800.0"), NutritionBasis.PER_100G, new BigDecimal("600.0"), "mg",
                        NutritionBasis.PER_100G, ReferenceType.DIETARY_GUIDELINE, NutrientValueState.DETECTED_VALUE,
                        ComparisonStatus.ABOVE_REFERENCE, NutritionSeverity.HIGH, "High sodium", List.of("WHO")
                )),
                List.of(),
                List.of("Elevated sodium"),
                List.of(),
                List.of("WHO"),
                "2026.09",
                Instant.now()
        );

        FoodRiskAssessment assessment = scoringEngine.assess(sessionId, classification, ingredientRisk, nutrition);

        assertNotNull(assessment.overallScore());
        // 100 - 30 (banned) - 15 (high sodium) = 55 -> NEEDS_ATTENTION
        assertEquals(55, assessment.overallScore());
        assertEquals(OverallFoodStatus.NEEDS_ATTENTION, assessment.overallStatus());
        assertTrue(assessment.scoreBreakdown().stream().anyMatch(s -> s.factor().equals("BANNED_ADDITIVE")));
    }

    @Test
    @DisplayName("Score bounds are clamped strictly between 0 and 100")
    void testScoreBoundsClamping() {
        UUID sessionId = UUID.randomUUID();
        FoodClassificationResult classification = new FoodClassificationResult(
                FoodCategory.HUMAN_FOOD,
                ClassificationCertainty.HIGH,
                0.95,
                ClassificationReasonCode.EXPLICIT_HUMAN_FOOD_MARKER,
                "UPF formulation",
                List.of(),
                List.of()
        );

        // Multiple severe penalties that exceed 100
        IngredientRiskAnalysisResult ingredientRisk = new IngredientRiskAnalysisResult(
                sessionId,
                new IngredientRiskSummary(4, 0, 0, 0, 4, 4, 0, 0, 4),
                List.of(
                        new IngredientRiskItem("INS 924a", "Potassium Bromate", IngredientRiskLevel.HIGH_ATTENTION, List.of(), List.of("FSSAI"), EvidenceStatus.SUPPORTED, null, null, RegulatoryStatus.BANNED), // -30
                        new IngredientRiskItem("INS 925", "Chlorine", IngredientRiskLevel.HIGH_ATTENTION, List.of(), List.of("FSSAI"), EvidenceStatus.SUPPORTED, null, null, RegulatoryStatus.BANNED), // -30
                        new IngredientRiskItem("INS 102", "Tartrazine", IngredientRiskLevel.HIGH_ATTENTION, List.of(), List.of("FSSAI"), EvidenceStatus.SUPPORTED, null, null, RegulatoryStatus.RESTRICTED), // -15
                        new IngredientRiskItem("INS 110", "Sunset Yellow", IngredientRiskLevel.HIGH_ATTENTION, List.of(), List.of("FSSAI"), EvidenceStatus.SUPPORTED, null, null, RegulatoryStatus.RESTRICTED) // -15
                ),
                Instant.now()
        );

        // Nutrition has high sodium (-15), high sat fat (-10), and trans fat (-20)
        NutritionAnalysisResult nutrition = new NutritionAnalysisResult(
                sessionId,
                DataCompleteness.COMPLETE,
                NutritionBasis.PER_100G,
                null,
                List.of(
                        new NutritionFinding(NutrientType.SODIUM, new BigDecimal("1200.0"), "mg", NutritionBasis.PER_100G, null, new BigDecimal("1200.0"), NutritionBasis.PER_100G, new BigDecimal("600.0"), "mg", NutritionBasis.PER_100G, ReferenceType.DIETARY_GUIDELINE, NutrientValueState.DETECTED_VALUE, ComparisonStatus.ABOVE_REFERENCE, NutritionSeverity.HIGH, "High sodium", List.of("WHO")),
                        new NutritionFinding(NutrientType.SATURATED_FAT, new BigDecimal("15.0"), "g", NutritionBasis.PER_100G, null, new BigDecimal("15.0"), NutritionBasis.PER_100G, new BigDecimal("5.0"), "g", NutritionBasis.PER_100G, ReferenceType.DIETARY_GUIDELINE, NutrientValueState.DETECTED_VALUE, ComparisonStatus.ABOVE_REFERENCE, NutritionSeverity.HIGH, "High sat fat", List.of("WHO")),
                        new NutritionFinding(NutrientType.TRANS_FAT, new BigDecimal("3.0"), "g", NutritionBasis.PER_100G, null, new BigDecimal("3.0"), NutritionBasis.PER_100G, new BigDecimal("2.0"), "g", NutritionBasis.PER_100G, ReferenceType.REGULATORY_LIMIT, NutrientValueState.DETECTED_VALUE, ComparisonStatus.ABOVE_REFERENCE, NutritionSeverity.HIGH, "Trans fat", List.of("FSSAI"))
                ),
                List.of(),
                List.of(),
                List.of(),
                List.of("FSSAI", "WHO"),
                "2026.09",
                Instant.now()
        );

        FoodRiskAssessment assessment = scoringEngine.assess(sessionId, classification, ingredientRisk, nutrition);

        assertNotNull(assessment.overallScore());
        assertEquals(0, assessment.overallScore(), "Score should be clamped at min 0 even with excessive penalties");
        assertEquals(OverallFoodStatus.HIGH_ATTENTION, assessment.overallStatus());
    }

    @Test
    @DisplayName("Scientific Honesty: Missing nutrition + benign ingredients yields UNRATED (null score, INSUFFICIENT_DATA)")
    void testMissingNutritionWithBenignIngredientYieldsUnrated() {
        UUID sessionId = UUID.randomUUID();
        FoodClassificationResult classification = new FoodClassificationResult(
                FoodCategory.HUMAN_FOOD,
                ClassificationCertainty.HIGH,
                0.95,
                ClassificationReasonCode.EXPLICIT_HUMAN_FOOD_MARKER,
                "Bakery biscuit",
                List.of(),
                List.of()
        );

        IngredientRiskAnalysisResult ingredientRisk = new IngredientRiskAnalysisResult(
                sessionId,
                new IngredientRiskSummary(2, 2, 0, 0, 0, 0, 0, 2, 0),
                List.of(
                        new IngredientRiskItem("Wheat Flour", "Wheat Flour", IngredientRiskLevel.NO_CONCERN, List.of(), List.of("FSSAI"), EvidenceStatus.SUPPORTED, null, null, RegulatoryStatus.PERMITTED),
                        new IngredientRiskItem("Palm Oil", "Palm Oil", IngredientRiskLevel.NO_CONCERN, List.of(), List.of("FSSAI"), EvidenceStatus.SUPPORTED, null, null, RegulatoryStatus.PERMITTED)
                ),
                Instant.now()
        );

        // Nutrition is null / missing
        FoodRiskAssessment assessment = scoringEngine.assess(sessionId, classification, ingredientRisk, null);

        assertNull(assessment.overallScore(), "Overall score must strictly be null when nutrition table is missing without severe hazards");
        assertEquals(OverallFoodStatus.INSUFFICIENT_DATA, assessment.overallStatus());
        assertEquals(ScoreEligibility.UNRATED, assessment.scoreEligibility());
        assertTrue(assessment.limitations().stream().anyMatch(l -> l.contains("Nutrition facts undeclared or unreadable")));
        assertTrue(assessment.scoreBreakdown().stream().anyMatch(s -> s.factor().equals("UNRATED_MISSING_NUTRITION")));
    }

    @Test
    @DisplayName("Severe hazard detection: Missing nutrition + banned additive yields PARTIALLY_RATED with penalties applied")
    void testMissingNutritionWithSevereBannedAdditiveYieldsPartiallyRated() {
        UUID sessionId = UUID.randomUUID();
        FoodClassificationResult classification = new FoodClassificationResult(
                FoodCategory.HUMAN_FOOD,
                ClassificationCertainty.HIGH,
                0.90,
                ClassificationReasonCode.EXPLICIT_HUMAN_FOOD_MARKER,
                "Bread loaf",
                List.of(),
                List.of()
        );

        IngredientRiskAnalysisResult ingredientRisk = new IngredientRiskAnalysisResult(
                sessionId,
                new IngredientRiskSummary(1, 0, 0, 0, 1, 1, 0, 0, 1),
                List.of(new IngredientRiskItem(
                        "INS 924a", "Potassium Bromate", IngredientRiskLevel.HIGH_ATTENTION,
                        List.of("Banned due to health concerns"), List.of("FSSAI"), EvidenceStatus.SUPPORTED,
                        "Banned under FSSAI regulations", null, RegulatoryStatus.BANNED
                )),
                Instant.now()
        );

        FoodRiskAssessment assessment = scoringEngine.assess(sessionId, classification, ingredientRisk, null);

        assertNotNull(assessment.overallScore(), "Banned additive hazard must apply penalty even if nutrition is missing");
        assertEquals(ScoreEligibility.PARTIALLY_RATED, assessment.scoreEligibility());
        // 100 (baseline) - 30 (banned) = 70 -> NEEDS_ATTENTION (< 80)
        assertEquals(70, assessment.overallScore());
        assertEquals(OverallFoodStatus.NEEDS_ATTENTION, assessment.overallStatus());
        assertTrue(assessment.limitations().stream().anyMatch(l -> l.contains("partially evaluated")));
    }

    @Test
    @DisplayName("Complete data: Both ingredients and nutrition present yields RATED")
    void testCompleteDataYieldsRated() {
        UUID sessionId = UUID.randomUUID();
        FoodClassificationResult classification = new FoodClassificationResult(
                FoodCategory.HUMAN_FOOD,
                ClassificationCertainty.HIGH,
                0.95,
                ClassificationReasonCode.EXPLICIT_HUMAN_FOOD_MARKER,
                "Oats",
                List.of(),
                List.of()
        );

        IngredientRiskAnalysisResult ingredientRisk = new IngredientRiskAnalysisResult(
                sessionId,
                new IngredientRiskSummary(1, 1, 0, 0, 0, 0, 0, 1, 0),
                List.of(new IngredientRiskItem("Rolled Oats", "Rolled Oats", IngredientRiskLevel.NO_CONCERN, List.of(), List.of("FSSAI"), EvidenceStatus.SUPPORTED, null, null, RegulatoryStatus.PERMITTED)),
                Instant.now()
        );

        NutritionAnalysisResult nutrition = new NutritionAnalysisResult(
                sessionId,
                DataCompleteness.COMPLETE,
                NutritionBasis.PER_100G,
                null,
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of("WHO"),
                "2026.09",
                Instant.now()
        );

        FoodRiskAssessment assessment = scoringEngine.assess(sessionId, classification, ingredientRisk, nutrition);

        assertNotNull(assessment.overallScore());
        assertEquals(ScoreEligibility.RATED, assessment.scoreEligibility());
    }
}
