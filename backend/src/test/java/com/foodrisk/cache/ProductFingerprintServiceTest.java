package com.foodrisk.cache;

import com.foodrisk.dto.NormalizedFoodData;
import com.foodrisk.dto.NormalizedIngredient;
import com.foodrisk.dto.NormalizedNutrition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProductFingerprintServiceTest {

    private ProductFingerprintService fingerprintService;

    @BeforeEach
    void setUp() {
        fingerprintService = new ProductFingerprintService();
    }

    @Test
    @DisplayName("Should produce identical hash for same product with different ingredient order")
    void shouldProduceSameFingerprintRegardlessOfIngredientOrder() {
        NormalizedIngredient ing1 = new NormalizedIngredient("Sugar", "Sugar", false, null, false);
        NormalizedIngredient ing2 = new NormalizedIngredient("Wheat Flour", "Wheat Flour", false, null, false);
        NormalizedIngredient ing3 = new NormalizedIngredient("Palm Oil", "Palm Oil", false, null, false);

        NormalizedNutrition nutrition = new NormalizedNutrition("PER_100G", 450.0, 6.0, 65.0, 25.0, 20.0, 18.0, 8.0, 0.0, 300.0, 2.0, Collections.emptyList());

        NormalizedFoodData data1 = new NormalizedFoodData("Digestive Biscuits", "25g", 25.0, List.of(ing1, ing2, ing3), nutrition, Collections.emptyList());
        NormalizedFoodData data2 = new NormalizedFoodData("digestive biscuits ", "25g", 25.0, List.of(ing3, ing1, ing2), nutrition, Collections.emptyList());

        String fp1 = fingerprintService.computeFingerprint(data1);
        String fp2 = fingerprintService.computeFingerprint(data2);

        assertThat(fp1).isEqualTo(fp2);
        assertThat(fp1).hasSize(64); // SHA-256 hex string length
    }

    @Test
    @DisplayName("Should produce different hash for products with different ingredients")
    void shouldProduceDifferentFingerprintForDifferentIngredients() {
        NormalizedIngredient ing1 = new NormalizedIngredient("Sugar", "Sugar", false, null, false);
        NormalizedIngredient ing2 = new NormalizedIngredient("Salt", "Salt", false, null, false);

        NormalizedFoodData data1 = new NormalizedFoodData("Cookie A", null, null, List.of(ing1), null, Collections.emptyList());
        NormalizedFoodData data2 = new NormalizedFoodData("Cookie A", null, null, List.of(ing2), null, Collections.emptyList());

        String fp1 = fingerprintService.computeFingerprint(data1);
        String fp2 = fingerprintService.computeFingerprint(data2);

        assertThat(fp1).isNotEqualTo(fp2);
    }

    @Test
    @DisplayName("Should throw IllegalArgumentException when data is null")
    void shouldThrowWhenDataIsNull() {
        assertThatThrownBy(() -> fingerprintService.computeFingerprint(null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
