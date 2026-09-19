package com.foodrisk.service.context;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.foodrisk.classification.FoodClassificationResult;
import com.foodrisk.dto.NormalizedFoodData;
import com.foodrisk.nutrition.NutritionAnalysisResult;
import com.foodrisk.risk.IngredientRiskAnalysisResult;
import com.foodrisk.scoring.FoodRiskAssessment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

/**
 * M12 Redis-backed implementation of AnalysisContextStore.
 *
 * Stores transient session analysis state with a 15-minute TTL in Redis.
 * Serializes entities to JSON using Jackson ObjectMapper.
 */
@Component
public class RedisAnalysisContextStore implements AnalysisContextStore {

    private static final Logger log = LoggerFactory.getLogger(RedisAnalysisContextStore.class);

    private static final String KEY_PREFIX = "analysis:session:";
    private static final String SUFFIX_NORMALIZED = ":normalized";
    private static final String SUFFIX_CLASSIFICATION = ":classification";
    private static final String SUFFIX_INGREDIENT_RISK = ":ingredient_risk";
    private static final String SUFFIX_NUTRITION = ":nutrition";
    private static final String SUFFIX_ASSESSMENT = ":assessment";
    private static final String SUFFIX_ACTIVE = ":active";

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final Duration ttl;

    public RedisAnalysisContextStore(
            StringRedisTemplate redisTemplate,
            ObjectMapper objectMapper,
            @Value("${analysis.session.ttl-minutes:15}") long ttlMinutes
    ) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.ttl = Duration.ofMinutes(ttlMinutes);
    }

    @Override
    public void storeNormalizedFoodData(UUID sessionId, NormalizedFoodData data) {
        if (sessionId == null || data == null) return;
        writeJson(KEY_PREFIX + sessionId + SUFFIX_NORMALIZED, data);
        markActive(sessionId);
    }

    @Override
    public Optional<NormalizedFoodData> getNormalizedFoodData(UUID sessionId) {
        if (sessionId == null) return Optional.empty();
        return readJson(KEY_PREFIX + sessionId + SUFFIX_NORMALIZED, NormalizedFoodData.class);
    }

    @Override
    public void storeClassificationResult(UUID sessionId, FoodClassificationResult result) {
        if (sessionId == null || result == null) return;
        writeJson(KEY_PREFIX + sessionId + SUFFIX_CLASSIFICATION, result);
        markActive(sessionId);
    }

    @Override
    public Optional<FoodClassificationResult> getClassificationResult(UUID sessionId) {
        if (sessionId == null) return Optional.empty();
        return readJson(KEY_PREFIX + sessionId + SUFFIX_CLASSIFICATION, FoodClassificationResult.class);
    }

    @Override
    public void storeIngredientRiskResult(UUID sessionId, IngredientRiskAnalysisResult result) {
        if (sessionId == null || result == null) return;
        writeJson(KEY_PREFIX + sessionId + SUFFIX_INGREDIENT_RISK, result);
        markActive(sessionId);
    }

    @Override
    public Optional<IngredientRiskAnalysisResult> getIngredientRiskResult(UUID sessionId) {
        if (sessionId == null) return Optional.empty();
        return readJson(KEY_PREFIX + sessionId + SUFFIX_INGREDIENT_RISK, IngredientRiskAnalysisResult.class);
    }

    @Override
    public void storeNutritionResult(UUID sessionId, NutritionAnalysisResult result) {
        if (sessionId == null || result == null) return;
        writeJson(KEY_PREFIX + sessionId + SUFFIX_NUTRITION, result);
        markActive(sessionId);
    }

    @Override
    public Optional<NutritionAnalysisResult> getNutritionResult(UUID sessionId) {
        if (sessionId == null) return Optional.empty();
        return readJson(KEY_PREFIX + sessionId + SUFFIX_NUTRITION, NutritionAnalysisResult.class);
    }

    @Override
    public void storeFoodRiskAssessment(UUID sessionId, FoodRiskAssessment assessment) {
        if (sessionId == null || assessment == null) return;
        writeJson(KEY_PREFIX + sessionId + SUFFIX_ASSESSMENT, assessment);
        markActive(sessionId);
    }

    @Override
    public Optional<FoodRiskAssessment> getFoodRiskAssessment(UUID sessionId) {
        if (sessionId == null) return Optional.empty();
        return readJson(KEY_PREFIX + sessionId + SUFFIX_ASSESSMENT, FoodRiskAssessment.class);
    }

    @Override
    public void remove(UUID sessionId) {
        if (sessionId == null) return;
        redisTemplate.delete(KEY_PREFIX + sessionId + SUFFIX_NORMALIZED);
        redisTemplate.delete(KEY_PREFIX + sessionId + SUFFIX_CLASSIFICATION);
        redisTemplate.delete(KEY_PREFIX + sessionId + SUFFIX_INGREDIENT_RISK);
        redisTemplate.delete(KEY_PREFIX + sessionId + SUFFIX_NUTRITION);
        redisTemplate.delete(KEY_PREFIX + sessionId + SUFFIX_ASSESSMENT);
        redisTemplate.delete(KEY_PREFIX + sessionId + SUFFIX_ACTIVE);
    }

    @Override
    public boolean contains(UUID sessionId) {
        if (sessionId == null) return false;
        Boolean exists = redisTemplate.hasKey(KEY_PREFIX + sessionId + SUFFIX_ACTIVE);
        return Boolean.TRUE.equals(exists);
    }

    private void markActive(UUID sessionId) {
        redisTemplate.opsForValue().set(KEY_PREFIX + sessionId + SUFFIX_ACTIVE, "1", ttl);
    }

    private void writeJson(String key, Object value) {
        try {
            String json = objectMapper.writeValueAsString(value);
            redisTemplate.opsForValue().set(key, json, ttl);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize object for key {}: {}", key, e.getMessage(), e);
            throw new RuntimeException("Redis serialization error", e);
        }
    }

    private <T> Optional<T> readJson(String key, Class<T> clazz) {
        String json = redisTemplate.opsForValue().get(key);
        if (json == null || json.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(objectMapper.readValue(json, clazz));
        } catch (JsonProcessingException e) {
            log.error("Failed to deserialize object for key {}: {}", key, e.getMessage(), e);
            return Optional.empty();
        }
    }
}
