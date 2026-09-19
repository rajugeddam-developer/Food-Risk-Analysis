package com.foodrisk.controller;

import com.foodrisk.dto.AnalysisSessionResponse;
import com.foodrisk.dto.OcrAnalysisResponse;
import com.foodrisk.entity.User;
import com.foodrisk.repository.UserRepository;
import com.foodrisk.service.AnalysisSessionService;
import com.foodrisk.service.OcrService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

/**
 * Controller handling transient food analysis sessions and offline OCR processing.
 *
 * Enforces privacy guarantees:
 * - Never returns or persists image binaries.
 * - Raw OCR responses are returned directly to the client without permanent database storage.
 * - Supports correlation tracing via X-Request-ID.
 */
@RestController
@RequestMapping("/api/analysis")
@Tag(name = "Analysis", description = "Privacy-first food label analysis coordination and OCR extraction")
public class AnalysisController {

    private static final Logger log = LoggerFactory.getLogger(AnalysisController.class);
    private static final String REQUEST_ID_HEADER = "X-Request-ID";

    private final AnalysisSessionService sessionService;
    private final OcrService ocrService;
    private final com.foodrisk.service.FoodNormalizationService normalizationService;
    private final com.foodrisk.service.context.AnalysisContextStore contextStore;
    private final com.foodrisk.classification.FoodClassificationService classificationService;
    private final com.foodrisk.risk.IngredientRiskService ingredientRiskService;
    private final com.foodrisk.nutrition.NutritionAnalysisService nutritionService;
    private final com.foodrisk.scoring.FoodRiskAssessmentService assessmentService;
    private final com.foodrisk.service.AnalysisOrchestratorService orchestratorService;
    private final com.foodrisk.service.FindingExplanationService explanationService;
    private final UserRepository userRepository;

    @org.springframework.beans.factory.annotation.Autowired
    public AnalysisController(
            AnalysisSessionService sessionService,
            OcrService ocrService,
            com.foodrisk.service.FoodNormalizationService normalizationService,
            com.foodrisk.service.context.AnalysisContextStore contextStore,
            com.foodrisk.classification.FoodClassificationService classificationService,
            com.foodrisk.risk.IngredientRiskService ingredientRiskService,
            com.foodrisk.nutrition.NutritionAnalysisService nutritionService,
            com.foodrisk.scoring.FoodRiskAssessmentService assessmentService,
            com.foodrisk.service.AnalysisOrchestratorService orchestratorService,
            com.foodrisk.service.FindingExplanationService explanationService,
            UserRepository userRepository
    ) {
        this.sessionService = sessionService;
        this.ocrService = ocrService;
        this.normalizationService = normalizationService;
        this.contextStore = contextStore;
        this.classificationService = classificationService;
        this.ingredientRiskService = ingredientRiskService;
        this.nutritionService = nutritionService;
        this.assessmentService = assessmentService;
        this.orchestratorService = orchestratorService;
        this.explanationService = explanationService;
        this.userRepository = userRepository;
    }

    public AnalysisController(
            AnalysisSessionService sessionService,
            OcrService ocrService,
            com.foodrisk.service.FoodNormalizationService normalizationService,
            com.foodrisk.service.context.AnalysisContextStore contextStore,
            com.foodrisk.classification.FoodClassificationService classificationService,
            com.foodrisk.risk.IngredientRiskService ingredientRiskService,
            com.foodrisk.nutrition.NutritionAnalysisService nutritionService,
            com.foodrisk.scoring.FoodRiskAssessmentService assessmentService,
            com.foodrisk.service.AnalysisOrchestratorService orchestratorService,
            UserRepository userRepository
    ) {
        this(sessionService, ocrService, normalizationService, contextStore, classificationService,
                ingredientRiskService, nutritionService, assessmentService, orchestratorService, null, userRepository);
    }

    @PostMapping({"/start", "/session"})
    @Operation(summary = "Create a new food analysis session", description = "Initiates a transient analysis session with a 15-minute TTL. Supports both authenticated and guest scans.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Session created successfully",
                    content = @Content(schema = @Schema(implementation = AnalysisSessionResponse.class))),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<AnalysisSessionResponse> createSession(
            Authentication authentication,
            @RequestHeader(value = REQUEST_ID_HEADER, required = false) String requestId,
            HttpServletResponse response
    ) {
        String resolvedRequestId = setupRequestId(requestId, response);
        try {
            User user = null;
            if (authentication != null && authentication.isAuthenticated() && !"anonymousUser".equals(authentication.getPrincipal())) {
                user = userRepository.findByEmail(authentication.getName()).orElse(null);
            }

            AnalysisSessionResponse sessionResponse = sessionService.createSession(user);
            log.info("[{}] Created analysis session {}", resolvedRequestId, sessionResponse.sessionId());
            return ResponseEntity.status(HttpStatus.CREATED).body(sessionResponse);
        } finally {
            MDC.remove("requestId");
        }
    }

