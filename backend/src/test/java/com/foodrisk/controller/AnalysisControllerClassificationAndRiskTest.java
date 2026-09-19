package com.foodrisk.controller;

import com.foodrisk.classification.ClassificationCertainty;
import com.foodrisk.classification.ClassificationReasonCode;
import com.foodrisk.classification.FoodCategory;
import com.foodrisk.classification.FoodClassificationResult;
import com.foodrisk.classification.FoodClassificationService;
import com.foodrisk.entity.AnalysisStatus;
import com.foodrisk.entity.FoodAnalysisSession;
import com.foodrisk.exception.SessionNotReadyException;
import com.foodrisk.repository.FoodAnalysisSessionRepository;
import com.foodrisk.risk.EvidenceStatus;
import com.foodrisk.risk.IngredientRiskAnalysisResult;
import com.foodrisk.risk.IngredientRiskItem;
import com.foodrisk.risk.IngredientRiskLevel;
import com.foodrisk.risk.IngredientRiskService;
import com.foodrisk.risk.IngredientRiskSummary;
import com.foodrisk.risk.RegulatoryStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AnalysisControllerClassificationAndRiskTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private FoodAnalysisSessionRepository sessionRepository;

    @MockBean
    private FoodClassificationService classificationService;

    @MockBean
    private IngredientRiskService ingredientRiskService;

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
    @DisplayName("POST /api/analysis/{sessionId}/classify returns 200 with FoodClassificationResult")
    void testClassifySuccess() throws Exception {
        FoodAnalysisSession session = createActiveSession();
        UUID sessionId = session.getId();
        FoodClassificationResult result = new FoodClassificationResult(
                FoodCategory.HUMAN_FOOD,
                ClassificationCertainty.HIGH,
                0.95,
                ClassificationReasonCode.EXPLICIT_HUMAN_FOOD_MARKER,
                "Standard packaged human food composition.",
                List.of("Wheat Flour", "Sugar"),
                List.of()
        );

        when(classificationService.classifyProduct(sessionId)).thenReturn(result);

        mockMvc.perform(post("/api/analysis/" + sessionId + "/classify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Request-ID", "req-classify-101"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Request-ID", "req-classify-101"))
                .andExpect(jsonPath("$.category").value("HUMAN_FOOD"))
                .andExpect(jsonPath("$.certainty").value("HIGH"))
                .andExpect(jsonPath("$.confidence").value(0.95))
                .andExpect(jsonPath("$.reasonCode").value("EXPLICIT_HUMAN_FOOD_MARKER"))
                .andExpect(jsonPath("$.reason").value("Standard packaged human food composition."))
                .andExpect(jsonPath("$.evidence[0]").value("Wheat Flour"));
    }

    @Test
    @DisplayName("POST /api/analysis/{sessionId}/classify returns 409 Conflict if M6 normalization not completed")
    void testClassifySessionNotReady() throws Exception {
        FoodAnalysisSession session = createActiveSession();
        UUID sessionId = session.getId();
        when(classificationService.classifyProduct(sessionId))
                .thenThrow(new SessionNotReadyException("Normalization not completed"));

        mockMvc.perform(post("/api/analysis/" + sessionId + "/classify")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Normalization not completed"));
    }

    @Test
    @DisplayName("POST /api/analysis/{sessionId}/classify returns 410 Gone if session expired")
    void testClassifySessionExpired() throws Exception {
        FoodAnalysisSession expiredSession = createExpiredSession();
        UUID sessionId = expiredSession.getId();

        mockMvc.perform(post("/api/analysis/" + sessionId + "/classify")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isGone())
                .andExpect(jsonPath("$.message", containsString("expired")));
    }

    @Test
    @DisplayName("POST /api/analysis/{sessionId}/classify returns 404 Not Found if session not found")
    void testClassifySessionNotFound() throws Exception {
        UUID sessionId = UUID.randomUUID();

        mockMvc.perform(post("/api/analysis/" + sessionId + "/classify")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message", containsString("not found")));
    }

    @Test
    @DisplayName("POST /api/analysis/{sessionId}/ingredient-risk returns 200 with IngredientRiskAnalysisResult")
    void testIngredientRiskSuccess() throws Exception {
        FoodAnalysisSession session = createActiveSession();
        UUID sessionId = session.getId();
        IngredientRiskSummary summary = new IngredientRiskSummary(
                2, 2, 0, 1, 0, 1, 0, 1, 0
        );
        IngredientRiskItem palmOil = new IngredientRiskItem(
                "Refined Palm Oil",
                "Palm Oil",
                IngredientRiskLevel.MODERATE_ATTENTION,
                List.of("High saturated fat profile."),
                List.of("FSSAI_FSSR_2011"),
                EvidenceStatus.SUPPORTED,
                null,
                null,
                RegulatoryStatus.PERMITTED
        );
        IngredientRiskItem citricAcid = new IngredientRiskItem(
                "INS 330",
                "Citric Acid",
                IngredientRiskLevel.NO_CONCERN,
                List.of("Generally recognized as safe."),
                List.of("FSSAI_FSSR_2011", "WHO_CODEX_STAN_192_1995"),
                EvidenceStatus.SUPPORTED,
                "INS 330",
                "Acidity Regulator / Antioxidant",
                RegulatoryStatus.PERMITTED
        );

        IngredientRiskAnalysisResult result = new IngredientRiskAnalysisResult(
                sessionId,
                summary,
                List.of(palmOil, citricAcid),
                Instant.now()
        );

        when(ingredientRiskService.analyzeIngredientRisk(sessionId)).thenReturn(result);

        mockMvc.perform(post("/api/analysis/" + sessionId + "/ingredient-risk")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Request-ID", "req-risk-202"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Request-ID", "req-risk-202"))
                .andExpect(jsonPath("$.sessionId").value(sessionId.toString()))
                .andExpect(jsonPath("$.summary.totalIngredients").value(2))
                .andExpect(jsonPath("$.summary.moderateAttentionIngredients").value(1))
                .andExpect(jsonPath("$.summary.noConcernIngredients").value(1))
                .andExpect(jsonPath("$.items[0].normalizedName").value("Palm Oil"))
                .andExpect(jsonPath("$.items[0].riskLevel").value("MODERATE_ATTENTION"))
                .andExpect(jsonPath("$.items[1].additiveCode").value("INS 330"))
                .andExpect(jsonPath("$.items[1].functionalClass").value("Acidity Regulator / Antioxidant"));
    }

    @Test
    @DisplayName("POST /api/analysis/{sessionId}/ingredient-risk returns 409 Conflict if M6 normalization not completed")
    void testIngredientRiskSessionNotReady() throws Exception {
        FoodAnalysisSession session = createActiveSession();
        UUID sessionId = session.getId();
        when(ingredientRiskService.analyzeIngredientRisk(sessionId))
                .thenThrow(new SessionNotReadyException("Normalization not completed"));

        mockMvc.perform(post("/api/analysis/" + sessionId + "/ingredient-risk")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Normalization not completed"));
    }
}
