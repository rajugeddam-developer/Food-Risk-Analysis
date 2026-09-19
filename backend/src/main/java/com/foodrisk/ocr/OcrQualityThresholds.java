package com.foodrisk.ocr;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Centralized, configurable thresholds for OCR text extraction and semantic validation.
 *
 * Threshold Taxonomy:
 * 1. Security & Technical Limits:
 *    Maximum payload character size to prevent excessive memory allocation or regex DoS.
 * 2. Engineering Heuristics:
 *    Minimum text volume, readable character ratios, and baseline confidence floors.
 * 3. Empirically Benchmarked Thresholds:
 *    Multi-pass triggering criteria and quality score weighting tuned against food packaging samples.
 */
@Component
@ConfigurationProperties(prefix = "ocr.quality.text")
public class OcrQualityThresholds {

    // =========================================================================
    // Category 1: Security & Technical Limits
    // =========================================================================
    /** Maximum allowed raw OCR text length to prevent memory saturation. Security limit. */
    public static final int DEFAULT_MAX_TEXT_LENGTH = 10_000;

    // =========================================================================
    // Category 2: Engineering Heuristics
    // =========================================================================
    /** Minimum character count for a meaningful ingredient statement. Engineering heuristic. */
    public static final int DEFAULT_MIN_INGREDIENT_LENGTH = 15;

    /** Minimum character count for a meaningful nutrition facts panel. Engineering heuristic. */
    public static final int DEFAULT_MIN_NUTRITION_LENGTH = 10;

    /**
     * Minimum ratio of readable (alphanumeric + standard punctuation) characters.
     * Engineering heuristic: values below 0.55 indicate camera noise, binary junk, or raster distortion.
     */
    public static final double DEFAULT_MIN_READABLE_RATIO = 0.55;

    /**
     * Minimum word-level OCR confidence floor (0 - 100).
     * Engineering heuristic: below 30% indicates severe character confusion.
     */
    public static final float DEFAULT_MIN_CONFIDENCE = 30.0f;

    // =========================================================================
    // Category 3: Empirically Benchmarked Settings
    // =========================================================================
    /**
     * Confidence threshold below which Pass 2 (Otsu thresholding) is triggered.
     * Empirically benchmarked: when Pass 1 confidence is between 30% and 55%,
     * thresholding frequently recovers missing characters on glare or colored backgrounds.
     */
    public static final float DEFAULT_PASS2_TRIGGER_CONFIDENCE = 55.0f;

    /**
     * Character count below which Pass 2 is triggered.
     * Empirically benchmarked: typical food labels have >= 35 characters.
     */
    public static final int DEFAULT_PASS2_TRIGGER_MIN_CHARS = 35;

    private int maxTextLength = DEFAULT_MAX_TEXT_LENGTH;
    private int minIngredientLength = DEFAULT_MIN_INGREDIENT_LENGTH;
    private int minNutritionLength = DEFAULT_MIN_NUTRITION_LENGTH;
    private double minReadableRatio = DEFAULT_MIN_READABLE_RATIO;
    private float minConfidence = DEFAULT_MIN_CONFIDENCE;
    private float pass2TriggerConfidence = DEFAULT_PASS2_TRIGGER_CONFIDENCE;
    private int pass2TriggerMinChars = DEFAULT_PASS2_TRIGGER_MIN_CHARS;

    public OcrQualityThresholds() {}

    public int getMaxTextLength() {
        return maxTextLength;
    }

    public void setMaxTextLength(int maxTextLength) {
        this.maxTextLength = maxTextLength;
    }

    public int getMinIngredientLength() {
        return minIngredientLength;
    }

    public void setMinIngredientLength(int minIngredientLength) {
        this.minIngredientLength = minIngredientLength;
    }

    public int getMinNutritionLength() {
        return minNutritionLength;
    }

    public void setMinNutritionLength(int minNutritionLength) {
        this.minNutritionLength = minNutritionLength;
    }

    public double getMinReadableRatio() {
        return minReadableRatio;
    }

    public void setMinReadableRatio(double minReadableRatio) {
        this.minReadableRatio = minReadableRatio;
    }

    public float getMinConfidence() {
        return minConfidence;
    }

    public void setMinConfidence(float minConfidence) {
        this.minConfidence = minConfidence;
    }

    public float getPass2TriggerConfidence() {
        return pass2TriggerConfidence;
    }

    public void setPass2TriggerConfidence(float pass2TriggerConfidence) {
        this.pass2TriggerConfidence = pass2TriggerConfidence;
    }

    public int getPass2TriggerMinChars() {
        return pass2TriggerMinChars;
    }

    public void setPass2TriggerMinChars(int pass2TriggerMinChars) {
        this.pass2TriggerMinChars = pass2TriggerMinChars;
    }
}
