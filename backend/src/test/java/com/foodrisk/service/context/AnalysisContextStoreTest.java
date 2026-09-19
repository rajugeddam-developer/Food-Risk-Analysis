package com.foodrisk.service.context;

import com.foodrisk.classification.ClassificationCertainty;
import com.foodrisk.classification.ClassificationReasonCode;
import com.foodrisk.classification.FoodCategory;
import com.foodrisk.classification.FoodClassificationResult;
import com.foodrisk.dto.NormalizedFoodData;
import com.foodrisk.dto.NormalizedNutrition;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class AnalysisContextStoreTest {

    @Test
    @DisplayName("Should store and retrieve normalized food data for an active session")
    void testStoreAndRetrieveNormalizedData() {
        InMemoryAnalysisContextStore store = new InMemoryAnalysisContextStore(15);
        UUID sessionId = UUID.randomUUID();

        NormalizedFoodData data = new NormalizedFoodData(
                "Oat Biscuits",
                "30g",
                30.0,
                List.of(),
                new NormalizedNutrition("per 100g", 450.0, 7.0, 60.0, 15.0, 10.0, 18.0, 6.0, 0.0, 250.0, 3.0, List.of()),
                List.of()
        );

        store.storeNormalizedFoodData(sessionId, data);

        assertThat(store.contains(sessionId)).isTrue();
        Optional<NormalizedFoodData> retrieved = store.getNormalizedFoodData(sessionId);
        assertThat(retrieved).isPresent();
        assertThat(retrieved.get().productName()).isEqualTo("Oat Biscuits");
    }

    @Test
    @DisplayName("Should store and retrieve classification result for an active session")
    void testStoreAndRetrieveClassificationResult() {
        InMemoryAnalysisContextStore store = new InMemoryAnalysisContextStore(15);
        UUID sessionId = UUID.randomUUID();

        FoodClassificationResult result = new FoodClassificationResult(
                FoodCategory.HUMAN_FOOD,
                ClassificationCertainty.HIGH,
                null,
                ClassificationReasonCode.EXPLICIT_HUMAN_FOOD_MARKER,
                "Human food product packaging",
                List.of("Nutrition table present"),
                List.of()
        );

        store.storeClassificationResult(sessionId, result);

        Optional<FoodClassificationResult> retrieved = store.getClassificationResult(sessionId);
        assertThat(retrieved).isPresent();
        assertThat(retrieved.get().category()).isEqualTo(FoodCategory.HUMAN_FOOD);
        assertThat(retrieved.get().reasonCode()).isEqualTo(ClassificationReasonCode.EXPLICIT_HUMAN_FOOD_MARKER);
    }

    @Test
    @DisplayName("Should return empty optional for non-existent session")
    void testMissingSessionReturnsEmpty() {
        InMemoryAnalysisContextStore store = new InMemoryAnalysisContextStore(15);
        UUID nonExistent = UUID.randomUUID();

        assertThat(store.contains(nonExistent)).isFalse();
        assertThat(store.getNormalizedFoodData(nonExistent)).isEmpty();
        assertThat(store.getClassificationResult(nonExistent)).isEmpty();
    }

    @Test
    @DisplayName("Should evict session when explicitly removed")
    void testRemoveSession() {
        InMemoryAnalysisContextStore store = new InMemoryAnalysisContextStore(15);
        UUID sessionId = UUID.randomUUID();

        store.storeNormalizedFoodData(sessionId, new NormalizedFoodData("Item", null, null, List.of(), null, List.of()));
        assertThat(store.contains(sessionId)).isTrue();

        store.remove(sessionId);
        assertThat(store.contains(sessionId)).isFalse();
        assertThat(store.getNormalizedFoodData(sessionId)).isEmpty();
    }

    @Test
    @DisplayName("Should not return data if TTL has expired")
    void testTtlExpiration() throws InterruptedException {
        // Configure with 0 minutes TTL (instant expiry)
        InMemoryAnalysisContextStore store = new InMemoryAnalysisContextStore(0);
        UUID sessionId = UUID.randomUUID();

        store.storeNormalizedFoodData(sessionId, new NormalizedFoodData("Expired Item", null, null, List.of(), null, List.of()));
        Thread.sleep(5);

        // Entry is already in the past or immediately expires
        assertThat(store.getNormalizedFoodData(sessionId)).isEmpty();
        assertThat(store.contains(sessionId)).isFalse();
    }
}
