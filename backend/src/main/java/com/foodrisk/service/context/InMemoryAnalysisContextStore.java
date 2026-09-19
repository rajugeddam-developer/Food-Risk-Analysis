package com.foodrisk.service.context;

import com.foodrisk.classification.FoodClassificationResult;
import com.foodrisk.dto.NormalizedFoodData;
import com.foodrisk.nutrition.NutritionAnalysisResult;
import com.foodrisk.risk.IngredientRiskAnalysisResult;
import com.foodrisk.scoring.FoodRiskAssessment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory thread-safe implementation of AnalysisContextStore using ConcurrentHashMap.
 *
 * Enforces strict 15-minute TTL per session context.
 * Expired entries are discarded and actively cleaned up.
 */
@Component
public class InMemoryAnalysisContextStore implements AnalysisContextStore {

    private static final Logger log = LoggerFactory.getLogger(InMemoryAnalysisContextStore.class);

    private final long ttlMinutes;
    private final Map<UUID, SessionEntry> store = new ConcurrentHashMap<>();

    public InMemoryAnalysisContextStore(@Value("${analysis.session.ttl-minutes:15}") long ttlMinutes) {
        this.ttlMinutes = ttlMinutes;
    }

    private static class SessionEntry {
        NormalizedFoodData normalizedFoodData;
        FoodClassificationResult classificationResult;
        IngredientRiskAnalysisResult ingredientRiskResult;
        NutritionAnalysisResult nutritionResult;
        FoodRiskAssessment foodRiskAssessment;
        final Instant expiresAt;

        SessionEntry(Instant expiresAt) {
            this.expiresAt = expiresAt;
        }

        boolean isExpired() {
            return !expiresAt.isAfter(Instant.now());
        }
    }

    @Override
    public void storeNormalizedFoodData(UUID sessionId, NormalizedFoodData data) {
        if (sessionId == null || data == null) return;
        evictExpiredEntries();

        store.compute(sessionId, (id, existing) -> {
            Instant expiry = (existing != null && !existing.isExpired())
                    ? existing.expiresAt
                    : Instant.now().plus(Duration.ofMinutes(ttlMinutes));
            SessionEntry entry = (existing != null && !existing.isExpired()) ? existing : new SessionEntry(expiry);
            entry.normalizedFoodData = data;
            return entry;
        });

        log.debug("Stored normalized food data in context store for session {}", sessionId);
    }

    @Override
    public Optional<NormalizedFoodData> getNormalizedFoodData(UUID sessionId) {
        if (sessionId == null) return Optional.empty();
        evictExpiredEntries();

        SessionEntry entry = store.get(sessionId);
        if (entry == null || entry.isExpired()) {
            if (entry != null && entry.isExpired()) {
                store.remove(sessionId);
            }
            return Optional.empty();
        }

        return Optional.ofNullable(entry.normalizedFoodData);
    }

    @Override
    public void storeClassificationResult(UUID sessionId, FoodClassificationResult result) {
        if (sessionId == null || result == null) return;
        evictExpiredEntries();

        store.compute(sessionId, (id, existing) -> {
            Instant expiry = (existing != null && !existing.isExpired())
                    ? existing.expiresAt
                    : Instant.now().plus(Duration.ofMinutes(ttlMinutes));
            SessionEntry entry = (existing != null && !existing.isExpired()) ? existing : new SessionEntry(expiry);
            entry.classificationResult = result;
            return entry;
        });

        log.debug("Stored classification result in context store for session {}", sessionId);
    }

    @Override
    public Optional<FoodClassificationResult> getClassificationResult(UUID sessionId) {
        if (sessionId == null) return Optional.empty();
        evictExpiredEntries();

        SessionEntry entry = store.get(sessionId);
        if (entry == null || entry.isExpired()) {
            if (entry != null && entry.isExpired()) {
                store.remove(sessionId);
            }
            return Optional.empty();
        }

        return Optional.ofNullable(entry.classificationResult);
    }

