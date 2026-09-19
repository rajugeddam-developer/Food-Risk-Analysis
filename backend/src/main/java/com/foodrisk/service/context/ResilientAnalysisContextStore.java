package com.foodrisk.service.context;

import com.foodrisk.classification.FoodClassificationResult;
import com.foodrisk.dto.NormalizedFoodData;
import com.foodrisk.nutrition.NutritionAnalysisResult;
import com.foodrisk.risk.IngredientRiskAnalysisResult;
import com.foodrisk.scoring.FoodRiskAssessment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * M12 Resilient Context Store.
 *
 * Primary implementation of AnalysisContextStore. Tries Redis first; if Redis
 * is offline or throws connection/operational exceptions, gracefully falls back
 * to InMemoryAnalysisContextStore.
 *
 * Ensures 100% test reliability and zero crashing when Redis is unavailable.
 */
@Component
@Primary
public class ResilientAnalysisContextStore implements AnalysisContextStore {

    private static final Logger log = LoggerFactory.getLogger(ResilientAnalysisContextStore.class);

    private final InMemoryAnalysisContextStore inMemoryStore;
    private final RedisAnalysisContextStore redisStore;
    private final AtomicBoolean redisWarned = new AtomicBoolean(false);

    public ResilientAnalysisContextStore(
            InMemoryAnalysisContextStore inMemoryStore,
            @Autowired(required = false) RedisAnalysisContextStore redisStore
    ) {
        this.inMemoryStore = inMemoryStore;
        this.redisStore = redisStore;
    }

    @Override
    public void storeNormalizedFoodData(UUID sessionId, NormalizedFoodData data) {
        inMemoryStore.storeNormalizedFoodData(sessionId, data);
        if (redisStore != null) {
            try {
                redisStore.storeNormalizedFoodData(sessionId, data);
            } catch (Exception e) {
                logRedisFallback("storeNormalizedFoodData", e);
            }
        }
    }

    @Override
    public Optional<NormalizedFoodData> getNormalizedFoodData(UUID sessionId) {
        if (redisStore != null) {
            try {
                Optional<NormalizedFoodData> fromRedis = redisStore.getNormalizedFoodData(sessionId);
                if (fromRedis.isPresent()) {
                    return fromRedis;
                }
            } catch (Exception e) {
                logRedisFallback("getNormalizedFoodData", e);
            }
        }
        return inMemoryStore.getNormalizedFoodData(sessionId);
    }

    @Override
    public void storeClassificationResult(UUID sessionId, FoodClassificationResult result) {
        inMemoryStore.storeClassificationResult(sessionId, result);
        if (redisStore != null) {
            try {
                redisStore.storeClassificationResult(sessionId, result);
            } catch (Exception e) {
                logRedisFallback("storeClassificationResult", e);
            }
        }
    }

    @Override
    public Optional<FoodClassificationResult> getClassificationResult(UUID sessionId) {
        if (redisStore != null) {
            try {
                Optional<FoodClassificationResult> fromRedis = redisStore.getClassificationResult(sessionId);
                if (fromRedis.isPresent()) {
                    return fromRedis;
                }
            } catch (Exception e) {
                logRedisFallback("getClassificationResult", e);
            }
        }
        return inMemoryStore.getClassificationResult(sessionId);
    }

    @Override
    public void storeIngredientRiskResult(UUID sessionId, IngredientRiskAnalysisResult result) {
        inMemoryStore.storeIngredientRiskResult(sessionId, result);
        if (redisStore != null) {
            try {
                redisStore.storeIngredientRiskResult(sessionId, result);
            } catch (Exception e) {
                logRedisFallback("storeIngredientRiskResult", e);
            }
        }
    }

    @Override
    public Optional<IngredientRiskAnalysisResult> getIngredientRiskResult(UUID sessionId) {
        if (redisStore != null) {
            try {
                Optional<IngredientRiskAnalysisResult> fromRedis = redisStore.getIngredientRiskResult(sessionId);
                if (fromRedis.isPresent()) {
                    return fromRedis;
                }
            } catch (Exception e) {
                logRedisFallback("getIngredientRiskResult", e);
            }
        }
        return inMemoryStore.getIngredientRiskResult(sessionId);
    }

    @Override
    public void storeNutritionResult(UUID sessionId, NutritionAnalysisResult result) {
        inMemoryStore.storeNutritionResult(sessionId, result);
        if (redisStore != null) {
            try {
                redisStore.storeNutritionResult(sessionId, result);
            } catch (Exception e) {
                logRedisFallback("storeNutritionResult", e);
            }
        }
    }

    @Override
    public Optional<NutritionAnalysisResult> getNutritionResult(UUID sessionId) {
        if (redisStore != null) {
            try {
                Optional<NutritionAnalysisResult> fromRedis = redisStore.getNutritionResult(sessionId);
                if (fromRedis.isPresent()) {
                    return fromRedis;
                }
            } catch (Exception e) {
                logRedisFallback("getNutritionResult", e);
            }
        }
        return inMemoryStore.getNutritionResult(sessionId);
    }

    @Override
    public void storeFoodRiskAssessment(UUID sessionId, FoodRiskAssessment assessment) {
        inMemoryStore.storeFoodRiskAssessment(sessionId, assessment);
        if (redisStore != null) {
            try {
                redisStore.storeFoodRiskAssessment(sessionId, assessment);
            } catch (Exception e) {
                logRedisFallback("storeFoodRiskAssessment", e);
            }
        }
    }

    @Override
    public Optional<FoodRiskAssessment> getFoodRiskAssessment(UUID sessionId) {
        if (redisStore != null) {
            try {
                Optional<FoodRiskAssessment> fromRedis = redisStore.getFoodRiskAssessment(sessionId);
                if (fromRedis.isPresent()) {
                    return fromRedis;
                }
            } catch (Exception e) {
                logRedisFallback("getFoodRiskAssessment", e);
            }
        }
        return inMemoryStore.getFoodRiskAssessment(sessionId);
    }

    @Override
    public void remove(UUID sessionId) {
        inMemoryStore.remove(sessionId);
        if (redisStore != null) {
            try {
                redisStore.remove(sessionId);
            } catch (Exception e) {
                logRedisFallback("remove", e);
            }
        }
    }

    @Override
    public boolean contains(UUID sessionId) {
        if (redisStore != null) {
            try {
                if (redisStore.contains(sessionId)) {
                    return true;
                }
            } catch (Exception e) {
                logRedisFallback("contains", e);
            }
        }
        return inMemoryStore.contains(sessionId);
    }

    private void logRedisFallback(String operation, Exception e) {
        if (redisWarned.compareAndSet(false, true)) {
            log.warn("Redis store unavailable during {}: {}. Resiliently falling back to InMemoryAnalysisContextStore.",
                    operation, e.getMessage());
        } else {
            log.debug("Redis operation {} failed, using in-memory store: {}", operation, e.getMessage());
        }
    }
}
