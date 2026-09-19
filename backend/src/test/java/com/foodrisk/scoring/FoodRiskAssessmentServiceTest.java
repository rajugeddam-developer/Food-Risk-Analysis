package com.foodrisk.scoring;

import com.foodrisk.classification.ClassificationCertainty;
import com.foodrisk.classification.ClassificationReasonCode;
import com.foodrisk.classification.FoodCategory;
import com.foodrisk.classification.FoodClassificationResult;
import com.foodrisk.entity.AnalysisStatus;
import com.foodrisk.entity.FoodAnalysisSession;
import com.foodrisk.exception.SessionExpiredException;
import com.foodrisk.exception.SessionNotFoundException;
import com.foodrisk.exception.SessionNotReadyException;
import com.foodrisk.nutrition.DataCompleteness;
import com.foodrisk.nutrition.NutritionAnalysisResult;
import com.foodrisk.nutrition.NutritionBasis;
import com.foodrisk.risk.IngredientRiskAnalysisResult;
import com.foodrisk.risk.IngredientRiskSummary;
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
class FoodRiskAssessmentServiceTest {

    @Mock
    private AnalysisSessionService sessionService;

    @Mock
    private AnalysisContextStore contextStore;

    @Mock
    private FoodScoringEngine scoringEngine;

    @InjectMocks
    private FoodRiskAssessmentService assessmentService;

    private UUID sessionId;
    private FoodAnalysisSession activeSession;
    private FoodClassificationResult classification;
    private IngredientRiskAnalysisResult ingredientRisk;
    private NutritionAnalysisResult nutrition;

    @BeforeEach
    void setUp() {
        sessionId = UUID.randomUUID();
        activeSession = new FoodAnalysisSession(
                "token-" + sessionId,
                AnalysisStatus.PROCESSING,
                Instant.now().plusSeconds(900)
        );

        classification = new FoodClassificationResult(
                FoodCategory.HUMAN_FOOD,
                ClassificationCertainty.HIGH,
                0.95,
                ClassificationReasonCode.EXPLICIT_HUMAN_FOOD_MARKER,
                "Human packaged food",
                List.of(),
                List.of()
        );

        ingredientRisk = new IngredientRiskAnalysisResult(
                sessionId,
                new IngredientRiskSummary(1, 1, 0, 0, 0, 0, 0, 1, 0),
                List.of(),
                Instant.now()
        );

        nutrition = new NutritionAnalysisResult(
                sessionId,
                DataCompleteness.COMPLETE,
                NutritionBasis.PER_100G,
                null,
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of("WHO"),
                "2026.09",
                Instant.now()
        );
    }

    @Test
    @DisplayName("getAssessment succeeds, executes engine, saves assessment, and marks session COMPLETED")
    void testGetAssessmentSuccess() {
        when(sessionService.getActiveSession(sessionId)).thenReturn(activeSession);
        when(contextStore.getFoodRiskAssessment(sessionId)).thenReturn(Optional.empty());
        when(contextStore.getClassificationResult(sessionId)).thenReturn(Optional.of(classification));
        when(contextStore.getIngredientRiskResult(sessionId)).thenReturn(Optional.of(ingredientRisk));
        when(contextStore.getNutritionResult(sessionId)).thenReturn(Optional.of(nutrition));

        FoodRiskAssessment expected = new FoodRiskAssessment(
                sessionId,
                FoodCategory.HUMAN_FOOD,
                "Human packaged food",
                HumanConsumptionStatus.HUMAN_FOOD,
                90,
                OverallFoodStatus.GOOD_CHOICE,
                AssessmentReliability.HIGH,
                DataCompleteness.COMPLETE,
                ClassificationCertainty.HIGH,
                List.of(),
                ingredientRisk.summary(),
                nutrition,
                List.of(),
                List.of(),
                List.of(),
                new PopulationGuidance("General", "Children", "Dietary"),
                List.of("WHO"),
                List.of(),
                "2026.09",
                "1.0",
                Instant.now()
        );

        when(scoringEngine.assess(sessionId, classification, ingredientRisk, nutrition)).thenReturn(expected);

        FoodRiskAssessment result = assessmentService.getAssessment(sessionId);

        assertNotNull(result);
        assertEquals(90, result.overallScore());
        assertEquals(OverallFoodStatus.GOOD_CHOICE, result.overallStatus());
        verify(contextStore).storeFoodRiskAssessment(sessionId, expected);
        verify(sessionService).updateStatus(activeSession, AnalysisStatus.COMPLETED);
    }

