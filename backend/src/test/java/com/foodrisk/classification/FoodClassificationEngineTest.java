package com.foodrisk.classification;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.foodrisk.dto.NormalizedFoodData;
import com.foodrisk.dto.NormalizedIngredient;
import com.foodrisk.dto.NormalizedNutrition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.DefaultResourceLoader;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class FoodClassificationEngineTest {

    private FoodClassificationEngine engine;

    @BeforeEach
    void setUp() {
        engine = new FoodClassificationEngine(new DefaultResourceLoader(), new ObjectMapper());
        engine.loadRules();
    }

    @Test
    @DisplayName("Should classify standard packaged human food product as HUMAN_FOOD with HIGH certainty")
    void testStandardHumanFood() {
        NormalizedNutrition nutrition = new NormalizedNutrition(
                "per 100g", 480.0, 6.0, 68.0, 24.0, 20.0, 20.0, 9.0, 0.0, 320.0, 2.0, List.of()
        );
        NormalizedFoodData humanProduct = new NormalizedFoodData(
                "Crunchy Chocolate Biscuits",
                "30g",
                30.0,
                List.of(
                        new NormalizedIngredient("Wheat Flour", "Wheat Flour", false, null, false),
                        new NormalizedIngredient("Sugar", "Sugar", false, null, false),
                        new NormalizedIngredient("Palm Oil", "Palm Oil", false, null, false)
                ),
                nutrition,
                List.of()
        );

        FoodClassificationResult result = engine.classify(humanProduct);

        assertThat(result.category()).isEqualTo(FoodCategory.HUMAN_FOOD);
        assertThat(result.certainty()).isEqualTo(ClassificationCertainty.HIGH);
        assertThat(result.reasonCode()).isEqualTo(ClassificationReasonCode.EXPLICIT_HUMAN_FOOD_MARKER);
        assertThat(result.evidence()).isNotEmpty();
        assertThat(result.warnings()).isEmpty();
    }

    @Test
    @DisplayName("Should classify pet food as PET_FOOD even if a nutrition table is present (hierarchy check)")
    void testExplicitPetFoodHierarchy() {
        NormalizedNutrition nutrition = new NormalizedNutrition(
                "per 100g", 350.0, 25.0, 40.0, 2.0, null, 12.0, null, null, 400.0, 4.0, List.of()
        );
        NormalizedFoodData petProduct = new NormalizedFoodData(
                "Adult Dog Food Chicken & Rice Formula",
                "100g",
                100.0,
                List.of(
                        new NormalizedIngredient("Chicken Meal", "Chicken Meal", false, null, false),
                        new NormalizedIngredient("Whole Grain Corn", "Whole Grain Corn", false, null, false)
                ),
                nutrition,
                List.of()
        );

        FoodClassificationResult result = engine.classify(petProduct);

        assertThat(result.category()).isEqualTo(FoodCategory.PET_FOOD);
        assertThat(result.certainty()).isEqualTo(ClassificationCertainty.HIGH);
        assertThat(result.reasonCode()).isEqualTo(ClassificationReasonCode.EXPLICIT_PET_FOOD_MARKER);
        assertThat(result.warnings()).anyMatch(w -> w.contains("Not intended for human consumption"));
    }

    @Test
    @DisplayName("Should classify animal livestock feed as ANIMAL_FEED")
    void testAnimalFeedClassification() {
        NormalizedFoodData feedProduct = new NormalizedFoodData(
                "Commercial Cattle Feed Pellets",
                null,
                null,
                List.of(new NormalizedIngredient("Dehulled Soybean Meal", "Dehulled Soybean Meal", false, null, false)),
                null,
                List.of()
        );

        FoodClassificationResult result = engine.classify(feedProduct);

        assertThat(result.category()).isEqualTo(FoodCategory.ANIMAL_FEED);
        assertThat(result.certainty()).isEqualTo(ClassificationCertainty.HIGH);
        assertThat(result.reasonCode()).isEqualTo(ClassificationReasonCode.EXPLICIT_ANIMAL_FEED_MARKER);
    }

    @Test
    @DisplayName("Should classify non-food item with 'not for human consumption' as NON_FOOD")
    void testNonFoodClassification() {
        NormalizedFoodData nonFood = new NormalizedFoodData(
                "Aromatic Bath Salts - For external use only, not for human consumption",
                null,
                null,
                List.of(new NormalizedIngredient("Epsom Salt", "Epsom Salt", false, null, false)),
                null,
                List.of()
        );

        FoodClassificationResult result = engine.classify(nonFood);

        assertThat(result.category()).isEqualTo(FoodCategory.NON_FOOD);
        assertThat(result.certainty()).isEqualTo(ClassificationCertainty.HIGH);
        assertThat(result.reasonCode()).isEqualTo(ClassificationReasonCode.EXPLICIT_NON_FOOD_MARKER);
        assertThat(result.warnings()).anyMatch(w -> w.contains("non-food"));
    }

    @Test
    @DisplayName("Should classify conflicting category evidence as UNKNOWN with CONFLICTING_EVIDENCE")
    void testConflictingEvidence() {
        NormalizedFoodData conflicting = new NormalizedFoodData(
                "Dog Food Biscuit treat - not for human consumption and laundry detergent",
                null,
                null,
                List.of(),
                null,
                List.of()
        );

        FoodClassificationResult result = engine.classify(conflicting);

        assertThat(result.category()).isEqualTo(FoodCategory.UNKNOWN);
        assertThat(result.certainty()).isEqualTo(ClassificationCertainty.LOW);
        assertThat(result.reasonCode()).isEqualTo(ClassificationReasonCode.CONFLICTING_EVIDENCE);
    }

    @Test
    @DisplayName("Should return UNKNOWN with INSUFFICIENT_INFORMATION when evidence is absent")
    void testInsufficientInformation() {
        NormalizedFoodData empty = new NormalizedFoodData(null, null, null, List.of(), null, List.of());

        FoodClassificationResult result = engine.classify(empty);

        assertThat(result.category()).isEqualTo(FoodCategory.UNKNOWN);
        assertThat(result.certainty()).isEqualTo(ClassificationCertainty.LOW);
        assertThat(result.reasonCode()).isEqualTo(ClassificationReasonCode.INSUFFICIENT_INFORMATION);
        assertThat(result.reason()).contains("insufficient");
    }

    @Test
    @DisplayName("Verify that FoodCategory enum does NOT include any 'WASTE_FOOD' category")
    void testNoWasteFoodCategory() {
        for (FoodCategory cat : FoodCategory.values()) {
            assertThat(cat.name()).isNotEqualTo("WASTE_FOOD");
            assertThat(cat.name()).isNotEqualTo("FAKE_FOOD");
            assertThat(cat.name()).isNotEqualTo("ILLEGAL_FOOD");
        }
    }
}
