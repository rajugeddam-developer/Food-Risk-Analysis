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
import com.foodrisk.exception.ImageSizeLimitExceededException;
import com.foodrisk.exception.InvalidImageException;
import com.foodrisk.exception.NormalizationException;
import com.foodrisk.exception.OcrProcessingException;
import com.foodrisk.exception.ProductMismatchException;
import com.foodrisk.exception.SessionExpiredException;
import com.foodrisk.exception.SessionNotFoundException;
import com.foodrisk.metrics.AnalysisMetrics;
import com.foodrisk.nutrition.NutritionAnalysisResult;
import com.foodrisk.nutrition.NutritionAnalysisService;
import com.foodrisk.ocr.BufferedImageInput;
import com.foodrisk.ocr.ImageValidator;
import com.foodrisk.ocr.OcrExtractionValidator;
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
import java.util.ArrayList;
import java.util.List;
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
 * 1. Synchronous image validation & in-memory buffering (prevents servlet lifecycle races)
 * 2. OCR text extraction & regional quality assessment
 * 3. OCR sufficiency validation (evaluates structural integrity & partial inputs)
 * 4. AI normalization (Gemini)
 * 5. Product fingerprint cache check (24h duplicate product cache)
 * 6. M7 Product categorization
 * 7. M8 Ingredient/additive risk analysis
 * 8. M9 Nutrition standards evaluation
 * 9. M10 Food Awareness Score synthesis
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
    private final OcrExtractionValidator extractionValidator;
    private final ImageValidator imageValidator;
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
            @Autowired(required = false) OcrExtractionValidator extractionValidator,
            @Autowired(required = false) ImageValidator imageValidator,
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
        this.extractionValidator = extractionValidator != null ? extractionValidator : new OcrExtractionValidator();
        this.imageValidator = imageValidator != null ? imageValidator : new ImageValidator();
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
            AnalysisMetrics metrics,
            Executor orchestrationExecutor,
            int timeoutSeconds
    ) {
        this(sessionService, ocrService, normalizationService, contextStore, classificationService,
             ingredientRiskService, nutritionService, assessmentService, fingerprintService, analysisCache,
             new OcrExtractionValidator(), new ImageValidator(), metrics, orchestrationExecutor, timeoutSeconds);
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
             new OcrExtractionValidator(), new ImageValidator(), null, null, timeoutSeconds);
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

        // 1. Synchronously validate upload before async handoff
        boolean ingEmpty = ingredientImage == null || ingredientImage.isEmpty();
        boolean nutEmpty = nutritionImage == null || nutritionImage.isEmpty();
        if (ingEmpty && nutEmpty) {
            runningSessions.remove(sessionId);
            throw new InvalidImageException("At least one packaging label image (ingredients or nutrition) must be provided.");
        }

        // 2. Synchronously capture image bytes into immutable payloads on the HTTP thread
        // This permanently fixes the Tomcat servlet-bound temp file recycling bug
        final BufferedImageInput safeIngredientInput;
        final BufferedImageInput safeNutritionInput;
        try {
            safeIngredientInput = !ingEmpty ? BufferedImageInput.from(ingredientImage) : null;
            safeNutritionInput = !nutEmpty ? BufferedImageInput.from(nutritionImage) : null;
        } catch (InvalidImageException | ImageSizeLimitExceededException e) {
            runningSessions.remove(sessionId);
            handlePipelineFailure(sessionId, sanitizeErrorMessage(e));
            return new AnalysisStatusResponse(
                    sessionId,
                    AnalysisStatus.FAILED,
                    "FAILED",
                    sanitizeErrorMessage(e),
                    session.getExpiresAt()
            );
        } catch (Exception e) {
            log.error("Failed to buffer uploaded images for session {}: {}", sessionId, e.getMessage());
            runningSessions.remove(sessionId);
            handlePipelineFailure(sessionId, "Failed to read uploaded packaging image data. Please retry.");
            return new AnalysisStatusResponse(
                    sessionId,
                    AnalysisStatus.FAILED,
                    "FAILED",
                    "Failed to read uploaded packaging image data. Please retry.",
                    session.getExpiresAt()
            );
        }

        final MultipartFile safeIngredientImage = safeIngredientInput != null ? safeIngredientInput.toMultipartFile("ingredientImage") : null;
        final MultipartFile safeNutritionImage = safeNutritionInput != null ? safeNutritionInput.toMultipartFile("nutritionImage") : null;

        sessionService.updateStatus(session, AnalysisStatus.PROCESSING);
        updateStage(sessionId, "IMAGE_VALIDATION");

        CompletableFuture.runAsync(() -> {
            try {
                executeAnalysisPipeline(sessionId, safeIngredientImage, safeNutritionImage);
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
            if (e instanceof com.foodrisk.exception.ProductMismatchException pme) {
                throw pme;
            }
            if (e instanceof com.foodrisk.exception.InvalidImageException iie) {
                throw iie;
            }
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
        boolean hasIng = ingredientImage != null && !ingredientImage.isEmpty();
        boolean hasNut = nutritionImage != null && !nutritionImage.isEmpty();

        if (!hasIng && !hasNut) {
            throw new InvalidImageException("At least one packaging label image (ingredients or nutrition) must be provided.");
        }

        // Single-image dual scan: If only 1 image was uploaded, examine it for both ingredients and nutrition
        MultipartFile effectiveIngImage = ingredientImage;
        MultipartFile effectiveNutImage = nutritionImage;
        if (hasIng && !hasNut) {
            effectiveNutImage = ingredientImage;
        } else if (hasNut && !hasIng) {
            effectiveIngImage = nutritionImage;
        }

        // 2. OCR processing
        updateStage(sessionId, "OCR_PROCESSING");
        log.info("Session {}: Starting OCR text extraction", sessionId);
        Instant ocrStart = Instant.now();
        OcrAnalysisResponse ocrResponse = ocrService.processOcr(sessionId, effectiveIngImage, effectiveNutImage);
        if (metrics != null) {
            metrics.recordOcrDuration(Duration.between(ocrStart, Instant.now()));
        }

        // 3. OCR Sufficiency & Semantic Validation
        OcrExtractionValidator.OverallValidationResult sufficiency =
                extractionValidator.evaluateSufficiency(
                        ocrResponse.ingredients(), hasIng,
                        ocrResponse.nutrition(), hasNut
                );

        if (!sufficiency.canProceed()) {
            log.info("Session {}: OCR extraction rejected: {}", sessionId, sufficiency.userGuidance());
            throw new OcrProcessingException(
                    sufficiency.category() != null ? sufficiency.category() : ErrorCategory.OCR_INSUFFICIENT,
                    sufficiency.userGuidance()
            );
        }

        // Support partial OCR states:
        // - If ingredients readable and nutrition insufficient -> proceed with ingredients, nutrition is null
        // - If ingredients insufficient and nutrition readable -> ingredients null, nutrition proceeds
        // - If both readable -> both proceed
        String ingredientText = (sufficiency.ingredientsUsable() && ocrResponse.ingredients() != null)
                ? ocrResponse.ingredients().rawText()
                : null;
        String nutritionText = (sufficiency.nutritionUsable() && ocrResponse.nutrition() != null)
                ? ocrResponse.nutrition().rawText()
                : null;

        log.info("Session {}: OCR text sufficiency verified (ingredientsUsable={}, nutritionUsable={})",
                sessionId, sufficiency.ingredientsUsable(), sufficiency.nutritionUsable());

        // 4. AI normalization (Gemini Multimodal Vision + structured entity extraction)
        updateStage(sessionId, "AI_NORMALIZATION");
        log.info("Session {}: Starting AI text normalization with Gemini Vision", sessionId);
        NormalizeRequest normRequest = new NormalizeRequest(
                ingredientText,
                nutritionText
        );

        List<com.foodrisk.gemini.GeminiClient.ImagePayload> imagePayloads = new ArrayList<>();
        try {
            if (ingredientImage != null && !ingredientImage.isEmpty()) {
                imagePayloads.add(new com.foodrisk.gemini.GeminiClient.ImagePayload(
                        ingredientImage.getBytes(),
                        ingredientImage.getContentType()
                ));
            }
            if (nutritionImage != null && !nutritionImage.isEmpty() && nutritionImage != ingredientImage) {
                imagePayloads.add(new com.foodrisk.gemini.GeminiClient.ImagePayload(
                        nutritionImage.getBytes(),
                        nutritionImage.getContentType()
                ));
            }
        } catch (Exception ex) {
            log.warn("Could not buffer packaging image bytes for Gemini Vision: {}", ex.getMessage());
        }

        Instant geminiStart = Instant.now();
        NormalizedFoodData normalizedData = normalizationService.normalize(sessionId, normRequest, imagePayloads);
        if (metrics != null) {
            metrics.recordGeminiDuration(Duration.between(geminiStart, Instant.now()));
        }

        // Strict Same-Product Consistency Check
        if (Boolean.FALSE.equals(normalizedData.productMatchVerified())) {
            String mismatchMsg = normalizedData.mismatchReason() != null && !normalizedData.mismatchReason().isBlank()
                    ? normalizedData.mismatchReason()
                    : "Product Mismatch Detected: The ingredients label and nutrition facts table appear to belong to two different products. Please upload packaging photos from the same food product.";
            log.warn("Session {}: Product mismatch detected: {}", sessionId, mismatchMsg);
            throw new com.foodrisk.exception.ProductMismatchException(mismatchMsg, normalizedData.mismatchReason());
        }

        contextStore.storeNormalizedFoodData(sessionId, normalizedData);

        // 5. Duplicate product fingerprint cache check
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

        // 6. Product classification (M7)
        updateStage(sessionId, "PRODUCT_CLASSIFICATION");
        log.info("Session {}: Classifying product category", sessionId);
        FoodClassificationResult classification = classificationService.classifyProduct(sessionId);

        // 7. Ingredient risk analysis (M8)
        updateStage(sessionId, "INGREDIENT_RISK_ANALYSIS");
        log.info("Session {}: Analyzing ingredient & additive risks", sessionId);
        IngredientRiskAnalysisResult ingredientRisk = ingredientRiskService.analyzeIngredientRisk(sessionId);

        // 8. Nutrition analysis (M9)
        updateStage(sessionId, "NUTRITION_ANALYSIS");
        log.info("Session {}: Analyzing nutrition against standards", sessionId);
        NutritionAnalysisResult nutrition = nutritionService.analyzeNutrition(sessionId);

        // 9. Food Awareness Score synthesis (M10)
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

        if (root instanceof ProductMismatchException mismatchEx) {
            return mismatchEx.getMessage();
        }
        if (ex instanceof ProductMismatchException mismatchEx) {
            return mismatchEx.getMessage();
        }
        if (root instanceof OcrProcessingException ocrEx) {
            return ocrEx.getMessage();
        }
        if (ex instanceof OcrProcessingException ocrEx) {
            return ocrEx.getMessage();
        }
        if (root instanceof InvalidImageException imgEx) {
            return imgEx.getMessage();
        }
        if (ex instanceof InvalidImageException imgEx) {
            return imgEx.getMessage();
        }
        if (root instanceof ImageSizeLimitExceededException || ex instanceof ImageSizeLimitExceededException) {
            return "Image file size exceeds the 10MB limit. Please upload a smaller photo.";
        }
        if (root instanceof NormalizationException || ex instanceof NormalizationException) {
            return "Label normalization temporarily unavailable. Please try again in a few moments.";
        }
        return sanitizeErrorMessage(ex.getMessage());
    }

    private String sanitizeErrorMessage(String rawMessage) {
        if (rawMessage == null || rawMessage.isBlank()) {
            return "An unexpected error occurred during food label analysis. Please try again.";
        }
        // Preserve specific user retake guidance and mismatch alerts
        if (rawMessage.contains("Product Mismatch") || rawMessage.contains("mismatch")
                || rawMessage.contains("different product") || rawMessage.contains("same product")
                || rawMessage.contains("same food product")) {
            return rawMessage;
        }
        if (rawMessage.contains("couldn't read") || rawMessage.contains("Please retake")
                || rawMessage.contains("capture the") || rawMessage.contains("blurry")
                || rawMessage.contains("focus") || rawMessage.contains("contrast")
                || rawMessage.contains("resolution") || rawMessage.contains("ingredients")
                || rawMessage.contains("nutrition") || rawMessage.contains("lighting")) {
            return rawMessage;
        }
        if (rawMessage.contains("timed out") || rawMessage.contains("Timeout")) {
            return "Analysis timed out. Please check image clarity and try again.";
        }
        if (rawMessage.contains("empty or missing")) {
            return "Uploaded packaging photo is empty or missing. Please select photos and retry.";
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
