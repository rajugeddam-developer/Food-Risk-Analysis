package com.foodrisk.nutrition;

/**
 * Distinguishes the legal authority and scientific nature of a nutritional comparison benchmark.
 */
public enum ReferenceType {
    /** Enforceable legal regulation enacted by a statutory authority (e.g. FSSAI limit). */
    REGULATORY_LIMIT,

    /** Authoritative public health dietary guidance (e.g. WHO Healthy Diet guidelines). */
    DIETARY_GUIDELINE,

    /** Standard nutrition reference values or qualifying benchmark (e.g. Codex claims). */
    NUTRITION_REFERENCE,

    /** Internal project scoring heuristic when no explicit statutory value exists. */
    PROJECT_HEURISTIC,

    /** Configured per-100g product classification threshold (e.g. solid food cutoffs informed by front-of-pack criteria). */
    PRODUCT_CLASSIFICATION_THRESHOLD,

    /** Authoritative public health dietary recommendation for total daily dietary intake. */
    DIETARY_RECOMMENDATION,

    /** Algorithmic threshold used specifically for deterministic food awareness scoring. */
    SCORING_THRESHOLD,

    /** Established clinical or medical limit. */
    CLINICAL_LIMIT
}