    @PostMapping(value = "/{sessionId}/analyze", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Execute full end-to-end food analysis orchestrator",
            description = "Coordinates image validation, OCR, Gemini normalization, product categorization, ingredient risk, nutrition evaluation, and score synthesis. Returns 202 Accepted for polling or sync assessment with ?sync=true.")
    @ApiResponses({
            @ApiResponse(responseCode = "202", description = "Analysis initiated, track with /status",
                    content = @Content(schema = @Schema(implementation = com.foodrisk.dto.AnalysisStatusResponse.class))),
            @ApiResponse(responseCode = "200", description = "Analysis completed synchronously",
                    content = @Content(schema = @Schema(implementation = com.foodrisk.scoring.FoodRiskAssessment.class))),
            @ApiResponse(responseCode = "400", description = "Missing packaging images or invalid input"),
            @ApiResponse(responseCode = "404", description = "Session not found"),
            @ApiResponse(responseCode = "410", description = "Session expired"),
            @ApiResponse(responseCode = "429", description = "Rate limit exceeded (max 10/min)")
    })
    public ResponseEntity<?> analyze(
            @Parameter(description = "UUID of the session") @PathVariable UUID sessionId,
            @RequestParam(value = "ingredientImage", required = false) MultipartFile ingredientImage,
            @RequestParam(value = "nutritionImage", required = false) MultipartFile nutritionImage,
            @RequestParam(value = "sync", defaultValue = "false") boolean sync,
            Authentication authentication,
            @RequestHeader(value = REQUEST_ID_HEADER, required = false) String requestId,
            HttpServletResponse response
    ) {
        String resolvedRequestId = setupRequestId(requestId, response);
        try {
            sessionService.validateSessionAccess(sessionId, authentication);
            log.info("[{}] Received analysis orchestration request for session {} (sync={})", resolvedRequestId, sessionId, sync);
            if (sync) {
                com.foodrisk.scoring.FoodRiskAssessment assessment = orchestratorService.executeAnalysisSync(sessionId, ingredientImage, nutritionImage);
                return ResponseEntity.ok(assessment);
            } else {
                com.foodrisk.dto.AnalysisStatusResponse statusResponse = orchestratorService.startAnalysisAsync(sessionId, ingredientImage, nutritionImage);
                return ResponseEntity.status(HttpStatus.ACCEPTED).body(statusResponse);
            }
        } finally {
            MDC.remove("requestId");
        }
    }

