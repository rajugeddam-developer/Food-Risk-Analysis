package com.foodrisk.controller;

import com.foodrisk.classification.ClassificationCertainty;
import com.foodrisk.classification.FoodCategory;
import com.foodrisk.dto.AnalysisSessionResponse;
import com.foodrisk.dto.AnalysisStatusResponse;
import com.foodrisk.entity.AnalysisStatus;
import com.foodrisk.nutrition.DataCompleteness;
import com.foodrisk.repository.UserRepository;
import com.foodrisk.scoring.AssessmentReliability;
import com.foodrisk.scoring.FoodRiskAssessment;
import com.foodrisk.scoring.FoodRiskAssessmentService;
import com.foodrisk.scoring.HumanConsumptionStatus;
import com.foodrisk.scoring.OverallFoodStatus;
import com.foodrisk.scoring.PopulationGuidance;
import com.foodrisk.service.AnalysisOrchestratorService;
import com.foodrisk.service.AnalysisSessionService;
import com.foodrisk.service.FoodNormalizationService;
import com.foodrisk.service.OcrService;
import com.foodrisk.service.context.AnalysisContextStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Milestone M11/M12 REST API Contract Tests for AnalysisController:
 * - POST /api/analysis/start
 * - POST /api/analysis/{sessionId}/analyze
 * - GET /api/analysis/{sessionId}/status
 * - GET /api/analysis/{sessionId}/assessment
 */
@ExtendWith(MockitoExtension.class)
class AnalysisControllerM11M12Test {

    private MockMvc mockMvc;

    @Mock
    private UserRepository userRepository;
    @Mock
    private AnalysisSessionService sessionService;
    @Mock
    private OcrService ocrService;
    @Mock
    private FoodNormalizationService normalizationService;
    @Mock
    private AnalysisContextStore contextStore;
    @Mock
    private com.foodrisk.classification.FoodClassificationService classificationService;
    @Mock
    private com.foodrisk.risk.IngredientRiskService ingredientRiskService;
    @Mock
    private com.foodrisk.nutrition.NutritionAnalysisService nutritionService;
    @Mock
    private FoodRiskAssessmentService assessmentService;
    @Mock
    private AnalysisOrchestratorService orchestratorService;

    @BeforeEach
    void setUp() {
        AnalysisController controller = new AnalysisController(
                sessionService, ocrService, normalizationService, contextStore,
                classificationService, ingredientRiskService, nutritionService,
                assessmentService, orchestratorService, userRepository
        );
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    @DisplayName("POST /api/analysis/start creates session and returns 201 CREATED")
    void testStartSession() throws Exception {
        UUID sessionId = UUID.randomUUID();
        AnalysisSessionResponse response = new AnalysisSessionResponse(
                sessionId, "token-start-123", "CREATED", Instant.now().plusSeconds(900), Instant.now()
        );

        when(sessionService.createSession(any())).thenReturn(response);

        mockMvc.perform(post("/api/analysis/start"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.sessionId").value(sessionId.toString()))
                .andExpect(jsonPath("$.status").value("CREATED"))
                .andExpect(header().exists("X-Request-ID"));
    }

    @Test
    @DisplayName("POST /api/analysis/{sessionId}/analyze returns 202 ACCEPTED with status tracking")
    void testAnalyzeEndpointAsync() throws Exception {
        UUID sessionId = UUID.randomUUID();
        MockMultipartFile file = new MockMultipartFile("ingredientImage", "test.jpg", "image/jpeg", new byte[]{1, 2, 3});

        AnalysisStatusResponse statusResponse = new AnalysisStatusResponse(
                sessionId, AnalysisStatus.PROCESSING, "IMAGE_VALIDATION", null, Instant.now().plusSeconds(900)
        );

        when(orchestratorService.startAnalysisAsync(eq(sessionId), any(), any())).thenReturn(statusResponse);

        mockMvc.perform(multipart("/api/analysis/" + sessionId + "/analyze")
                        .file(file))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.sessionId").value(sessionId.toString()))
                .andExpect(jsonPath("$.status").value("PROCESSING"))
                .andExpect(jsonPath("$.currentStage").value("IMAGE_VALIDATION"));
    }

    @Test
    @DisplayName("GET /api/analysis/{sessionId}/status returns 200 with current stage")
    void testGetStatus() throws Exception {
        UUID sessionId = UUID.randomUUID();
        AnalysisStatusResponse statusResponse = new AnalysisStatusResponse(
                sessionId, AnalysisStatus.PROCESSING, "AI_NORMALIZATION", null, Instant.now().plusSeconds(900)
        );

        when(orchestratorService.getStatus(sessionId)).thenReturn(statusResponse);

        mockMvc.perform(get("/api/analysis/" + sessionId + "/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sessionId").value(sessionId.toString()))
                .andExpect(jsonPath("$.status").value("PROCESSING"))
                .andExpect(jsonPath("$.currentStage").value("AI_NORMALIZATION"));
    }

    @Test
    @DisplayName("GET /api/analysis/{sessionId}/assessment returns 200 with FoodRiskAssessment")
    void testGetAssessment() throws Exception {
        UUID sessionId = UUID.randomUUID();
        FoodRiskAssessment assessment = new FoodRiskAssessment(
                sessionId,
                FoodCategory.HUMAN_FOOD,
                "Human packaged snack",
                HumanConsumptionStatus.HUMAN_FOOD,
                78,
                OverallFoodStatus.NEEDS_ATTENTION,
                AssessmentReliability.HIGH,
                DataCompleteness.COMPLETE,
                ClassificationCertainty.HIGH,
                Collections.emptyList(),
                null,
                null,
                List.of("High Sugar"),
                List.of("Source of Fibre"),
                List.of("Consume in moderation"),
                new PopulationGuidance("Moderate intake", "Limit for children", "Consult physician"),
                List.of("WHO", "FSSAI"),
                Collections.emptyList(),
                "2026.09",
                "1.0",
                Instant.now()
        );

        when(assessmentService.getAssessment(sessionId)).thenReturn(assessment);

        mockMvc.perform(get("/api/analysis/" + sessionId + "/assessment"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sessionId").value(sessionId.toString()))
                .andExpect(jsonPath("$.overallScore").value(78))
                .andExpect(jsonPath("$.overallStatus").value("NEEDS_ATTENTION"))
                .andExpect(jsonPath("$.humanConsumptionStatus").value("HUMAN_FOOD"));
    }
}
