package com.foodrisk.service;

import com.foodrisk.cache.ProductAnalysisCache;
import com.foodrisk.cache.ProductFingerprintService;
import com.foodrisk.classification.FoodClassificationService;
import com.foodrisk.dto.AnalysisStatusResponse;
import com.foodrisk.dto.OcrAnalysisResponse;
import com.foodrisk.dto.OcrLabelResult;
import com.foodrisk.entity.AnalysisStatus;
import com.foodrisk.entity.FoodAnalysisSession;
import com.foodrisk.exception.NormalizationException;
import com.foodrisk.exception.OcrProcessingException;
import com.foodrisk.nutrition.NutritionAnalysisService;
import com.foodrisk.risk.IngredientRiskService;
import com.foodrisk.scoring.FoodRiskAssessmentService;
import com.foodrisk.service.context.InMemoryAnalysisContextStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * M13 Reliability Tests: Orchestrator Resilience, Idempotency & No-Stuck-PROCESSING.
 */
@ExtendWith(MockitoExtension.class)
class AnalysisOrchestratorM13ResilienceTest {

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
    private FoodAnalysisSession session;
    private MockMultipartFile ingredientFile;
    private MockMultipartFile nutritionFile;

    @BeforeEach
    void setUp() {
        sessionId = UUID.randomUUID();
        session = new FoodAnalysisSession("token-xyz", AnalysisStatus.CREATED, Instant.now().plusSeconds(900));
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
                2 // 2-second timeout for testing timeout behavior quickly
        );

