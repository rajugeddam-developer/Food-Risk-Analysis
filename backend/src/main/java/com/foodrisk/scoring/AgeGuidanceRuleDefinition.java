package com.foodrisk.scoring;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * Data definition loaded from age-guidance.json representing an authoritative demographic rule.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record AgeGuidanceRuleDefinition(
        String ageGroup,
        String triggerType,
        String nutrient,
        Double threshold,
        String unit,
        List<String> keywords,
        String attentionLevel,
        String reason,
        String sourceId,
        boolean evidenceAvailable
) {}
