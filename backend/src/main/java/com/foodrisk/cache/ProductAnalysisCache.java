package com.foodrisk.cache;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.foodrisk.classification.FoodCategory;
import com.foodrisk.classification.FoodClassificationResult;
import com.foodrisk.dto.NormalizedFoodData;
import com.foodrisk.nutrition.NutritionAnalysisResult;
import com.foodrisk.risk.IngredientRiskAnalysisResult;
import com.foodrisk.scoring.AssessmentReliability;
import com.foodrisk.scoring.FoodRiskAssessment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * M12 Duplicate Product Analysis Cache.
 *
 * Provides a 24-hour cache for identical food products by fingerprint.
 * Enforces rule-version safety (scoringRuleVersion: 1.0, nutritionReferenceVersion: 2026.09).
 * Rejects LOW reliability products or UNKNOWN food categories from caching.
 */
@Service
public class ProductAnalysisCache {

    private static final Logger log = LoggerFactory.getLogger(ProductAnalysisCache.class);

    public static final String CURRENT_SCORING_RULE_VERSION = "1.0";
    public static final String CURRENT_NUTRITION_REFERENCE_VERSION = "2026.09";
    private static final Duration TTL = Duration.ofHours(24);
    private static final String KEY_PREFIX = "analysis:cache:product:";

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final Map<String, CacheEntry> inMemoryCache = new ConcurrentHashMap<>();

    private static class CacheEntry {
        final CachedProductAnalysis data;
        final Instant expiresAt;

        CacheEntry(CachedProductAnalysis data, Instant expiresAt) {
            this.data = data;
            this.expiresAt = expiresAt;
        }

        boolean isExpired() {
            return !expiresAt.isAfter(Instant.now());
        }
    }

    public ProductAnalysisCache(
            @Autowired(required = false) StringRedisTemplate redisTemplate,
            ObjectMapper objectMapper
    ) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    /**
     * Retrieves cached analysis for a product fingerprint if present, unexpired, and matching versions.
     */
    public Optional<CachedProductAnalysis> get(String fingerprint) {
        if (fingerprint == null || fingerprint.isBlank()) {
            return Optional.empty();
        }

        // 1. Try Redis
        if (redisTemplate != null) {
            try {
                String key = KEY_PREFIX + fingerprint;
                String json = redisTemplate.opsForValue().get(key);
                if (json != null && !json.isBlank()) {
                    CachedProductAnalysis cached = objectMapper.readValue(json, CachedProductAnalysis.class);
                    if (isVersionValid(cached)) {
                        log.debug("Cache hit in Redis for product fingerprint {}", fingerprint);
                        return Optional.of(cached);
                    } else {
                        log.info("Evicting outdated cached analysis in Redis for fingerprint {}", fingerprint);
                        redisTemplate.delete(key);
                    }
                }
            } catch (Exception e) {
                log.debug("Redis read failed for fingerprint {}: {}", fingerprint, e.getMessage());
            }
        }

        // 2. Fallback to in-memory cache
        CacheEntry entry = inMemoryCache.get(fingerprint);
        if (entry != null) {
            if (entry.isExpired()) {
                inMemoryCache.remove(fingerprint);
                return Optional.empty();
            }
            if (isVersionValid(entry.data)) {
                log.debug("Cache hit in memory for product fingerprint {}", fingerprint);
                return Optional.of(entry.data);
            } else {
                inMemoryCache.remove(fingerprint);
            }
        }

        return Optional.empty();
    }

    /**
     * Caches analysis results for 24 hours if reliability is not LOW and category is not UNKNOWN.
     */
    public void put(
            String fingerprint,
            NormalizedFoodData normalizedFoodData,
            FoodClassificationResult classificationResult,
            IngredientRiskAnalysisResult ingredientRiskResult,
            NutritionAnalysisResult nutritionResult,
            FoodRiskAssessment foodRiskAssessment
    ) {
        if (fingerprint == null || fingerprint.isBlank() || foodRiskAssessment == null) {
            return;
        }

        // Safety rule: Never cache products with LOW reliability
        if (foodRiskAssessment.assessmentReliability() == AssessmentReliability.LOW) {
            log.info("Skipping cache for fingerprint {}: Reliability is LOW", fingerprint);
            return;
        }

        // Safety rule: Never cache products with UNKNOWN category
        if (classificationResult == null || classificationResult.category() == FoodCategory.UNKNOWN) {
            log.info("Skipping cache for fingerprint {}: Category is UNKNOWN", fingerprint);
            return;
        }

        CachedProductAnalysis cached = new CachedProductAnalysis(
                fingerprint,
                CURRENT_SCORING_RULE_VERSION,
                CURRENT_NUTRITION_REFERENCE_VERSION,
                normalizedFoodData,
                classificationResult,
                ingredientRiskResult,
                nutritionResult,
                foodRiskAssessment
        );

        // Store in memory
        inMemoryCache.put(fingerprint, new CacheEntry(cached, Instant.now().plus(TTL)));

        // Store in Redis if available
        if (redisTemplate != null) {
            try {
                String key = KEY_PREFIX + fingerprint;
                String json = objectMapper.writeValueAsString(cached);
                redisTemplate.opsForValue().set(key, json, TTL);
                log.debug("Cached analysis in Redis for product fingerprint {}", fingerprint);
            } catch (Exception e) {
                log.debug("Redis write failed for fingerprint {}: {}", fingerprint, e.getMessage());
            }
        }
    }

    private boolean isVersionValid(CachedProductAnalysis cached) {
        return cached != null
                && CURRENT_SCORING_RULE_VERSION.equals(cached.getScoringRuleVersion())
                && CURRENT_NUTRITION_REFERENCE_VERSION.equals(cached.getNutritionReferenceVersion());
    }

    public void clear() {
        inMemoryCache.clear();
    }
}
