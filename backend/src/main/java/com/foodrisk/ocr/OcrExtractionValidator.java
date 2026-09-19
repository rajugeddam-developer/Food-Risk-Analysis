package com.foodrisk.ocr;

import com.foodrisk.dto.OcrLabelResult;
import com.foodrisk.exception.ErrorCategory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Validates raw OCR extracted text to ensure it is structurally and semantically
 * sufficient for downstream analysis before invoking AI normalization or risk scoring.
 *
 * Enforces:
 * - Minimum text volume and readable character ratios (rejects noise, line artifacts, and binary garbage).
 * - Structural integrity: generic keyword presence alone (e.g. the single word "protein" or "ingredients")
 *   is NOT accepted as proof of valid OCR. The system validates delimiters, parenthetical declarations,
 *   INS/E-numbers, numerical measurements, and unit associations.
 * - Sufficiency model for nutrition: recognizes partial but useful nutrition information
 *   without requiring every possible field to be present.
 */
@Component
public class OcrExtractionValidator {

    private static final Logger log = LoggerFactory.getLogger(OcrExtractionValidator.class);

    private final OcrQualityThresholds thresholds;

    // Pattern matching numbers followed by units (e.g. "15g", "250 kcal", "40mg", "12 %")
    private static final Pattern VALUE_WITH_UNIT_PATTERN = Pattern.compile(
            "\\b\\d+(?:\\.\\d+)?\\s*(?:g|mg|mcg|µg|kcal|kj|%|ml)\\b",
            Pattern.CASE_INSENSITIVE
    );

    // Pattern matching INS/E additive codes (e.g. "INS 330", "E621", "INS 150d")
    private static final Pattern INS_PATTERN = Pattern.compile(
            "\\b(?:ins|e)\\s*\\d{3,4}[a-z]?\\b",
            Pattern.CASE_INSENSITIVE
    );

    private static final List<String> COMMON_INGREDIENT_KEYWORDS = List.of(
            "ingredient", "contains", "composition", "water", "sugar", "salt", "oil",
            "flour", "milk", "syrup", "extract", "acid", "starch", "cocoa", "preservative",
            "emulsifier", "flavour", "flavor", "color", "colour", "spice", "oats", "wheat"
    );

    private static final List<String> COMMON_NUTRITION_KEYWORDS = List.of(
            "energy", "calorie", "kcal", "protein", "carbohydrate", "carb", "total fat",
            "saturated fat", "trans fat", "fat", "sugar", "sodium", "salt", "serving",
            "per 100", "per 100g", "nutritional", "nutrition facts", "value per"
    );

    @Autowired
    public OcrExtractionValidator(OcrQualityThresholds thresholds) {
        this.thresholds = thresholds != null ? thresholds : new OcrQualityThresholds();
    }

    public OcrExtractionValidator() {
        this(new OcrQualityThresholds());
    }

    public record ExtractionValidation(
            boolean isValid,
            String issueReason,
            String userGuidance,
            ErrorCategory category
    ) {
        public static ExtractionValidation valid() {
            return new ExtractionValidation(true, null, null, null);
        }

        public static ExtractionValidation invalid(String issueReason, String userGuidance, ErrorCategory category) {
            return new ExtractionValidation(false, issueReason, userGuidance, category != null ? category : ErrorCategory.OCR_INSUFFICIENT);
        }
    }

