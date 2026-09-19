package com.foodrisk.controller;

import com.foodrisk.classification.ClassificationCertainty;
import com.foodrisk.classification.FoodCategory;
import com.foodrisk.entity.AnalysisStatus;
import com.foodrisk.entity.FoodAnalysisSession;
import com.foodrisk.exception.SessionNotReadyException;
import com.foodrisk.nutrition.ComparisonStatus;
import com.foodrisk.nutrition.DataCompleteness;
import com.foodrisk.nutrition.NutrientType;
import com.foodrisk.nutrition.NutrientValueState;
import com.foodrisk.nutrition.NutritionAnalysisResult;
import com.foodrisk.nutrition.NutritionAnalysisService;
import com.foodrisk.nutrition.NutritionBasis;
import com.foodrisk.nutrition.NutritionFinding;
import com.foodrisk.nutrition.NutritionSeverity;
import com.foodrisk.nutrition.ReferenceType;
import com.foodrisk.repository.FoodAnalysisSessionRepository;
import com.foodrisk.scoring.AssessmentReliability;
import com.foodrisk.scoring.FoodRiskAssessment;
import com.foodrisk.scoring.FoodRiskAssessmentService;
import com.foodrisk.scoring.HumanConsumptionStatus;
import com.foodrisk.scoring.OverallFoodStatus;
import com.foodrisk.scoring.PopulationGuidance;
import com.foodrisk.scoring.ScoreImpact;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AnalysisControllerNutritionAndAssessmentTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private FoodAnalysisSessionRepository sessionRepository;

    @MockBean
    private NutritionAnalysisService nutritionService;

    @MockBean
    private FoodRiskAssessmentService assessmentService;

    private FoodAnalysisSession createActiveSession() {
        FoodAnalysisSession session = new FoodAnalysisSession(
                "token-" + UUID.randomUUID(),
                AnalysisStatus.PROCESSING,
                Instant.now().plusSeconds(900)
        );
        return sessionRepository.save(session);
    }

    private FoodAnalysisSession createExpiredSession() {
        FoodAnalysisSession session = new FoodAnalysisSession(
                "token-" + UUID.randomUUID(),
                AnalysisStatus.CREATED,
                Instant.now().minusSeconds(120)
        );
        return sessionRepository.save(session);
    }

    @Test
    @DisplayName("POST /api/analysis/{sessionId}/nutrition returns 200 with NutritionAnalysisResult")
    void testAnalyzeNutritionSuccess() throws Exception {
        FoodAnalysisSession session = createActiveSession();
        UUID sessionId = session.getId();
        NutritionFinding sugarFinding = new NutritionFinding(
                NutrientType.TOTAL_SUGARS,
                new BigDecimal("30.0"),
                "g",
                NutritionBasis.PER_100G,
                null,
                new BigDecimal("30.0"),
                NutritionBasis.PER_100G,
                new BigDecimal("22.5"),
                "g",
                NutritionBasis.PER_100G,
                ReferenceType.DIETARY_GUIDELINE,
                NutrientValueState.DETECTED_VALUE,
                ComparisonStatus.ABOVE_REFERENCE,
                NutritionSeverity.HIGH,
                "Exceeds WHO benchmark for free sugars.",
                List.of("WHO")
        );

        NutritionAnalysisResult result = new NutritionAnalysisResult(
                sessionId,
                DataCompleteness.COMPLETE,
                NutritionBasis.PER_100G,
                null,
                List.of(sugarFinding),
                List.of(),
                List.of("Elevated sugar content (30.0g/100g) exceeds WHO reference threshold."),
                List.of(),
                List.of("WHO", "FSSAI"),
                "2026.09",
                Instant.now()
        );

        when(nutritionService.analyzeNutrition(sessionId)).thenReturn(result);

        mockMvc.perform(post("/api/analysis/" + sessionId + "/nutrition")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Request-ID", "req-nutrition-303"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Request-ID", "req-nutrition-303"))
                .andExpect(jsonPath("$.sessionId").value(sessionId.toString()))
                .andExpect(jsonPath("$.dataCompleteness").value("COMPLETE"))
                .andExpect(jsonPath("$.declaredBasis").value("PER_100G"))
                .andExpect(jsonPath("$.findings[0].nutrient").value("TOTAL_SUGARS"))
                .andExpect(jsonPath("$.findings[0].status").value("ABOVE_REFERENCE"))
                .andExpect(jsonPath("$.findings[0].severity").value("HIGH"))
                .andExpect(jsonPath("$.attentionIndicators[0]").value("Elevated sugar content (30.0g/100g) exceeds WHO reference threshold."));
    }

    @Test
    @DisplayName("POST /api/analysis/{sessionId}/nutrition returns 409 Conflict if M6 normalization not completed")
    void testAnalyzeNutritionSessionNotReady() throws Exception {
        FoodAnalysisSession session = createActiveSession();
        UUID sessionId = session.getId();
        when(nutritionService.analyzeNutrition(sessionId))
                .thenThrow(new SessionNotReadyException("Normalization not completed"));

        mockMvc.perform(post("/api/analysis/" + sessionId + "/nutrition")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Normalization not completed"));
    }

    @Test
    @DisplayName("GET /api/analysis/{sessionId}/assessment returns 200 with FoodRiskAssessment")
    void testGetAssessmentSuccess() throws Exception {
        FoodAnalysisSession session = createActiveSession();
        UUID sessionId = session.getId();
        FoodRiskAssessment assessment = new FoodRiskAssessment(
                sessionId,
                FoodCategory.HUMAN_FOOD,
                "Processed snack product",
                HumanConsumptionStatus.HUMAN_FOOD,
                75,
                OverallFoodStatus.NEEDS_ATTENTION,
                AssessmentReliability.HIGH,
                DataCompleteness.COMPLETE,
                ClassificationCertainty.HIGH,
                List.of(
                        new ScoreImpact("BASELINE", 100, "Starting score", "SYSTEM"),
                        new ScoreImpact("SUGAR_OVERLAP", -20, "Corroborated high sugar formulation", "FSSAI/WHO"),
                        new ScoreImpact("LOW_SODIUM_BONUS", 5, "Low sodium benchmark", "WHO")
                ),
                null,
                null,
                List.of("Elevated sugar corroborated by both ingredients and nutrition facts."),
                List.of("Low sodium content."),
                List.of("Moderation recommended due to sugar content."),
                new PopulationGuidance(
                        "Balanced portion sizing recommended.",
                        "Limit portion frequency for children.",
                        "Inspect nutrient table for sugar values."
                ),
                List.of("FSSAI", "WHO"),
                List.of("Informational awareness tool only."),
                "2026.09",
                "1.0",
                Instant.now()
        );

        when(assessmentService.getAssessment(sessionId)).thenReturn(assessment);

        mockMvc.perform(get("/api/analysis/" + sessionId + "/assessment")
                        .header("X-Request-ID", "req-assessment-404"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Request-ID", "req-assessment-404"))
                .andExpect(jsonPath("$.sessionId").value(sessionId.toString()))
                .andExpect(jsonPath("$.productCategory").value("HUMAN_FOOD"))
                .andExpect(jsonPath("$.humanConsumptionStatus").value("HUMAN_FOOD"))
                .andExpect(jsonPath("$.overallScore").value(75))
                .andExpect(jsonPath("$.overallStatus").value("NEEDS_ATTENTION"))
                .andExpect(jsonPath("$.assessmentReliability").value("HIGH"))
                .andExpect(jsonPath("$.scoreBreakdown[1].factor").value("SUGAR_OVERLAP"))
                .andExpect(jsonPath("$.scoreBreakdown[1].impact").value(-20))
                .andExpect(jsonPath("$.populationGuidance.generalPopulation").value("Balanced portion sizing recommended."));
    }

    @Test
    @DisplayName("GET /api/analysis/{sessionId}/assessment returns 409 Conflict if prior steps not ready")
    void testGetAssessmentSessionNotReady() throws Exception {
        FoodAnalysisSession session = createActiveSession();
        UUID sessionId = session.getId();
        when(assessmentService.getAssessment(sessionId))
                .thenThrow(new SessionNotReadyException("Nutrition analysis not completed"));

        mockMvc.perform(get("/api/analysis/" + sessionId + "/assessment"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Nutrition analysis not completed"));
    }

    @Test
    @DisplayName("GET /api/analysis/{sessionId}/assessment returns 410 Gone if session expired")
    void testGetAssessmentSessionExpired() throws Exception {
        FoodAnalysisSession expiredSession = createExpiredSession();
        UUID sessionId = expiredSession.getId();

        mockMvc.perform(get("/api/analysis/" + sessionId + "/assessment"))
                .andExpect(status().isGone())
                .andExpect(jsonPath("$.message", containsString("expired")));
    }

    @Test
    @DisplayName("GET /api/analysis/{sessionId}/assessment returns 404 Not Found if session not found")
    void testGetAssessmentSessionNotFound() throws Exception {
        UUID sessionId = UUID.randomUUID();

        mockMvc.perform(get("/api/analysis/" + sessionId + "/assessment"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message", containsString("not found")));
    }
}
