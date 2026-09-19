package com.foodrisk.nutrition;

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
 * Service orchestrating nutrition analysis against WHO/FSSAI reference standards for active analysis sessions.
 */
@Service
public class NutritionAnalysisService {

    private static final Logger log = LoggerFactory.getLogger(NutritionAnalysisService.class);

    private final AnalysisSessionService sessionService;
    private final AnalysisContextStore contextStore;
    private final NutritionAnalysisEngine nutritionEngine;

    public NutritionAnalysisService(
            AnalysisSessionService sessionService,
            AnalysisContextStore contextStore,
            NutritionAnalysisEngine nutritionEngine
    ) {
        this.sessionService = sessionService;
        this.contextStore = contextStore;
        this.nutritionEngine = nutritionEngine;
    }

    public NutritionAnalysisResult analyzeNutrition(UUID sessionId) {
        // Validates session exists and has not expired (throws 404 or 410)
        FoodAnalysisSession session = sessionService.getActiveSession(sessionId);

        // Fetch normalized M6 data from context store
        NormalizedFoodData normalizedData = contextStore.getNormalizedFoodData(sessionId)
                .orElseThrow(() -> new SessionNotReadyException(
                        "Analysis session " + sessionId + " does not contain normalized food data. Please complete M6 normalization first."
                ));

        log.info("Running nutrition standards evaluation for session {}", session.getId());
        NutritionAnalysisResult result = nutritionEngine.evaluate(sessionId, normalizedData);

        // Cache nutrition result into ephemeral context store
        contextStore.storeNutritionResult(sessionId, result);
        log.info("Completed nutrition evaluation for session {}: completeness={}, findingsCount={}",
                sessionId, result.dataCompleteness(), result.findings().size());

        return result;
    }
}