    /**
     * Validates whether extracted ingredient text has sufficient structure and substance.
     */
    public ExtractionValidation validateIngredients(String text, Float confidence) {
        if (text == null || text.isBlank()) {
            return ExtractionValidation.invalid(
                    "EMPTY_INGREDIENTS",
                    "We couldn't read the ingredients list clearly. Please capture the ingredients section closer and in focus.",
                    ErrorCategory.OCR_INSUFFICIENT
            );
        }

        String cleaned = text.trim();
        if (cleaned.length() < thresholds.getMinIngredientLength()) {
            return ExtractionValidation.invalid(
                    "INSUFFICIENT_INGREDIENTS_TEXT",
                    "We couldn't read the ingredients list clearly. Please capture the ingredients section closer and in focus.",
                    ErrorCategory.OCR_INSUFFICIENT
            );
        }

        // Check readable ratio
        double ratio = calculateReadableRatio(cleaned);
        if (ratio < thresholds.getMinReadableRatio()) {
            log.info("Ingredients rejected: readable ratio {} < {}", ratio, thresholds.getMinReadableRatio());
            return ExtractionValidation.invalid(
                    "GARBAGE_OCR_TEXT",
                    "We couldn't read the ingredients list clearly. Please capture the ingredients section closer and in focus.",
                    ErrorCategory.OCR_LOW_CONFIDENCE
            );
        }

        // Check confidence if available
        if (confidence != null && confidence > 0.0f && confidence < thresholds.getMinConfidence()) {
            log.info("Ingredients rejected: OCR confidence {} < {}", confidence, thresholds.getMinConfidence());
            return ExtractionValidation.invalid(
                    "LOW_CONFIDENCE",
                    "We couldn't read the ingredients list clearly. Please capture the ingredients section closer and in focus.",
                    ErrorCategory.OCR_LOW_CONFIDENCE
            );
        }

        // Structural validation: do not rely solely on keyword presence
        // Verify multiple items, delimiters, INS codes, or parenthetical declarations
        String lower = cleaned.toLowerCase(Locale.ROOT);
        boolean hasKeyword = COMMON_INGREDIENT_KEYWORDS.stream().anyMatch(lower::contains);
        boolean hasIns = INS_PATTERN.matcher(cleaned).find();
        long delimiterCount = cleaned.chars().filter(c -> c == ',' || c == ';' || c == '(' || c == ')' || c == ':').count();

        // Must have substance: either multiple delimiters, an INS code, or a keyword with at least one delimiter/colon
        boolean hasValidStructure = hasIns || delimiterCount >= 2 || (hasKeyword && delimiterCount >= 1);

        if (!hasValidStructure) {
            log.info("Ingredients text lacked domain structure: {}", cleaned.substring(0, Math.min(60, cleaned.length())));
            return ExtractionValidation.invalid(
                    "NO_INGREDIENTS_STRUCTURE",
                    "We couldn't read the ingredients list clearly. Please capture the ingredients section closer and in focus.",
                    ErrorCategory.OCR_INSUFFICIENT
            );
        }

        return ExtractionValidation.valid();
    }

    /**
     * Validates whether extracted nutrition table text has sufficient structure and values.
     * Uses a sufficiency model: does not require every nutrient to be present.
     */
    public ExtractionValidation validateNutrition(String text, Float confidence) {
        if (text == null || text.isBlank()) {
            return ExtractionValidation.invalid(
                    "EMPTY_NUTRITION",
                    "We couldn't read the nutrition information clearly. Please capture the nutrition table closer and in focus.",
                    ErrorCategory.OCR_INSUFFICIENT
            );
        }

        String cleaned = text.trim();
        if (cleaned.length() < thresholds.getMinNutritionLength()) {
            return ExtractionValidation.invalid(
                    "INSUFFICIENT_NUTRITION_TEXT",
                    "We couldn't read the nutrition information clearly. Please capture the nutrition table closer and in focus.",
                    ErrorCategory.OCR_INSUFFICIENT
            );
        }

        // Check readable ratio
        double ratio = calculateReadableRatio(cleaned);
        if (ratio < thresholds.getMinReadableRatio()) {
            log.info("Nutrition rejected: readable ratio {} < {}", ratio, thresholds.getMinReadableRatio());
            return ExtractionValidation.invalid(
                    "GARBAGE_OCR_TEXT",
                    "We couldn't read the nutrition information clearly. Please capture the nutrition table closer and in focus.",
                    ErrorCategory.OCR_LOW_CONFIDENCE
            );
        }

        // Check confidence if available
        if (confidence != null && confidence > 0.0f && confidence < thresholds.getMinConfidence()) {
            log.info("Nutrition rejected: OCR confidence {} < {}", confidence, thresholds.getMinConfidence());
            return ExtractionValidation.invalid(
                    "LOW_CONFIDENCE",
                    "We couldn't read the nutrition information clearly. Please capture the nutrition table closer and in focus.",
                    ErrorCategory.OCR_LOW_CONFIDENCE
            );
        }

        // Structural validation: do not rely solely on keyword presence (e.g. just the word "protein")
        // Must contain recognized nutrition metric AND numerical measurement tokens or units
        String lower = cleaned.toLowerCase(Locale.ROOT);
        long keywordMatches = COMMON_NUTRITION_KEYWORDS.stream().filter(lower::contains).count();
        boolean hasValueWithUnit = VALUE_WITH_UNIT_PATTERN.matcher(cleaned).find();
        long digitCount = cleaned.chars().filter(Character::isDigit).count();

        // Sufficiency: at least 1 nutrition concept and numeric measurements (either explicit unit or >= 2 digits)
        boolean hasValidNutritionStructure = keywordMatches >= 1 && (hasValueWithUnit || digitCount >= 2);

        if (!hasValidNutritionStructure) {
            log.info("Nutrition table lacked required fields (keywords: {}, hasValueWithUnit: {}, digits: {})",
                    keywordMatches, hasValueWithUnit, digitCount);
            return ExtractionValidation.invalid(
                    "NO_NUTRITION_STRUCTURE",
                    "We couldn't read the nutrition information clearly. Please capture the nutrition table closer and in focus.",
                    ErrorCategory.OCR_INSUFFICIENT
            );
        }

        return ExtractionValidation.valid();
    }