    @Override
    public void storeIngredientRiskResult(UUID sessionId, IngredientRiskAnalysisResult result) {
        if (sessionId == null || result == null) return;
        evictExpiredEntries();

        store.compute(sessionId, (id, existing) -> {
            Instant expiry = (existing != null && !existing.isExpired())
                    ? existing.expiresAt
                    : Instant.now().plus(Duration.ofMinutes(ttlMinutes));
            SessionEntry entry = (existing != null && !existing.isExpired()) ? existing : new SessionEntry(expiry);
            entry.ingredientRiskResult = result;
            return entry;
        });

        log.debug("Stored ingredient risk result in context store for session {}", sessionId);
    }

    @Override
    public Optional<IngredientRiskAnalysisResult> getIngredientRiskResult(UUID sessionId) {
        if (sessionId == null) return Optional.empty();
        evictExpiredEntries();

        SessionEntry entry = store.get(sessionId);
        if (entry == null || entry.isExpired()) {
            if (entry != null && entry.isExpired()) {
                store.remove(sessionId);
            }
            return Optional.empty();
        }

        return Optional.ofNullable(entry.ingredientRiskResult);
    }

    @Override
    public void storeNutritionResult(UUID sessionId, NutritionAnalysisResult result) {
        if (sessionId == null || result == null) return;
        evictExpiredEntries();

        store.compute(sessionId, (id, existing) -> {
            Instant expiry = (existing != null && !existing.isExpired())
                    ? existing.expiresAt
                    : Instant.now().plus(Duration.ofMinutes(ttlMinutes));
            SessionEntry entry = (existing != null && !existing.isExpired()) ? existing : new SessionEntry(expiry);
            entry.nutritionResult = result;
            return entry;
        });

        log.debug("Stored nutrition analysis result in context store for session {}", sessionId);
    }

    @Override
    public Optional<NutritionAnalysisResult> getNutritionResult(UUID sessionId) {
        if (sessionId == null) return Optional.empty();
        evictExpiredEntries();

        SessionEntry entry = store.get(sessionId);
        if (entry == null || entry.isExpired()) {
            if (entry != null && entry.isExpired()) {
                store.remove(sessionId);
            }
            return Optional.empty();
        }

        return Optional.ofNullable(entry.nutritionResult);
    }

    @Override
    public void storeFoodRiskAssessment(UUID sessionId, FoodRiskAssessment assessment) {
        if (sessionId == null || assessment == null) return;
        evictExpiredEntries();

        store.compute(sessionId, (id, existing) -> {
            Instant expiry = (existing != null && !existing.isExpired())
                    ? existing.expiresAt
                    : Instant.now().plus(Duration.ofMinutes(ttlMinutes));
            SessionEntry entry = (existing != null && !existing.isExpired()) ? existing : new SessionEntry(expiry);
            entry.foodRiskAssessment = assessment;
            return entry;
        });

        log.debug("Stored food risk assessment in context store for session {}", sessionId);
    }

    @Override
    public Optional<FoodRiskAssessment> getFoodRiskAssessment(UUID sessionId) {
        if (sessionId == null) return Optional.empty();
        evictExpiredEntries();

        SessionEntry entry = store.get(sessionId);
        if (entry == null || entry.isExpired()) {
            if (entry != null && entry.isExpired()) {
                store.remove(sessionId);
            }
            return Optional.empty();
        }

        return Optional.ofNullable(entry.foodRiskAssessment);
    }

    @Override
    public void remove(UUID sessionId) {
        if (sessionId != null) {
            store.remove(sessionId);
            log.debug("Evicted session {} from context store", sessionId);
        }
    }

    @Override
    public boolean contains(UUID sessionId) {
        if (sessionId == null) return false;
        SessionEntry entry = store.get(sessionId);
        if (entry != null && entry.isExpired()) {
            store.remove(sessionId);
            return false;
        }
        return entry != null;
    }

    private void evictExpiredEntries() {
        // Opportunistic eviction
        store.entrySet().removeIf(e -> e.getValue().isExpired());
    }
}
