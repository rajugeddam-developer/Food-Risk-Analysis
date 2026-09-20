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
    private volatile boolean redisOperational = true;
    private volatile long lastRedisFailureTime = 0;
    private static final long REDIS_RETRY_INTERVAL_MS = 60_000;

    public ResilientAnalysisContextStore(
            InMemoryAnalysisContextStore inMemoryStore,
            @Autowired(required = false) RedisAnalysisContextStore redisStore
    ) {
        this.inMemoryStore = inMemoryStore;
        this.redisStore = redisStore;
    }

    private boolean isRedisAvailable() {
        if (redisStore == null) {
            return false;
        }
        if (!redisOperational) {
            if (System.currentTimeMillis() - lastRedisFailureTime > REDIS_RETRY_INTERVAL_MS) {
                // Allow a single retry probe after 60 seconds
                return true;
            }
            return false;
        }
        return true;
    }

    private void handleRedisError(String operation, Exception e) {
        redisOperational = false;
        lastRedisFailureTime = System.currentTimeMillis();
        logRedisFallback(operation, e);
    }

    @Override
    public void storeNormalizedFoodData(UUID sessionId, NormalizedFoodData data) {
        inMemoryStore.storeNormalizedFoodData(sessionId, data);
        if (isRedisAvailable()) {
            try {
                redisStore.storeNormalizedFoodData(sessionId, data);
                redisOperational = true;
            } catch (Exception e) {
                handleRedisError("storeNormalizedFoodData", e);
            }
        }
    }

    @Override
    public Optional<NormalizedFoodData> getNormalizedFoodData(UUID sessionId) {
        if (isRedisAvailable()) {
            try {
                Optional<NormalizedFoodData> fromRedis = redisStore.getNormalizedFoodData(sessionId);
                redisOperational = true;
                if (fromRedis.isPresent()) {
                    return fromRedis;
                }
            } catch (Exception e) {
                handleRedisError("getNormalizedFoodData", e);
            }
        }
        return inMemoryStore.getNormalizedFoodData(sessionId);
    }

    @Override
    public void storeClassificationResult(UUID sessionId, FoodClassificationResult result) {
        inMemoryStore.storeClassificationResult(sessionId, result);
        if (isRedisAvailable()) {
            try {
                redisStore.storeClassificationResult(sessionId, result);
                redisOperational = true;
            } catch (Exception e) {
                handleRedisError("storeClassificationResult", e);
            }
        }
    }

    @Override
    public Optional<FoodClassificationResult> getClassificationResult(UUID sessionId) {
        if (isRedisAvailable()) {
            try {
                Optional<FoodClassificationResult> fromRedis = redisStore.getClassificationResult(sessionId);
                redisOperational = true;
                if (fromRedis.isPresent()) {
                    return fromRedis;
                }
            } catch (Exception e) {
                handleRedisError("getClassificationResult", e);
            }
        }
        return inMemoryStore.getClassificationResult(sessionId);
    }

    @Override
    public void storeIngredientRiskResult(UUID sessionId, IngredientRiskAnalysisResult result) {
        inMemoryStore.storeIngredientRiskResult(sessionId, result);
        if (isRedisAvailable()) {
            try {
                redisStore.storeIngredientRiskResult(sessionId, result);
                redisOperational = true;
            } catch (Exception e) {
                handleRedisError("storeIngredientRiskResult", e);
            }
        }
    }

    @Override
    public Optional<IngredientRiskAnalysisResult> getIngredientRiskResult(UUID sessionId) {
        if (isRedisAvailable()) {
            try {
                Optional<IngredientRiskAnalysisResult> fromRedis = redisStore.getIngredientRiskResult(sessionId);
                redisOperational = true;
                if (fromRedis.isPresent()) {
                    return fromRedis;
                }
            } catch (Exception e) {
                handleRedisError("getIngredientRiskResult", e);
            }
        }
        return inMemoryStore.getIngredientRiskResult(sessionId);
    }

    @Override
    public void storeNutritionResult(UUID sessionId, NutritionAnalysisResult result) {
        inMemoryStore.storeNutritionResult(sessionId, result);
        if (isRedisAvailable()) {
            try {
                redisStore.storeNutritionResult(sessionId, result);
                redisOperational = true;
            } catch (Exception e) {
                handleRedisError("storeNutritionResult", e);
            }
        }
    }

    @Override
    public Optional<NutritionAnalysisResult> getNutritionResult(UUID sessionId) {
        if (isRedisAvailable()) {
            try {
                Optional<NutritionAnalysisResult> fromRedis = redisStore.getNutritionResult(sessionId);
                redisOperational = true;
                if (fromRedis.isPresent()) {
                    return fromRedis;
                }
            } catch (Exception e) {
                handleRedisError("getNutritionResult", e);
            }
        }
        return inMemoryStore.getNutritionResult(sessionId);
    }

    @Override
    public void storeFoodRiskAssessment(UUID sessionId, FoodRiskAssessment assessment) {
        inMemoryStore.storeFoodRiskAssessment(sessionId, assessment);
        if (isRedisAvailable()) {
            try {
                redisStore.storeFoodRiskAssessment(sessionId, assessment);
                redisOperational = true;
            } catch (Exception e) {
                handleRedisError("storeFoodRiskAssessment", e);
            }
        }
    }

    @Override
    public Optional<FoodRiskAssessment> getFoodRiskAssessment(UUID sessionId) {
        if (isRedisAvailable()) {
            try {
                Optional<FoodRiskAssessment> fromRedis = redisStore.getFoodRiskAssessment(sessionId);
                redisOperational = true;
                if (fromRedis.isPresent()) {
                    return fromRedis;
                }
            } catch (Exception e) {
                handleRedisError("getFoodRiskAssessment", e);
            }
        }
        return inMemoryStore.getFoodRiskAssessment(sessionId);
    }

    @Override
    public void remove(UUID sessionId) {
        inMemoryStore.remove(sessionId);
        if (isRedisAvailable()) {
            try {
                redisStore.remove(sessionId);
                redisOperational = true;
            } catch (Exception e) {
                handleRedisError("remove", e);
            }
        }
    }

    @Override
    public boolean contains(UUID sessionId) {
        if (isRedisAvailable()) {
            try {
                if (redisStore.contains(sessionId)) {
                    redisOperational = true;
                    return true;
                }
            } catch (Exception e) {
                handleRedisError("contains", e);
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
