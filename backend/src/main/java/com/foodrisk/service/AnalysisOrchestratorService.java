package com.foodrisk.service;

import com.foodrisk.cache.CachedProductAnalysis;
import com.foodrisk.cache.ProductAnalysisCache;
import com.foodrisk.cache.ProductFingerprintService;
import com.foodrisk.classification.FoodClassificationResult;
import com.foodrisk.classification.FoodClassificationService;
import com.foodrisk.dto.AnalysisStatusResponse;
import com.foodrisk.dto.NormalizeRequest;
import com.foodrisk.dto.NormalizedFoodData;
import com.foodrisk.dto.OcrAnalysisResponse;
import com.foodrisk.entity.AnalysisStatus;
import com.foodrisk.entity.FoodAnalysisSession;
import com.foodrisk.exception.ErrorCategory;
import com.foodrisk.exception.NormalizationException;
import com.foodrisk.exception.OcrProcessingException;
import com.foodrisk.exception.SessionExpiredException;
import com.foodrisk.exception.SessionNotFoundException;
import com.foodrisk.metrics.AnalysisMetrics;
import com.foodrisk.nutrition.NutritionAnalysisResult;
import com.foodrisk.nutrition.NutritionAnalysisService;
import com.foodrisk.risk.IngredientRiskAnalysisResult;
import com.foodrisk.risk.IngredientRiskService;
import com.foodrisk.scoring.FoodRiskAssessment;
import com.foodrisk.scoring.FoodRiskAssessmentService;
import com.foodrisk.service.context.AnalysisContextStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * M11/M12 Internal Analysis Orchestrator Service.
 *
 * Coordinates the full analysis pipeline behind a unified endpoint:
 * 1. Image validation
 * 2. OCR text extraction
 * 3. AI normalization
 * 4. Product fingerprint cache check (24h duplicate product cache)
 * 5. M7 Product categorization
 * 6. M8 Ingredient/additive risk analysis
 * 7. M9 Nutrition standards evaluation
 * 8. M10 Food Awareness Score synthesis
 *
 * Enforces timeout protection (default: 60s). On failure, transitions session to FAILED
 * with a sanitized message and never leaves it stuck in PROCESSING.
 */
@Service
public class AnalysisOrchestratorService {

    private static final Logger log = LoggerFactory.getLogger(AnalysisOrchestratorService.class);

    private final AnalysisSessionService sessionService;
    private final OcrService ocrService;
    private final FoodNormalizationService normalizationService;
    private final AnalysisContextStore contextStore;
    private final FoodClassificationService classificationService;
    private final IngredientRiskService ingredientRiskService;
    private final NutritionAnalysisService nutritionService;
    private final FoodRiskAssessmentService assessmentService;
    private final ProductFingerprintService fingerprintService;
    private final ProductAnalysisCache analysisCache;
    private final AnalysisMetrics metrics;
    private final int timeoutSeconds;

    private final Executor orchestrationExecutor;
    private final Map<UUID, StageInfo> sessionStages = new ConcurrentHashMap<>();
    private final Set<UUID> runningSessions = ConcurrentHashMap.newKeySet();

    public static class StageInfo {
        private String stage;
        private String errorMessage;
        private final Instant startedAt;

        public StageInfo(String stage) {
            this.stage = stage;
            this.startedAt = Instant.now();
        }

        public String getStage() {
            return stage;
        }

        public void setStage(String stage) {
            this.stage = stage;
        }

        public String getErrorMessage() {
            return errorMessage;
        }

        public void setErrorMessage(String errorMessage) {
            this.errorMessage = errorMessage;
        }

        public Instant getStartedAt() {
            return startedAt;
        }
    }

