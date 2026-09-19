package com.foodrisk.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.foodrisk.dto.NormalizeRequest;
import com.foodrisk.dto.NormalizedFoodData;
import com.foodrisk.dto.NormalizedIngredient;
import com.foodrisk.dto.NormalizedNutrition;
import com.foodrisk.entity.AnalysisStatus;
import com.foodrisk.entity.FoodAnalysisSession;
import com.foodrisk.exception.NormalizationException;
import com.foodrisk.repository.FoodAnalysisSessionRepository;
import com.foodrisk.service.FoodNormalizationService;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AnalysisControllerNormalizeTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private FoodAnalysisSessionRepository sessionRepository;

    @MockBean
    private FoodNormalizationService normalizationService;

    @Test
    @DisplayName("POST /api/analysis/{sessionId}/normalize successfully returns normalized food data")
    void testNormalizeSuccess() throws Exception {
        FoodAnalysisSession session = new FoodAnalysisSession(
                "token-" + UUID.randomUUID(),
                AnalysisStatus.PROCESSING,
                Instant.now().plusSeconds(900)
        );
        FoodAnalysisSession saved = sessionRepository.save(session);

        NormalizedNutrition nutrition = new NormalizedNutrition(
                "per 100g", 500.0, 6.0, 65.0, 20.0, 15.0, 25.0, 10.0, 0.0, 200.0, 2.0, List.of()
        );
        NormalizedFoodData mockData = new NormalizedFoodData(
                "Test Biscuit",
                "25g",
                25.0,
                List.of(new NormalizedIngredient("Palm Oil", "Pa1m Oi1", false, null, true)),
                nutrition,
                List.of("Spelling corrected")
        );

        when(normalizationService.normalize(eq(saved.getId()), any(NormalizeRequest.class)))
                .thenReturn(mockData);

        NormalizeRequest request = new NormalizeRequest("Pa1m Oi1, Sugar", "Energy 500 kcal, Fat 25g");

        mockMvc.perform(post("/api/analysis/" + saved.getId() + "/normalize")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                        .header("X-Request-ID", "norm-req-789"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Request-ID", "norm-req-789"))
                .andExpect(jsonPath("$.productName").value("Test Biscuit"))
                .andExpect(jsonPath("$.servingSizeGrams").value(25.0))
                .andExpect(jsonPath("$.ingredients[0].name").value("Palm Oil"))
                .andExpect(jsonPath("$.ingredients[0].uncertain").value(true))
                .andExpect(jsonPath("$.nutrition.energyKcal").value(500.0));
    }

    @Test
    @DisplayName("POST /api/analysis/{sessionId}/normalize returns 410 GONE for expired session")
    void testNormalizeExpiredSession() throws Exception {
        FoodAnalysisSession expiredSession = new FoodAnalysisSession(
                "token-" + UUID.randomUUID(),
                AnalysisStatus.CREATED,
                Instant.now().minusSeconds(120)
        );
        FoodAnalysisSession saved = sessionRepository.save(expiredSession);

        when(normalizationService.normalize(eq(saved.getId()), any(NormalizeRequest.class)))
                .thenThrow(new com.foodrisk.exception.SessionExpiredException("Analysis session " + saved.getId() + " has expired."));

        NormalizeRequest request = new NormalizeRequest("Sugar", "Fat 10g");

        mockMvc.perform(post("/api/analysis/" + saved.getId() + "/normalize")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isGone())
                .andExpect(jsonPath("$.message", containsString("expired")));
    }

    @Test
    @DisplayName("POST /api/analysis/{sessionId}/normalize returns 400 when OCR text is empty")
    void testNormalizeEmptyRequest() throws Exception {
        FoodAnalysisSession session = new FoodAnalysisSession(
                "token-" + UUID.randomUUID(),
                AnalysisStatus.PROCESSING,
                Instant.now().plusSeconds(900)
        );
        session = sessionRepository.save(session);
        when(normalizationService.normalize(eq(session.getId()), any(NormalizeRequest.class)))
                .thenThrow(new IllegalArgumentException("At least one OCR text section must be provided"));

        NormalizeRequest emptyReq = new NormalizeRequest("", "   ");

        mockMvc.perform(post("/api/analysis/" + session.getId() + "/normalize")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(emptyReq)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("At least one OCR text section")));
    }

    @Test
    @DisplayName("POST /api/analysis/{sessionId}/normalize returns 503 when AI service is unavailable")
    void testNormalizeServiceUnavailable() throws Exception {
        FoodAnalysisSession session = new FoodAnalysisSession(
                "token-" + UUID.randomUUID(),
                AnalysisStatus.PROCESSING,
                Instant.now().plusSeconds(900)
        );
        session = sessionRepository.save(session);
        when(normalizationService.normalize(eq(session.getId()), any(NormalizeRequest.class)))
                .thenThrow(new NormalizationException("AI_SERVICE_UNAVAILABLE", "Food normalization is temporarily unavailable. Please try again."));

        NormalizeRequest req = new NormalizeRequest("Sugar", "Energy 100");

        mockMvc.perform(post("/api/analysis/" + session.getId() + "/normalize")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.message", containsString("Food normalization is temporarily unavailable")));
    }
}
