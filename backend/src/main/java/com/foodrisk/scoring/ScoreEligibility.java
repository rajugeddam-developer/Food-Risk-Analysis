package com.foodrisk.scoring;

/**
 * Declares the scientific eligibility of a food product for overall awareness scoring.
 *
 * Enforces scientific honesty:
 * - RATED: Full scoring synthesized from both ingredient and nutrition facts.
 * - PARTIALLY_RATED: Partial scoring derived when one evidence stream is incomplete but critical hazards are detected.
 * - UNRATED: Unrated due to missing nutrition facts without critical additive hazards. Score is null.
 */
public enum ScoreEligibility {
    /** Full scoring synthesized from both ingredient and nutrition facts. */
    RATED,

    /** Partial scoring derived when one evidence stream is incomplete but critical hazards are detected. */
    PARTIALLY_RATED,

    /** Unrated due to missing nutrition facts without critical additive hazards. Score is null. */
    UNRATED
}
