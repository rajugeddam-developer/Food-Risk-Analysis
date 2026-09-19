package com.foodrisk.benchmark.model;

/**
 * Failure record capturing benchmark discrepancy, root cause, and consumer impact.
 */
public record FailureClassification(
        String benchmarkId,
        String category,
        String observedOutput,
        String expectedOutput,
        RootCause rootCause,
        String consumerImpact,
        Priority priority
) {
    public enum RootCause {
        OCR_ERROR,
        PARSING_ERROR,
        NORMALIZATION_ERROR,
        KNOWLEDGE_BASE_GAP,
        NUTRITION_MAPPING_ERROR,
        UNIT_ERROR,
        SERVING_BASIS_ERROR,
        REGULATORY_DATA_GAP,
        AMBIGUOUS_LABEL,
        INSUFFICIENT_INPUT,
        FRONTEND_DISPLAY_ERROR,
        OTHER
    }

    public enum Priority {
        P0, // Potentially dangerous systemic error or major false reassurance
        P1, // Frequent / high-impact accuracy problem
        P2, // Moderate issue affecting meaningful usability
        P3  // Minor or cosmetic issue
    }
}
