package com.foodrisk.risk;

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
 * Service coordinating ingredient and additive risk analysis for active analysis sessions.
 */
@Service
public class IngredientRiskService {

    private static final Logger log = LoggerFactory.getLogger(IngredientRiskService.class);

    private final AnalysisSessionService sessionService;
    private final AnalysisContextStore contextStore;
    private final IngredientRiskEngine riskEngine;

    public IngredientRiskService(
            AnalysisSessionService sessionService,
            AnalysisContextStore contextStore,
            IngredientRiskEngine riskEngine
    ) {
        this.sessionService = sessionService;
        this.contextStore = contextStore;
        this.riskEngine = riskEngine;
    }

    public IngredientRiskAnalysisResult analyzeIngredientRisk(UUID sessionId) {
        // Validates session exists and has not expired (throws 404 or 410)
        FoodAnalysisSession session = sessionService.getActiveSession(sessionId);

        // Fetch normalized M6 data from context store
        NormalizedFoodData normalizedData = contextStore.getNormalizedFoodData(sessionId)
                .orElseThrow(() -> new SessionNotReadyException(
                        "Analysis session " + sessionId + " does not contain normalized food data. Please complete M6 normalization first."
                ));

        log.info("Running ingredient risk evaluation for session {}", session.getId());
        IngredientRiskAnalysisResult result = riskEngine.evaluate(sessionId, normalizedData.ingredients());
        contextStore.storeIngredientRiskResult(sessionId, result);
        return result;
    }
}
