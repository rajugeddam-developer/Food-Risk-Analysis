package com.foodrisk.risk;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Result of comprehensive ingredient and additive risk evaluation for an analysis session.
 */
public record IngredientRiskAnalysisResult(
        UUID sessionId,
        IngredientRiskSummary summary,
        List<IngredientRiskItem> items,
        Instant evaluatedAt
) {}