    @GetMapping("/{sessionId}/status")
    @Operation(summary = "Get analysis lifecycle status and stage",
            description = "Fetches current analysis stage (IMAGE_VALIDATION, OCR_PROCESSING, AI_NORMALIZATION, etc.) and completion status.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Session status retrieved",
                    content = @Content(schema = @Schema(implementation = com.foodrisk.dto.AnalysisStatusResponse.class))),
            @ApiResponse(responseCode = "403", description = "Access denied to session"),
            @ApiResponse(responseCode = "404", description = "Session not found"),
            @ApiResponse(responseCode = "410", description = "Session expired")
    })
    public ResponseEntity<com.foodrisk.dto.AnalysisStatusResponse> getStatus(
            @Parameter(description = "UUID of the session") @PathVariable UUID sessionId,
            Authentication authentication,
            @RequestHeader(value = REQUEST_ID_HEADER, required = false) String requestId,
            HttpServletResponse response
    ) {
        String resolvedRequestId = setupRequestId(requestId, response);
        try {
            sessionService.validateSessionAccess(sessionId, authentication);
            com.foodrisk.dto.AnalysisStatusResponse statusResponse = orchestratorService.getStatus(sessionId);
            return ResponseEntity.ok(statusResponse);
        } finally {
            MDC.remove("requestId");
        }
    }

    @GetMapping("/{sessionId}")
    @Operation(summary = "Get analysis session details", description = "Fetches the current status and TTL expiration timestamp for a session.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Session details retrieved",
                    content = @Content(schema = @Schema(implementation = AnalysisSessionResponse.class))),
            @ApiResponse(responseCode = "403", description = "Access denied to session"),
            @ApiResponse(responseCode = "404", description = "Session not found"),
            @ApiResponse(responseCode = "410", description = "Session expired")
    })
    public ResponseEntity<AnalysisSessionResponse> getSession(
            @Parameter(description = "UUID of the session") @PathVariable UUID sessionId,
            Authentication authentication,
            @RequestHeader(value = REQUEST_ID_HEADER, required = false) String requestId,
            HttpServletResponse response
    ) {
        String resolvedRequestId = setupRequestId(requestId, response);
        try {
            sessionService.validateSessionAccess(sessionId, authentication);
            AnalysisSessionResponse sessionResponse = sessionService.getSessionResponse(sessionId);
            return ResponseEntity.ok(sessionResponse);
        } finally {
            MDC.remove("requestId");
        }
    }

    @PostMapping(value = "/{sessionId}/ocr", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Extract raw text from food packaging images via OCR",
            description = "Accepts ingredients and/or nutrition table packaging images, performs offline Tesseract OCR, and returns raw extracted text. Images are deleted immediately after processing.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OCR completed successfully",
                    content = @Content(schema = @Schema(implementation = OcrAnalysisResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid image format, corrupted image, or missing files"),
            @ApiResponse(responseCode = "403", description = "Access denied to session"),
            @ApiResponse(responseCode = "404", description = "Session not found"),
            @ApiResponse(responseCode = "410", description = "Session expired"),
            @ApiResponse(responseCode = "413", description = "Image exceeds maximum allowed size (10MB)")
    })
    public ResponseEntity<OcrAnalysisResponse> runOcr(
            @Parameter(description = "UUID of the session") @PathVariable UUID sessionId,
            @RequestParam(value = "ingredientImage", required = false) MultipartFile ingredientImage,
            @RequestParam(value = "nutritionImage", required = false) MultipartFile nutritionImage,
            Authentication authentication,
            @RequestHeader(value = REQUEST_ID_HEADER, required = false) String requestId,
            HttpServletResponse response
    ) {
        String resolvedRequestId = setupRequestId(requestId, response);
        try {
            sessionService.validateSessionAccess(sessionId, authentication);
            log.info("[{}] Received OCR extraction request for session {}", resolvedRequestId, sessionId);
            OcrAnalysisResponse ocrResponse = ocrService.processOcr(sessionId, ingredientImage, nutritionImage);
            return ResponseEntity.ok(ocrResponse);
        } finally {
            MDC.remove("requestId");
        }
    }

    @PostMapping(value = "/{sessionId}/normalize", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Normalize raw OCR text into structured food data via Gemini AI",
            description = "Submits raw OCR text to Gemini AI with strict guardrails to structure ingredients, additives, and nutritional facts. Does NOT generate health risk scores.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Normalization completed successfully",
                    content = @Content(schema = @Schema(implementation = com.foodrisk.dto.NormalizedFoodData.class))),
            @ApiResponse(responseCode = "400", description = "Missing or invalid OCR text"),
            @ApiResponse(responseCode = "403", description = "Access denied to session"),
            @ApiResponse(responseCode = "404", description = "Session not found"),
            @ApiResponse(responseCode = "410", description = "Session expired"),
            @ApiResponse(responseCode = "502", description = "AI normalization engine error or malformed response"),
            @ApiResponse(responseCode = "503", description = "AI service unavailable or timed out")
    })
    public ResponseEntity<com.foodrisk.dto.NormalizedFoodData> normalize(
            @Parameter(description = "UUID of the session") @PathVariable UUID sessionId,
            @jakarta.validation.Valid @org.springframework.web.bind.annotation.RequestBody com.foodrisk.dto.NormalizeRequest request,
            Authentication authentication,
            @RequestHeader(value = REQUEST_ID_HEADER, required = false) String requestId,
            HttpServletResponse response
    ) {
        String resolvedRequestId = setupRequestId(requestId, response);
        try {
            sessionService.validateSessionAccess(sessionId, authentication);
            log.info("[{}] Received AI normalization request for session {}", resolvedRequestId, sessionId);
            com.foodrisk.dto.NormalizedFoodData normalizedData = normalizationService.normalize(sessionId, request);
            contextStore.storeNormalizedFoodData(sessionId, normalizedData);
            return ResponseEntity.ok(normalizedData);
        } finally {
            MDC.remove("requestId");
        }
    }

    @PostMapping("/{sessionId}/classify")
    @Operation(summary = "Classify food product intent and category",
            description = "Evaluates normalized food data to classify the product into HUMAN_FOOD, PET_FOOD, ANIMAL_FEED, NON_FOOD, or UNKNOWN with machine-readable reasonCode and evidence hierarchy.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Product classified successfully",
                    content = @Content(schema = @Schema(implementation = com.foodrisk.classification.FoodClassificationResult.class))),
            @ApiResponse(responseCode = "403", description = "Access denied to session"),
            @ApiResponse(responseCode = "404", description = "Session not found"),
            @ApiResponse(responseCode = "409", description = "M6 normalization has not been completed for this session"),
            @ApiResponse(responseCode = "410", description = "Session expired"),
            @ApiResponse(responseCode = "500", description = "Internal classification error")
    })
    public ResponseEntity<com.foodrisk.classification.FoodClassificationResult> classify(
            @Parameter(description = "UUID of the session") @PathVariable UUID sessionId,
            Authentication authentication,
            @RequestHeader(value = REQUEST_ID_HEADER, required = false) String requestId,
            HttpServletResponse response
    ) {
        String resolvedRequestId = setupRequestId(requestId, response);
        try {
            sessionService.validateSessionAccess(sessionId, authentication);
            log.info("[{}] Received product classification request for session {}", resolvedRequestId, sessionId);
            com.foodrisk.classification.FoodClassificationResult result = classificationService.classifyProduct(sessionId);
            return ResponseEntity.ok(result);
        } finally {
            MDC.remove("requestId");
        }
    }

    @PostMapping("/{sessionId}/ingredient-risk")
    @Operation(summary = "Evaluate ingredient and additive risks against verified rules",
            description = "Evaluates normalized ingredients against verified FSSAI/WHO additive and ingredient rules. Strictly decouples regulatory status from risk level and does NOT generate overall health scores.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Ingredient risk analysis completed successfully",
                    content = @Content(schema = @Schema(implementation = com.foodrisk.risk.IngredientRiskAnalysisResult.class))),
            @ApiResponse(responseCode = "403", description = "Access denied to session"),
            @ApiResponse(responseCode = "404", description = "Session not found"),
            @ApiResponse(responseCode = "409", description = "M6 normalization has not been completed for this session"),
            @ApiResponse(responseCode = "410", description = "Session expired"),
            @ApiResponse(responseCode = "500", description = "Internal risk evaluation error")
    })
    public ResponseEntity<com.foodrisk.risk.IngredientRiskAnalysisResult> analyzeIngredientRisk(
            @Parameter(description = "UUID of the session") @PathVariable UUID sessionId,
            Authentication authentication,
            @RequestHeader(value = REQUEST_ID_HEADER, required = false) String requestId,
            HttpServletResponse response
    ) {
        String resolvedRequestId = setupRequestId(requestId, response);
        try {
            sessionService.validateSessionAccess(sessionId, authentication);
            log.info("[{}] Received ingredient risk analysis request for session {}", resolvedRequestId, sessionId);
            com.foodrisk.risk.IngredientRiskAnalysisResult result = ingredientRiskService.analyzeIngredientRisk(sessionId);
            return ResponseEntity.ok(result);
        } finally {
            MDC.remove("requestId");
        }
    }

    @PostMapping("/{sessionId}/nutrition")
    @Operation(summary = "Evaluate nutrition facts against authoritative WHO and FSSAI standards",
            description = "Evaluates normalized nutrition facts against WHO and FSSAI reference thresholds with basis normalization, missing value distinction, and evidence grounding. Does NOT produce final overall score.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Nutrition analysis completed successfully",
                    content = @Content(schema = @Schema(implementation = com.foodrisk.nutrition.NutritionAnalysisResult.class))),
            @ApiResponse(responseCode = "403", description = "Access denied to session"),
            @ApiResponse(responseCode = "404", description = "Session not found"),
            @ApiResponse(responseCode = "409", description = "M6 normalization has not been completed for this session"),
            @ApiResponse(responseCode = "410", description = "Session expired"),
            @ApiResponse(responseCode = "500", description = "Internal nutrition evaluation error")
    })
    public ResponseEntity<com.foodrisk.nutrition.NutritionAnalysisResult> analyzeNutrition(
            @Parameter(description = "UUID of the session") @PathVariable UUID sessionId,
            Authentication authentication,
            @RequestHeader(value = REQUEST_ID_HEADER, required = false) String requestId,
            HttpServletResponse response
    ) {
        String resolvedRequestId = setupRequestId(requestId, response);
        try {
            sessionService.validateSessionAccess(sessionId, authentication);
            log.info("[{}] Received nutrition analysis request for session {}", resolvedRequestId, sessionId);
            com.foodrisk.nutrition.NutritionAnalysisResult result = nutritionService.analyzeNutrition(sessionId);
            return ResponseEntity.ok(result);
        } finally {
            MDC.remove("requestId");
        }
    }

    @GetMapping("/{sessionId}/assessment")
    @Operation(summary = "Synthesize Food Awareness Assessment and Food Awareness Score",
            description = "Synthesizes M7 classification, M8 ingredient risks, and M9 nutrition findings into the Food Awareness Score (0–100), transparent score breakdown with anti-double-counting, and actionable non-medical population guidance.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Food Awareness Assessment synthesized successfully",
                    content = @Content(schema = @Schema(implementation = com.foodrisk.scoring.FoodRiskAssessment.class))),
            @ApiResponse(responseCode = "403", description = "Access denied to session"),
            @ApiResponse(responseCode = "404", description = "Session not found"),
            @ApiResponse(responseCode = "409", description = "Prior analysis steps (M7/M8/M9) not completed for this session"),
            @ApiResponse(responseCode = "410", description = "Session expired"),
            @ApiResponse(responseCode = "500", description = "Internal assessment synthesis error")
    })
    public ResponseEntity<com.foodrisk.scoring.FoodRiskAssessment> getAssessment(
            @Parameter(description = "UUID of the session") @PathVariable UUID sessionId,
            Authentication authentication,
            @RequestHeader(value = REQUEST_ID_HEADER, required = false) String requestId,
            HttpServletResponse response
    ) {
        String resolvedRequestId = setupRequestId(requestId, response);
        try {
            sessionService.validateSessionAccess(sessionId, authentication);
            log.info("[{}] Received food risk assessment request for session {}", resolvedRequestId, sessionId);
            com.foodrisk.scoring.FoodRiskAssessment assessment = assessmentService.getAssessment(sessionId);
            return ResponseEntity.ok(assessment);
        } finally {
            MDC.remove("requestId");
        }
    }

    @PostMapping("/{sessionId}/explain")
    @Operation(summary = "Explain a verified food finding with grounded AI guardrails",
            description = "Produces a user-triggered, non-medical educational explanation for a verified ingredient or nutrient finding. Server strictly validates findings against the session's verified assessment.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Explanation produced successfully",
                    content = @Content(schema = @Schema(implementation = com.foodrisk.dto.ExplainFindingResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid request or finding not detected in verified session"),
            @ApiResponse(responseCode = "403", description = "Access denied to this session"),
            @ApiResponse(responseCode = "404", description = "Session not found"),
            @ApiResponse(responseCode = "410", description = "Session expired"),
            @ApiResponse(responseCode = "500", description = "Internal explanation error")
    })
    public ResponseEntity<com.foodrisk.dto.ExplainFindingResponse> explainFinding(
            @Parameter(description = "UUID of the session") @PathVariable UUID sessionId,
            @jakarta.validation.Valid @org.springframework.web.bind.annotation.RequestBody com.foodrisk.dto.ExplainFindingRequest request,
            Authentication authentication,
            @RequestHeader(value = REQUEST_ID_HEADER, required = false) String requestId,
            HttpServletResponse response
    ) {
        String resolvedRequestId = setupRequestId(requestId, response);
        try {
            log.info("[{}] Received explain request for session {}, item '{}'", resolvedRequestId, sessionId, request.itemName());
            com.foodrisk.dto.ExplainFindingResponse result = explanationService.explainFinding(sessionId, request, authentication);
            return ResponseEntity.ok(result);
        } finally {
            MDC.remove("requestId");
        }
    }

    private String setupRequestId(String incomingRequestId, HttpServletResponse response) {
        String requestId = (incomingRequestId != null && !incomingRequestId.isBlank())
                ? incomingRequestId.trim()
                : UUID.randomUUID().toString();
        MDC.put("requestId", requestId);
        response.setHeader(REQUEST_ID_HEADER, requestId);
        return requestId;
    }
}