    @Autowired
    public AnalysisOrchestratorService(
            AnalysisSessionService sessionService,
            OcrService ocrService,
            FoodNormalizationService normalizationService,
            AnalysisContextStore contextStore,
            FoodClassificationService classificationService,
            IngredientRiskService ingredientRiskService,
            NutritionAnalysisService nutritionService,
            FoodRiskAssessmentService assessmentService,
            ProductFingerprintService fingerprintService,
            ProductAnalysisCache analysisCache,
            @Autowired(required = false) AnalysisMetrics metrics,
            @Qualifier("analysisTaskExecutor") @Autowired(required = false) Executor orchestrationExecutor,
            @Value("${analysis.orchestrator.timeout-seconds:60}") int timeoutSeconds
    ) {
        this.sessionService = sessionService;
        this.ocrService = ocrService;
        this.normalizationService = normalizationService;
        this.contextStore = contextStore;
        this.classificationService = classificationService;
        this.ingredientRiskService = ingredientRiskService;
        this.nutritionService = nutritionService;
        this.assessmentService = assessmentService;
        this.fingerprintService = fingerprintService;
        this.analysisCache = analysisCache;
        this.metrics = metrics;
        this.orchestrationExecutor = orchestrationExecutor != null ? orchestrationExecutor : Executors.newFixedThreadPool(25);
        this.timeoutSeconds = timeoutSeconds;
    }

    public AnalysisOrchestratorService(
            AnalysisSessionService sessionService,
            OcrService ocrService,
            FoodNormalizationService normalizationService,
            AnalysisContextStore contextStore,
            FoodClassificationService classificationService,
            IngredientRiskService ingredientRiskService,
            NutritionAnalysisService nutritionService,
            FoodRiskAssessmentService assessmentService,
            ProductFingerprintService fingerprintService,
            ProductAnalysisCache analysisCache,
            int timeoutSeconds
    ) {
        this(sessionService, ocrService, normalizationService, contextStore, classificationService,
             ingredientRiskService, nutritionService, assessmentService, fingerprintService, analysisCache,
             null, null, timeoutSeconds);
    }

    /**
     * Initiates asynchronous end-to-end analysis for the session.
     * Enforces duplicate request safety (idempotency): if already PROCESSING or COMPLETED,
     * returns current status without re-spawning a pipeline.
     */
    public AnalysisStatusResponse startAnalysisAsync(
            UUID sessionId,
            MultipartFile ingredientImage,
            MultipartFile nutritionImage
    ) {
        FoodAnalysisSession session = sessionService.getActiveSession(sessionId);

        // Idempotency: avoid redundant execution if already running or completed
        if (session.getStatus() == AnalysisStatus.PROCESSING ||
            session.getStatus() == AnalysisStatus.COMPLETED ||
            !runningSessions.add(sessionId)) {
            log.info("Session {} is already in status {} (in-flight: {}), returning current status without re-spawning pipeline",
                    sessionId, session.getStatus(), runningSessions.contains(sessionId));
            return getStatus(sessionId);
        }

        sessionService.updateStatus(session, AnalysisStatus.PROCESSING);
        updateStage(sessionId, "IMAGE_VALIDATION");

        CompletableFuture.runAsync(() -> {
            try {
                executeAnalysisPipeline(sessionId, ingredientImage, nutritionImage);
            } catch (Exception e) {
                log.error("Analysis pipeline failed for session {}: {}", sessionId, e.getMessage(), e);
                handlePipelineFailure(sessionId, sanitizeErrorMessage(e));
            }
        }, orchestrationExecutor).orTimeout(timeoutSeconds, TimeUnit.SECONDS)
                .exceptionally(ex -> {
                    if (ex instanceof TimeoutException || (ex.getCause() != null && ex.getCause() instanceof TimeoutException)) {
                        log.error("Analysis pipeline timed out after {} seconds for session {}", timeoutSeconds, sessionId);
                        handlePipelineTimeout(sessionId);
                    } else {
                        log.error("Pipeline unexpected error for session {}: {}", sessionId, ex.getMessage(), ex);
                        handlePipelineFailure(sessionId, sanitizeErrorMessage(ex));
                    }
                    return null;
                });

        return new AnalysisStatusResponse(
                sessionId,
                AnalysisStatus.PROCESSING,
                "IMAGE_VALIDATION",
                null,
                session.getExpiresAt()
        );
    }

