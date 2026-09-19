package com.foodrisk.service;

import com.foodrisk.classification.ClassificationCertainty;
import com.foodrisk.classification.FoodCategory;
import com.foodrisk.dto.ExplainFindingRequest;
import com.foodrisk.dto.ExplainFindingResponse;
import com.foodrisk.entity.FoodAnalysisSession;
import com.foodrisk.entity.User;
import com.foodrisk.gemini.GeminiClient;
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
import com.foodrisk.risk.IngredientRiskItem;
import com.foodrisk.risk.IngredientRiskLevel;
import com.foodrisk.risk.RegulatoryStatus;
import com.foodrisk.scoring.AssessmentReliability;
import com.foodrisk.scoring.FoodRiskAssessment;
import com.foodrisk.scoring.FoodRiskAssessmentService;
import com.foodrisk.scoring.HumanConsumptionStatus;
import com.foodrisk.scoring.OverallFoodStatus;
import com.foodrisk.scoring.PopulationGuidance;
import com.foodrisk.service.context.AnalysisContextStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

class FindingExplanationServiceTest {

    private AnalysisSessionService sessionService;
    private AnalysisContextStore contextStore;
    private FoodRiskAssessmentService assessmentService;
    private GeminiClient geminiClient;
    private FindingExplanationService service;

    private UUID sessionId;
    private FoodAnalysisSession session;
    private FoodRiskAssessment assessment;

    @BeforeEach
    void setUp() {
        sessionService = Mockito.mock(AnalysisSessionService.class);
        contextStore = Mockito.mock(AnalysisContextStore.class);
        assessmentService = Mockito.mock(FoodRiskAssessmentService.class);
        geminiClient = Mockito.mock(GeminiClient.class);

        service = new FindingExplanationService(sessionService, contextStore, assessmentService, geminiClient);

        sessionId = UUID.randomUUID();
        session = new FoodAnalysisSession(sessionId.toString(), com.foodrisk.entity.AnalysisStatus.COMPLETED, Instant.now().plusSeconds(900));

        IngredientRiskItem saltItem = new IngredientRiskItem(
                "Iodised Salt", "Salt", IngredientRiskLevel.NO_CONCERN,
                List.of("Permitted standard seasoning ingredient."), List.of("FSSAI"), EvidenceStatus.SUPPORTED,
                null, "Seasoning", RegulatoryStatus.PERMITTED
        );

        NutritionFinding sodiumFinding = new NutritionFinding(
                NutrientType.SODIUM, new BigDecimal("750.0"), "mg", NutritionBasis.PER_100G, null,
                new BigDecimal("750.0"), NutritionBasis.PER_100G, new BigDecimal("600.0"), "mg",
                NutritionBasis.PER_100G, ReferenceType.DIETARY_GUIDELINE, NutrientValueState.DETECTED_VALUE,
                ComparisonStatus.ABOVE_REFERENCE, NutritionSeverity.HIGH, "Elevated sodium above WHO benchmark", List.of("WHO")
        );

        NutritionAnalysisResult nutrition = new NutritionAnalysisResult(
                sessionId, DataCompleteness.COMPLETE, NutritionBasis.PER_100G, null,
                List.of(sodiumFinding), List.of(), List.of(), List.of(), List.of("WHO"), "2026.09", Instant.now()
        );

        assessment = new FoodRiskAssessment(
                sessionId, FoodCategory.HUMAN_FOOD, "Packaged food", HumanConsumptionStatus.HUMAN_FOOD,
                70, OverallFoodStatus.NEEDS_ATTENTION, AssessmentReliability.HIGH, DataCompleteness.COMPLETE,
                ClassificationCertainty.HIGH, List.of(), null, List.of(saltItem), nutrition,
                List.of(), List.of(), List.of(), new PopulationGuidance("General", "Kids", "Special"),
                List.of(), List.of("FSSAI", "WHO"), List.of(), "2026.09", "2026.09", Instant.now()
        );

        when(sessionService.getActiveSession(sessionId)).thenReturn(session);
        when(contextStore.getFoodRiskAssessment(sessionId)).thenReturn(Optional.of(assessment));
    }

    @Test
    @DisplayName("Valid verified ingredient finding produces AI explanation when Gemini succeeds")
    void testExplainValidIngredientWithGemini() {
        when(geminiClient.generateExplanation(anyString(), anyString()))
                .thenReturn("Salt is a standard dietary mineral used for seasoning. In moderate amounts, it is permitted under FSSAI regulations.");

        ExplainFindingRequest request = new ExplainFindingRequest("Salt", "INGREDIENT");
        ExplainFindingResponse response = service.explainFinding(sessionId, request, null);

        assertNotNull(response);
        assertEquals("Salt", response.itemName());
        assertTrue(response.aiGenerated());
        assertTrue(response.explanation().contains("Salt is a standard"));
        assertTrue(response.sourceIds().contains("FSSAI"));
    }

    @Test
    @DisplayName("When Gemini call fails or times out, deterministic fallback summary is returned safely")
    void testExplainDeterministicFallbackOnGeminiFailure() {
        when(geminiClient.generateExplanation(anyString(), anyString())).thenReturn(null);

        ExplainFindingRequest request = new ExplainFindingRequest("Salt", "INGREDIENT");
        ExplainFindingResponse response = service.explainFinding(sessionId, request, null);

        assertNotNull(response);
        assertEquals("Salt", response.itemName());
        assertFalse(response.aiGenerated());
        assertNotNull(response.explanation());
        assertFalse(response.explanation().isBlank());
    }

    @Test
    @DisplayName("Client cannot query or inject unverified arbitrary findings not detected in session")
    void testRejectUnverifiedFinding() {
        ExplainFindingRequest fakeRequest = new ExplainFindingRequest("Fabricated Toxins", "INGREDIENT");

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> service.explainFinding(sessionId, fakeRequest, null)
        );
        assertTrue(ex.getMessage().contains("was not detected in this verified session"));
    }

    @Test
    @DisplayName("Authenticated user mismatch throws AccessDeniedException")
    void testAccessDeniedOnUserMismatch() {
        User owner = new User("owner@example.com", "hash", "Owner User");
        FoodAnalysisSession ownedSession = new FoodAnalysisSession(sessionId.toString(), com.foodrisk.entity.AnalysisStatus.COMPLETED, owner, Instant.now().plusSeconds(900));
        when(sessionService.getActiveSession(sessionId)).thenReturn(ownedSession);

        Authentication auth = Mockito.mock(Authentication.class);
        when(auth.isAuthenticated()).thenReturn(true);
        when(auth.getName()).thenReturn("intruder@example.com");

        ExplainFindingRequest request = new ExplainFindingRequest("Salt", "INGREDIENT");

        assertThrows(
                AccessDeniedException.class,
                () -> service.explainFinding(sessionId, request, auth)
        );
    }
}
