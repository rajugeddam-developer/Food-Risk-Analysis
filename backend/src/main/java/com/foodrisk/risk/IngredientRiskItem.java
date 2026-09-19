package com.foodrisk.risk;

import java.util.List;
import java.util.Map;

/**
 * Detailed evaluation item for a single ingredient or additive.
 *
 * Explicitly separates:
 * - Original OCR snippet from normalized canonical name.
 * - Regulatory status (PERMITTED/RESTRICTED/BANNED) from attention risk level.
 * - Authoritative source provenance and evidence substantiation level.
 * - Jurisdiction and jurisdictional regulatory variation.
 */
public record IngredientRiskItem(
        String originalIngredient,
        String normalizedName,
        IngredientRiskLevel riskLevel,
        List<String> reasons,
        List<String> sourceIds,
        EvidenceStatus evidenceStatus,
        String additiveCode,
        String functionalClass,
        RegulatoryStatus regulatoryStatus,
        String summary,
        String jurisdiction,
        Map<String, String> jurisdictionalStatus
) {
    public IngredientRiskItem(
            String originalIngredient,
            String normalizedName,
            IngredientRiskLevel riskLevel,
            List<String> reasons,
            List<String> sourceIds,
            EvidenceStatus evidenceStatus,
            String additiveCode,
            String functionalClass,
            RegulatoryStatus regulatoryStatus,
            String summary
    ) {
        this(originalIngredient, normalizedName, riskLevel, reasons, sourceIds, evidenceStatus, additiveCode, functionalClass, regulatoryStatus, summary, null, null);
    }

    public IngredientRiskItem(
            String originalIngredient,
            String normalizedName,
            IngredientRiskLevel riskLevel,
            List<String> reasons,
            List<String> sourceIds,
            EvidenceStatus evidenceStatus,
            String additiveCode,
            String functionalClass,
            RegulatoryStatus regulatoryStatus,
            String jurisdiction,
            Map<String, String> jurisdictionalStatus
    ) {
        this(originalIngredient, normalizedName, riskLevel, reasons, sourceIds, evidenceStatus, additiveCode, functionalClass, regulatoryStatus,
                deriveSummary(riskLevel, normalizedName, reasons), jurisdiction, jurisdictionalStatus);
    }

    public IngredientRiskItem(
            String originalIngredient,
            String normalizedName,
            IngredientRiskLevel riskLevel,
            List<String> reasons,
            List<String> sourceIds,
            EvidenceStatus evidenceStatus,
            String additiveCode,
            String functionalClass,
            RegulatoryStatus regulatoryStatus
    ) {
        this(originalIngredient, normalizedName, riskLevel, reasons, sourceIds, evidenceStatus, additiveCode, functionalClass, regulatoryStatus,
                deriveSummary(riskLevel, normalizedName, reasons), null, null);
    }

    private static String deriveSummary(IngredientRiskLevel level, String name, List<String> reasons) {
        if (reasons != null && !reasons.isEmpty() && reasons.get(0) != null && !reasons.get(0).isBlank()) {
            String first = reasons.get(0).trim();
            int dotIdx = first.indexOf('.');
            if (dotIdx > 0 && dotIdx < first.length() - 1) {
                return first.substring(0, dotIdx + 1);
            }
            return first;
        }
        if (level == IngredientRiskLevel.UNKNOWN) {
            return "Ingredient could not be verified against the configured knowledge base.";
        }
        if (level == IngredientRiskLevel.POSITIVE) {
            return "Recognized beneficial food ingredient with positive nutritional contribution.";
        }
        if (level == IngredientRiskLevel.NO_CONCERN || level == IngredientRiskLevel.NO_SPECIFIC_CONCERN) {
            return "No specific concern identified from the configured evidence base.";
        }
        return (name != null ? name : "Ingredient") + " evaluated under standard food safety guidance.";
    }
}