    /**
     * Executes analysis synchronously (useful for tests and sync requests).
     */
    public FoodRiskAssessment executeAnalysisSync(
            UUID sessionId,
            MultipartFile ingredientImage,
            MultipartFile nutritionImage
    ) {
        FoodAnalysisSession session = sessionService.getActiveSession(sessionId);
        sessionService.updateStatus(session, AnalysisStatus.PROCESSING);
        try {
            return executeAnalysisPipeline(sessionId, ingredientImage, nutritionImage);
        } catch (Exception e) {
            log.error("Sync analysis pipeline failed for session {}: {}", sessionId, e.getMessage(), e);
            handlePipelineFailure(sessionId, sanitizeErrorMessage(e));
            throw new RuntimeException(sanitizeErrorMessage(e), e);
        }
    }

    /**
     * Retrieves current status and execution stage of a session.
     */
    public AnalysisStatusResponse getStatus(UUID sessionId) {
        FoodAnalysisSession session;
        try {
            session = sessionService.getActiveSession(sessionId);
        } catch (SessionExpiredException e) {
            return new AnalysisStatusResponse(sessionId, AnalysisStatus.EXPIRED, "EXPIRED", e.getMessage(), Instant.now());
        } catch (SessionNotFoundException e) {
            throw e;
        }

        StageInfo info = sessionStages.get(sessionId);
        String currentStage = info != null ? info.getStage() : session.getStatus().name();
        String error = info != null ? info.getErrorMessage() : null;

        AnalysisStatus effectiveStatus = session.getStatus();
        if (("FAILED".equalsIgnoreCase(currentStage) || "ANALYSIS_TIMEOUT".equalsIgnoreCase(currentStage))
                && effectiveStatus != AnalysisStatus.FAILED) {
            effectiveStatus = AnalysisStatus.FAILED;
        }

        return new AnalysisStatusResponse(
                sessionId,
                effectiveStatus,
                currentStage,
                error,
                session.getExpiresAt()
        );
    }

