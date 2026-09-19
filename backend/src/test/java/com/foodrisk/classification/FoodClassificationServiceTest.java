package com.foodrisk.classification;

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
class FoodClassificationServiceTest {

    @Mock
    private AnalysisSessionService sessionService;

    @Mock
    private AnalysisContextStore contextStore;

    @Mock
    private FoodClassificationEngine classificationEngine;

    @InjectMocks
    private FoodClassificationService classificationService;

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
    @DisplayName("classifyProduct succeeds when active session and normalized data exist")
    void testClassifyProductSuccess() {
        when(sessionService.getActiveSession(sessionId)).thenReturn(activeSession);
        when(contextStore.getNormalizedFoodData(sessionId)).thenReturn(Optional.of(normalizedFoodData));

        FoodClassificationResult expectedResult = new FoodClassificationResult(
                FoodCategory.HUMAN_FOOD,
                ClassificationCertainty.HIGH,
                0.95,
                ClassificationReasonCode.EXPLICIT_HUMAN_FOOD_MARKER,
                "Standard packaged human food composition.",
                List.of("Wheat Flour"),
                List.of()
        );

        when(classificationEngine.classify(normalizedFoodData)).thenReturn(expectedResult);

        FoodClassificationResult result = classificationService.classifyProduct(sessionId);

        assertNotNull(result);
        assertEquals(FoodCategory.HUMAN_FOOD, result.category());
        assertEquals(ClassificationCertainty.HIGH, result.certainty());
        assertEquals(ClassificationReasonCode.EXPLICIT_HUMAN_FOOD_MARKER, result.reasonCode());

        verify(contextStore).storeClassificationResult(sessionId, expectedResult);
    }

    @Test
    @DisplayName("classifyProduct throws SessionNotReadyException when normalized food data is missing")
    void testClassifyProductMissingNormalizedData() {
        when(sessionService.getActiveSession(sessionId)).thenReturn(activeSession);
        when(contextStore.getNormalizedFoodData(sessionId)).thenReturn(Optional.empty());

        SessionNotReadyException exception = assertThrows(
                SessionNotReadyException.class,
                () -> classificationService.classifyProduct(sessionId)
        );

        assertTrue(exception.getMessage().contains("does not contain normalized food data"));
        verifyNoInteractions(classificationEngine);
        verify(contextStore, never()).storeClassificationResult(any(), any());
    }

    @Test
    @DisplayName("classifyProduct propagates SessionExpiredException from sessionService")
    void testClassifyProductExpiredSession() {
        when(sessionService.getActiveSession(sessionId))
                .thenThrow(new SessionExpiredException("Session has expired"));

        assertThrows(
                SessionExpiredException.class,
                () -> classificationService.classifyProduct(sessionId)
        );

        verifyNoInteractions(contextStore);
        verifyNoInteractions(classificationEngine);
    }

    @Test
    @DisplayName("classifyProduct propagates SessionNotFoundException from sessionService")
    void testClassifyProductNotFoundSession() {
        when(sessionService.getActiveSession(sessionId))
                .thenThrow(new SessionNotFoundException("Session not found"));

        assertThrows(
                SessionNotFoundException.class,
                () -> classificationService.classifyProduct(sessionId)
        );

        verifyNoInteractions(contextStore);
        verifyNoInteractions(classificationEngine);
    }
}