    /**
     * Evaluates whether partial or full extraction is sufficient to continue the pipeline.
     */
    public record OverallValidationResult(
            boolean canProceed,
            boolean ingredientsUsable,
            boolean nutritionUsable,
            String userGuidance,
            ErrorCategory category
    ) {}

    public OverallValidationResult evaluateSufficiency(
            OcrLabelResult ingredientResult,
            boolean ingredientProvided,
            OcrLabelResult nutritionResult,
            boolean nutritionProvided
    ) {
        boolean ingUsable = false;
        ExtractionValidation ingVal = null;
        if (ingredientProvided && ingredientResult != null && ingredientResult.present()) {
            ingVal = validateIngredients(ingredientResult.rawText(), ingredientResult.confidence());
            ingUsable = ingVal.isValid();
        }

        boolean nutUsable = false;
        ExtractionValidation nutVal = null;
        if (nutritionProvided && nutritionResult != null && nutritionResult.present()) {
            nutVal = validateNutrition(nutritionResult.rawText(), nutritionResult.confidence());
            nutUsable = nutVal.isValid();
        }

        // Case 1: At least one label is usable -> pipeline can proceed!
        if (ingUsable || nutUsable) {
            return new OverallValidationResult(true, ingUsable, nutUsable, null, null);
        }

        // Case 2: Neither label is usable -> abort with actionable retake guidance
        if (ingredientProvided && nutritionProvided) {
            return new OverallValidationResult(
                    false, false, false,
                    "The label information is not clear enough to analyze reliably. Please retake the ingredients and nutrition photos.",
                    ErrorCategory.OCR_INSUFFICIENT
            );
        } else if (ingredientProvided) {
            return new OverallValidationResult(
                    false, false, false,
                    ingVal != null ? ingVal.userGuidance() : "We couldn't read the ingredients list clearly. Please capture the ingredients section closer and in focus.",
                    ingVal != null ? ingVal.category() : ErrorCategory.OCR_INSUFFICIENT
            );
        } else if (nutritionProvided) {
            return new OverallValidationResult(
                    false, false, false,
                    nutVal != null ? nutVal.userGuidance() : "We couldn't read the nutrition information clearly. Please capture the nutrition table closer and in focus.",
                    nutVal != null ? nutVal.category() : ErrorCategory.OCR_INSUFFICIENT
            );
        }

        return new OverallValidationResult(
                false, false, false,
                "At least one packaging label photo must be provided.",
                ErrorCategory.INVALID_IMAGE
        );
    }

    private double calculateReadableRatio(String text) {
        if (text == null || text.isEmpty()) {
            return 0.0;
        }
        int readableCount = 0;
        int totalNonWhitespace = 0;

        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (Character.isWhitespace(c)) {
                continue;
            }
            totalNonWhitespace++;
            if (Character.isLetterOrDigit(c) || isStandardPunctuation(c)) {
                readableCount++;
            }
        }

        if (totalNonWhitespace == 0) {
            return 0.0;
        }
        return (double) readableCount / totalNonWhitespace;
    }

    private boolean isStandardPunctuation(char c) {
        return c == ',' || c == '.' || c == ';' || c == ':' || c == '(' || c == ')'
                || c == '%' || c == '-' || c == '/' || c == '[' || c == ']' || c == '\''
                || c == '"' || c == '&' || c == '+';
    }
}
