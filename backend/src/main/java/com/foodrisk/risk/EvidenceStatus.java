package com.foodrisk.risk;

/**
 * Declares the degree to which an ingredient or additive evaluation is substantiated
 * by verified regulatory or public health evidence.
 */
public enum EvidenceStatus {
    SUPPORTED,
    PARTIALLY_SUPPORTED,
    STRONG,
    MODERATE,
    LIMITED,
    MIXED,
    INSUFFICIENT,
    UNKNOWN
}
