package com.foodrisk.ocr;

import com.foodrisk.dto.OcrLabelResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OcrExtractionValidatorTest {

    private OcrExtractionValidator validator;

    @BeforeEach
    void setUp() {
        validator = new OcrExtractionValidator(new OcrQualityThresholds());
    }

    @Test
    @DisplayName("Should accept valid ingredients statement with delimiters and INS codes")
    void testValidIngredientsAccepted() {
        String text = "INGREDIENTS: Refined Wheat Flour, Sugar, Edible Vegetable Oil (Palm), Invert Sugar, Salt, Emulsifier (INS 322), Raising Agent (INS 500ii).";
        OcrExtractionValidator.ExtractionValidation val = validator.validateIngredients(text, 88.5f);

        assertThat(val.isValid()).isTrue();
        assertThat(val.issueReason()).isNull();
    }

    @Test
    @DisplayName("Should reject single keyword alone without structure (e.g. just 'ingredients')")
    void testSingleKeywordAloneRejected() {
        String text = "Ingredients";
        OcrExtractionValidator.ExtractionValidation val = validator.validateIngredients(text, 95.0f);

        assertThat(val.isValid()).isFalse();
        assertThat(val.userGuidance()).contains("couldn't read the ingredients list clearly");
    }

    @Test
    @DisplayName("Should reject empty or blank ingredient text")
    void testEmptyIngredientsRejected() {
        OcrExtractionValidator.ExtractionValidation val = validator.validateIngredients("   ", 0.0f);

        assertThat(val.isValid()).isFalse();
        assertThat(val.issueReason()).isEqualTo("EMPTY_INGREDIENTS");
        assertThat(val.userGuidance()).contains("couldn't read the ingredients list clearly");
    }

    @Test
    @DisplayName("Should reject brief / insufficient ingredient text")
    void testBriefIngredientsRejected() {
        OcrExtractionValidator.ExtractionValidation val = validator.validateIngredients("Sugar", 90.0f);

        assertThat(val.isValid()).isFalse();
        assertThat(val.issueReason()).isEqualTo("INSUFFICIENT_INGREDIENTS_TEXT");
    }

    @Test
    @DisplayName("Should reject garbage OCR text with low readable ratio")
    void testGarbageTextRejected() {
        String garbage = "###$$$%%%%@@@@!!!!||||\\\\////????^^^^";
        OcrExtractionValidator.ExtractionValidation val = validator.validateIngredients(garbage, 75.0f);

        assertThat(val.isValid()).isFalse();
        assertThat(val.issueReason()).isEqualTo("GARBAGE_OCR_TEXT");
    }

    @Test
    @DisplayName("Should reject OCR text with very low confidence")
    void testLowConfidenceRejected() {
        String text = "INGREDIENTS: Sugar, Salt, Water, Flavor";
        OcrExtractionValidator.ExtractionValidation val = validator.validateIngredients(text, 18.0f);

        assertThat(val.isValid()).isFalse();
        assertThat(val.issueReason()).isEqualTo("LOW_CONFIDENCE");
    }

    @Test
    @DisplayName("Should reject non-food text lacking ingredient structure or keywords")
    void testNonFoodTextRejected() {
        String text = "BEST BEFORE SIX MONTHS FROM PACKAGING DATE. KEEP IN COOL PLACE.";
        OcrExtractionValidator.ExtractionValidation val = validator.validateIngredients(text, 85.0f);

        assertThat(val.isValid()).isFalse();
        assertThat(val.issueReason()).isEqualTo("NO_INGREDIENTS_STRUCTURE");
    }

    @Test
    @DisplayName("Should accept valid nutrition table with energy, protein, fats, and units")
    void testValidNutritionAccepted() {
        String table = """
                NUTRITION INFORMATION (Per 100g):
                Energy: 480 kcal
                Protein: 7.2 g
                Carbohydrate: 65.0 g
                Total Sugars: 24.5 g
                Total Fat: 21.0 g
                Sodium: 340 mg
                """;
        OcrExtractionValidator.ExtractionValidation val = validator.validateNutrition(table, 91.0f);

        assertThat(val.isValid()).isTrue();
    }

    @Test
    @DisplayName("Should reject single keyword alone without numeric values (e.g. just 'protein')")
    void testSingleNutritionKeywordAloneRejected() {
        String table = "High Protein Snack Brand";
        OcrExtractionValidator.ExtractionValidation val = validator.validateNutrition(table, 92.0f);

        assertThat(val.isValid()).isFalse();
        assertThat(val.issueReason()).isEqualTo("NO_NUTRITION_STRUCTURE");
    }

    @Test
    @DisplayName("Should reject nutrition text missing numbers and units")
    void testIncompleteNutritionRejected() {
        String table = "Company Name: ABC Foods Ltd. Net Wt: Two Hundred. Price: Fifty";
        OcrExtractionValidator.ExtractionValidation val = validator.validateNutrition(table, 85.0f);

        assertThat(val.isValid()).isFalse();
        assertThat(val.issueReason()).isEqualTo("NO_NUTRITION_STRUCTURE");
    }

    @Test
    @DisplayName("Partial OCR: Should allow pipeline to proceed when ingredients valid and nutrition unclear")
    void testPartialIngredientsValidNutritionUnclear() {
        OcrLabelResult ingResult = new OcrLabelResult("INGREDIENTS: Wheat, Sugar, Salt, INS 330", 85.0f, 100, true);
        OcrLabelResult nutResult = new OcrLabelResult("Unreadable blur ###", 20.0f, 120, true);

        OcrExtractionValidator.OverallValidationResult result = validator.evaluateSufficiency(ingResult, true, nutResult, true);
        assertThat(result.canProceed()).isTrue();
        assertThat(result.ingredientsUsable()).isTrue();
        assertThat(result.nutritionUsable()).isFalse();
    }

    @Test
    @DisplayName("Partial OCR: Should allow pipeline to proceed when nutrition valid and ingredients unclear")
    void testPartialNutritionValidIngredientsUnclear() {
        OcrLabelResult ingResult = new OcrLabelResult("Unreadable ###", 15.0f, 100, true);
        OcrLabelResult nutResult = new OcrLabelResult("Energy: 400 kcal, Protein: 5g, Total Fat: 12g, Sodium: 200mg", 88.0f, 120, true);

        OcrExtractionValidator.OverallValidationResult result = validator.evaluateSufficiency(ingResult, true, nutResult, true);
        assertThat(result.canProceed()).isTrue();
        assertThat(result.ingredientsUsable()).isFalse();
        assertThat(result.nutritionUsable()).isTrue();
    }

    @Test
    @DisplayName("Overall validation: Should reject when both labels are unclear and prompt retake for both")
    void testBothLabelsUnclear() {
        OcrLabelResult ingResult = new OcrLabelResult("Unreadable blur ###", 15.0f, 100, true);
        OcrLabelResult nutResult = new OcrLabelResult("Unreadable blur ###", 20.0f, 120, true);

        OcrExtractionValidator.OverallValidationResult result = validator.evaluateSufficiency(ingResult, true, nutResult, true);
        assertThat(result.canProceed()).isFalse();
        assertThat(result.ingredientsUsable()).isFalse();
        assertThat(result.nutritionUsable()).isFalse();
        assertThat(result.userGuidance()).contains("The label information is not clear enough to analyze reliably. Please retake the ingredients and nutrition photos.");
    }
}