    @Test
    @DisplayName("getAssessment returns cached assessment directly if already present")
    void testGetAssessmentReturnsCached() {
        when(sessionService.getActiveSession(sessionId)).thenReturn(activeSession);

        FoodRiskAssessment cached = new FoodRiskAssessment(
                sessionId,
                FoodCategory.HUMAN_FOOD,
                "Human packaged food",
                HumanConsumptionStatus.HUMAN_FOOD,
                85,
                OverallFoodStatus.GOOD_CHOICE,
                AssessmentReliability.HIGH,
                DataCompleteness.COMPLETE,
                ClassificationCertainty.HIGH,
                List.of(),
                ingredientRisk.summary(),
                nutrition,
                List.of(),
                List.of(),
                List.of(),
                new PopulationGuidance("General", "Children", "Dietary"),
                List.of("WHO"),
                List.of(),
                "2026.09",
                "1.0",
                Instant.now()
        );

        when(contextStore.getFoodRiskAssessment(sessionId)).thenReturn(Optional.of(cached));

        FoodRiskAssessment result = assessmentService.getAssessment(sessionId);

        assertSame(cached, result);
        verifyNoInteractions(scoringEngine);
    }

    @Test
    @DisplayName("getAssessment throws SessionNotReadyException when M7 classification is missing")
    void testGetAssessmentMissingClassification() {
        when(sessionService.getActiveSession(sessionId)).thenReturn(activeSession);
        when(contextStore.getFoodRiskAssessment(sessionId)).thenReturn(Optional.empty());
        when(contextStore.getClassificationResult(sessionId)).thenReturn(Optional.empty());

        SessionNotReadyException ex = assertThrows(
                SessionNotReadyException.class,
                () -> assessmentService.getAssessment(sessionId)
        );

        assertTrue(ex.getMessage().contains("product classification data"));
        verifyNoInteractions(scoringEngine);
    }

    @Test
    @DisplayName("getAssessment throws SessionNotReadyException when M8 ingredient risk is missing")
    void testGetAssessmentMissingIngredientRisk() {
        when(sessionService.getActiveSession(sessionId)).thenReturn(activeSession);
        when(contextStore.getFoodRiskAssessment(sessionId)).thenReturn(Optional.empty());
        when(contextStore.getClassificationResult(sessionId)).thenReturn(Optional.of(classification));
        when(contextStore.getIngredientRiskResult(sessionId)).thenReturn(Optional.empty());

        SessionNotReadyException ex = assertThrows(
                SessionNotReadyException.class,
                () -> assessmentService.getAssessment(sessionId)
        );

        assertTrue(ex.getMessage().contains("ingredient risk data"));
        verifyNoInteractions(scoringEngine);
    }

    @Test
    @DisplayName("getAssessment throws SessionNotReadyException when M9 nutrition is missing")
    void testGetAssessmentMissingNutrition() {
        when(sessionService.getActiveSession(sessionId)).thenReturn(activeSession);
        when(contextStore.getFoodRiskAssessment(sessionId)).thenReturn(Optional.empty());
        when(contextStore.getClassificationResult(sessionId)).thenReturn(Optional.of(classification));
        when(contextStore.getIngredientRiskResult(sessionId)).thenReturn(Optional.of(ingredientRisk));
        when(contextStore.getNutritionResult(sessionId)).thenReturn(Optional.empty());

        SessionNotReadyException ex = assertThrows(
                SessionNotReadyException.class,
                () -> assessmentService.getAssessment(sessionId)
        );

        assertTrue(ex.getMessage().contains("nutrition analysis data"));
        verifyNoInteractions(scoringEngine);
    }

    @Test
    @DisplayName("getAssessment propagates SessionExpiredException")
    void testGetAssessmentExpiredSession() {
        when(sessionService.getActiveSession(sessionId))
                .thenThrow(new SessionExpiredException("Session has expired"));

        assertThrows(
                SessionExpiredException.class,
                () -> assessmentService.getAssessment(sessionId)
        );

        verifyNoInteractions(contextStore);
        verifyNoInteractions(scoringEngine);
    }

    @Test
    @DisplayName("getAssessment propagates SessionNotFoundException")
    void testGetAssessmentNotFoundSession() {
        when(sessionService.getActiveSession(sessionId))
                .thenThrow(new SessionNotFoundException("Session not found"));

        assertThrows(
                SessionNotFoundException.class,
                () -> assessmentService.getAssessment(sessionId)
        );

        verifyNoInteractions(contextStore);
        verifyNoInteractions(scoringEngine);
    }
}
