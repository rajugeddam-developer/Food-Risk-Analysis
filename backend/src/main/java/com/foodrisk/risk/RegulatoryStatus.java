package com.foodrisk.risk;

/**
 * Statutory regulatory status established by authoritative food safety agencies (e.g. FSSAI, Codex, WHO).
 *
 * Strictly separated from IngredientRiskLevel:
 * - PERMITTED does not automatically imply NO_CONCERN.
 * - BANNED may only be used when an authoritative source explicitly enacts a prohibition.
 */
public enum RegulatoryStatus {
    PERMITTED,
    RESTRICTED,
    BANNED,
    PROHIBITED,
    UNKNOWN
}
