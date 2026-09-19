package com.foodrisk.service;

import com.foodrisk.cache.CachedProductAnalysis;
import com.foodrisk.cache.ProductAnalysisCache;
import com.foodrisk.cache.ProductFingerprintService;
import com.foodrisk.classification.ClassificationCertainty;
import com.foodrisk.classification.ClassificationReasonCode;
import com.foodrisk.classification.FoodCategory;
import com.foodrisk.classification.FoodClassificationResult;
import com.foodrisk.classification.FoodClassificationService;
import com.foodrisk.dto.AnalysisStatusResponse;
import com.foodrisk.dto.NormalizedFoodData;
import com.foodrisk.dto.OcrAnalysisResponse;
import com.foodrisk.dto.OcrLabelResult;
import com.foodrisk.entity.AnalysisStatus;
import com.foodrisk.entity.FoodAnalysisSession;
import com.foodrisk.nutrition.DataCompleteness;
import com.foodrisk.nutrition.NutritionAnalysisResult;
import com.foodrisk.nutrition.NutritionAnalysisService;
import com.foodrisk.nutrition.NutritionBasis;
import com.foodrisk.risk.IngredientRiskAnalysisResult;
import com.foodrisk.risk.IngredientRiskService;
import com.foodrisk.risk.IngredientRiskSummary;
import com.foodrisk.scoring.AssessmentReliability;
import com.foodrisk.scoring.FoodRiskAssessment;
import com.foodrisk.scoring.FoodRiskAssessmentService;
import com.foodrisk.scoring.HumanConsumptionStatus;
import com.foodrisk.scoring.OverallFoodStatus;
import com.foodrisk.scoring.PopulationGuidance;
import com.foodrisk.service.context.InMemoryAnalysisContextStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AnalysisOrchestratorServiceTest {

    @Mock
    private AnalysisSessionService sessionService;
    @Mock
    private OcrService ocrService;
    @Mock
    private FoodNormalizationService normalizationService;
    @Mock
    private FoodClassificationService classificationService;
    @Mock
    private IngredientRiskService ingredientRiskService;
    @Mock
    private NutritionAnalysisService nutritionService;
    @Mock
    private FoodRiskAssessmentService assessmentService;
    @Mock
    private ProductFingerprintService fingerprintService;
    @Mock
    private ProductAnalysisCache analysisCache;

    private InMemoryAnalysisContextStore contextStore;
    private AnalysisOrchestratorService orchestrator;

    private UUID sessionId;
    private FoodAnalysisSession activeSession;

    @BeforeEach
    void setUp() {
        sessionId = UUID.randomUUID();
        activeSession = new FoodAnalysisSession("token-123", AnalysisStatus.CREATED, Instant.now().plusSeconds(900));
        contextStore = new InMemoryAnalysisContextStore(15);

        orchestrator = new AnalysisOrchestratorService(
                sessionService,
                ocrService,
                normalizationService,
                contextStore,
                classificationService,
                ingredientRiskService,
                nutritionService,
                assessmentService,
                fingerprintService,
                analysisCache,
                60
        );

        when(sessionService.getActiveSession(sessionId)).thenReturn(activeSession);
    }

    private FoodRiskAssessment createMockAssessment() {
        return new FoodRiskAssessment(
                sessionId,
                FoodCategory.HUMAN_FOOD,
                "Human snack",
                HumanConsumptionStatus.HUMAN_FOOD,
                82,
                OverallFoodStatus.GOOD_CHOICE,
                AssessmentReliability.HIGH,
                DataCompleteness.COMPLETE,
                ClassificationCertainty.HIGH,
                Collections.emptyList(),
                new IngredientRiskSummary(4, 4, 0, 0, 0, 0, 0, 4, 0),
                new NutritionAnalysisResult(sessionId, DataCompleteness.COMPLETE, NutritionBasis.PER_100G, null, Collections.emptyList(), Collections.emptyList(), Collections.emptyList(), Collections.emptyList(), Collections.emptyList(), "2026.09", Instant.now()),
                Collections.emptyList(),
                List.of("Low sugar"),
                List.of("Healthy choice"),
                new PopulationGuidance("Good", "Good", "Good"),
                List.of("FSSAI"),
                Collections.emptyList(),
                "2026.09",
                "1.0",
                Instant.now()
        );
    }

    @Test
    @DisplayName("Should execute full end-to-end analysis pipeline synchronously on cache miss")
    void shouldExecuteFullPipelineOnCacheMiss() {
        MockMultipartFile ingredientFile = new MockMultipartFile("ingredientImage", "ing.jpg", "image/jpeg", new byte[]{1, 2, 3});
        MockMultipartFile nutritionFile = new MockMultipartFile("nutritionImage", "nut.jpg", "image/jpeg", new byte[]{4, 5, 6});

        OcrAnalysisResponse ocrResp = new OcrAnalysisResponse(
                sessionId, "COMPLETED",
                new OcrLabelResult("Ingredients: Oats, Milk", 90.0f, 100, true),
                new OcrLabelResult("Energy 350kcal", 92.0f, 100, true),
                200
        );
        NormalizedFoodData normData = new NormalizedFoodData("Oatmeal", null, 100.0, Collections.emptyList(), null, Collections.emptyList());
        FoodClassificationResult classification = new FoodClassificationResult(FoodCategory.HUMAN_FOOD, ClassificationCertainty.HIGH, 0.99, ClassificationReasonCode.EXPLICIT_HUMAN_FOOD_MARKER, "Human food", List.of("Oats"), Collections.emptyList());
        IngredientRiskAnalysisResult ingredientRisk = new IngredientRiskAnalysisResult(sessionId, new IngredientRiskSummary(2, 2, 0, 0, 0, 0, 0, 2, 0), Collections.emptyList(), Instant.now());
        NutritionAnalysisResult nutrition = new NutritionAnalysisResult(sessionId, DataCompleteness.COMPLETE, NutritionBasis.PER_100G, null, Collections.emptyList(), Collections.emptyList(), Collections.emptyList(), Collections.emptyList(), Collections.emptyList(), "2026.09", Instant.now());
        FoodRiskAssessment assessment = createMockAssessment();

        when(ocrService.processOcr(sessionId, ingredientFile, nutritionFile)).thenReturn(ocrResp);
        when(normalizationService.normalize(eq(sessionId), any())).thenReturn(normData);
        when(fingerprintService.computeFingerprint(normData)).thenReturn("sha256-fp");
        when(analysisCache.get("sha256-fp")).thenReturn(Optional.empty()); // Cache miss

        when(classificationService.classifyProduct(sessionId)).thenReturn(classification);
        when(ingredientRiskService.analyzeIngredientRisk(sessionId)).thenReturn(ingredientRisk);
        when(nutritionService.analyzeNutrition(sessionId)).thenReturn(nutrition);
        when(assessmentService.getAssessment(sessionId)).thenReturn(assessment);

        FoodRiskAssessment result = orchestrator.executeAnalysisSync(sessionId, ingredientFile, nutritionFile);

        assertThat(result).isNotNull();
        assertThat(result.overallScore()).isEqualTo(82);

        verify(sessionService).updateStatus(activeSession, AnalysisStatus.PROCESSING);
        verify(sessionService).updateStatus(activeSession, AnalysisStatus.COMPLETED);
        verify(analysisCache).put(eq("sha256-fp"), eq(normData), eq(classification), eq(ingredientRisk), eq(nutrition), eq(assessment));
    }

    @Test
    @DisplayName("Should return cached assessment and skip downstream engines on cache hit")
    void shouldReturnCachedAnalysisOnCacheHit() {
        MockMultipartFile ingredientFile = new MockMultipartFile("ingredientImage", "ing.jpg", "image/jpeg", new byte[]{1, 2, 3});
        OcrAnalysisResponse ocrResp = new OcrAnalysisResponse(
                sessionId, "COMPLETED",
                new OcrLabelResult("Ingredients: Oats", 90.0f, 100, true),
                new OcrLabelResult(null, null, 0, false),
                100
        );
        NormalizedFoodData normData = new NormalizedFoodData("Oatmeal", null, 100.0, Collections.emptyList(), null, Collections.emptyList());
        FoodRiskAssessment cachedAssessment = createMockAssessment();
        CachedProductAnalysis cached = new CachedProductAnalysis(
                "cached-fp", "1.0", "2026.09",
                normData, null, null, null, cachedAssessment
        );

        when(ocrService.processOcr(sessionId, ingredientFile, null)).thenReturn(ocrResp);
        when(normalizationService.normalize(eq(sessionId), any())).thenReturn(normData);
        when(fingerprintService.computeFingerprint(normData)).thenReturn("cached-fp");
        when(analysisCache.get("cached-fp")).thenReturn(Optional.of(cached));

        FoodRiskAssessment result = orchestrator.executeAnalysisSync(sessionId, ingredientFile, null);

        assertThat(result).isNotNull();
        assertThat(result.overallScore()).isEqualTo(82);

        // Verify downstream engines were NOT invoked!
        verifyNoInteractions(classificationService);
        verifyNoInteractions(ingredientRiskService);
        verifyNoInteractions(nutritionService);
        verifyNoInteractions(assessmentService);
    }

    @Test
    @DisplayName("Should return status response for session tracking")
    void shouldReturnStatusResponse() {
        AnalysisStatusResponse status = orchestrator.getStatus(sessionId);
        assertThat(status).isNotNull();
        assertThat(status.sessionId()).isEqualTo(sessionId);
        assertThat(status.status()).isEqualTo(AnalysisStatus.CREATED);
    }
}
