package com.foodrisk.scoring;

/**
 * An individual explainable contribution to the overall Food Awareness Score.
 */
public record ScoreImpact(
        String factor,
        int impact,
        String reason,
        String source
) {}
