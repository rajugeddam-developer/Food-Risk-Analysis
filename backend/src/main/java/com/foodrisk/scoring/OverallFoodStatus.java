package com.foodrisk.scoring;

/**
 * Consumer-facing Food Awareness classification category.
 *
 * Strictly awareness-oriented; does NOT make medical or safety claims.
 */
public enum OverallFoodStatus {
    /** 80–100 score: Favorable nutritional and clean additive profile. */
    GOOD_CHOICE,

    /** 50–79 score: Moderate processing or elevated sugar/sodium/fat requiring portion awareness. */
    NEEDS_ATTENTION,

    /** 0–49 score: High attention required due to multiple critical nutrient thresholds or flagged additives. */
    HIGH_ATTENTION,

    /** Packaged formulation verified as pet food, animal feed, or non-food item. */
    NOT_INTENDED_FOR_HUMAN_CONSUMPTION,

    /** Insufficient packaging label evidence to produce a reliable assessment. */
    INSUFFICIENT_DATA
}
