package com.foodrisk.service;

import com.foodrisk.cache.ProductAnalysisCache;
import com.foodrisk.cache.ProductFingerprintService;
import com.foodrisk.classification.ClassificationCertainty;
import com.foodrisk.classification.ClassificationReasonCode;
import com.foodrisk.classification.FoodCategory;
import com.foodrisk.classification.FoodClassificationResult;
import com.foodrisk.classification.FoodClassificationService;
import com.foodrisk.dto.NormalizedFoodData;
import com.foodrisk.dto.OcrAnalysisResponse;
import com.foodrisk.dto.OcrLabelResult;
import com.foodrisk.entity.AnalysisStatus;
import com.foodrisk.entity.FoodAnalysisSession;
import com.foodrisk.nutrition.ComparisonStatus;
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
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;

/**
 * Milestone M12: High-Concurrency Stress Test.
 *
 * Simulates high concurrency (50 parallel client threads) executing analysis orchestration
 * pipelines simultaneously to verify thread-safety, executor stability, cache synchronization,
 * and zero race conditions under load.
 */
@ExtendWith(MockitoExtension.class)
class OrchestratorConcurrencyTest {

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

    private ProductFingerprintService fingerprintService;
    private ProductAnalysisCache analysisCache;
    private InMemoryAnalysisContextStore contextStore;
    private AnalysisOrchestratorService orchestrator;

    @BeforeEach
    void setUp() {
        fingerprintService = new ProductFingerprintService();
        analysisCache = new ProductAnalysisCache(null, new com.fasterxml.jackson.databind.ObjectMapper());
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

        lenient().when(sessionService.getActiveSession(any(UUID.class))).thenAnswer(invocation -> {
            return new FoodAnalysisSession("token-" + UUID.randomUUID(), AnalysisStatus.CREATED, Instant.now().plusSeconds(900));
        });
    }

    @Test
    @DisplayName("50 concurrent analysis pipelines execute concurrently without deadlock or data corruption")
    void testHighConcurrencyExecution() throws InterruptedException, ExecutionException {
        int concurrentUsers = 50;
        ExecutorService executor = Executors.newFixedThreadPool(concurrentUsers);
        CountDownLatch readyLatch = new CountDownLatch(concurrentUsers);
        CountDownLatch startLatch = new CountDownLatch(1);

        MockMultipartFile dummyImage = new MockMultipartFile(
                "ingredientImage", "label.jpg", "image/jpeg", new byte[]{0xFF - 256, 0xD8 - 256, 0xFF - 256}
        );

        OcrAnalysisResponse mockOcr = new OcrAnalysisResponse(
                UUID.randomUUID(), "COMPLETED",
                new OcrLabelResult("Ingredients: Whole Oats, Sea Salt", 95.0f, 50, true),
                new OcrLabelResult(null, null, 0, false),
                50
        );
        NormalizedFoodData mockNorm = new NormalizedFoodData(
                "Organic Oats", "40g", 40.0,
                Collections.emptyList(), null, Collections.emptyList()
        );
        FoodClassificationResult mockClass = new FoodClassificationResult(
                FoodCategory.HUMAN_FOOD, ClassificationCertainty.HIGH, 0.95,
                ClassificationReasonCode.EXPLICIT_HUMAN_FOOD_MARKER, "Human cereal",
                List.of("Oats"), Collections.emptyList()
        );
        IngredientRiskSummary mockSummary = new IngredientRiskSummary(2, 2, 0, 0, 0, 0, 0, 2, 0);
        IngredientRiskAnalysisResult mockRisk = new IngredientRiskAnalysisResult(
                UUID.randomUUID(), mockSummary, Collections.emptyList(), Instant.now()
        );
        NutritionAnalysisResult mockNutrition = new NutritionAnalysisResult(
                UUID.randomUUID(), DataCompleteness.PARTIAL, NutritionBasis.PER_100G,
                100.0, Collections.emptyList(), List.of("High Fibre"), Collections.emptyList(),
                Collections.emptyList(), List.of("WHO"), "2026.09", Instant.now()
        );
        FoodRiskAssessment mockAssessment = new FoodRiskAssessment(
                UUID.randomUUID(), FoodCategory.HUMAN_FOOD, "Human food",
                HumanConsumptionStatus.HUMAN_FOOD, 88, OverallFoodStatus.GOOD_CHOICE,
                AssessmentReliability.HIGH, DataCompleteness.COMPLETE, ClassificationCertainty.HIGH,
                Collections.emptyList(), mockSummary, mockNutrition, Collections.emptyList(),
                List.of("Whole grain"), List.of("Healthy choice"),
                new PopulationGuidance("Suitable for all", "Good nutrition", "Low sodium"),
                List.of("WHO", "FSSAI"), Collections.emptyList(), "2026.09", "1.0", Instant.now()
        );

        lenient().when(ocrService.processOcr(any(), any(), any())).thenReturn(mockOcr);
        lenient().when(normalizationService.normalize(any(), any())).thenReturn(mockNorm);
        lenient().when(classificationService.classifyProduct(any())).thenReturn(mockClass);
        lenient().when(ingredientRiskService.analyzeIngredientRisk(any())).thenReturn(mockRisk);
        lenient().when(nutritionService.analyzeNutrition(any())).thenReturn(mockNutrition);
        lenient().when(assessmentService.getAssessment(any())).thenReturn(mockAssessment);

        List<Future<FoodRiskAssessment>> futures = new ArrayList<>();
        AtomicInteger successCount = new AtomicInteger(0);

        for (int i = 0; i < concurrentUsers; i++) {
            final UUID sessionId = UUID.randomUUID();
            Callable<FoodRiskAssessment> task = () -> {
                readyLatch.countDown();
                startLatch.await(5, TimeUnit.SECONDS); // Synchronous blast off

                FoodRiskAssessment assessment = orchestrator.executeAnalysisSync(sessionId, dummyImage, null);
                successCount.incrementAndGet();
                return assessment;
            };
            futures.add(executor.submit(task));
        }

        // Wait for all 50 threads to be ready
        assertThat(readyLatch.await(5, TimeUnit.SECONDS)).isTrue();
        // Release all 50 threads simultaneously
        startLatch.countDown();

        for (Future<FoodRiskAssessment> future : futures) {
            FoodRiskAssessment assessment = future.get();
            assertThat(assessment).isNotNull();
            assertThat(assessment.overallScore()).isEqualTo(88);
        }

        assertThat(successCount.get()).isEqualTo(concurrentUsers);
        executor.shutdown();
        assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
    }
}
