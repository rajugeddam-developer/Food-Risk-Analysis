package com.foodrisk.cache;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.foodrisk.classification.ClassificationCertainty;
import com.foodrisk.classification.ClassificationReasonCode;
import com.foodrisk.classification.FoodCategory;
import com.foodrisk.classification.FoodClassificationResult;
import com.foodrisk.dto.NormalizedFoodData;
import com.foodrisk.nutrition.DataCompleteness;
import com.foodrisk.nutrition.NutritionAnalysisResult;
import com.foodrisk.nutrition.NutritionBasis;
import com.foodrisk.risk.IngredientRiskAnalysisResult;
import com.foodrisk.risk.IngredientRiskSummary;
import com.foodrisk.scoring.AssessmentReliability;
import com.foodrisk.scoring.FoodRiskAssessment;
import com.foodrisk.scoring.HumanConsumptionStatus;
import com.foodrisk.scoring.OverallFoodStatus;
import com.foodrisk.scoring.PopulationGuidance;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ProductAnalysisCacheTest {

    private ProductAnalysisCache cache;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        // In-memory mode (redisTemplate null)
        cache = new ProductAnalysisCache(null, objectMapper);
    }

    private FoodRiskAssessment createAssessment(AssessmentReliability reliability, FoodCategory category) {
        return new FoodRiskAssessment(
                UUID.randomUUID(),
                category,
                "Valid food reason",
                HumanConsumptionStatus.HUMAN_FOOD,
                78,
                OverallFoodStatus.GOOD_CHOICE,
                reliability,
                DataCompleteness.COMPLETE,
                ClassificationCertainty.HIGH,
                Collections.emptyList(),
                new IngredientRiskSummary(5, 5, 0, 1, 0, 0, 1, 4, 0),
                new NutritionAnalysisResult(UUID.randomUUID(), DataCompleteness.COMPLETE, NutritionBasis.PER_100G, null, Collections.emptyList(), Collections.emptyList(), Collections.emptyList(), Collections.emptyList(), Collections.emptyList(), "2026.09", Instant.now()),
                Collections.emptyList(),
                List.of("Low sodium"),
                List.of("Enjoy in moderation"),
                new PopulationGuidance("Suitable", "Suitable", "Suitable"),
                List.of("FSSAI"),
                Collections.emptyList(),
                "2026.09",
                "1.0",
                Instant.now()
        );
    }

    private FoodClassificationResult createClassification(FoodCategory category) {
        return new FoodClassificationResult(
                category,
                ClassificationCertainty.HIGH,
                0.95,
                ClassificationReasonCode.EXPLICIT_HUMAN_FOOD_MARKER,
                "Human food",
                List.of("Edible"),
                Collections.emptyList()
        );
    }

    @Test
    @DisplayName("Should cache eligible product and return cache hit on subsequent get")
    void shouldCacheAndRetrieveValidProduct() {
        String fp = "test-fingerprint-123";
        NormalizedFoodData normData = new NormalizedFoodData("Healthy Oats", null, 100.0, Collections.emptyList(), null, Collections.emptyList());
        FoodClassificationResult classification = createClassification(FoodCategory.HUMAN_FOOD);
        FoodRiskAssessment assessment = createAssessment(AssessmentReliability.HIGH, FoodCategory.HUMAN_FOOD);

        cache.put(fp, normData, classification, null, null, assessment);

        Optional<CachedProductAnalysis> result = cache.get(fp);
        assertThat(result).isPresent();
        assertThat(result.get().getFingerprint()).isEqualTo(fp);
        assertThat(result.get().getFoodRiskAssessment().overallScore()).isEqualTo(78);
        assertThat(result.get().getScoringRuleVersion()).isEqualTo("1.0");
        assertThat(result.get().getNutritionReferenceVersion()).isEqualTo("2026.09");
    }

    @Test
    @DisplayName("Should reject caching when reliability is LOW")
    void shouldNotCacheWhenReliabilityIsLow() {
        String fp = "test-fingerprint-low-rel";
        NormalizedFoodData normData = new NormalizedFoodData("Uncertain Snack", null, null, Collections.emptyList(), null, Collections.emptyList());
        FoodClassificationResult classification = createClassification(FoodCategory.HUMAN_FOOD);
        FoodRiskAssessment assessment = createAssessment(AssessmentReliability.LOW, FoodCategory.HUMAN_FOOD);

        cache.put(fp, normData, classification, null, null, assessment);

        Optional<CachedProductAnalysis> result = cache.get(fp);
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("Should reject caching when food category is UNKNOWN")
    void shouldNotCacheWhenCategoryIsUnknown() {
        String fp = "test-fingerprint-unknown-cat";
        NormalizedFoodData normData = new NormalizedFoodData("Mystery Substance", null, null, Collections.emptyList(), null, Collections.emptyList());
        FoodClassificationResult classification = createClassification(FoodCategory.UNKNOWN);
        FoodRiskAssessment assessment = createAssessment(AssessmentReliability.HIGH, FoodCategory.UNKNOWN);

        cache.put(fp, normData, classification, null, null, assessment);

        Optional<CachedProductAnalysis> result = cache.get(fp);
        assertThat(result).isEmpty();
    }
}
