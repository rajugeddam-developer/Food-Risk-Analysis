package com.foodrisk.risk;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

/**
 * Data model for a verified ingredient rule loaded from ingredient-rules.json.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record IngredientRuleDefinition(
        String canonicalName,
        List<String> aliases,
        RegulatoryStatus regulatoryStatus,
        IngredientRiskLevel riskLevel,
        List<String> sourceIds,
        List<String> reasons
) {}
