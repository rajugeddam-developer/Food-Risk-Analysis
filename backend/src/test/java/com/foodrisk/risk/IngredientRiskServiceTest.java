package com.foodrisk.risk;

import com.foodrisk.dto.NormalizedFoodData;
import com.foodrisk.dto.NormalizedIngredient;
import com.foodrisk.dto.NormalizedNutrition;
import com.foodrisk.entity.AnalysisStatus;
import com.foodrisk.entity.FoodAnalysisSession;
import com.foodrisk.exception.SessionExpiredException;
import com.foodrisk.exception.SessionNotFoundException;
import com.foodrisk.exception.SessionNotReadyException;
import com.foodrisk.service.AnalysisSessionService;
import com.foodrisk.service.context.AnalysisContextStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class IngredientRiskServiceTest {

    @Mock
    private AnalysisSessionService sessionService;

    @Mock
    private AnalysisContextStore contextStore;

    @Mock
    private IngredientRiskEngine riskEngine;

    @InjectMocks
    private IngredientRiskService riskService;

    private UUID sessionId;
    private FoodAnalysisSession activeSession;
    private NormalizedFoodData normalizedFoodData;

    @BeforeEach
    void setUp() {
        sessionId = UUID.randomUUID();
        activeSession = new FoodAnalysisSession(
                "token-" + sessionId,
                AnalysisStatus.PROCESSING,
                Instant.now().plusSeconds(900)
        );

        NormalizedNutrition nutrition = new NormalizedNutrition(
                "per 100g", 250.0, 10.0, 30.0, 5.0, 5.0, 10.0, 2.0, 0.0, 150.0, 1.0, List.of()
        );
        normalizedFoodData = new NormalizedFoodData(
                "Whole Wheat Bread",
                "50g",
                50.0,
                List.of(new NormalizedIngredient("Wheat Flour", "Wheat Flour", false, null, false)),
                nutrition,
                List.of()
        );
    }

    @Test
    @DisplayName("analyzeIngredientRisk succeeds when active session and normalized data exist")
    void testAnalyzeIngredientRiskSuccess() {
        when(sessionService.getActiveSession(sessionId)).thenReturn(activeSession);
        when(contextStore.getNormalizedFoodData(sessionId)).thenReturn(Optional.of(normalizedFoodData));

        IngredientRiskSummary summary = new IngredientRiskSummary(1, 1, 0, 0, 0, 0, 0, 1, 0);
        IngredientRiskAnalysisResult expectedResult = new IngredientRiskAnalysisResult(
                sessionId,
                summary,
                List.of(new IngredientRiskItem(
                        "Wheat Flour",
                        "Wheat Flour",
                        IngredientRiskLevel.NO_CONCERN,
                        List.of("Common culinary food ingredient."),
                        List.of("FSSAI"),
                        EvidenceStatus.PARTIALLY_SUPPORTED,
                        null,
                        null,
                        RegulatoryStatus.PERMITTED
                )),
                Instant.now()
        );

        when(riskEngine.evaluate(sessionId, normalizedFoodData.ingredients())).thenReturn(expectedResult);

        IngredientRiskAnalysisResult result = riskService.analyzeIngredientRisk(sessionId);

        assertNotNull(result);
        assertEquals(sessionId, result.sessionId());
        assertEquals(1, result.items().size());
        assertEquals(1, result.summary().noConcernIngredients());
    }

    @Test
    @DisplayName("analyzeIngredientRisk throws SessionNotReadyException when normalized data is missing")
    void testAnalyzeIngredientRiskMissingData() {
        when(sessionService.getActiveSession(sessionId)).thenReturn(activeSession);
        when(contextStore.getNormalizedFoodData(sessionId)).thenReturn(Optional.empty());

        SessionNotReadyException exception = assertThrows(
                SessionNotReadyException.class,
                () -> riskService.analyzeIngredientRisk(sessionId)
        );

        assertTrue(exception.getMessage().contains("does not contain normalized food data"));
        verifyNoInteractions(riskEngine);
    }

    @Test
    @DisplayName("analyzeIngredientRisk propagates SessionExpiredException")
    void testAnalyzeIngredientRiskExpiredSession() {
        when(sessionService.getActiveSession(sessionId))
                .thenThrow(new SessionExpiredException("Session has expired"));

        assertThrows(
                SessionExpiredException.class,
                () -> riskService.analyzeIngredientRisk(sessionId)
        );

        verifyNoInteractions(contextStore);
        verifyNoInteractions(riskEngine);
    }

    @Test
    @DisplayName("analyzeIngredientRisk propagates SessionNotFoundException")
    void testAnalyzeIngredientRiskNotFoundSession() {
        when(sessionService.getActiveSession(sessionId))
                .thenThrow(new SessionNotFoundException("Session not found"));

        assertThrows(
                SessionNotFoundException.class,
                () -> riskService.analyzeIngredientRisk(sessionId)
        );

        verifyNoInteractions(contextStore);
        verifyNoInteractions(riskEngine);
    }
}
