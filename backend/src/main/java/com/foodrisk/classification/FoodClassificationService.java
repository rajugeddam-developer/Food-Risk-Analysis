package com.foodrisk.classification;

import com.foodrisk.dto.NormalizedFoodData;
import com.foodrisk.entity.FoodAnalysisSession;
import com.foodrisk.exception.SessionNotReadyException;
import com.foodrisk.service.AnalysisSessionService;
import com.foodrisk.service.context.AnalysisContextStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Service orchestrating product category and intent classification for active analysis sessions.
 */
@Service
public class FoodClassificationService {

    private static final Logger log = LoggerFactory.getLogger(FoodClassificationService.class);

    private final AnalysisSessionService sessionService;
    private final AnalysisContextStore contextStore;
    private final FoodClassificationEngine classificationEngine;

    public FoodClassificationService(
            AnalysisSessionService sessionService,
            AnalysisContextStore contextStore,
            FoodClassificationEngine classificationEngine
    ) {
        this.sessionService = sessionService;
        this.contextStore = contextStore;
        this.classificationEngine = classificationEngine;
    }

    public FoodClassificationResult classifyProduct(UUID sessionId) {
        // Validates session exists and has not expired (throws 404 or 410)
        FoodAnalysisSession session = sessionService.getActiveSession(sessionId);

        // Fetch normalized M6 data from context store
        NormalizedFoodData normalizedData = contextStore.getNormalizedFoodData(sessionId)
                .orElseThrow(() -> new SessionNotReadyException(
                        "Analysis session " + sessionId + " does not contain normalized food data. Please complete M6 normalization first."
                ));

        log.info("Classifying product intent for session {}", session.getId());
        FoodClassificationResult result = classificationEngine.classify(normalizedData);

        // Cache classification result into ephemeral context store
        contextStore.storeClassificationResult(sessionId, result);
        log.info("Completed classification for session {}: category={}, reasonCode={}",
                sessionId, result.category(), result.reasonCode());

        return result;
    }
}
