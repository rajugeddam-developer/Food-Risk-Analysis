package com.foodrisk.service;

import com.foodrisk.dto.NormalizeRequest;
import com.foodrisk.dto.NormalizedFoodData;
import com.foodrisk.dto.NormalizedIngredient;
import com.foodrisk.dto.NormalizedNutrition;
import com.foodrisk.entity.AnalysisStatus;
import com.foodrisk.entity.FoodAnalysisSession;
import com.foodrisk.exception.NormalizationException;
import com.foodrisk.exception.SessionExpiredException;
import com.foodrisk.gemini.GeminiClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FoodNormalizationServiceTest {

    @Mock
    private GeminiClient geminiClient;

    @Mock
    private AnalysisSessionService sessionService;

    private FoodNormalizationService normalizationService;
    private UUID sessionId;
    private FoodAnalysisSession activeSession;

    @BeforeEach
    void setUp() {
        normalizationService = new FoodNormalizationService(geminiClient, sessionService);
        sessionId = UUID.randomUUID();
        activeSession = new FoodAnalysisSession("token", AnalysisStatus.PROCESSING, Instant.now().plusSeconds(900));
        activeSession.setId(sessionId);
    }

    @Test
    @DisplayName("Should successfully validate correctly structured food data")
    void testValidNormalizedData() {
        when(sessionService.getActiveSession(sessionId)).thenReturn(activeSession);

        NormalizedNutrition nutrition = new NormalizedNutrition(
                "per 100g",
                450.0,
                8.0,
                60.0,
                20.0,
                15.0,
                20.0,
                8.0,
                0.0,
                300.0,
                3.0,
                List.of("Energy: 450 kcal")
        );

        NormalizedFoodData rawData = new NormalizedFoodData(
                "Whole Wheat Crackers",
                "30g",
                30.0,
                List.of(new NormalizedIngredient("Wheat Flour", "Wheat F1our", false, null, true)),
                nutrition,
                List.of("Spelling normalized for Wheat Flour")
        );

        when(geminiClient.normalize(anyString(), anyString())).thenReturn(rawData);

        NormalizedFoodData result = normalizationService.normalize(
                sessionId,
                new NormalizeRequest("Wheat F1our", "Energy 450 kcal")
        );

        assertThat(result).isNotNull();
        assertThat(result.productName()).isEqualTo("Whole Wheat Crackers");
        assertThat(result.nutrition().energyKcal()).isEqualTo(450.0);
        assertThat(result.ingredients()).hasSize(1);
    }

    @Test
    @DisplayName("Should reject negative energy or nutrient values")
    void testRejectNegativeNutrients() {
        NormalizedNutrition badNutrition = new NormalizedNutrition(
                "per 100g",
                -100.0, // Negative calories!
                5.0, 50.0, 10.0, null, 10.0, null, null, null, null, List.of()
        );

        NormalizedFoodData badData = new NormalizedFoodData("Item", null, null, List.of(), badNutrition, List.of());

        assertThatThrownBy(() -> normalizationService.validateNormalizedData(badData))
                .isInstanceOf(NormalizationException.class)
                .hasMessageContaining("Invalid negative value for nutrient Energy (kcal)");
    }

    @Test
    @DisplayName("Should reject physically impossible nutrient quantities (>100g per 100g)")
    void testRejectImpossibleNutrientBasis() {
        NormalizedNutrition impossibleNutrition = new NormalizedNutrition(
                "per 100g",
                900.0,
                10.0, 50.0, 10.0, null,
                120.0, // > 100g fat per 100g!
                null, null, null, null, List.of()
        );

        NormalizedFoodData badData = new NormalizedFoodData("Item", null, null, List.of(), impossibleNutrition, List.of());

        assertThatThrownBy(() -> normalizationService.validateNormalizedData(badData))
                .isInstanceOf(NormalizationException.class)
                .hasMessageContaining("Physically impossible total fat");
    }

    @Test
    @DisplayName("Should preserve null values without forcing missing metrics to zero")
    void testPreservesNullsWithoutFabrication() {
        NormalizedNutrition partialNutrition = new NormalizedNutrition(
                "per serving",
                120.0,
                null, // Missing protein
                null, // Missing carb
                null, null, null, null, null, null, null,
                List.of()
        );

        NormalizedFoodData partialData = new NormalizedFoodData(null, null, null, List.of(), partialNutrition, List.of());
        NormalizedFoodData validated = normalizationService.validateNormalizedData(partialData);

        assertThat(validated.nutrition().proteinG()).isNull();
        assertThat(validated.nutrition().carbohydrateG()).isNull();
        assertThat(validated.nutrition().totalFatG()).isNull();
        assertThat(validated.productName()).isNull();
    }

    @Test
    @DisplayName("Should reject normalization on expired sessions (HTTP 410)")
    void testExpiredSessionRejection() {
        when(sessionService.getActiveSession(sessionId))
                .thenThrow(new SessionExpiredException("Analysis session " + sessionId + " has expired."));

        assertThatThrownBy(() -> normalizationService.normalize(sessionId, new NormalizeRequest("Sugar", "Fat 10g")))
                .isInstanceOf(SessionExpiredException.class)
                .hasMessageContaining("expired");
    }

    @Test
    @DisplayName("Should reject empty normalization request")
    void testEmptyRequestRejection() {
        assertThatThrownBy(() -> normalizationService.normalize(sessionId, new NormalizeRequest(null, "  ")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("At least one OCR text section");
    }
}
