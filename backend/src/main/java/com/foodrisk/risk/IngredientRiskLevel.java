package com.foodrisk.risk;

/**
 * Contextual attention level for individual ingredients or additives.
 *
 * Avoids sensationalist terminology (POISON, TOXIC, WORST) in favor of factual,
 * evidence-based attention gradations.
 */
public enum IngredientRiskLevel {
    NO_CONCERN,
    NO_SPECIFIC_CONCERN,
    POSITIVE,
    LOW_ATTENTION,
    MODERATE_ATTENTION,
    HIGH_ATTENTION,
    UNKNOWN
}
