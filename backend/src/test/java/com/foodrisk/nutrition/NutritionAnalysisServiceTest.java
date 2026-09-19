package com.foodrisk.nutrition;

import com.foodrisk.dto.NormalizedFoodData;
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
class NutritionAnalysisServiceTest {

    @Mock
    private AnalysisSessionService sessionService;

    @Mock
    private AnalysisContextStore contextStore;

    @Mock
    private NutritionAnalysisEngine nutritionEngine;

    @InjectMocks
    private NutritionAnalysisService nutritionService;

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
                "per 100g", 250.0, 10.0, 3.0, 0.0, 30.0, 5.0, 2.0, 8.0, 4.0, 150.0, List.of()
        );
        normalizedFoodData = new NormalizedFoodData(
                "Multigrain Bread",
                "40g",
                40.0,
                List.of(),
                nutrition,
                List.of()
        );
    }

    @Test
    @DisplayName("analyzeNutrition succeeds, runs engine, and stores result in context store")
    void testAnalyzeNutritionSuccess() {
        when(sessionService.getActiveSession(sessionId)).thenReturn(activeSession);
        when(contextStore.getNormalizedFoodData(sessionId)).thenReturn(Optional.of(normalizedFoodData));

        NutritionAnalysisResult expectedResult = new NutritionAnalysisResult(
                sessionId,
                DataCompleteness.COMPLETE,
                NutritionBasis.PER_100G,
                40.0,
                List.of(),
                List.of("Low saturated fat"),
                List.of(),
                List.of(),
                List.of("WHO", "FSSAI"),
                "2026.09",
                Instant.now()
        );

        when(nutritionEngine.evaluate(sessionId, normalizedFoodData)).thenReturn(expectedResult);

        NutritionAnalysisResult result = nutritionService.analyzeNutrition(sessionId);

        assertNotNull(result);
        assertEquals(sessionId, result.sessionId());
        assertEquals(DataCompleteness.COMPLETE, result.dataCompleteness());
        verify(contextStore).storeNutritionResult(sessionId, expectedResult);
    }

    @Test
    @DisplayName("analyzeNutrition throws SessionNotReadyException when normalized data missing")
    void testAnalyzeNutritionMissingData() {
        when(sessionService.getActiveSession(sessionId)).thenReturn(activeSession);
        when(contextStore.getNormalizedFoodData(sessionId)).thenReturn(Optional.empty());

        SessionNotReadyException ex = assertThrows(
                SessionNotReadyException.class,
                () -> nutritionService.analyzeNutrition(sessionId)
        );

        assertTrue(ex.getMessage().contains("does not contain normalized food data"));
        verifyNoInteractions(nutritionEngine);
    }

    @Test
    @DisplayName("analyzeNutrition propagates SessionExpiredException")
    void testAnalyzeNutritionExpiredSession() {
        when(sessionService.getActiveSession(sessionId))
                .thenThrow(new SessionExpiredException("Session has expired"));

        assertThrows(
                SessionExpiredException.class,
                () -> nutritionService.analyzeNutrition(sessionId)
        );

        verifyNoInteractions(contextStore);
        verifyNoInteractions(nutritionEngine);
    }

    @Test
    @DisplayName("analyzeNutrition propagates SessionNotFoundException")
    void testAnalyzeNutritionNotFoundSession() {
        when(sessionService.getActiveSession(sessionId))
                .thenThrow(new SessionNotFoundException("Session not found"));

        assertThrows(
                SessionNotFoundException.class,
                () -> nutritionService.analyzeNutrition(sessionId)
        );

        verifyNoInteractions(contextStore);
        verifyNoInteractions(nutritionEngine);
    }
}