    private FoodRiskAssessment executeAnalysisPipeline(
            UUID sessionId,
            MultipartFile ingredientImage,
            MultipartFile nutritionImage
    ) {
        Instant pipelineStart = Instant.now();

        // 1. Image validation
        updateStage(sessionId, "IMAGE_VALIDATION");
        if ((ingredientImage == null || ingredientImage.isEmpty()) && (nutritionImage == null || nutritionImage.isEmpty())) {
            throw new IllegalArgumentException("At least one food packaging image (ingredients or nutrition) must be provided.");
        }

        // 2. OCR processing
        updateStage(sessionId, "OCR_PROCESSING");
        log.info("Session {}: Starting OCR text extraction", sessionId);
        Instant ocrStart = Instant.now();
        OcrAnalysisResponse ocrResponse = ocrService.processOcr(sessionId, ingredientImage, nutritionImage);
        if (metrics != null) {
            metrics.recordOcrDuration(Duration.between(ocrStart, Instant.now()));
        }

        // 3. AI normalization
        updateStage(sessionId, "AI_NORMALIZATION");
        log.info("Session {}: Starting AI text normalization", sessionId);
        String ingredientText = ocrResponse.ingredients() != null ? ocrResponse.ingredients().rawText() : null;
        String nutritionText = ocrResponse.nutrition() != null ? ocrResponse.nutrition().rawText() : null;

        // OCR Quality & Confidence check
        boolean hasExtractedText = (ingredientText != null && !ingredientText.isBlank()) ||
                                   (nutritionText != null && !nutritionText.isBlank());
        if (!hasExtractedText) {
            throw new OcrProcessingException(
                    ErrorCategory.OCR_LOW_CONFIDENCE,
                    "We couldn't read the label clearly. Please capture a sharper image with the ingredient list fully visible."
            );
        }

        boolean lowConfidence = (ocrResponse.ingredients() != null && ocrResponse.ingredients().present() &&
                ocrResponse.ingredients().confidence() != null && ocrResponse.ingredients().confidence() > 0.0f && ocrResponse.ingredients().confidence() < 20.0f) &&
            (ocrResponse.nutrition() == null || !ocrResponse.nutrition().present() ||
                (ocrResponse.nutrition().confidence() != null && ocrResponse.nutrition().confidence() > 0.0f && ocrResponse.nutrition().confidence() < 20.0f));
        if (lowConfidence) {
            throw new OcrProcessingException(
                    ErrorCategory.OCR_LOW_CONFIDENCE,
                    "We couldn't read the label clearly. Please capture a sharper image with the ingredient list fully visible."
            );
        }

        // 3. AI normalization
        updateStage(sessionId, "AI_NORMALIZATION");
        log.info("Session {}: Starting AI text normalization", sessionId);
        NormalizeRequest normRequest = new NormalizeRequest(
                ingredientText,
                nutritionText
        );
        Instant geminiStart = Instant.now();
        NormalizedFoodData normalizedData = normalizationService.normalize(sessionId, normRequest);
        if (metrics != null) {
            metrics.recordGeminiDuration(Duration.between(geminiStart, Instant.now()));
        }
        contextStore.storeNormalizedFoodData(sessionId, normalizedData);

        // 4. Duplicate product fingerprint cache check
        String fingerprint = fingerprintService.computeFingerprint(normalizedData);
        Optional<CachedProductAnalysis> cachedOpt = analysisCache.get(fingerprint);
        if (cachedOpt.isPresent()) {
            CachedProductAnalysis cached = cachedOpt.get();
            log.info("Session {}: Product cache hit for fingerprint {}", sessionId, fingerprint);
            if (metrics != null) {
                metrics.recordCacheHit();
            }
            contextStore.storeClassificationResult(sessionId, cached.getClassificationResult());
            contextStore.storeIngredientRiskResult(sessionId, cached.getIngredientRiskResult());
            contextStore.storeNutritionResult(sessionId, cached.getNutritionResult());
            contextStore.storeFoodRiskAssessment(sessionId, cached.getFoodRiskAssessment());

            completeSession(sessionId);
            if (metrics != null) {
                metrics.recordAnalysisDuration(Duration.between(pipelineStart, Instant.now()));
            }
            return cached.getFoodRiskAssessment();
        }

        if (metrics != null) {
            metrics.recordCacheMiss();
        }

        // 5. Product classification (M7)
        updateStage(sessionId, "PRODUCT_CLASSIFICATION");
        log.info("Session {}: Classifying product category", sessionId);
        FoodClassificationResult classification = classificationService.classifyProduct(sessionId);

        // 6. Ingredient risk analysis (M8)
        updateStage(sessionId, "INGREDIENT_RISK_ANALYSIS");
        log.info("Session {}: Analyzing ingredient & additive risks", sessionId);
        IngredientRiskAnalysisResult ingredientRisk = ingredientRiskService.analyzeIngredientRisk(sessionId);

        // 7. Nutrition analysis (M9)
        updateStage(sessionId, "NUTRITION_ANALYSIS");
        log.info("Session {}: Analyzing nutrition against standards", sessionId);
        NutritionAnalysisResult nutrition = nutritionService.analyzeNutrition(sessionId);

        // 8. Food Awareness Score synthesis (M10)
        updateStage(sessionId, "SCORE_SYNTHESIS");
        log.info("Session {}: Synthesizing Food Awareness Assessment", sessionId);
        FoodRiskAssessment assessment = assessmentService.getAssessment(sessionId);

        // Cache the result for identical products (if eligible)
        analysisCache.put(fingerprint, normalizedData, classification, ingredientRisk, nutrition, assessment);

        completeSession(sessionId);
        if (metrics != null) {
            metrics.recordAnalysisDuration(Duration.between(pipelineStart, Instant.now()));
        }
        return assessment;
    }

    private void updateStage(UUID sessionId, String stage) {
        sessionStages.compute(sessionId, (id, existing) -> {
            if (existing == null) {
                return new StageInfo(stage);
            }
            existing.setStage(stage);
            return existing;
        });
    }

