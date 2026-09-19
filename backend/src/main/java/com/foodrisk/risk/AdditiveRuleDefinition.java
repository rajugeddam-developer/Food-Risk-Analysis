package com.foodrisk.risk;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;
import java.util.Map;

/**
 * Data model for a verified food additive entry loaded from additives.json.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record AdditiveRuleDefinition(
        String code,
        String name,
        String functionalClass,
        RegulatoryStatus regulatoryStatus,
        IngredientRiskLevel riskLevel,
        List<String> sourceIds,
        List<String> reasons,
        List<String> synonyms,
        String jurisdiction,
        Map<String, String> jurisdictionalStatus
) {
    public AdditiveRuleDefinition(
            String code,
            String name,
            String functionalClass,
            RegulatoryStatus regulatoryStatus,
            IngredientRiskLevel riskLevel,
            List<String> sourceIds,
            List<String> reasons,
            List<String> synonyms
    ) {
        this(code, name, functionalClass, regulatoryStatus, riskLevel, sourceIds, reasons, synonyms, null, null);
    }
}
