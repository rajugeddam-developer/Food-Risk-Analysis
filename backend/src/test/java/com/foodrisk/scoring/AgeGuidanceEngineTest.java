package com.foodrisk.scoring;

import com.fasterxml.jackson.databind.ObjectMapper;
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

class AgeGuidanceEngineTest {

    private AgeGuidanceEngine engine;

    @BeforeEach
    void setUp() {
        JsonAgeGuidanceRepository repository = new JsonAgeGuidanceRepository(
                new DefaultResourceLoader(),
                new ObjectMapper()
        );
        repository.initialize();
        engine = new AgeGuidanceEngine(repository);
    }

    @Test
    @DisplayName("Demographic evaluation produces guidance for all 3 target age groups (Children, Adults, Older Adults)")
    void testAllThreeDemographicsGenerated() {
        UUID sessionId = UUID.randomUUID();
        IngredientRiskAnalysisResult ingredientRisk = new IngredientRiskAnalysisResult(
                sessionId,
                new IngredientRiskSummary(1, 1, 0, 0, 0, 0, 0, 1, 0),
                List.of(new IngredientRiskItem("Oats", "Oats", IngredientRiskLevel.POSITIVE, List.of(), List.of("CODEX"), EvidenceStatus.SUPPORTED, null, null, RegulatoryStatus.PERMITTED)),
                Instant.now()
        );

        NutritionAnalysisResult nutrition = new NutritionAnalysisResult(
                sessionId,
                DataCompleteness.COMPLETE,
                NutritionBasis.PER_100G,
                null,
                List.of(new NutritionFinding(
                        NutrientType.PROTEIN, new BigDecimal("12.0"), "g", NutritionBasis.PER_100G, null,
                        new BigDecimal("12.0"), NutritionBasis.PER_100G, new BigDecimal("5.0"), "g",
                        NutritionBasis.PER_100G, ReferenceType.NUTRITION_REFERENCE, NutrientValueState.DETECTED_VALUE,
                        ComparisonStatus.WITHIN_REFERENCE, NutritionSeverity.LOW, "Good protein", List.of("CODEX")
                )),
                List.of("Source of protein"),
                List.of(),
                List.of(),
                List.of("CODEX"),
                "2026.09",
                Instant.now()
        );

        List<AgeGroupAwareness> result = engine.evaluateDemographics(ingredientRisk, nutrition);

        assertEquals(3, result.size());
        assertTrue(result.stream().anyMatch(a -> a.ageGroup() == AgeGroup.CHILDREN));
        assertTrue(result.stream().anyMatch(a -> a.ageGroup() == AgeGroup.ADULTS));
        assertTrue(result.stream().anyMatch(a -> a.ageGroup() == AgeGroup.OLDER_ADULTS));

        for (AgeGroupAwareness ag : result) {
            assertEquals(AgeAttentionLevel.ACCEPTABLE, ag.attentionLevel());
            assertNotNull(ag.summary());
            assertFalse(ag.summary().isBlank());
        }
    }

    @Test
    @DisplayName("Elevated sugar triggers higher attention for Children with authoritative guidance")
    void testHighSugarTriggersChildrenAttention() {
        UUID sessionId = UUID.randomUUID();
        NutritionAnalysisResult nutrition = new NutritionAnalysisResult(
                sessionId,
                DataCompleteness.COMPLETE,
                NutritionBasis.PER_100G,
                null,
                List.of(new NutritionFinding(
                        NutrientType.TOTAL_SUGARS, new BigDecimal("35.0"), "g", NutritionBasis.PER_100G, null,
                        new BigDecimal("35.0"), NutritionBasis.PER_100G, new BigDecimal("22.5"), "g",
                        NutritionBasis.PER_100G, ReferenceType.DIETARY_GUIDELINE, NutrientValueState.DETECTED_VALUE,
                        ComparisonStatus.ABOVE_REFERENCE, NutritionSeverity.HIGH, "Elevated sugar", List.of("WHO")
                )),
                List.of(),
                List.of("High sugar"),
                List.of(),
                List.of("WHO"),
                "2026.09",
                Instant.now()
        );

        List<AgeGroupAwareness> result = engine.evaluateDemographics(null, nutrition);

        AgeGroupAwareness childrenAwareness = result.stream()
                .filter(a -> a.ageGroup() == AgeGroup.CHILDREN)
                .findFirst()
                .orElseThrow();

        assertNotEquals(AgeAttentionLevel.ACCEPTABLE, childrenAwareness.attentionLevel());
        assertFalse(childrenAwareness.contributingFactors().isEmpty());
        assertTrue(childrenAwareness.contributingFactors().stream().anyMatch(f -> f.toLowerCase().contains("sugar")));
    }
}