    private void completeSession(UUID sessionId) {
        runningSessions.remove(sessionId);
        updateStage(sessionId, "COMPLETED");
        try {
            FoodAnalysisSession session = sessionService.getActiveSession(sessionId);
            sessionService.updateStatus(session, AnalysisStatus.COMPLETED);
        } catch (Exception e) {
            log.warn("Could not update session {} status to COMPLETED: {}", sessionId, e.getMessage());
            try {
                sessionService.updateStatus(sessionId, AnalysisStatus.COMPLETED);
            } catch (Exception ignored) {}
        }
        log.info("Session {}: Orchestrated analysis completed successfully", sessionId);
    }

    private void handlePipelineFailure(UUID sessionId, String sanitizedError) {
        runningSessions.remove(sessionId);
        if (metrics != null) {
            metrics.recordFailure();
        }

        sessionStages.compute(sessionId, (id, existing) -> {
            StageInfo info = existing != null ? existing : new StageInfo("FAILED");
            info.setStage("FAILED");
            info.setErrorMessage(sanitizedError);
            return info;
        });

        try {
            FoodAnalysisSession session = sessionService.getActiveSession(sessionId);
            sessionService.updateStatus(session, AnalysisStatus.FAILED);
        } catch (Exception e) {
            log.warn("Could not update session {} status to FAILED: {}", sessionId, e.getMessage());
            try {
                sessionService.updateStatus(sessionId, AnalysisStatus.FAILED);
            } catch (Exception ignored) {}
        }
    }

    private void handlePipelineTimeout(UUID sessionId) {
        runningSessions.remove(sessionId);
        if (metrics != null) {
            metrics.recordFailure();
        }

        String timeoutMsg = "Analysis timed out after " + timeoutSeconds + " seconds. Please try again with clearer packaging images.";
        sessionStages.compute(sessionId, (id, existing) -> {
            StageInfo info = existing != null ? existing : new StageInfo("ANALYSIS_TIMEOUT");
            info.setStage("ANALYSIS_TIMEOUT");
            info.setErrorMessage(timeoutMsg);
            return info;
        });

        try {
            FoodAnalysisSession session = sessionService.getActiveSession(sessionId);
            sessionService.updateStatus(session, AnalysisStatus.FAILED);
        } catch (Exception e) {
            log.warn("Could not update session {} status to FAILED on timeout: {}", sessionId, e.getMessage());
            try {
                sessionService.updateStatus(sessionId, AnalysisStatus.FAILED);
            } catch (Exception ignored) {}
        }
    }

    private String sanitizeErrorMessage(Throwable ex) {
        if (ex == null) {
            return "An unexpected error occurred during food label analysis. Please try again.";
        }
        Throwable root = ex.getCause() != null ? ex.getCause() : ex;
        if (root instanceof NormalizationException || ex instanceof NormalizationException) {
            return "Label normalization temporarily unavailable. Please try again in a few moments.";
        }
        if (root instanceof OcrProcessingException) {
            return root.getMessage();
        }
        if (ex instanceof OcrProcessingException) {
            return ex.getMessage();
        }
        return sanitizeErrorMessage(ex.getMessage());
    }

    private String sanitizeErrorMessage(String rawMessage) {
        if (rawMessage == null || rawMessage.isBlank()) {
            return "An unexpected error occurred during food label analysis. Please try again.";
        }
        if (rawMessage.contains("couldn't read the label") || rawMessage.contains("sharper image")) {
            return rawMessage;
        }
        if (rawMessage.contains("timed out") || rawMessage.contains("Timeout")) {
            return "Analysis timed out. Please check image clarity and try again.";
        }
        if (rawMessage.contains("corrupt") || rawMessage.contains("Invalid image") || rawMessage.contains("Unsupported")) {
            return "Unable to process the image. Please upload a clear JPEG, PNG, or WebP photo.";
        }
        if (rawMessage.contains("GEMINI") || rawMessage.contains("normalization") || rawMessage.contains("AI")
                || rawMessage.contains("temporarily unavailable") || rawMessage.contains("503") || rawMessage.contains("Service Unavailable")) {
            return "Label normalization temporarily unavailable. Please try again in a few moments.";
        }
        return "Unable to complete food risk assessment. Please check packaging images and retry.";
    }
}
