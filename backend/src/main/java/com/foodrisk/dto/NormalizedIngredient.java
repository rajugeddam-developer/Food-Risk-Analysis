package com.foodrisk.dto;

/**
 * Normalized ingredient entry extracted from food packaging OCR text.
 *
 * @param name normalized canonical ingredient name (e.g. "Palm Oil")
 * @param rawText original raw OCR snippet for traceability (e.g. "Pa1m Oi1")
 * @param isAdditive true if recognized as a food additive or preservative
 * @param additiveCode INS or E-number code if supported by evidence (e.g. "INS 330", "E330")
 * @param uncertain true if spelling correction or identity resolution had ambiguity
 */
public record NormalizedIngredient(
        String name,
        String rawText,
        boolean isAdditive,
        String additiveCode,
        boolean uncertain
) {}
