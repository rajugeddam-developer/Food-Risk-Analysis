package com.foodrisk.service.context;

import com.foodrisk.classification.FoodClassificationResult;
import com.foodrisk.dto.NormalizedFoodData;
import com.foodrisk.nutrition.NutritionAnalysisResult;
import com.foodrisk.risk.IngredientRiskAnalysisResult;
import com.foodrisk.scoring.FoodRiskAssessment;

import java.util.Optional;
import java.util.UUID;

/**
 * Strategy interface for storing and retrieving transient session analysis state.
 *
 * Designed to cleanly decouple application services from in-memory or remote cache implementations,
 * enabling seamless migration to Redis (M12) without altering domain logic.
 */
public interface AnalysisContextStore {

    /**
     * Stores normalized food data for an active session.
     */
    void storeNormalizedFoodData(UUID sessionId, NormalizedFoodData data);

    /**
     * Retrieves normalized food data if present and not expired.
     */
    Optional<NormalizedFoodData> getNormalizedFoodData(UUID sessionId);

    /**
     * Stores food classification result for an active session.
     */
    void storeClassificationResult(UUID sessionId, FoodClassificationResult result);

    /**
     * Retrieves food classification result if present and not expired.
     */
    Optional<FoodClassificationResult> getClassificationResult(UUID sessionId);

    /**
     * Stores ingredient risk analysis result for an active session.
     */
    void storeIngredientRiskResult(UUID sessionId, IngredientRiskAnalysisResult result);

    /**
     * Retrieves ingredient risk analysis result if present and not expired.
     */
    Optional<IngredientRiskAnalysisResult> getIngredientRiskResult(UUID sessionId);

    /**
     * Stores nutrition analysis result for an active session.
     */
    void storeNutritionResult(UUID sessionId, NutritionAnalysisResult result);

    /**
     * Retrieves nutrition analysis result if present and not expired.
     */
    Optional<NutritionAnalysisResult> getNutritionResult(UUID sessionId);

    /**
     * Stores food risk assessment for an active session.
     */
    void storeFoodRiskAssessment(UUID sessionId, FoodRiskAssessment assessment);

    /**
     * Retrieves food risk assessment if present and not expired.
     */
    Optional<FoodRiskAssessment> getFoodRiskAssessment(UUID sessionId);

    /**
     * Evicts all transient context for a session.
     */
    void remove(UUID sessionId);

    /**
     * Checks if active context exists for a session.
     */
    boolean contains(UUID sessionId);
}
