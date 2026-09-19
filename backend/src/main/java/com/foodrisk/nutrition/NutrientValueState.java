package com.foodrisk.nutrition;

/**
 * Explicit state of a nutrient value on the label, strictly ensuring missing values are not conflated with zero.
 */
public enum NutrientValueState {
    /** The package label explicitly declared a zero value (e.g. 0g, 0mg). */
    EXPLICIT_ZERO,

    /** A positive or measured numerical quantity was clearly declared. */
    DETECTED_VALUE,

    /** The package label did not declare or mention this nutrient. */
    NOT_DECLARED,

    /** The nutrient identity was uncertain or unparseable from available label evidence. */
    UNKNOWN,

    /** OCR or image quality resulted in ambiguous or uncertain numerical extraction. */
    OCR_UNCERTAIN
}