        ingredientFile = new MockMultipartFile("ingredientImage", "label.jpg", "image/jpeg", new byte[]{1, 2, 3});
        nutritionFile = new MockMultipartFile("nutritionImage", "nutrition.png", "image/png", new byte[]{4, 5, 6});
    }

    @Test
    @DisplayName("Idempotency: Duplicate request on PROCESSING session does not re-spawn analysis job")
    void testDuplicateRequestOnProcessingSession() {
        FoodAnalysisSession processingSession = new FoodAnalysisSession("token-xyz", AnalysisStatus.PROCESSING, Instant.now().plusSeconds(900));
        when(sessionService.getActiveSession(sessionId)).thenReturn(processingSession);

        AnalysisStatusResponse response = orchestrator.startAnalysisAsync(sessionId, ingredientFile, nutritionFile);

        assertThat(response.status()).isEqualTo(AnalysisStatus.PROCESSING);
        // Verify no OCR or pipeline actions were invoked
        verify(ocrService, never()).processOcr(any(), any(), any());
        verify(normalizationService, never()).normalize(any(), any());
    }

    @Test
    @DisplayName("Idempotency: Duplicate request on COMPLETED session returns completed status without re-running")
    void testDuplicateRequestOnCompletedSession() {
        FoodAnalysisSession completedSession = new FoodAnalysisSession("token-xyz", AnalysisStatus.COMPLETED, Instant.now().plusSeconds(900));
        when(sessionService.getActiveSession(sessionId)).thenReturn(completedSession);

        AnalysisStatusResponse response = orchestrator.startAnalysisAsync(sessionId, ingredientFile, nutritionFile);

        assertThat(response.status()).isEqualTo(AnalysisStatus.COMPLETED);
        verify(ocrService, never()).processOcr(any(), any(), any());
    }

    @Test
    @DisplayName("No stuck PROCESSING: OCR throws exception -> session marks FAILED with sanitized message")
    void testOcrExceptionTransitionsToFailed() throws Exception {
        when(sessionService.getActiveSession(sessionId)).thenReturn(session);
        when(ocrService.processOcr(eq(sessionId), any(), any()))
                .thenThrow(new RuntimeException("Tesseract crash: native segmentation fault"));

        orchestrator.startAnalysisAsync(sessionId, ingredientFile, nutritionFile);

        // Wait briefly for async completion
        Thread.sleep(500);

        verify(sessionService).updateStatus(eq(session), eq(AnalysisStatus.FAILED));
        AnalysisStatusResponse status = orchestrator.getStatus(sessionId);
        assertThat(status.status()).isEqualTo(AnalysisStatus.FAILED);
        assertThat(status.errorMessage()).isNotNull();
        // Zero internal technical details leaked
        assertThat(status.errorMessage()).doesNotContain("segmentation fault");
    }

    @Test
    @DisplayName("No stuck PROCESSING: Empty OCR text -> transitions to FAILED with low confidence message")
    void testEmptyOcrTextTransitionsToFailed() throws Exception {
        when(sessionService.getActiveSession(sessionId)).thenReturn(session);
        OcrAnalysisResponse emptyOcr = new OcrAnalysisResponse(
                sessionId,
                "PROCESSING",
                new OcrLabelResult("", null, 100, true),
                new OcrLabelResult("   ", null, 100, true),
                200
        );
        when(ocrService.processOcr(eq(sessionId), any(), any())).thenReturn(emptyOcr);

        orchestrator.startAnalysisAsync(sessionId, ingredientFile, nutritionFile);

        Thread.sleep(500);

        verify(sessionService).updateStatus(eq(session), eq(AnalysisStatus.FAILED));
        AnalysisStatusResponse status = orchestrator.getStatus(sessionId);
        assertThat(status.status()).isEqualTo(AnalysisStatus.FAILED);
        assertThat(status.errorMessage()).contains("retake");
    }

    @Test
    @DisplayName("No stuck PROCESSING: Gemini failure -> transitions to FAILED with safe message")
    void testGeminiFailureTransitionsToFailed() throws Exception {
        when(sessionService.getActiveSession(sessionId)).thenReturn(session);
        OcrAnalysisResponse validOcr = new OcrAnalysisResponse(
                sessionId,
                "PROCESSING",
                new OcrLabelResult("Ingredients: Sugar, Salt, Palm Oil", 85.0f, 100, true),
                new OcrLabelResult("Per 100g Energy 500kcal", 88.0f, 100, true),
                200
        );
        when(ocrService.processOcr(eq(sessionId), any(), any())).thenReturn(validOcr);
        when(normalizationService.normalize(eq(sessionId), any(), any()))
                .thenThrow(new NormalizationException("AI_SERVICE_UNAVAILABLE", "503 Service Unavailable"));

        orchestrator.startAnalysisAsync(sessionId, ingredientFile, nutritionFile);

        Thread.sleep(500);

        verify(sessionService).updateStatus(eq(session), eq(AnalysisStatus.FAILED));
        AnalysisStatusResponse status = orchestrator.getStatus(sessionId);
        assertThat(status.status()).isEqualTo(AnalysisStatus.FAILED);
        assertThat(status.errorMessage()).contains("temporarily unavailable");
    }

    @Test
    @DisplayName("Timeout handling: Orchestrator timeout transitions to FAILED with ANALYSIS_TIMEOUT stage")
    void testOrchestratorTimeoutTransitionsToFailed() throws Exception {
        when(sessionService.getActiveSession(sessionId)).thenReturn(session);
        // Simulate a hanging OCR step that exceeds the 2-second test timeout
        when(ocrService.processOcr(eq(sessionId), any(), any())).thenAnswer(invocation -> {
            Thread.sleep(4000);
            return null;
        });

        orchestrator.startAnalysisAsync(sessionId, ingredientFile, nutritionFile);

        // Wait for 2s timeout to fire
        Thread.sleep(2600);

        verify(sessionService, atLeastOnce()).updateStatus(eq(session), eq(AnalysisStatus.FAILED));
        AnalysisStatusResponse status = orchestrator.getStatus(sessionId);
        assertThat(status.status()).isEqualTo(AnalysisStatus.FAILED);
        assertThat(status.currentStage()).isEqualTo("ANALYSIS_TIMEOUT");
        assertThat(status.errorMessage()).contains("timed out");
    }
}
