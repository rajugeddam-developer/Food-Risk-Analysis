package com.foodrisk.service.context;

import com.foodrisk.classification.ClassificationCertainty;
import com.foodrisk.classification.ClassificationReasonCode;
import com.foodrisk.classification.FoodCategory;
import com.foodrisk.classification.FoodClassificationResult;
import com.foodrisk.dto.NormalizedFoodData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.RedisConnectionFailureException;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ResilientAnalysisContextStoreTest {

    private InMemoryAnalysisContextStore inMemoryStore;

    @Mock
    private RedisAnalysisContextStore mockRedisStore;

    private ResilientAnalysisContextStore resilientStore;

    @BeforeEach
    void setUp() {
        inMemoryStore = new InMemoryAnalysisContextStore(15);
        resilientStore = new ResilientAnalysisContextStore(inMemoryStore, mockRedisStore);
    }

    @Test
    @DisplayName("Should gracefully fallback to in-memory store when Redis throws RedisConnectionFailureException")
    void shouldFallbackToInMemoryWhenRedisFails() {
        UUID sessionId = UUID.randomUUID();
        NormalizedFoodData testData = new NormalizedFoodData("Test Biscuits", null, 100.0, Collections.emptyList(), null, Collections.emptyList());

        // Redis throws on store and get
        doThrow(new RedisConnectionFailureException("Redis server unreachable"))
                .when(mockRedisStore).storeNormalizedFoodData(any(), any());
        lenient().doThrow(new RedisConnectionFailureException("Redis server unreachable"))
                .when(mockRedisStore).getNormalizedFoodData(any());

        // Call resilient store
        resilientStore.storeNormalizedFoodData(sessionId, testData);
        Optional<NormalizedFoodData> retrieved = resilientStore.getNormalizedFoodData(sessionId);

        // Verify fallback succeeded
        assertThat(retrieved).isPresent();
        assertThat(retrieved.get().productName()).isEqualTo("Test Biscuits");
    }

    @Test
    @DisplayName("Should use Redis data when Redis is healthy and returns value")
    void shouldUseRedisWhenHealthy() {
        UUID sessionId = UUID.randomUUID();
        FoodClassificationResult redisResult = new FoodClassificationResult(
                FoodCategory.HUMAN_FOOD,
                ClassificationCertainty.HIGH,
                0.98,
                ClassificationReasonCode.EXPLICIT_HUMAN_FOOD_MARKER,
                "Explicit human food label",
                List.of("Biscuits"),
                Collections.emptyList()
        );

        when(mockRedisStore.getClassificationResult(sessionId)).thenReturn(Optional.of(redisResult));

        Optional<FoodClassificationResult> result = resilientStore.getClassificationResult(sessionId);

        assertThat(result).isPresent();
        assertThat(result.get().category()).isEqualTo(FoodCategory.HUMAN_FOOD);
        assertThat(result.get().certainty()).isEqualTo(ClassificationCertainty.HIGH);
    }

    @Test
    @DisplayName("Should work cleanly when redisStore is null (Redis disabled)")
    void shouldWorkWhenRedisStoreIsNull() {
        ResilientAnalysisContextStore storeNoRedis = new ResilientAnalysisContextStore(inMemoryStore, null);
        UUID sessionId = UUID.randomUUID();
        NormalizedFoodData testData = new NormalizedFoodData("No Redis Food", null, 50.0, Collections.emptyList(), null, Collections.emptyList());

        storeNoRedis.storeNormalizedFoodData(sessionId, testData);
        Optional<NormalizedFoodData> retrieved = storeNoRedis.getNormalizedFoodData(sessionId);

        assertThat(retrieved).isPresent();
        assertThat(retrieved.get().productName()).isEqualTo("No Redis Food");
        assertThat(storeNoRedis.contains(sessionId)).isTrue();

        storeNoRedis.remove(sessionId);
        assertThat(storeNoRedis.contains(sessionId)).isFalse();
    }
}
