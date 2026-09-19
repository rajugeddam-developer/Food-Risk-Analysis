package com.foodrisk.scoring;

import com.foodrisk.classification.FoodClassificationResult;
import com.foodrisk.entity.AnalysisStatus;
import com.foodrisk.entity.FoodAnalysisSession;
import com.foodrisk.exception.SessionNotReadyException;
import com.foodrisk.nutrition.NutritionAnalysisResult;
import com.foodrisk.risk.IngredientRiskAnalysisResult;
import com.foodrisk.service.AnalysisSessionService;
import com.foodrisk.service.context.AnalysisContextStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.UUID;

/**
 * Service synthesizing final Food Awareness Assessment for an active analysis session.
 *
 * Integrates M7 (classification), M8 (ingredient risk), and M9 (nutrition standards) from the
 * ephemeral AnalysisContextStore into a comprehensive assessment with the Food Awareness Score (0–100).
 */
@Service
public class FoodRiskAssessmentService {

    private static final Logger log = LoggerFactory.getLogger(FoodRiskAssessmentService.class);

    private final AnalysisSessionService sessionService;
    private final AnalysisContextStore contextStore;
    private final FoodScoringEngine scoringEngine;

    public FoodRiskAssessmentService(
            AnalysisSessionService sessionService,
            AnalysisContextStore contextStore,
            FoodScoringEngine scoringEngine
    ) {
        this.sessionService = sessionService;
        this.contextStore = contextStore;
        this.scoringEngine = scoringEngine;
    }

    public FoodRiskAssessment getAssessment(UUID sessionId) {
        // Validates session exists and has not expired (throws 404 or 410)
        FoodAnalysisSession session = sessionService.getActiveSession(sessionId);

        // Check if assessment was already computed for this active session
        Optional<FoodRiskAssessment> existing = contextStore.getFoodRiskAssessment(sessionId);
        if (existing.isPresent()) {
            log.debug("Returning cached food risk assessment for session {}", sessionId);
            return existing.get();
        }

        // Retrieve required pipeline results from ephemeral context store
        FoodClassificationResult classification = contextStore.getClassificationResult(sessionId)
                .orElseThrow(() -> new SessionNotReadyException(
                        "Analysis session " + sessionId + " does not contain product classification data. Please complete M7 first."
                ));

        IngredientRiskAnalysisResult ingredientRisk = contextStore.getIngredientRiskResult(sessionId)
                .orElseThrow(() -> new SessionNotReadyException(
                        "Analysis session " + sessionId + " does not contain ingredient risk data. Please complete M8 first."
                ));

        NutritionAnalysisResult nutrition = contextStore.getNutritionResult(sessionId)
                .orElseThrow(() -> new SessionNotReadyException(
                        "Analysis session " + sessionId + " does not contain nutrition analysis data. Please complete M9 first."
                ));

        log.info("Generating Food Awareness Assessment for session {}", sessionId);
        FoodRiskAssessment assessment = scoringEngine.assess(sessionId, classification, ingredientRisk, nutrition);

        // Store assessment in ephemeral context store
        contextStore.storeFoodRiskAssessment(sessionId, assessment);

        // Update session lifecycle status to COMPLETED
        sessionService.updateStatus(session, AnalysisStatus.COMPLETED);
        log.info("Completed food risk assessment for session {}: score={}, status={}",
                sessionId, assessment.overallScore(), assessment.overallStatus());

        return assessment;
    }
}
